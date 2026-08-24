package com.ultralatency.matching.qualification;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** JDK-only SHA-256 hashing for committed summaries and ignored raw artifacts. */
public final class QualificationArtifactHasher {

    private QualificationArtifactHasher() {
    }

    /** Returns the lowercase SHA-256 digest of one regular file. */
    public static String sha256(final Path file) throws IOException {
        if (file == null || !Files.isRegularFile(file)) {
            throw new IOException("artifact is not a regular file: " + file);
        }
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
        try (InputStream input = Files.newInputStream(file)) {
            final byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
