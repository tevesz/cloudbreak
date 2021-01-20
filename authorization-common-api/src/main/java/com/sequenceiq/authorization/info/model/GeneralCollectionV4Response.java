package com.sequenceiq.authorization.info.model;

import java.util.Collection;

public class GeneralCollectionV4Response<T> {

    private Collection<T> responses;

    public GeneralCollectionV4Response(Collection<T> responses) {
        this.responses = responses;
    }

    public Collection<T> getResponses() {
        return responses;
    }

    public void setResponses(Collection<T> responses) {
        this.responses = responses;
    }
}
