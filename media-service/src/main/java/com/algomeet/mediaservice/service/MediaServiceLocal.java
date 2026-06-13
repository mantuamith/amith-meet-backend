package com.algomeet.mediaservice.service;

import java.nio.file.Path;
import java.util.UUID;

public interface MediaServiceLocal extends MediaService {

    Path read(String userKey, UUID groupId, String mediaId);

    /**
     * Returns a scaled-down thumbnail path for the given media.
     * Supported for image files only; returns null for other types.
     *
     * @param userKey authenticated user
     * @param mediaId media document id
     * @param maxWidth maximum width of the thumbnail (default 320)
     * @return Path of the generated thumbnail, or null if not applicable
     */
    Path thumbnail(String userKey, UUID groupId, String mediaId, int maxWidth);
}
