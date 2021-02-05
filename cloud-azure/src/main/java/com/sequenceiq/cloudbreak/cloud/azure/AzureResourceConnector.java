package com.sequenceiq.cloudbreak.cloud.azure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;
import com.microsoft.azure.CloudException;
import com.microsoft.azure.management.resources.Deployment;
import com.sequenceiq.cloudbreak.cloud.azure.client.AzureClient;
import com.sequenceiq.cloudbreak.cloud.azure.connector.resource.AzureComputeResourceService;
import com.sequenceiq.cloudbreak.cloud.azure.connector.resource.AzureDatabaseResourceService;
import com.sequenceiq.cloudbreak.cloud.azure.upscale.AzureUpscaleService;
import com.sequenceiq.cloudbreak.cloud.azure.view.AzureCredentialView;
import com.sequenceiq.cloudbreak.cloud.azure.view.AzureStackView;
import com.sequenceiq.cloudbreak.cloud.context.AuthenticatedContext;
import com.sequenceiq.cloudbreak.cloud.context.CloudContext;
import com.sequenceiq.cloudbreak.cloud.exception.CloudConnectorException;
import com.sequenceiq.cloudbreak.cloud.model.CloudInstance;
import com.sequenceiq.cloudbreak.cloud.model.CloudResource;
import com.sequenceiq.cloudbreak.cloud.model.CloudResource.Builder;
import com.sequenceiq.cloudbreak.cloud.model.CloudResourceStatus;
import com.sequenceiq.cloudbreak.cloud.model.CloudStack;
import com.sequenceiq.cloudbreak.cloud.model.DatabaseStack;
import com.sequenceiq.cloudbreak.cloud.model.ExternalDatabaseStatus;
import com.sequenceiq.cloudbreak.cloud.model.ResourceStatus;
import com.sequenceiq.cloudbreak.cloud.model.TlsInfo;
import com.sequenceiq.cloudbreak.cloud.notification.PersistenceNotifier;
import com.sequenceiq.cloudbreak.cloud.template.AbstractResourceConnector;
import com.sequenceiq.cloudbreak.service.Retry.ActionFailedException;
import com.sequenceiq.cloudbreak.util.NullUtil;
import com.sequenceiq.common.api.type.AdjustmentType;
import com.sequenceiq.common.api.type.ResourceType;

@Service
public class AzureResourceConnector extends AbstractResourceConnector {

    private static final Logger LOGGER = LoggerFactory.getLogger(AzureResourceConnector.class);

    @Inject
    private AzureTemplateBuilder azureTemplateBuilder;

    @Inject
    private AzureUtils azureUtils;

    @Inject
    private AzureStorage azureStorage;

    @Inject
    private AzureResourceGroupMetadataProvider azureResourceGroupMetadataProvider;

    @Inject
    private AzureComputeResourceService azureComputeResourceService;

    @Inject
    private AzureDatabaseResourceService azureDatabaseResourceService;

    @Inject
    private AzureUpscaleService azureUpscaleService;

    @Inject
    private AzureStackViewProvider azureStackViewProvider;

    @Inject
    private AzureCloudResourceService azureCloudResourceService;

    @Inject
    private AzureTerminationHelperService azureTerminationHelperService;

    @Override
    public List<CloudResourceStatus> launch(AuthenticatedContext ac, CloudStack stack, PersistenceNotifier notifier,
            AdjustmentType adjustmentType, Long threshold) {
        AzureCredentialView azureCredentialView = new AzureCredentialView(ac.getCloudCredential());
        CloudContext cloudContext = ac.getCloudContext();
        String stackName = azureUtils.getStackName(cloudContext);
        String resourceGroupName = azureResourceGroupMetadataProvider.getResourceGroupName(cloudContext, stack);
        AzureClient client = ac.getParameter(AzureClient.class);

        AzureImage image = azureStorage.getCustomImage(client, ac, stack);
        if (!image.getAlreadyExists()) {
            LOGGER.debug("Image {} has been created now, so we need to persist it", image.getName());
            CloudResource imageCloudResource = azureCloudResourceService.buildCloudResource(image.getName(), image.getId(), ResourceType.AZURE_MANAGED_IMAGE);
            azureCloudResourceService.saveCloudResources(notifier, ac.getCloudContext(), List.of(imageCloudResource));
        }
        String customImageId = image.getId();
        AzureStackView azureStackView = azureStackViewProvider.getAzureStack(azureCredentialView, stack, client, ac);
        String template = azureTemplateBuilder.build(stackName, customImageId, azureCredentialView, azureStackView,
                cloudContext, stack, AzureInstanceTemplateOperation.PROVISION);

        String parameters = azureTemplateBuilder.buildParameters(ac.getCloudCredential(), stack.getNetwork(), stack.getImage());

        boolean resourcesPersisted = false;
        try {
            List<CloudResource> instances;
            if (!client.templateDeploymentExists(resourceGroupName, stackName)) {
                Deployment templateDeployment = client.createTemplateDeployment(resourceGroupName, stackName, template, parameters);
                LOGGER.debug("Created template deployment for launch: {}", templateDeployment.exportTemplate().template());
                instances = persistCloudResources(ac, stack, notifier, cloudContext, stackName, resourceGroupName, templateDeployment);
            } else {
                Deployment templateDeployment = client.getTemplateDeployment(resourceGroupName, stackName);
                LOGGER.debug("Get template deployment for launch as it exists: {}", templateDeployment.exportTemplate().template());
                instances = persistCloudResources(ac, stack, notifier, cloudContext, stackName, resourceGroupName, templateDeployment);
            }
            resourcesPersisted = true;
            String networkName = azureUtils.getCustomNetworkId(stack.getNetwork());
            List<String> subnetNameList = azureUtils.getCustomSubnetIds(stack.getNetwork());
            List<CloudResource> networkResources = azureCloudResourceService.collectAndSaveNetworkAndSubnet(
                    resourceGroupName, stackName, notifier, cloudContext, subnetNameList, networkName, client);
            azureComputeResourceService.buildComputeResourcesForLaunch(ac, stack, adjustmentType, threshold, instances, networkResources);
        } catch (CloudException e) {
            throw azureUtils.convertToCloudConnectorException(e, "Stack provisioning");
        } catch (Exception e) {
            LOGGER.warn("Provisioning error:", e);
            throw new CloudConnectorException(String.format("Error in provisioning stack %s: %s", stackName, e.getMessage()));
        } finally {
            if (!resourcesPersisted) {
                Deployment templateDeployment = client.getTemplateDeployment(resourceGroupName, stackName);
                if (templateDeployment != null && templateDeployment.exportTemplate() != null) {
                    LOGGER.debug("Get template deployment to persist created resources: {}", templateDeployment.exportTemplate().template());
                    persistCloudResources(ac, stack, notifier, cloudContext, stackName, resourceGroupName, templateDeployment);
                }
            }
        }

        CloudResource cloudResource = new Builder()
                .type(ResourceType.ARM_TEMPLATE)
                .name(resourceGroupName)
                .build();
        List<CloudResourceStatus> resources = check(ac, Collections.singletonList(cloudResource));
        LOGGER.debug("Launched resources: {}", resources);
        return resources;
    }

    private List<CloudResource> persistCloudResources(
            AuthenticatedContext ac, CloudStack stack, PersistenceNotifier notifier, CloudContext cloudContext, String stackName,
            String resourceGroupName, Deployment templateDeployment) {
        List<CloudResource> allResourcesToPersist = new ArrayList<>();
        List<CloudResource> instances;
        try {
            List<CloudResource> templateResources = azureCloudResourceService.getDeploymentCloudResources(templateDeployment);
            LOGGER.debug("Template resources retrieved: {}", templateResources);
            allResourcesToPersist.addAll(templateResources);
            instances = azureCloudResourceService.getInstanceCloudResources(stackName, templateResources, stack.getGroups(), resourceGroupName);
            AzureClient azureClient = ac.getParameter(AzureClient.class);
            List<CloudResource> osDiskResources = azureCloudResourceService.getAttachedOsDiskResources(instances, resourceGroupName, azureClient);
            LOGGER.debug("OS disk resources retrieved: {}", osDiskResources);
            allResourcesToPersist.addAll(osDiskResources);
        } finally {
            azureCloudResourceService.deleteCloudResources(notifier, cloudContext, allResourcesToPersist);
            azureCloudResourceService.saveCloudResources(notifier, cloudContext, allResourcesToPersist);
            LOGGER.info("Resources persisted: {}", allResourcesToPersist);
        }
        return instances;
    }

    @Override
    public List<CloudResourceStatus> launchDatabaseServer(AuthenticatedContext authenticatedContext, DatabaseStack stack,
            PersistenceNotifier persistenceNotifier) {
        return azureDatabaseResourceService.buildDatabaseResourcesForLaunch(authenticatedContext, stack, persistenceNotifier);
    }

    @Override
    public ExternalDatabaseStatus getDatabaseServerStatus(AuthenticatedContext authenticatedContext, DatabaseStack stack) {
        return azureDatabaseResourceService.getDatabaseServerStatus(authenticatedContext, stack);
    }

    @Override
    public List<CloudResourceStatus> check(AuthenticatedContext authenticatedContext, List<CloudResource> resources) {
        List<CloudResourceStatus> result = new ArrayList<>();
        AzureClient client = authenticatedContext.getParameter(AzureClient.class);
        String stackName = azureUtils.getStackName(authenticatedContext.getCloudContext());

        for (CloudResource resource : resources) {
            ResourceType resourceType = resource.getType();
            if (resourceType == ResourceType.ARM_TEMPLATE) {
                LOGGER.debug("Checking Azure stack status of: {}", stackName);
                checkTemplateDeployment(result, client, stackName, resource);
            } else {
                if (!resourceType.name().startsWith("AZURE")) {
                    throw new CloudConnectorException(String.format("Invalid resource type: %s", resourceType));
                }
            }
        }
        return result;
    }

    private void checkTemplateDeployment(List<CloudResourceStatus> result, AzureClient client, String stackName, CloudResource resource) {
        try {
            String resourceGroupName = resource.getName();
            CloudResourceStatus templateResourceStatus;
            if (client.templateDeploymentExists(resourceGroupName, stackName)) {
                Deployment resourceGroupDeployment = client.getTemplateDeployment(resourceGroupName, stackName);
                templateResourceStatus = azureUtils.getTemplateStatus(resource, resourceGroupDeployment, client, stackName);
            } else {
                templateResourceStatus = new CloudResourceStatus(resource, ResourceStatus.DELETED);
            }
            result.add(templateResourceStatus);
        } catch (CloudException e) {
            if (e.response().code() == AzureConstants.NOT_FOUND) {
                result.add(new CloudResourceStatus(resource, ResourceStatus.DELETED));
            } else {
                throw new CloudConnectorException(e.body().message(), e);
            }
        } catch (RuntimeException e) {
            throw new CloudConnectorException(String.format("Invalid resource exception: %s", e.getMessage()), e);
        }
    }

    @Override
    public List<CloudResourceStatus> terminate(AuthenticatedContext ac, CloudStack stack, List<CloudResource> resources) {
        AzureClient client = ac.getParameter(AzureClient.class);
        String resourceGroupName = azureResourceGroupMetadataProvider.getResourceGroupName(ac.getCloudContext(), stack);
        ResourceGroupUsage resourceGroupUsage = azureResourceGroupMetadataProvider.getResourceGroupUsage(stack);

        if (resourceGroupUsage != ResourceGroupUsage.MULTIPLE) {

            String deploymentName = azureUtils.getStackName(ac.getCloudContext());
            List<CloudResource> transientResources = azureTerminationHelperService.handleTransientDeployment(client, resourceGroupName, deploymentName);
            NullUtil.doIfNotNull(transientResources, resources::addAll);
            azureTerminationHelperService.terminate(ac, stack, resources);
            return check(ac, Collections.emptyList());
        } else {
            try {
                try {
                    azureUtils.checkResourceGroupExistence(client, resourceGroupName);
                    client.deleteResourceGroup(resourceGroupName);
                } catch (ActionFailedException ignored) {
                    LOGGER.debug("Resource group not found with name: {}", resourceGroupName);
                }
            } catch (CloudException e) {
                if (e.response().code() != AzureConstants.NOT_FOUND) {
                    throw new CloudConnectorException(String.format("Could not delete resource group: %s", resourceGroupName), e);
                } else {
                    return check(ac, Collections.emptyList());
                }
            }
            return check(ac, resources);
        }
    }

    @Override
    public List<CloudResourceStatus> terminateDatabaseServer(AuthenticatedContext authenticatedContext, DatabaseStack stack,
            List<CloudResource> resources, PersistenceNotifier persistenceNotifier, boolean force) {

        azureDatabaseResourceService.handleTransientDeployment(authenticatedContext, resources);
        return azureDatabaseResourceService.terminateDatabaseServer(authenticatedContext, stack, resources, force, persistenceNotifier);
    }

    @Override
    public List<CloudResourceStatus> update(AuthenticatedContext authenticatedContext, CloudStack stack, List<CloudResource> resources) {
        return new ArrayList<>();
    }

    @Override
    public List<CloudResourceStatus> upscale(AuthenticatedContext ac, CloudStack stack, List<CloudResource> resources) {
        AzureClient client = ac.getParameter(AzureClient.class);
        AzureStackView azureStackView = azureStackViewProvider.getAzureStack(new AzureCredentialView(ac.getCloudCredential()), stack, client, ac);
        return azureUpscaleService.upscale(ac, stack, resources, azureStackView, client);
    }

    @Override
    public List<CloudResourceStatus> downscale(AuthenticatedContext ac, CloudStack stack, List<CloudResource> resources, List<CloudInstance> vms,
            List<CloudResource> resourcesToRemove) {
        return azureTerminationHelperService.downscale(ac, stack, vms, resources, resourcesToRemove);
    }

    @Override
    public List<CloudResource> collectResourcesToRemove(AuthenticatedContext authenticatedContext, CloudStack stack,
            List<CloudResource> resources, List<CloudInstance> vms) {

        List<CloudResource> result = Lists.newArrayList();

        result.addAll(getDeletableResources(resources, vms));
        result.addAll(collectProviderSpecificResources(resources, vms));
        return result;
    }

    @Override
    protected Collection<CloudResource> getDeletableResources(Iterable<CloudResource> resources, Iterable<CloudInstance> instances) {
        Collection<CloudResource> result = new ArrayList<>();
        for (CloudInstance instance : instances) {
            String instanceId = instance.getInstanceId();
            for (CloudResource resource : resources) {
                if (instanceId.equalsIgnoreCase(resource.getName()) || instanceId.equalsIgnoreCase(resource.getInstanceId())) {
                    result.add(resource);
                }
            }
        }
        LOGGER.debug("Collected deletable resources for downscale are: {}", result.toString());
        return result;
    }

    @Override
    protected List<CloudResource> collectProviderSpecificResources(List<CloudResource> resources, List<CloudInstance> vms) {
        return List.of();
    }

    @Override
    public TlsInfo getTlsInfo(AuthenticatedContext authenticatedContext, CloudStack cloudStack) {
        return new TlsInfo(false);
    }

    @Override
    public String getStackTemplate() {
        return azureTemplateBuilder.getTemplateString();
    }

    @Override
    public String getDBStackTemplate() {
        return azureDatabaseResourceService.getDBStackTemplate();
    }

    @Override
    public List<CloudResourceStatus> updateLoadBalancers(AuthenticatedContext authenticatedContext, CloudStack stack,
            PersistenceNotifier persistenceNotifier) {
        // no-op
        return List.of();
    }
}
