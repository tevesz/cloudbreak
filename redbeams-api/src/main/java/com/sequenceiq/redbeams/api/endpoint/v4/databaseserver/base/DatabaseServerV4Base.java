package com.sequenceiq.redbeams.api.endpoint.v4.databaseserver.base;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sequenceiq.cloudbreak.api.endpoint.v4.JsonEntity;
import com.sequenceiq.redbeams.doc.ModelDescriptions;
import com.sequenceiq.redbeams.doc.ModelDescriptions.DatabaseServer;
// import com.sequenceiq.cloudbreak.validation.ValidRDSConfigJson;

import io.swagger.annotations.ApiModelProperty;

// @ValidRDSConfigJson
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class DatabaseServerV4Base implements JsonEntity {

    @NotNull
    @Size(max = 100, min = 5, message = "The length of the database servers's name has to be in range of 5 to 100")
    @Pattern(regexp = "(^[a-z][-a-z0-9]*[a-z0-9]$)",
            message = "The database server's name may only contain lowercase letters, digits, and hyphens, and must start with an alphanumeric character")
    @ApiModelProperty(value = DatabaseServer.NAME, required = true)
    private String name;

    @Size(max = 1000000)
    @ApiModelProperty(ModelDescriptions.DESCRIPTION)
    private String description;

    @NotNull
    @ApiModelProperty(value = DatabaseServer.HOST, required = true)
    private String host;

    @NotNull
    @ApiModelProperty(value = DatabaseServer.PORT, required = true)
    private Integer port;

    @NotNull
    @ApiModelProperty(value = DatabaseServer.DATABASE_VENDOR, required = true)
    private String databaseVendor;

    @ApiModelProperty(DatabaseServer.CONNECTOR_JAR_URL)
    private String connectorJarUrl;

    @ApiModelProperty(value = ModelDescriptions.ENVIRONMENT_ID, required = true)
    private String environmentId;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getDatabaseVendor() {
        return databaseVendor;
    }

    public void setDatabaseVendor(String databaseVendor) {
        this.databaseVendor = databaseVendor;
    }

    public String getConnectorJarUrl() {
        return connectorJarUrl;
    }

    public void setConnectorJarUrl(String connectorJarUrl) {
        this.connectorJarUrl = connectorJarUrl;
    }

    public String getEnvironmentId() {
        return environmentId;
    }

    public void setEnvironmentId(String environmentId) {
        this.environmentId = environmentId;
    }
}
