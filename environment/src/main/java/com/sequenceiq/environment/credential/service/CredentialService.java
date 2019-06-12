package com.sequenceiq.environment.credential.service;


import static com.sequenceiq.cloudbreak.common.exception.NotFoundException.notFound;
import static com.sequenceiq.environment.TempConstants.TEMP_USER_ID;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.ws.rs.BadRequestException;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import com.sequenceiq.cloudbreak.auth.altus.Crn;
import com.sequenceiq.cloudbreak.cloud.model.Platform;
import com.sequenceiq.cloudbreak.cloud.response.CredentialPrerequisitesResponse;
import com.sequenceiq.cloudbreak.common.json.Json;
import com.sequenceiq.cloudbreak.message.CloudbreakMessagesService;
import com.sequenceiq.cloudbreak.service.secret.service.SecretService;
import com.sequenceiq.environment.credential.domain.Credential;
import com.sequenceiq.environment.credential.exception.CredentialOperationException;
import com.sequenceiq.environment.credential.repository.CredentialRepository;
import com.sequenceiq.environment.credential.validation.CredentialValidator;
import com.sequenceiq.notification.NotificationSender;
import com.sequenceiq.notification.ResourceEvent;

@Service
public class CredentialService extends AbstractCredentialService {

    private static final String DEPLOYMENT_ADDRESS_ATTRIBUTE_NOT_FOUND = "The 'deploymentAddress' parameter needs to be specified in the interactive login "
            + "request!";

    private static final Logger LOGGER = LoggerFactory.getLogger(CredentialService.class);

    private static final String NOT_FOUND_FORMAT_MESS_NAME = "Credential with name:";

    @Inject
    private CredentialRepository repository;

    @Inject
    private CredentialValidator credentialValidator;

    @Inject
    private ServiceProviderCredentialAdapter credentialAdapter;

    @Inject
    private CredentialPrerequisiteService credentialPrerequisiteService;

    @Inject
    private SecretService secretService;

    protected CredentialService(NotificationSender notificationSender, CloudbreakMessagesService messagesService,
            @Value("${environment.enabledplatforms}") Set<String> enabledPlatforms) {
        super(notificationSender, messagesService, enabledPlatforms);
    }

    public Set<Credential> listAvailablesByAccountId(String accountId) {
        return repository.findAllByAccountId(accountId, getEnabledPlatforms());
    }

    public Credential getByNameForAccountId(String name, String accountId) {
        return repository.findByNameAndAccountId(name, accountId, getEnabledPlatforms()).orElseThrow(notFound(NOT_FOUND_FORMAT_MESS_NAME, name));
    }

    public Credential getByCrnForAccountId(String crn, String accountId) {
        return repository.findByCrnAndAccountId(crn, accountId, getEnabledPlatforms()).orElseThrow(notFound(NOT_FOUND_FORMAT_MESS_NAME, crn));
    }

    public Credential getByEnvCrnForAccountId(String crn, String accountId) {
        return repository.findByEnvCrnAndAccountId(crn, accountId, getEnabledPlatforms()).orElseThrow(notFound(NOT_FOUND_FORMAT_MESS_NAME, crn));
    }

    public Map<String, String> interactiveLogin(String accountId, Credential credential) {
        validateDeploymentAddress(credential);
        return credentialAdapter.interactiveLogin(credential, accountId, "0");
    }

    public Credential updateByAccountId(Credential credential, String accountId) {
        Credential original = repository.findByNameAndAccountId(credential.getName(), accountId, getEnabledPlatforms())
                .orElseThrow(notFound(NOT_FOUND_FORMAT_MESS_NAME, credential.getName()));
        if (!Objects.equals(credential.getCloudPlatform(), original.getCloudPlatform())) {
            throw new BadRequestException("Modifying credential platform is forbidden");
        }
        credential.setId(original.getId());
        credential.setAccountId(accountId);
        credential.setResourceCrn(original.getResourceCrn());
        Credential updated = repository.save(credentialAdapter.verify(credential, accountId));
        secretService.delete(original.getAttributesSecret());
        sendCredentialNotification(credential, ResourceEvent.CREDENTIAL_MODIFIED);
        return updated;
    }

    @Retryable(value = BadRequestException.class, maxAttempts = 30, backoff = @Backoff(delay = 2000))
    public void createWithRetry(Credential credential, String accountId, String creator) {
        create(credential, accountId, creator);
    }

    public Credential create(Credential credential, @Nonnull String accountId, @Nonnull String creator) {
        repository.findByNameAndAccountId(credential.getName(), accountId, getEnabledPlatforms())
                .map(Credential::getName)
                .ifPresent(name -> {
                    throw new BadRequestException("Credential already exists with name: " + name);
                });
        credentialValidator.validateCredentialCloudPlatform(credential.getCloudPlatform());
        credentialValidator.validateParameters(Platform.platform(credential.getCloudPlatform()), new Json(credential.getAttributes()));
        credential.setResourceCrn(createCRN(accountId));
        credential.setCreator(creator);
        credential.setAccountId(accountId);
        Credential created = repository.save(credentialAdapter.verify(credential, accountId));
        sendCredentialNotification(credential, ResourceEvent.CREDENTIAL_CREATED);
        return created;
    }

    public CredentialPrerequisitesResponse getPrerequisites(String cloudPlatform, String deploymentAddress) {
        String cloudPlatformUppercased = cloudPlatform.toUpperCase();
        credentialValidator.validateCredentialCloudPlatform(cloudPlatformUppercased);
        return credentialPrerequisiteService.getPrerequisites(cloudPlatformUppercased, deploymentAddress);
    }

    public String initCodeGrantFlow(String accountId, @Nonnull Credential credential) {
        credentialValidator.validateCredentialCloudPlatform(credential.getCloudPlatform());
        validateDeploymentAddress(credential);
        putToCredentialAttributes(credential, Map.of("codeGrantFlow", true));
        Credential created = credentialAdapter.initCodeGrantFlow(credential, accountId, TEMP_USER_ID);
        created = repository.save(created);
        return getCodeGrantFlowAppLoginUrl(created.getAttributes());
    }

    public String initCodeGrantFlow(String accountId, String name) {
        Credential original = repository.findByNameAndAccountId(name, accountId, getEnabledPlatforms())
                .orElseThrow(notFound(NOT_FOUND_FORMAT_MESS_NAME, name));
        String originalAttributes = original.getAttributes();
        boolean codeGrantFlow = Boolean.valueOf(new Json(originalAttributes).getMap().get("codeGrantFlow").toString());
        if (!codeGrantFlow) {
            throw new UnsupportedOperationException("This operation is only allowed on Authorization Code Grant flow based credentails.");
        }
        Credential updated = credentialAdapter.initCodeGrantFlow(original, accountId, TEMP_USER_ID);
        updated = repository.save(updated);
        secretService.delete(originalAttributes);
        return getCodeGrantFlowAppLoginUrl(updated.getAttributes());
    }

    public Credential authorizeCodeGrantFlow(String code, @Nonnull String state, String accountId, @Nonnull String platform) {
        String cloudPlatformUpperCased = platform.toUpperCase();
        Set<Credential> credentials = repository.findAllByAccountId(accountId, List.of(cloudPlatformUpperCased));
        Credential original = credentials.stream()
                .filter(cred -> state.equalsIgnoreCase(String.valueOf(new Json(cred.getAttributes()).getMap().get("codeGrantFlowState"))))
                .findFirst()
                .orElseThrow(notFound("Code grant flow based credential for user with state:", state));
        LOGGER.info("Authorizing credential('{}') with Authorization Code Grant flow.", original.getName());
        String attributesSecret = original.getAttributesSecret();
        putToCredentialAttributes(original, Map.of("authorizationCode", code));
        Credential updated = repository.save(credentialAdapter.verify(original, accountId));
        secretService.delete(attributesSecret);
        return updated;
    }

    private void validateDeploymentAddress(Credential credential) {
        String deploymentAddress = (String) new Json(credential.getAttributes()).getMap().get("deploymentAddress");
        if (StringUtils.isEmpty(deploymentAddress)) {
            throw new BadRequestException(DEPLOYMENT_ADDRESS_ATTRIBUTE_NOT_FOUND);
        }
    }

    private void putToCredentialAttributes(Credential credential, Map<String, Object> attributesToAdd) {
        Json attributes = new Json(credential.getAttributes());
        Map<String, Object> newAttributes = attributes.getMap();
        newAttributes.putAll(attributesToAdd);
        credential.setAttributes(new Json(newAttributes).getValue());
    }

    private String getCodeGrantFlowAppLoginUrl(String credentialAttributes) {
        Object appLoginUrl = Optional.ofNullable(new Json(credentialAttributes).getMap().get("appLoginUrl"))
                .orElseThrow(() -> new CredentialOperationException("Unable to obtain App login url!"));
        return String.valueOf(appLoginUrl);
    }

    private String createCRN(@Nonnull String accountId) {
        return Crn.builder()
                .setService(Crn.Service.ENVIRONMENTS)
                .setAccountId(accountId)
                .setResourceType(Crn.ResourceType.CREDENTIAL)
                .setResource(UUID.randomUUID().toString())
                .build()
                .toString();
    }

}