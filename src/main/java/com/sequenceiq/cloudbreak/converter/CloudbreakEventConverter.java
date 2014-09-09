package com.sequenceiq.cloudbreak.converter;

import com.sequenceiq.cloudbreak.controller.json.CloudbreakEventsJson;
import com.sequenceiq.cloudbreak.domain.CloudbreakEvent;

public class CloudbreakEventConverter extends AbstractConverter<CloudbreakEventsJson, CloudbreakEvent> {
    @Override
    public CloudbreakEventsJson convert(CloudbreakEvent entity) {
        CloudbreakEventsJson json = new CloudbreakEventsJson();
        json.setAccountId(entity.getAccountId());
        json.setAccountName(entity.getAccountName());
        json.setBlueprintId(entity.getBlueprintId());
        json.setBlueprintName(entity.getBlueprintName());
        json.setCloud(entity.getCloud());
        json.setEventMessage(entity.getEventMessage());
        json.setEventType(entity.getEventType());
        json.setEventTimestamp(entity.getEventTimestamp());
        json.setRegion(entity.getRegion());
        json.setVmType(entity.getVmType());
        json.setUserName(entity.getUserName());
        json.setUserId(entity.getUserId());
        return json;
    }

    @Override
    public CloudbreakEvent convert(CloudbreakEventsJson json) {
        CloudbreakEvent entity = new CloudbreakEvent();
        entity.setAccountId(json.getAccountId());
        entity.setAccountName(json.getAccountName());
        entity.setBlueprintId(json.getBlueprintId());
        entity.setBlueprintName(json.getBlueprintName());
        entity.setCloud(json.getCloud());
        entity.setEventMessage(json.getEventMessage());
        entity.setEventType(json.getEventType());
        entity.setEventTimestamp(json.getEventTimestamp());
        entity.setRegion(json.getRegion());
        entity.setVmType(json.getVmType());
        entity.setUserName(json.getUserName());
        entity.setUserId(json.getUserId());
        return entity;
    }
}
