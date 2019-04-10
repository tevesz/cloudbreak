package com.sequenceiq.cloudbreak.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.request.cluster.ClusterV4Request;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.request.cluster.ambari.AmbariV4Request;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.request.cluster.ambari.ambarirepository.AmbariRepositoryV4Request;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.request.cluster.ambari.stackrepository.StackRepositoryV4Request;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.request.cluster.cm.ClouderaManagerV4Request;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.request.cluster.cm.product.ClouderaManagerProductV4Request;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.request.cluster.cm.repository.ClouderaManagerRepositoryV4Request;
import com.sequenceiq.cloudbreak.api.endpoint.v4.util.responses.AmbariStackDescriptorV4Response;
import com.sequenceiq.cloudbreak.api.endpoint.v4.util.responses.StackMatrixV4Response;
import com.sequenceiq.cloudbreak.api.util.ConverterUtil;
import com.sequenceiq.cloudbreak.aspect.Measure;
import com.sequenceiq.cloudbreak.cloud.VersionComparator;
import com.sequenceiq.cloudbreak.cloud.model.AmbariRepo;
import com.sequenceiq.cloudbreak.cloud.model.CloudCredential;
import com.sequenceiq.cloudbreak.cloud.model.ClouderaManagerProduct;
import com.sequenceiq.cloudbreak.cloud.model.ClouderaManagerRepo;
import com.sequenceiq.cloudbreak.cloud.model.Image;
import com.sequenceiq.cloudbreak.cloud.model.component.AmbariDefaultStackRepoDetails;
import com.sequenceiq.cloudbreak.cloud.model.component.DefaultCDHEntries;
import com.sequenceiq.cloudbreak.cloud.model.component.DefaultCDHInfo;
import com.sequenceiq.cloudbreak.cloud.model.component.DefaultHDFEntries;
import com.sequenceiq.cloudbreak.cloud.model.component.DefaultHDFInfo;
import com.sequenceiq.cloudbreak.cloud.model.component.DefaultHDPEntries;
import com.sequenceiq.cloudbreak.cloud.model.component.DefaultHDPInfo;
import com.sequenceiq.cloudbreak.cloud.model.component.ManagementPackComponent;
import com.sequenceiq.cloudbreak.cloud.model.component.StackInfo;
import com.sequenceiq.cloudbreak.cloud.model.component.StackRepoDetails;
import com.sequenceiq.cloudbreak.cluster.api.ClusterPreCreationApi;
import com.sequenceiq.cloudbreak.clusterdefinition.utils.ClusterDefinitionUtils;
import com.sequenceiq.cloudbreak.common.type.ComponentType;
import com.sequenceiq.cloudbreak.controller.exception.BadRequestException;
import com.sequenceiq.cloudbreak.controller.validation.ValidationResult;
import com.sequenceiq.cloudbreak.controller.validation.environment.ClusterCreationEnvironmentValidator;
import com.sequenceiq.cloudbreak.controller.validation.filesystem.FileSystemValidator;
import com.sequenceiq.cloudbreak.controller.validation.mpack.ManagementPackValidator;
import com.sequenceiq.cloudbreak.controller.validation.rds.RdsConfigValidator;
import com.sequenceiq.cloudbreak.converter.spi.CredentialToCloudCredentialConverter;
import com.sequenceiq.cloudbreak.converter.v4.stacks.cluster.ambari.StackRepositoryV4RequestToStackRepoDetailsConverter;
import com.sequenceiq.cloudbreak.core.CloudbreakImageNotFoundException;
import com.sequenceiq.cloudbreak.domain.ClusterDefinition;
import com.sequenceiq.cloudbreak.domain.Constraint;
import com.sequenceiq.cloudbreak.domain.FileSystem;
import com.sequenceiq.cloudbreak.domain.json.Json;
import com.sequenceiq.cloudbreak.domain.stack.Component;
import com.sequenceiq.cloudbreak.domain.stack.Stack;
import com.sequenceiq.cloudbreak.domain.stack.cluster.Cluster;
import com.sequenceiq.cloudbreak.domain.stack.cluster.ClusterComponent;
import com.sequenceiq.cloudbreak.domain.workspace.User;
import com.sequenceiq.cloudbreak.domain.workspace.Workspace;
import com.sequenceiq.cloudbreak.logger.MDCBuilder;
import com.sequenceiq.cloudbreak.service.TransactionService.TransactionExecutionException;
import com.sequenceiq.cloudbreak.service.cluster.ClusterApiConnectors;
import com.sequenceiq.cloudbreak.service.cluster.ClusterService;
import com.sequenceiq.cloudbreak.service.clusterdefinition.ClusterDefinitionService;
import com.sequenceiq.cloudbreak.service.decorator.ClusterDecorator;
import com.sequenceiq.cloudbreak.service.filesystem.FileSystemConfigService;
import com.sequenceiq.cloudbreak.util.Benchmark.MultiCheckedSupplier;
import com.sequenceiq.cloudbreak.util.JsonUtil;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.sequenceiq.cloudbreak.cloud.model.component.StackRepoDetails.CUSTOM_VDF_REPO_KEY;
import static com.sequenceiq.cloudbreak.cloud.model.component.StackRepoDetails.VDF_REPO_KEY_PREFIX;
import static com.sequenceiq.cloudbreak.controller.exception.NotFoundException.notFound;
import static com.sequenceiq.cloudbreak.util.Benchmark.checkedMeasure;
import static com.sequenceiq.cloudbreak.util.Benchmark.measure;

@Service
public class ClusterCreationSetupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterCreationSetupService.class);

    @Inject
    private FileSystemValidator fileSystemValidator;

    @Inject
    private FileSystemConfigService fileSystemConfigService;

    @Inject
    private CredentialToCloudCredentialConverter credentialToCloudCredentialConverter;

    @Inject
    private ConverterUtil converterUtil;

    @Inject
    private ClusterDecorator clusterDecorator;

    @Inject
    private StackRepositoryV4RequestToStackRepoDetailsConverter stackRepoDetailsConverter;

    @Inject
    private ClusterService clusterService;

    @Inject
    private ComponentConfigProvider componentConfigProvider;

    @Inject
    private ClusterDefinitionUtils clusterDefinitionUtils;

    @Inject
    private DefaultHDPEntries defaultHDPEntries;

    @Inject
    private DefaultHDFEntries defaultHDFEntries;

    @Inject
    private DefaultCDHEntries defaultCDHEntries;

    @Inject
    private ClusterDefinitionService clusterDefinitionService;

    @Inject
    private ClusterApiConnectors clusterApiConnectors;

    @Inject
    private DefaultAmbariRepoService defaultAmbariRepoService;

    @Inject
    private DefaultClouderaManagerRepoService defaultClouderaManagerRepoService;

    @Inject
    private StackMatrixService stackMatrixService;

    @Inject
    private ManagementPackValidator mpackValidator;

    @Inject
    private RdsConfigValidator rdsConfigValidator;

    @Inject
    private ClusterCreationEnvironmentValidator environmentValidator;

    public void validate(ClusterV4Request request, Stack stack, User user, Workspace workspace) {
        validate(request, null, stack, user, workspace);
    }

    @Measure(ClusterCreationSetupService.class)
    public void validate(ClusterV4Request request, CloudCredential cloudCredential, Stack stack, User user, Workspace workspace) {
        if (stack.getDatalakeResourceId() != null && StringUtils.isNotBlank(request.getKerberosName())) {
            throw new BadRequestException("Invalid kerberos settings, attached cluster should inherit kerberos parameters");
        }
        MDCBuilder.buildUserMdcContext(user.getUserId(), user.getUserName());
        CloudCredential credential = cloudCredential;
        if (credential == null) {
            credential = credentialToCloudCredentialConverter.convert(stack.getCredential());
        }
        fileSystemValidator.validateCloudStorage(stack.cloudPlatform(), credential, request.getCloudStorage(),
                stack.getCreator().getUserId(), stack.getWorkspace().getId());
        mpackValidator.validateMpacks(request.getAmbari(), workspace);
        rdsConfigValidator.validateRdsConfigs(request, user, workspace);
        ValidationResult environmentValidationResult = environmentValidator.validate(request, stack);
        if (environmentValidationResult.hasError()) {
            throw new BadRequestException(environmentValidationResult.getFormattedErrors());
        }
    }

    public Cluster prepare(ClusterV4Request request, Stack stack, User user, Workspace workspace) throws CloudbreakImageNotFoundException, IOException,
            TransactionExecutionException {
        return prepare(request, stack, null, user, workspace);
    }

    public Cluster prepare(ClusterV4Request request, Stack stack, ClusterDefinition clusterDefinition, User user, Workspace workspace) throws IOException,
            CloudbreakImageNotFoundException, TransactionExecutionException {
        String stackName = stack.getName();
        Cluster clusterStub = stack.getCluster();
        stack.setCluster(null);

        decorateHostGroupWithConstraint(stack, clusterStub);

        if (request.getCloudStorage() != null) {
            FileSystem fs = measure(() -> fileSystemConfigService.create(converterUtil.convert(request.getCloudStorage(), FileSystem.class),
                        stack.getWorkspace(), stack.getCreator()),
                    LOGGER, "File system saving took {} ms for stack {}", stackName);
        }

        clusterStub.setStack(stack);
        clusterStub.setWorkspace(stack.getWorkspace());

        Cluster cluster = clusterDecorator.decorate(clusterStub, request, clusterDefinition, user, stack.getWorkspace(), stack);

        decorateStackWithCustomDomainIfAdOrIpaJoinable(stack, cluster);

        List<ClusterComponent> components = checkedMeasure((MultiCheckedSupplier<List<ClusterComponent>, IOException, CloudbreakImageNotFoundException>) () -> {
            if (clusterDefinition != null) {
                Set<Component> allComponent = componentConfigProvider.getAllComponentsByStackIdAndType(stack.getId(),
                        Sets.newHashSet(ComponentType.AMBARI_REPO_DETAILS, ComponentType.HDP_REPO_DETAILS, ComponentType.IMAGE));
                Optional<Component> stackAmbariRepoConfig = allComponent.stream().filter(c -> c.getComponentType().equals(ComponentType.AMBARI_REPO_DETAILS)
                        && c.getName().equalsIgnoreCase(ComponentType.AMBARI_REPO_DETAILS.name())).findAny();
                Optional<Component> stackHdpRepoConfig = allComponent.stream().filter(c -> c.getComponentType().equals(ComponentType.HDP_REPO_DETAILS)
                        && c.getName().equalsIgnoreCase(ComponentType.HDP_REPO_DETAILS.name())).findAny();
                Optional<Component> stackImageComponent = allComponent.stream().filter(c -> c.getComponentType().equals(ComponentType.IMAGE)
                        && c.getName().equalsIgnoreCase(ComponentType.IMAGE.name())).findAny();

                if (clusterDefinitionService.isAmbariBlueprint(clusterDefinition)) {
                    return prepareAmbariCluster(request, stack, clusterDefinition, cluster, stackAmbariRepoConfig, stackHdpRepoConfig, stackImageComponent);
                } else if (clusterDefinitionService.isClouderaManagerTemplate(clusterDefinition)) {
                    return prepareClouderaManagerCluster(request, cluster, stackImageComponent);
                }
            }
            return Collections.emptyList();
        }, LOGGER, "Cluster components saved in {} ms for stack {}", stackName);

        return clusterService.create(stack, cluster, components, user);
    }

    private List<ClusterComponent> prepareAmbariCluster(ClusterV4Request request, Stack stack, ClusterDefinition clusterDefinition, Cluster cluster,
            Optional<Component> stackAmbariRepoConfig, Optional<Component> stackHdpRepoConfig, Optional<Component> stackImageComponent) throws IOException,
            CloudbreakImageNotFoundException {
        List<ClusterComponent> components = new ArrayList<>();
        AmbariRepositoryV4Request repoDetailsJson = Optional.ofNullable(request.getAmbari()).map(AmbariV4Request::getRepository).orElse(null);
        ClusterComponent ambariRepoConfig = determineAmbariRepoConfig(stackAmbariRepoConfig, repoDetailsJson, stackImageComponent, cluster);
        components.add(ambariRepoConfig);
        ClusterComponent hdpRepoConfig = determineHDPRepoConfig(clusterDefinition,
                stack.getId(), stackHdpRepoConfig, request, cluster, stack.getWorkspace(),
                stackImageComponent);
        components.add(hdpRepoConfig);
        checkRepositories(ambariRepoConfig, hdpRepoConfig, stackImageComponent.get());
        checkVDFFile(ambariRepoConfig, hdpRepoConfig, cluster);
        return components;
    }

    private List<ClusterComponent> prepareClouderaManagerCluster(ClusterV4Request request, Cluster cluster,
            Optional<Component> stackImageComponent) throws IOException {
        List<ClusterComponent> components = new ArrayList<>();
        ClouderaManagerRepositoryV4Request repoDetailsJson = Optional.ofNullable(
                request.getCm()).map(ClouderaManagerV4Request::getRepository).orElse(null);
        if (repoDetailsJson == null) {
            ClusterComponent cmRepoConfig = determineDefaultCmRepoConfig(stackImageComponent, cluster);
            components.add(cmRepoConfig);
        }
        List<ClouderaManagerProductV4Request> products = Optional.ofNullable(request.getCm()).map(ClouderaManagerV4Request::getProducts).orElse(null);

        if (products == null || products.isEmpty()) {
            ClusterComponent cdhProductRepoConfig = determineDefaultCdhRepoConfig(cluster, stackImageComponent);
            components.add(cdhProductRepoConfig);
        }
        components.addAll(cluster.getComponents());
        return components;
    }

    private void decorateHostGroupWithConstraint(Stack stack, Cluster cluster) {
        stack.getInstanceGroups().forEach(ig -> cluster.getHostGroups().stream()
                .filter(hostGroup -> hostGroup.getName().equals(ig.getGroupName()))
                .findFirst()
                .ifPresent(hostGroup -> {
                    Constraint constraint = new Constraint();
                    constraint.setInstanceGroup(ig);
                    hostGroup.setConstraint(constraint);
                }));
    }

    private void decorateStackWithCustomDomainIfAdOrIpaJoinable(Stack stack, Cluster cluster) {
        if (cluster.getKerberosConfig() != null && StringUtils.isNotBlank(cluster.getKerberosConfig().getDomain())) {
            stack.setCustomDomain(cluster.getKerberosConfig().getDomain());
        }
    }

    private void checkRepositories(ClusterComponent ambariRepoComponent, ClusterComponent stackRepoComponent, Component imageComponent)
            throws IOException {
        AmbariRepo ambariRepo = ambariRepoComponent.getAttributes().get(AmbariRepo.class);
        StackRepoDetails stackRepoDetails = stackRepoComponent.getAttributes().get(StackRepoDetails.class);
        Image image = imageComponent.getAttributes().get(Image.class);
        StackMatrixV4Response stackMatrixV4Response = stackMatrixService.getStackMatrix();
        String stackMajorVersion = stackRepoDetails.getMajorHdpVersion();
        Map<String, AmbariStackDescriptorV4Response> stackDescriptorMap;

        String stackType = stackRepoDetails.getStack().get(StackRepoDetails.REPO_ID_TAG);
        if (stackType.contains("-")) {
            stackType = stackType.substring(0, stackType.indexOf('-'));
        }
        switch (stackType) {
            case "HDP":
                stackDescriptorMap = stackMatrixV4Response.getHdp();
                break;
            case "HDF":
                stackDescriptorMap = stackMatrixV4Response.getHdf();
                break;
            default:
                LOGGER.warn("No stack descriptor map found for stacktype {}, using 'HDP'", stackType);
                stackDescriptorMap = stackMatrixV4Response.getHdp();
        }
        AmbariStackDescriptorV4Response stackDescriptorV4 = stackDescriptorMap.get(stackMajorVersion);
        if (stackDescriptorV4 != null) {
            boolean hasDefaultStackRepoUrlForOsType = stackDescriptorV4.getRepository().getStack().containsKey(image.getOsType());
            boolean hasDefaultAmbariRepoUrlForOsType = stackDescriptorV4.getAmbari().getRepository().containsKey(image.getOsType());
            boolean compatibleAmbari = new VersionComparator().compare(() -> ambariRepo.getVersion().substring(0, stackDescriptorV4.getMinAmbari().length()),
                    stackDescriptorV4::getMinAmbari) >= 0;
            if (!hasDefaultAmbariRepoUrlForOsType || !hasDefaultStackRepoUrlForOsType || !compatibleAmbari) {
                String message = String.format("The given repository information seems to be incompatible."
                                + " Ambari version: %s, Stack type: %s, Stack version: %s, Image Id: %s, Os type: %s.", ambariRepo.getVersion(),
                        stackType, stackRepoDetails.getHdpVersion(), image.getImageId(), image.getOsType());
                LOGGER.debug(message);
            }
        }
    }

    private void checkVDFFile(ClusterComponent ambariRepoConfig, ClusterComponent hdpRepoConfig, Cluster cluster) throws IOException {
        AmbariRepo ambariRepo = ambariRepoConfig.getAttributes().get(AmbariRepo.class);
        ClusterPreCreationApi connector = clusterApiConnectors.getConnector(cluster);
        if (connector.isVdfReady(ambariRepo) && !containsVDFUrl(hdpRepoConfig.getAttributes())) {
            throw new BadRequestException(String.format("Couldn't determine any VDF file for the stack: %s", cluster.getName()));
        }
    }

    private ClusterComponent determineAmbariRepoConfig(Optional<Component> stackAmbariRepoConfig,
            AmbariRepositoryV4Request ambariRepoDetailsJson, Optional<Component> stackImageComponent, Cluster cluster) throws IOException {
        Json json;
        if (!stackAmbariRepoConfig.isPresent()) {
            String clusterDefinitionText = cluster.getClusterDefinition().getClusterDefinitionText();
            JsonNode bluePrintJson = JsonUtil.readTree(clusterDefinitionText);
            String stackVersion = clusterDefinitionUtils.getBlueprintStackVersion(bluePrintJson);
            String stackName = clusterDefinitionUtils.getBlueprintStackName(bluePrintJson);
            AmbariRepo ambariRepo = ambariRepoDetailsJson != null
                    ? converterUtil.convert(ambariRepoDetailsJson, AmbariRepo.class)
                    : defaultAmbariRepoService.getDefault(getOsType(stackImageComponent), stackName, stackVersion);
            if (ambariRepo == null) {
                throw new BadRequestException(String.format("Couldn't determine Ambari repo for the stack: %s", cluster.getStack().getName()));
            }
            json = new Json(ambariRepo);
        } else {
            json = stackAmbariRepoConfig.get().getAttributes();
        }
        return new ClusterComponent(ComponentType.AMBARI_REPO_DETAILS, json, cluster);
    }

    private ClusterComponent determineHDPRepoConfig(ClusterDefinition clusterDefinition, long stackId, Optional<Component> stackHdpRepoConfig,
            ClusterV4Request request, Cluster cluster, Workspace workspace, Optional<Component> stackImageComponent)
            throws IOException, CloudbreakImageNotFoundException {
        Json stackRepoDetailsJson;
        StackRepositoryV4Request ambariStackDetails = request.getAmbari() == null ? null : request.getAmbari().getStackRepository();
        if (!stackHdpRepoConfig.isPresent()) {
            if (ambariStackDetails != null && (stackRepoDetailsConverter.isBaseRepoRequiredFieldsExists(ambariStackDetails)
                    || stackRepoDetailsConverter.isVdfRequiredFieldsExists(ambariStackDetails))) {
                setOsTypeFromImageIfMissing(cluster, stackImageComponent, ambariStackDetails);
                StackRepoDetails stackRepoDetails = stackRepoDetailsConverter.convert(ambariStackDetails);
                stackRepoDetailsJson = new Json(stackRepoDetails);
            } else {
                AmbariDefaultStackRepoDetails stackRepoDetails = SerializationUtils.clone(defaultHDPInfo(clusterDefinition, request, workspace).getRepo());
                String osType = getOsType(stackId);
                StackRepoDetails repo = createStackRepoDetails(stackRepoDetails, osType);
                Optional<String> vdfUrl = getVDFUrlByOsType(osType, stackRepoDetails);
                vdfUrl.ifPresent(s -> stackRepoDetails.getStack().put(CUSTOM_VDF_REPO_KEY, s));
                if (ambariStackDetails != null && ambariStackDetails.getMpacks() != null) {
                    repo.getMpacks().addAll(ambariStackDetails.getMpacks().stream().map(
                            rmpack -> converterUtil.convert(rmpack, ManagementPackComponent.class)).collect(Collectors.toList()));
                }
                setEnableGplIfAvailable(repo, ambariStackDetails);
                stackRepoDetailsJson = new Json(repo);
            }
        } else {
            stackRepoDetailsJson = stackHdpRepoConfig.get().getAttributes();
            StackRepoDetails stackRepoDetails = stackRepoDetailsJson.get(StackRepoDetails.class);
            if (ambariStackDetails != null && !ambariStackDetails.getMpacks().isEmpty()) {
                stackRepoDetails.getMpacks().addAll(ambariStackDetails.getMpacks().stream().map(
                        rmpack -> converterUtil.convert(rmpack, ManagementPackComponent.class)).collect(Collectors.toList()));
            }
            setEnableGplIfAvailable(stackRepoDetails, ambariStackDetails);
            stackRepoDetailsJson = new Json(stackRepoDetails);
        }
        return new ClusterComponent(ComponentType.HDP_REPO_DETAILS, stackRepoDetailsJson, cluster);
    }

    private void setEnableGplIfAvailable(StackRepoDetails repo, StackRepositoryV4Request ambariStackDetails) {
        if (ambariStackDetails != null) {
            repo.setEnableGplRepo(ambariStackDetails.isEnableGplRepo());
        }
    }

    private ClusterComponent determineDefaultCmRepoConfig(Optional<Component> stackImageComponent, Cluster cluster) throws IOException {
        ClouderaManagerRepo clouderaManagerRepo = defaultClouderaManagerRepoService.getDefault(getOsType(stackImageComponent));
        if (clouderaManagerRepo == null) {
            throw new BadRequestException(String.format("Couldn't determine Cloudera Manager repo for the stack: %s", cluster.getStack().getName()));
        }
        Json json = new Json(clouderaManagerRepo);
        return new ClusterComponent(ComponentType.CM_REPO_DETAILS, json, cluster);
    }

    private ClusterComponent determineDefaultCdhRepoConfig(Cluster cluster, Optional<Component> stackImageComponent) throws IOException {
        String osType = getOsType(stackImageComponent);

        Entry<String, DefaultCDHInfo> defaultCDHInfoEntry = defaultCDHEntries.
                getEntries().
                entrySet().
                stream().
                filter(entry -> Objects.nonNull(entry.getValue().getRepo().getStack().get(osType))).
                max(DefaultCDHEntries.CDH_ENTRY_COMPARATOR).
                orElseThrow(notFound("Default Product Info with OS type:", osType));
        ClouderaManagerProduct cmProduct = new ClouderaManagerProduct();
        DefaultCDHInfo defaultCDHInfo = defaultCDHInfoEntry.getValue();
        Map<String, String> stack = defaultCDHInfo.getRepo().getStack();
        cmProduct.
                withVersion(defaultCDHInfo.getVersion()).
                withName(stack.get("repoid").split("-")[0]).
                withParcel(stack.get(osType));
        Json json = new Json(cmProduct);
        return new ClusterComponent(ComponentType.CDH_PRODUCT_DETAILS, json, cluster);
    }

    private StackRepoDetails createStackRepoDetails(AmbariDefaultStackRepoDetails stackRepoDetails, String osType) {
        StackRepoDetails repo = new StackRepoDetails();
        repo.setHdpVersion(stackRepoDetails.getHdpVersion());
        repo.setStack(stackRepoDetails.getStack());
        repo.setUtil(stackRepoDetails.getUtil());
        repo.setEnableGplRepo(stackRepoDetails.isEnableGplRepo());
        repo.setVerify(stackRepoDetails.isVerify());
        List<ManagementPackComponent> mpacks = stackRepoDetails.getMpacks().get(osType);
        repo.setMpacks(Objects.requireNonNullElseGet(mpacks, LinkedList::new));
        return repo;
    }

    private void setOsTypeFromImageIfMissing(Cluster cluster, Optional<Component> stackImageComponent,
            StackRepositoryV4Request ambariStackDetails) {
        if (StringUtils.isBlank(ambariStackDetails.getOs()) && stackImageComponent.isPresent()) {
            try {
                String osType = getOsType(stackImageComponent);
                if (StringUtils.isNotBlank(osType)) {
                    ambariStackDetails.setOs(osType);
                }
            } catch (IOException e) {
                LOGGER.info("Couldn't convert image component for stack: {} {}", cluster.getStack().getId(), cluster.getStack().getName(), e);
            }
        }
    }

    private String getOsType(Optional<Component> stackImageComponent) throws IOException {
        Image image = stackImageComponent.get().getAttributes().get(Image.class);
        return image.getOsType();
    }

    private boolean containsVDFUrl(Json stackRepoDetailsJson) {
        try {
            return stackRepoDetailsJson.get(StackRepoDetails.class).getStack().containsKey(CUSTOM_VDF_REPO_KEY);
        } catch (IOException e) {
            LOGGER.error("JSON parse error.", e);
        }
        return false;
    }

    private StackInfo defaultHDPInfo(ClusterDefinition clusterDefinition, ClusterV4Request request, Workspace workspace) {
        try {
            JsonNode root = getClusterDefinitionJsonNode(clusterDefinition, request, workspace);
            if (root != null) {
                String stackVersion = clusterDefinitionUtils.getBlueprintStackVersion(root);
                String stackName = clusterDefinitionUtils.getBlueprintStackName(root);
                if ("HDF".equalsIgnoreCase(stackName)) {
                    LOGGER.debug("Stack name is HDF, use the default HDF repo for version: " + stackVersion);
                    for (Entry<String, DefaultHDFInfo> entry : defaultHDFEntries.getEntries().entrySet()) {
                        if (entry.getKey().equals(stackVersion)) {
                            return entry.getValue();
                        }
                    }
                } else {
                    LOGGER.debug("Stack name is HDP, use the default HDP repo for version: " + stackVersion);
                    for (Entry<String, DefaultHDPInfo> entry : defaultHDPEntries.getEntries().entrySet()) {
                        if (entry.getKey().equals(stackVersion)) {
                            return entry.getValue();
                        }
                    }
                }
            }
        } catch (IOException ex) {
            LOGGER.warn("Can not initiate default hdp info: ", ex);
        }
        return defaultHDPEntries.getEntries().values().iterator().next();
    }

    private JsonNode getClusterDefinitionJsonNode(ClusterDefinition clusterDefinition, ClusterV4Request request, Workspace workspace) throws IOException {
        JsonNode root = null;
        if (clusterDefinition != null) {
            String clusterDefinitionText = clusterDefinition.getClusterDefinitionText();
            root = JsonUtil.readTree(clusterDefinitionText);
        } else if (request.getClusterDefinitionName() != null) {
            clusterDefinition = clusterDefinitionService.getByNameForWorkspace(request.getClusterDefinitionName(), workspace);
            String clusterDefinitionText = clusterDefinition.getClusterDefinitionText();
            root = JsonUtil.readTree(clusterDefinitionText);
        }
        return root;
    }

    private String getOsType(Long stackId) throws CloudbreakImageNotFoundException {
        Image image = componentConfigProvider.getImage(stackId);
        return image.getOsType();
    }

    private Optional<String> getVDFUrlByOsType(String osType, AmbariDefaultStackRepoDetails stackRepoDetails) {
        String vdfStackRepoKeyFilter = VDF_REPO_KEY_PREFIX;
        if (!StringUtils.isEmpty(osType)) {
            vdfStackRepoKeyFilter += osType;
        }
        String filter = vdfStackRepoKeyFilter;
        return stackRepoDetails.getStack().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(filter))
                .map(Entry::getValue)
                .findFirst();
    }
}
