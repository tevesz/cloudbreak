package com.sequenceiq.cloudbreak.api.endpoint.v4.common;

public enum DetailedStackStatus {
    UNKNOWN(null),
    // Provision statuses
    PROVISION_REQUESTED(Status.REQUESTED),
    PROVISION_SETUP(Status.CREATE_IN_PROGRESS),
    IMAGE_SETUP(Status.CREATE_IN_PROGRESS),
    CREATING_INFRASTRUCTURE(Status.CREATE_IN_PROGRESS),
    METADATA_COLLECTION(Status.CREATE_IN_PROGRESS),
    LOAD_BALANCER_METADATA_COLLECTION(Status.CREATE_IN_PROGRESS),
    TLS_SETUP(Status.CREATE_IN_PROGRESS),
    PROVISIONED(Status.AVAILABLE),
    PROVISION_FAILED(Status.CREATE_FAILED),
    // Orchestration statuses
    REGISTERING_TO_CLUSTER_PROXY(Status.UPDATE_IN_PROGRESS),
    REGISTERING_GATEWAY_TO_CLUSTER_PROXY(Status.UPDATE_IN_PROGRESS),
    BOOTSTRAPPING_MACHINES(Status.UPDATE_IN_PROGRESS),
    COLLECTING_HOST_METADATA(Status.UPDATE_IN_PROGRESS),
    MOUNTING_DISKS(Status.UPDATE_IN_PROGRESS),
    STARTING_CLUSTER_MANAGER_SERVICES(Status.UPDATE_IN_PROGRESS),
    // Start statuses
    START_REQUESTED(Status.START_REQUESTED),
    START_IN_PROGRESS(Status.START_IN_PROGRESS),
    STARTED(Status.AVAILABLE),
    START_FAILED(Status.START_FAILED),
    // Stop statuses
    STOP_REQUESTED(Status.STOP_REQUESTED),
    STOP_IN_PROGRESS(Status.STOP_IN_PROGRESS),
    STOPPED(Status.STOPPED),
    STOP_FAILED(Status.STOP_FAILED),
    // Upscale statuses
    UPSCALE_REQUESTED(Status.UPDATE_REQUESTED),
    ADDING_NEW_INSTANCES(Status.UPDATE_IN_PROGRESS),
    EXTENDING_METADATA(Status.UPDATE_IN_PROGRESS),
    BOOTSTRAPPING_NEW_NODES(Status.UPDATE_IN_PROGRESS),
    EXTENDING_HOST_METADATA(Status.UPDATE_IN_PROGRESS),
    MOUNTING_DISKS_ON_NEW_HOSTS(Status.UPDATE_IN_PROGRESS),
    UPSCALE_COMPLETED(Status.AVAILABLE),
    UPSCALE_FAILED(Status.AVAILABLE),
    // Downscale statuses
    DOWNSCALE_REQUESTED(Status.UPDATE_REQUESTED),
    DOWNSCALE_IN_PROGRESS(Status.UPDATE_IN_PROGRESS),
    DOWNSCALE_COMPLETED(Status.AVAILABLE),
    DOWNSCALE_FAILED(Status.AVAILABLE),
    // Termination statuses
    PRE_DELETE_IN_PROGRESS(Status.PRE_DELETE_IN_PROGRESS),
    DELETE_IN_PROGRESS(Status.DELETE_IN_PROGRESS),
    DEREGISTERING_CCM_KEY(Status.DELETE_IN_PROGRESS),
    DELETE_COMPLETED(Status.DELETE_COMPLETED),
    DELETE_FAILED(Status.DELETE_FAILED),
    DELETED_ON_PROVIDER_SIDE(Status.DELETED_ON_PROVIDER_SIDE),
    // Rollback statuses
    ROLLING_BACK(Status.UPDATE_IN_PROGRESS),
    // The stack is available
    AVAILABLE(Status.AVAILABLE),
    // Instance removing status
    REMOVE_INSTANCE(Status.UPDATE_IN_PROGRESS),
    // Cluster operation is in progress
    CLUSTER_OPERATION(Status.UPDATE_IN_PROGRESS),
    // Wait for sync
    WAIT_FOR_SYNC(Status.WAIT_FOR_SYNC),
    // Retry
    RETRY(Status.UPDATE_IN_PROGRESS),
    // Repair status
    REPAIR_IN_PROGRESS(Status.UPDATE_IN_PROGRESS),
    REPAIR_FAILED(Status.UPDATE_FAILED),
    // External database statuses
    EXTERNAL_DATABASE_CREATION_IN_PROGRESS(Status.EXTERNAL_DATABASE_CREATION_IN_PROGRESS),
    EXTERNAL_DATABASE_CREATION_FAILED(Status.EXTERNAL_DATABASE_CREATION_FAILED),
    EXTERNAL_DATABASE_DELETION_IN_PROGRESS(Status.EXTERNAL_DATABASE_DELETION_IN_PROGRESS),
    EXTERNAL_DATABASE_DELETION_FINISHED(Status.EXTERNAL_DATABASE_DELETION_FINISHED),
    EXTERNAL_DATABASE_DELETION_FAILED(Status.EXTERNAL_DATABASE_DELETION_FAILED),
    EXTERNAL_DATABASE_START_IN_PROGRESS(Status.EXTERNAL_DATABASE_START_IN_PROGRESS),
    EXTERNAL_DATABASE_START_FINISHED(Status.EXTERNAL_DATABASE_START_FINISHED),
    EXTERNAL_DATABASE_START_FAILED(Status.EXTERNAL_DATABASE_START_FAILED),
    EXTERNAL_DATABASE_STOP_IN_PROGRESS(Status.EXTERNAL_DATABASE_STOP_IN_PROGRESS),
    EXTERNAL_DATABASE_STOP_FINISHED(Status.EXTERNAL_DATABASE_STOP_FINISHED),
    EXTERNAL_DATABASE_STOP_FAILED(Status.EXTERNAL_DATABASE_STOP_FAILED),

    CLUSTER_UPGRADE_INIT_FAILED(Status.AVAILABLE),
    CLUSTER_MANAGER_UPGRADE_FAILED(Status.AVAILABLE),
    CLUSTER_UPGRADE_FAILED(Status.AVAILABLE),
    CLUSTER_UPGRADE_FINISHED(Status.AVAILABLE),
    // Database backup/restore statuses
    DATABASE_BACKUP_IN_PROGRESS(Status.BACKUP_IN_PROGRESS),
    DATABASE_BACKUP_FINISHED(Status.AVAILABLE),
    DATABASE_BACKUP_FAILED(Status.BACKUP_FAILED),
    DATABASE_RESTORE_IN_PROGRESS(Status.RESTORE_IN_PROGRESS),
    DATABASE_RESTORE_FINISHED(Status.AVAILABLE),
    DATABASE_RESTORE_FAILED(Status.RESTORE_FAILED),
    // Load balancer update status
    CREATE_LOAD_BALANCER_ENTITY(Status.LOAD_BALANCER_UPDATE_IN_PROGRESS),
    CREATE_CLOUD_LOAD_BALANCER(Status.LOAD_BALANCER_UPDATE_IN_PROGRESS),
    COLLECT_LOAD_BALANCER_METADATA(Status.LOAD_BALANCER_UPDATE_IN_PROGRESS),
    LOAD_BALANCER_REGISTER_PUBLIC_DNS(Status.LOAD_BALANCER_UPDATE_IN_PROGRESS),
    LOAD_BALANCER_REGISTER_FREEIPA_DNS(Status.LOAD_BALANCER_UPDATE_IN_PROGRESS),
    LOAD_BALANCER_UPDATE_CM_CONFIG(Status.LOAD_BALANCER_UPDATE_IN_PROGRESS),
    LOAD_BALANCER_RESTART_CM(Status.LOAD_BALANCER_UPDATE_IN_PROGRESS),
    LOAD_BALANCER_UPDATE_FINISHED(Status.AVAILABLE),
    LOAD_BALANCER_UPDATE_FAILED(Status.LOAD_BALANCER_UPDATE_FAILED);


    private final Status status;

    DetailedStackStatus(Status status) {
        this.status = status;
    }

    public Status getStatus() {
        return status;
    }
}
