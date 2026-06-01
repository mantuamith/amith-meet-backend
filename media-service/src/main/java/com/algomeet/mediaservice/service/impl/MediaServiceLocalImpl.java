package com.algomeet.mediaservice.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.algomeet.mediaservice.config.StorageProperties;
import com.algomeet.mediaservice.document.FilePermission;
import com.algomeet.mediaservice.document.UserFileDocument;
import com.algomeet.mediaservice.dto.MediaUploadResponse;
import com.algomeet.mediaservice.dto.StorageUsageAdjustmentRequest;
import com.algomeet.mediaservice.enums.Storage;
import com.algomeet.mediaservice.enums.UploadContext;
import com.algomeet.mediaservice.service.MediaServiceLocal;
import com.algomeet.mediaservice.service.UserFileService;
import com.algomeet.mediaservice.util.MediaMetadataExtractor;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@AllArgsConstructor
public class MediaServiceLocalImpl implements MediaServiceLocal {

    private StorageProperties storageProperties;
    private UserFileService userFileService;
    private UserStorageUsageService userStorageUsageService;
    private MediaMetadataExtractor metadataExtractor;

    @Override
    public MediaUploadResponse upload(
            String userKey,
            MultipartFile file,
            String contentType,
            boolean encrypted,
            boolean autoExpire,
            String conversationId,
            UploadContext uploadContext
    ) {
        try {
            String storageDir = storageProperties.getLocal().getDir() +
                    (storageProperties.getLocal().getDir().trim().endsWith("/") ? "" : "/");

            Files.createDirectories(Paths.get(storageDir));

            String mediaId = UUID.randomUUID().toString();
            String filename = mediaId + "_" + file.getOriginalFilename();

            Path target = Paths.get(storageDir).resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            log.info("Media stored at {}", target);

            // Build response with optional metadata
            MediaUploadResponse.MediaUploadResponseBuilder responseBuilder = MediaUploadResponse.builder()
                    .mediaId(mediaId)
                    .originalFilename(file.getOriginalFilename())
                    .contentType(contentType != null ? contentType : file.getContentType())
                    .size(file.getSize())
                    .encrypted(encrypted)
                    .url("/media/" + mediaId)
                    .conversationId(conversationId);

            metadataExtractor.populate(file, responseBuilder);
            MediaUploadResponse response = responseBuilder.build();

            // Persist document
            UserFileDocument userFile = new UserFileDocument();
            userFile.setId(mediaId);
            userFile.setFilename(filename);
            userFile.setContentType(contentType != null ? contentType : file.getContentType());
            userFile.setSize(file.getSize());
            userFile.setAbsolutePath(target.toUri().getPath());
            userFile.setEncrypted(encrypted);
            userFile.setOwner(userKey);
            userFile.setStorage(Storage.LOCAL.name());
            userFile.setConversationId(conversationId);
            userFile.setUploadContext(uploadContext != null ? uploadContext.name() : UploadContext.MEDIA.name());
            userFile.setMediaWidth(response.getMediaWidth());
            userFile.setMediaHeight(response.getMediaHeight());

            if (autoExpire) {
                userFile.setCleanupEligibleAt(
                        Instant.now().plus(Duration.ofHours(storageProperties.getUnsharedFileExpirationHours())));
            } else {
                adjustStorageUsage(userKey, file.getSize(), uploadContext);
            }

            userFileService.create(userFile);
            return response;

        } catch (IOException e) {
            throw new RuntimeException("Failed to store media", e);
        }
    }

    @Override
    public Path read(String userKey, String mediaId) {
        UserFileDocument file = userFileService.getFile(mediaId, userKey, FilePermission.READ);

        Path filePath = Paths.get(file.getAbsolutePath());
        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            throw new RuntimeException("File not found: " + file.getFilename());
        }

        return filePath;
    }

    /**
     * Generates a scaled thumbnail in a temp directory for image files.
     * Returns null for non-image files (video thumbnail requires native tooling).
     */
    @Override
    public Path thumbnail(String userKey, String mediaId, int maxWidth) {
        UserFileDocument fileDoc = userFileService.getFile(mediaId, userKey, FilePermission.READ);

        String ct = fileDoc.getContentType();
        if (ct == null || !ct.startsWith("image/")) {
            return null;
        }

        Path source = Paths.get(fileDoc.getAbsolutePath());
        if (!Files.exists(source)) return null;

        try {
            BufferedImage original = ImageIO.read(source.toFile());
            if (original == null) return null;

            int origWidth = original.getWidth();
            int origHeight = original.getHeight();

            int thumbWidth = Math.min(maxWidth, origWidth);
            int thumbHeight = (int) ((double) origHeight / origWidth * thumbWidth);

            BufferedImage thumb = new BufferedImage(thumbWidth, thumbHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = thumb.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(original, 0, 0, thumbWidth, thumbHeight, null);
            g.dispose();

            String format = ct.contains("png") ? "png" : "jpg";
            Path thumbPath = Files.createTempFile("thumb_" + mediaId + "_", "." + format);
            try (OutputStream out = Files.newOutputStream(thumbPath)) {
                ImageIO.write(thumb, format, out);
            }

            return thumbPath;

        } catch (IOException e) {
            log.warn("Could not generate thumbnail for {}: {}", mediaId, e.getMessage());
            return null;
        }
    }

    @Override
    public boolean deleteIfExists(String fileLocation) throws IOException {
        return Files.deleteIfExists(Paths.get(fileLocation));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void adjustStorageUsage(String userKey, long fileSize, UploadContext context) {
        StorageUsageAdjustmentRequest req = new StorageUsageAdjustmentRequest();
        if (context == UploadContext.CHAT) {
            req.setChatStorageBytesDelta(fileSize);
            req.setChatMessageCountDelta(1L);
        } else {
            req.setMediaStorageBytesDelta(fileSize);
            req.setMediaFileCountDelta(1L);
        }
        userStorageUsageService.adjustUsage(UUID.fromString(userKey), req);
    }
}
