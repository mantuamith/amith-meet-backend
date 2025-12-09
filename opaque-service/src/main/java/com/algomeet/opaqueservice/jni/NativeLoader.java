package com.algomeet.opaqueservice.jni;

import java.io.*;
import java.nio.file.*;

public class NativeLoader {

    public static void loadLibrary(String baseName) throws IOException {
        // Determine OS
        String os = System.getProperty("os.name").toLowerCase();
        String libFileName;

        if (os.contains("mac")) {
            libFileName = "lib" + baseName + ".dylib";
        } else if (os.contains("linux")) {
            libFileName = "lib" + baseName + ".so";
        } else {
            throw new UnsupportedOperationException("Unsupported OS: " + os);
        }

        // Resource path inside JAR
        String resourcePath = "/native/" + libFileName;
        InputStream in = NativeLoader.class.getResourceAsStream(resourcePath);
        if (in == null) {
            throw new FileNotFoundException("Native library not found in resources: " + resourcePath);
        }

        // Copy to temp file
        Path tempFile = Files.createTempFile("lib", libFileName);
        tempFile.toFile().deleteOnExit();
        Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);

        // Load library
        System.load(tempFile.toAbsolutePath().toString());
    }
}