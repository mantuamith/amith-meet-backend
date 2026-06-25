package com.algomeet.mediaservice.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.algomeet.mediaservice.config.AcceptedFileProperties;
import com.algomeet.mediaservice.exceptions.FileSizeExceededException;
import com.algomeet.mediaservice.exceptions.FileTypeNotSupportedException;

class FileValidatorTest {

    private FileValidator validator;
    private AcceptedFileProperties props;

    // Minimal valid file bytes per type (real magic bytes so Tika detects correctly)
    private static final byte[] JPEG_BYTES = new byte[]{
            (byte)0xFF,(byte)0xD8,(byte)0xFF,(byte)0xE0,0x00,0x10,
            0x4A,0x46,0x49,0x46,0x00,0x01,0x01,0x00,0x00,0x01,0x00,0x01,0x00,0x00,
            (byte)0xFF,(byte)0xD9
    };
    private static final byte[] MP4_BYTES  = hexToBytes("0000001C667479706D703432");
    private static final byte[] MP3_BYTES  = hexToBytes("494433"); // ID3 tag
    private static final byte[] PDF_BYTES  = "%PDF-1.4 test".getBytes();
    private static final byte[] ZIP_BYTES  = hexToBytes("504B0304");

    @BeforeEach
    void setup() {
        props = new AcceptedFileProperties();
        props.setImageExtensions(Set.of("jpg", "jpeg", "png", "gif"));
        props.setVideoExtensions(Set.of("mp4", "mov", "mkv"));
        props.setAudioExtensions(Set.of("mp3", "aac", "m4a"));
        props.setDocumentExtensions(Set.of("pdf", "doc", "docx", "txt"));
        props.setArchiveExtensions(Set.of("zip", "7z", "rar", "gz", "tar"));
        props.setMaxArchiveSize(100 * 1024 * 1024L);
        props.setMaxImageSize(20 * 1024 * 1024L);    // 20 MB
        props.setMaxVideoSize(200 * 1024 * 1024L);   // 200 MB
        props.setMaxAudioSize(50 * 1024 * 1024L);    // 50 MB
        props.setMaxDocumentSize(100 * 1024 * 1024L); // 100 MB
        validator = new FileValidator(props);
    }

    /* =========================
       SUPPORTED FILE TYPES
       ========================= */

    @Test
    void image_withinLimit_passes() {
        MockMultipartFile file = mockFile("photo.jpg", "image/jpeg", JPEG_BYTES);
        assertDoesNotThrow(() -> validator.validate(file, false));
    }

    @Test
    void pdf_withinLimit_passes() {
        MockMultipartFile file = mockFile("doc.pdf", "application/pdf", PDF_BYTES);
        assertDoesNotThrow(() -> validator.validate(file, false));
    }

    @Test
    void zip_withinLimit_passes() {
        MockMultipartFile file = mockFile("archive.zip", "application/zip", ZIP_BYTES);
        assertDoesNotThrow(() -> validator.validate(file, false));
    }

    /* =========================
       UNSUPPORTED FILE TYPES
       ========================= */

    @Test
    void exe_rejected() {
        MockMultipartFile file = new MockMultipartFile("file", "virus.exe",
                "application/octet-stream", new byte[]{0x4D, 0x5A}); // MZ header
        assertThrows(FileTypeNotSupportedException.class,
                () -> validator.validate(file, false));
    }

    @Test
    void unsupportedExtension_rejected() {
        // valid JPEG bytes but saved as .bmp which is not in our set
        MockMultipartFile file = mockFile("photo.bmp", "image/bmp", JPEG_BYTES);
        assertThrows(FileTypeNotSupportedException.class,
                () -> validator.validate(file, false));
    }

    /* =========================
       IMAGE SIZE LIMIT (20 MB)
       ========================= */

    @Test
    void image_exactlyAtLimit_passes() {
        byte[] data = buildJpeg(20 * 1024 * 1024);
        MockMultipartFile file = mockFile("photo.jpg", "image/jpeg", data);
        assertDoesNotThrow(() -> validator.validate(file, false));
    }

    @Test
    void image_overLimit_throws413() {
        byte[] data = buildJpeg(21 * 1024 * 1024); // 21 MB
        MockMultipartFile file = mockFile("photo.jpg", "image/jpeg", data);

        FileSizeExceededException ex = assertThrows(FileSizeExceededException.class,
                () -> validator.validate(file, false));
        assertTrue(ex.getMessage().contains("20 MB"));
        assertTrue(ex.getMessage().contains("image"));
    }

    /* =========================
       VIDEO SIZE LIMIT (200 MB)
       ========================= */

    @Test
    void video_withinLimit_passes() {
        byte[] data = buildMp4(100 * 1024 * 1024); // 100 MB — under 200 MB limit
        MockMultipartFile file = mockFile("clip.mp4", "video/mp4", data);
        assertDoesNotThrow(() -> validator.validate(file, false));
    }

    @Test
    void video_overLimit_throws413() {
        byte[] data = buildMp4(201 * 1024 * 1024); // 201 MB
        MockMultipartFile file = mockFile("clip.mp4", "video/mp4", data);

        FileSizeExceededException ex = assertThrows(FileSizeExceededException.class,
                () -> validator.validate(file, false));
        assertTrue(ex.getMessage().contains("200 MB"));
        assertTrue(ex.getMessage().contains("video"));
    }

    /* =========================
       AUDIO SIZE LIMIT (50 MB)
       ========================= */

    @Test
    void audio_withinLimit_passes() {
        byte[] data = buildMp3(10 * 1024 * 1024); // 10 MB
        MockMultipartFile file = mockFile("song.mp3", "audio/mpeg", data);
        assertDoesNotThrow(() -> validator.validate(file, false));
    }

    @Test
    void audio_overLimit_throws413() {
        byte[] data = buildMp3(51 * 1024 * 1024); // 51 MB
        MockMultipartFile file = mockFile("song.mp3", "audio/mpeg", data);

        FileSizeExceededException ex = assertThrows(FileSizeExceededException.class,
                () -> validator.validate(file, false));
        assertTrue(ex.getMessage().contains("50 MB"));
        assertTrue(ex.getMessage().contains("audio"));
    }

    /* =========================
       DOCUMENT SIZE LIMIT (100 MB)
       ========================= */

    @Test
    void document_withinLimit_passes() {
        byte[] data = buildPdf(50 * 1024 * 1024); // 50 MB
        MockMultipartFile file = mockFile("report.pdf", "application/pdf", data);
        assertDoesNotThrow(() -> validator.validate(file, false));
    }

    @Test
    void document_overLimit_throws413() {
        byte[] data = buildPdf(101 * 1024 * 1024); // 101 MB
        MockMultipartFile file = mockFile("report.pdf", "application/pdf", data);

        FileSizeExceededException ex = assertThrows(FileSizeExceededException.class,
                () -> validator.validate(file, false));
        assertTrue(ex.getMessage().contains("100 MB"));
        assertTrue(ex.getMessage().contains("document"));
    }

    /* =========================
       COMPRESSED FILE SIZE LIMIT (100 MB — uses document limit)
       ========================= */

    @Test
    void zip_overLimit_throws413() {
        byte[] data = buildZip(101 * 1024 * 1024); // 101 MB
        MockMultipartFile file = mockFile("archive.zip", "application/zip", data);

        FileSizeExceededException ex = assertThrows(FileSizeExceededException.class,
                () -> validator.validate(file, false));
        assertTrue(ex.getMessage().contains("100 MB"));
    }

    /* =========================
       ERROR MESSAGE QUALITY
       ========================= */

    @Test
    void errorMessage_includesActualSizeMB() {
        byte[] data = buildJpeg(21 * 1024 * 1024); // 21 MB
        MockMultipartFile file = mockFile("photo.jpg", "image/jpeg", data);

        FileSizeExceededException ex = assertThrows(FileSizeExceededException.class,
                () -> validator.validate(file, false));
        // Should say something like "received 21.0 MB"
        assertTrue(ex.getMessage().contains("21.0 MB"), "Message was: " + ex.getMessage());
    }

    /* =========================
       ENCRYPTED FILES (skip size check based on extension only)
       ========================= */

    @Test
    void encrypted_validExtension_passes() {
        MockMultipartFile file = mockFile("photo.jpg", "application/octet-stream",
                new byte[]{0x01, 0x02, 0x03});
        assertDoesNotThrow(() -> validator.validate(file, true));
    }

    @Test
    void encrypted_invalidExtension_rejected() {
        MockMultipartFile file = mockFile("file.exe", "application/octet-stream",
                new byte[]{0x01, 0x02});
        assertThrows(FileTypeNotSupportedException.class,
                () -> validator.validate(file, true));
    }

    /* =========================
       HELPERS
       ========================= */

    private MockMultipartFile mockFile(String filename, String contentType, byte[] content) {
        return new MockMultipartFile("file", filename, contentType, content);
    }

    /** Builds a valid JPEG with padded size */
    private byte[] buildJpeg(int totalSize) {
        byte[] header = new byte[]{
                (byte)0xFF,(byte)0xD8,(byte)0xFF,(byte)0xE0,0x00,0x10,
                0x4A,0x46,0x49,0x46,0x00,0x01,0x01,0x00,0x00,0x01,0x00,0x01,0x00,0x00
        };
        byte[] footer = new byte[]{(byte)0xFF,(byte)0xD9};
        byte[] data = new byte[totalSize];
        System.arraycopy(header, 0, data, 0, header.length);
        System.arraycopy(footer, 0, data, totalSize - footer.length, footer.length);
        return data;
    }

    /** Builds a valid MP4 ftyp box with padded size */
    private byte[] buildMp4(int totalSize) {
        byte[] header = hexToBytes("0000001C667479706D703432");
        byte[] data = new byte[totalSize];
        System.arraycopy(header, 0, data, 0, header.length);
        return data;
    }

    /** Builds an MP3 with ID3 header and padded size */
    private byte[] buildMp3(int totalSize) {
        byte[] header = new byte[]{0x49, 0x44, 0x33, 0x04, 0x00, 0x00}; // ID3v2.4
        byte[] data = new byte[totalSize];
        System.arraycopy(header, 0, data, 0, header.length);
        return data;
    }

    /** Builds a PDF with magic bytes and padded size */
    private byte[] buildPdf(int totalSize) {
        byte[] header = "%PDF-1.4\n".getBytes();
        byte[] data = new byte[totalSize];
        System.arraycopy(header, 0, data, 0, header.length);
        return data;
    }

    /** Builds a ZIP with magic bytes and padded size */
    private byte[] buildZip(int totalSize) {
        byte[] header = hexToBytes("504B0304");
        byte[] data = new byte[totalSize];
        System.arraycopy(header, 0, data, 0, header.length);
        return data;
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
