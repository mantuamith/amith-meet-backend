package com.algomeet.mediaservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import com.algomeet.mediaservice.config.StorageProperties;
import com.algomeet.mediaservice.document.FilePermission;
import com.algomeet.mediaservice.document.UserFileDocument;
import com.algomeet.mediaservice.service.FileAccessPermission;
import com.algomeet.mediaservice.service.UserFileService;
import com.algomeet.mediaservice.util.MediaMetadataExtractor;

import ws.schild.jave.MultimediaObject;
import ws.schild.jave.ScreenExtractor;
import ws.schild.jave.info.MultimediaInfo;

@ExtendWith(MockitoExtension.class)
class MediaServiceLocalImplThumbnailTest {

    @TempDir
    Path tempDir;

    @Mock private StorageProperties storageProperties;
    @Mock private UserFileService userFileService;
    @Mock private UserStorageUsageService userStorageUsageService;
    @Mock private MediaMetadataExtractor metadataExtractor;
    
    @Mock
	private FileAccessPermission fileAccessPermission;

    @InjectMocks
    private MediaServiceLocalImpl mediaService;

    private static final String USER_KEY = "user-123";
    private static final String MEDIA_ID = "media-abc";
    private static final UUID GROUP_ID = UUID.fromString("22211111-1111-1111-1111-111111111111");

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Creates a real JPEG on disk and returns its path. */
    private Path writeJpeg(int width, int height) throws Exception {
        Path p = tempDir.resolve("test.jpg");
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        try (OutputStream out = Files.newOutputStream(p)) {
            ImageIO.write(img, "jpg", out);
        }
        return p;
    }

    /** Creates a real PNG on disk and returns its path. */
    private Path writePng(int width, int height) throws Exception {
        Path p = tempDir.resolve("test.png");
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.GREEN);
        g.fillRect(0, 0, width, height);
        g.dispose();
        try (OutputStream out = Files.newOutputStream(p)) {
            ImageIO.write(img, "png", out);
        }
        return p;
    }

    /** Stubs userFileService.getFile() to return a doc with given content-type and path. */
    private UserFileDocument stubFile(String contentType, Path absolutePath) {
        UserFileDocument doc = new UserFileDocument();
        doc.setContentType(contentType);
        doc.setAbsolutePath(absolutePath != null ? absolutePath.toString() : "/nonexistent/missing.jpg");
        when(fileAccessPermission.hasPermission(eq(doc), eq(USER_KEY), eq(GROUP_ID), eq(FilePermission.READ)))
                .thenReturn(true);
        
        return doc;
    }

    // ── null / unsupported content-type ──────────────────────────────────────

    @Test
    void thumbnail_nullContentType_returnsNull() {
    	UserFileDocument doc = stubFile(null, tempDir.resolve("x.jpg"));
        assertNull(mediaService.thumbnail(doc, USER_KEY, GROUP_ID, MEDIA_ID, 320));
    }

    @Test
    void thumbnail_audioContentType_returnsNull() {
    	UserFileDocument doc = stubFile("audio/mpeg", tempDir.resolve("x.mp3"));
        assertNull(mediaService.thumbnail(doc, USER_KEY, GROUP_ID, MEDIA_ID, 320));
    }

    @Test
    void thumbnail_documentContentType_returnsNull() {
    	UserFileDocument doc = stubFile("application/pdf", tempDir.resolve("x.pdf"));
        assertNull(mediaService.thumbnail(doc, USER_KEY, GROUP_ID, MEDIA_ID, 320));
    }

    // ── image — file missing ──────────────────────────────────────────────────

    @Test
    void thumbnail_imageMissingOnDisk_returnsNull() {
    	UserFileDocument doc = stubFile("image/jpeg", null);   // path set to /nonexistent/…
        assertNull(mediaService.thumbnail(doc, USER_KEY, GROUP_ID, MEDIA_ID, 320));
    }

    // ── image — happy paths ───────────────────────────────────────────────────

    @Test
    void thumbnail_validJpeg_returnsThumbnailPath() throws Exception {
        Path img = writeJpeg(640, 480);
        UserFileDocument doc = stubFile("image/jpeg", img);

        Path result = mediaService.thumbnail(doc, USER_KEY, GROUP_ID, MEDIA_ID, 320);

        assertNotNull(result);
        assertTrue(Files.exists(result));
        assertTrue(result.getFileName().toString().endsWith(".jpg"));
    }

    @Test
    void thumbnail_validPng_returnsPngThumbnail() throws Exception {
        Path img = writePng(400, 300);
        UserFileDocument doc = stubFile("image/png", img);

        Path result = mediaService.thumbnail(doc, USER_KEY, GROUP_ID, MEDIA_ID, 320);

        assertNotNull(result);
        assertTrue(Files.exists(result));
        assertTrue(result.getFileName().toString().endsWith(".png"));
    }

    @Test
    void thumbnail_imageWiderThanMaxWidth_isScaledDown() throws Exception {
        // 640 px wide → maxWidth 100 → thumb should be 100 px wide
        Path img = writeJpeg(640, 480);
        UserFileDocument doc = stubFile("image/jpeg", img);

        Path result = mediaService.thumbnail(doc, USER_KEY, GROUP_ID, MEDIA_ID, 100);

        assertNotNull(result);
        BufferedImage thumb = ImageIO.read(result.toFile());
        assertNotNull(thumb);
        assertEquals(100, thumb.getWidth());
        // aspect ratio preserved: 480/640 * 100 = 75
        assertEquals(75, thumb.getHeight());
    }

    @Test
    void thumbnail_imageSmallerThanMaxWidth_isNotUpscaled() throws Exception {
        // 50 px wide, maxWidth 320 → thumb stays at 50 px wide
        Path img = writeJpeg(50, 40);
        UserFileDocument doc = stubFile("image/jpeg", img);

        Path result = mediaService.thumbnail(doc, USER_KEY, GROUP_ID, MEDIA_ID, 320);

        assertNotNull(result);
        BufferedImage thumb = ImageIO.read(result.toFile());
        assertNotNull(thumb);
        assertEquals(50, thumb.getWidth());
        assertEquals(40, thumb.getHeight());
    }

    @Test
    void thumbnail_imageExactlyMaxWidth_isNotChanged() throws Exception {
        Path img = writeJpeg(320, 240);
        UserFileDocument doc = stubFile("image/jpeg", img);

        Path result = mediaService.thumbnail(doc, USER_KEY, GROUP_ID, MEDIA_ID, 320);

        assertNotNull(result);
        BufferedImage thumb = ImageIO.read(result.toFile());
        assertNotNull(thumb);
        assertEquals(320, thumb.getWidth());
    }

    // ── video — happy path (JAVE2 mocked via mockConstruction) ───────────────

    @Test
    void thumbnail_videoContentType_returnsThumbnailPath() throws Exception {
        Path fakeVideo = Files.createTempFile(tempDir, "video-", ".mp4");
        UserFileDocument doc = stubFile("video/mp4", fakeVideo);

        MultimediaInfo info = new MultimediaInfo();
        info.setDuration(30_000L);  // 30-second video

        try (
            MockedConstruction<MultimediaObject> mockedMedia =
                mockConstruction(MultimediaObject.class,
                    (mock, ctx) -> when(mock.getInfo()).thenReturn(info));
            MockedConstruction<ScreenExtractor> mockedExtractor =
                mockConstruction(ScreenExtractor.class, (mock, ctx) ->
                    doAnswer(inv -> {
                        // Write minimal non-empty bytes so the empty-file guard passes
                        File out = inv.getArgument(4);
                        try (FileOutputStream fos = new FileOutputStream(out)) { fos.write(new byte[]{0x42}); }
                        return null;
                    }).when(mock).renderOneImage(any(), anyInt(), anyInt(), anyLong(), any(File.class), anyInt()))
        ) {
            Path result = mediaService.thumbnail(doc, USER_KEY, GROUP_ID, MEDIA_ID, 320);

            assertNotNull(result);
            assertTrue(Files.exists(result));
            assertTrue(result.getFileName().toString().endsWith(".jpg"));

            // verify seek was 10 % of 30 000 ms = 3 000 ms (≥ 1 000 ms floor)
            ScreenExtractor extractor = mockedExtractor.constructed().get(0);
            // height falls back to maxWidth (320) because MultimediaInfo.getVideo() is null in the mock
            verify(extractor).renderOneImage(any(), eq(320), eq(320), eq(3_000L), any(File.class), eq(1));
        }
    }

    @Test
    void thumbnail_shortVideo_seeksCappedAtOneSecond() throws Exception {
        Path fakeVideo = Files.createTempFile(tempDir, "video-", ".mp4");
        UserFileDocument doc = stubFile("video/mp4", fakeVideo);

        // 5-second video → 10 % = 500 ms, below the 1 000 ms floor → expect 1 000
        MultimediaInfo info = new MultimediaInfo();
        info.setDuration(5_000L);

        try (
            MockedConstruction<MultimediaObject> mockedMedia =
                mockConstruction(MultimediaObject.class,
                    (mock, ctx) -> when(mock.getInfo()).thenReturn(info));
            MockedConstruction<ScreenExtractor> mockedExtractor =
                mockConstruction(ScreenExtractor.class, (mock, ctx) ->
                    doAnswer(inv -> {
                        File out = inv.getArgument(4);
                        try (FileOutputStream fos = new FileOutputStream(out)) { fos.write(new byte[]{0x42}); }
                        return null;
                    }).when(mock).renderOneImage(any(), anyInt(), anyInt(), anyLong(), any(File.class), anyInt()))
        ) {
            Path result = mediaService.thumbnail(doc, USER_KEY, GROUP_ID, MEDIA_ID, 320);

            assertNotNull(result);
            ScreenExtractor extractor = mockedExtractor.constructed().get(0);
            // height falls back to maxWidth (320) because MultimediaInfo.getVideo() is null in the mock
            verify(extractor).renderOneImage(any(), eq(320), eq(320), eq(1_000L), any(File.class), eq(1));
        }
    }

    @Test
    void thumbnail_videoExtractionFails_returnsNull() throws Exception {
        Path fakeVideo = Files.createTempFile(tempDir, "video-", ".mp4");
        UserFileDocument doc = stubFile("video/mp4", fakeVideo);

        MultimediaInfo info = new MultimediaInfo();
        info.setDuration(10_000L);

        try (
            MockedConstruction<MultimediaObject> mockedMedia =
                mockConstruction(MultimediaObject.class,
                    (mock, ctx) -> when(mock.getInfo()).thenReturn(info));
            MockedConstruction<ScreenExtractor> mockedExtractor =
                mockConstruction(ScreenExtractor.class,
                    (mock, ctx) -> doThrow(new RuntimeException("ffmpeg error"))
                        .when(mock).renderOneImage(any(), anyInt(), anyInt(), anyLong(), any(File.class), anyInt()))
        ) {
            Path result = mediaService.thumbnail(doc, USER_KEY, GROUP_ID, MEDIA_ID, 320);
            assertNull(result);
        }
    }
}
