package com.algomeet.mediaservice.util;

import java.awt.image.BufferedImage;
import java.io.InputStream;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.algomeet.mediaservice.dto.MediaUploadResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Extracts width/height for images from the raw bytes.
 * Video duration requires native tooling (FFmpeg) and is intentionally left null
 * so the mobile client can supply it from the local capture session.
 */
@Slf4j
@Component
public class MediaMetadataExtractor {

    public void populate(MultipartFile file, MediaUploadResponse.MediaUploadResponseBuilder builder) {
        String ct = file.getContentType();
        if (ct == null) return;

        if (ct.startsWith("image/")) {
            extractImageDimensions(file, builder);
        }
        // video/audio: duration stays null — client knows it from capture
    }

    private void extractImageDimensions(MultipartFile file,
                                        MediaUploadResponse.MediaUploadResponseBuilder builder) {
        try (InputStream in = file.getInputStream()) {
            BufferedImage img = ImageIO.read(in);
            if (img != null) {
                builder.mediaWidth(img.getWidth());
                builder.mediaHeight(img.getHeight());
            }
        } catch (Exception e) {
            log.debug("Could not extract image dimensions for {}: {}", file.getOriginalFilename(), e.getMessage());
        }
    }
}
