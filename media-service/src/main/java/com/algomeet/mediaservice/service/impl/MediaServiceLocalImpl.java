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

import ws.schild.jave.MultimediaObject;
import ws.schild.jave.ScreenExtractor;
import ws.schild.jave.info.VideoSize;

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
    public Path read(String userKey, UUID groupId, String mediaId) {
        UserFileDocument file = userFileService.getFile(mediaId, userKey, groupId, FilePermission.READ);

        Path filePath = Paths.get(file.getAbsolutePath());
        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            throw new RuntimeException("File not found: " + file.getFilename());
        }

        return filePath;
    }

    /**
     * Generates a scaled thumbnail for image and video files.
     * - Images: scaled in-process via ImageIO/AWT.
     * - Videos: first frame extracted via JAVE2 (lightweight FFmpeg wrapper).
     * Returns null for unsupported content types or on any error.
     */
    @Override
    public Path thumbnail(String userKey, UUID groupId, String mediaId, int maxWidth) {
        UserFileDocument fileDoc = userFileService.getFile(mediaId, userKey, groupId, FilePermission.READ);

        String ct = fileDoc.getContentType();
        // the file is neither an image nor a video.
        if (ct == null || (!ct.startsWith("image/") && !ct.startsWith("video/"))) {
            return null;
        }

        Path source = Paths.get(fileDoc.getAbsolutePath());
        if (!Files.exists(source)) return null;

        if (ct.startsWith("video/")) {
            return generateVideoThumbnail(source, mediaId, maxWidth);
        } else {
            return generateImageThumbnail(source, ct, mediaId, maxWidth);
        }
    }

    // ── thumbnail helpers ─────────────────────────────────────────────────────

    private Path generateImageThumbnail(Path source, String ct, String mediaId, int maxWidth) {
        try {
            BufferedImage original = ImageIO.read(source.toFile());
            if (original == null) return null;

            int origWidth  = original.getWidth();
            int origHeight = original.getHeight();

            int thumbWidth  = Math.min(maxWidth, origWidth);
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
            log.warn("Could not generate image thumbnail for {}: {}", mediaId, e.getMessage());
            return null;
        }
    }

    private Path generateVideoThumbnail(Path source, String mediaId, int maxWidth) {
        try {
            MultimediaObject media = new MultimediaObject(source.toFile());
            long durationMs = media.getInfo().getDuration();

            // Seek to 10 % of the video duration (min 1 s) to avoid black opening frames.
            // renderOneImage() takes millis — no conversion needed.
            long seekMs = Math.max(1_000L, durationMs / 10);

            // Compute actual pixel dimensions.
            // renderOneImage() passes -s <width>x<height> to ffmpeg, which does NOT support -1
            // as an "auto" value (unlike -vf scale=W:-1). Passing -s 320x-1 causes ffmpeg to
            // exit with an error → empty output file. We must supply exact integer dimensions.
            int thumbWidth = maxWidth;
            int thumbHeight = maxWidth; // fallback square; overwritten when video info is available
            try {
                VideoSize vs = media.getInfo().getVideo().getSize();
                if (vs != null && vs.getWidth() > 0 && vs.getHeight() > 0) {
                    thumbWidth  = Math.min(maxWidth, vs.getWidth());
                    thumbHeight = (int) Math.round((double) vs.getHeight() / vs.getWidth() * thumbWidth);
                    if (thumbHeight < 1) thumbHeight = 1;
                }
            } catch (Exception ignored) {
                // if video info unavailable, fall back to square crop
            }

            Path thumbPath = Files.createTempFile("thumb_" + mediaId + "_", ".jpg");
            Files.delete(thumbPath); // remove placeholder — ffmpeg must create it fresh
            ScreenExtractor extractor = new ScreenExtractor();
            extractor.renderOneImage(
                media,
                thumbWidth, thumbHeight, // exact dimensions — no -1 allowed in -s flag
                seekMs,                  // seek position in milliseconds
                thumbPath.toFile(),
                1                        // quality: 1 = best, 31 = worst
            );

            // If the file doesn't exist or is empty, extraction silently failed.
            if (!Files.exists(thumbPath) || Files.size(thumbPath) == 0) {
                log.warn("Video thumbnail empty after extraction for {}", mediaId);
                Files.deleteIfExists(thumbPath);
                return null;
            }

            return thumbPath;

        } catch (Exception e) {
            log.warn("Could not generate video thumbnail for {}: {}", mediaId, e.getMessage());
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
