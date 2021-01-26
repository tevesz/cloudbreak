package com.sequenceiq.redbeams.controller.mapper;

import javax.ws.rs.core.Response.Status;

import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.common.exception.BadRequestException;

@Component
public class SpringBadRequestExceptionMapper extends BaseExceptionMapper<BadRequestException> {

    @Override
    Status getResponseStatus(BadRequestException exception) {
        return Status.BAD_REQUEST;
    }

    @Override
    Class<BadRequestException> getExceptionType() {
        return BadRequestException.class;
    }

}
