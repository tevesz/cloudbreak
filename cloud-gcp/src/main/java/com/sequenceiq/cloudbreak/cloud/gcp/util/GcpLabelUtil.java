package com.sequenceiq.cloudbreak.cloud.gcp.util;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sequenceiq.cloudbreak.auth.altus.Crn;
import com.sequenceiq.cloudbreak.cloud.model.CloudStack;

public final class GcpLabelUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(GcpLabelUtil.class);

    private static final int GCP_MAX_TAG_LEN = 63;

    private GcpLabelUtil() {
    }

    public static Map<String, String> createLabelsFromTags(CloudStack cloudStack) {
        Map<String, String> tags = cloudStack.getTags();
        return createLabelsFromTagsMap(tags);
    }

    public static Map<String, String> createLabelsFromTagsMap(Map<String, String> tags) {
        Map<String, String> result = new HashMap<>();
        if (tags != null) {
            tags.forEach((key, value) -> result.put(transform(key), transform(value)));
        }
        return result;
    }

    private static String transform(String value) {
        // GCP labels have strict rules https://cloud.google.com/compute/docs/labeling-resources
        LOGGER.debug("Transforming tag key/value for GCP.");
        if (Crn.isCrn(value)) {
            try {
                Crn crn = Crn.fromString(value);
                value = crn == null ? value : crn.getResource();
            } catch (Exception e) {
                LOGGER.debug("Ignoring CRN ({}) parse error during tag value generation : {}", value, e.getMessage());
            }
        }
        String sanitized = value.split("@")[0].toLowerCase().replaceAll("[^\\w]", "-");
        String shortenedValue = StringUtils.right(sanitized, GCP_MAX_TAG_LEN);
        LOGGER.debug("GCP label element has been transformed from '{}' to '{}'", value, shortenedValue);
        return shortenedValue;
    }
}
