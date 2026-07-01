package com.algomeet.mediaservice.security;

public record ProsodyFileSharingPrincipal(
        String userId,
        String displayName,
        String affiliation,
        String meetingId,
        boolean fileUploadFeatureEnabled) {
}
