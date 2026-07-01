package com.algomeet.mediaservice.util;

import org.springframework.stereotype.Component;

@Component
public class RequestUserContext {

    public String getUserKey() {
        return SecurityUtil.getUserKey();
    }

    public Integer getTenantId() {
        return SecurityUtil.getTenantId();
    }

    public String getMeetingId() {
        return SecurityUtil.getMeetingId();
    }

    public boolean isFileUploadFeatureEnabled() {
        return SecurityUtil.isFileUploadFeatureEnabled();
    }
}
