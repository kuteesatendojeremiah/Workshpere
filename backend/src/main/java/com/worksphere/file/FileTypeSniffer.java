package com.worksphere.file;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Decides what a file REALLY is by looking at its first bytes ("magic numbers"), because the
 * filename extension and the Content-Type header are both chosen by the uploader and can lie
 * (spec section 18: never trust filename extensions alone).
 *
 * <p>Only an allow-list is accepted: PNG, JPEG, GIF, PDF and plain text. Anything else (zip,
 * executables, HTML, SVG, ...) is rejected. A file is accepted only if its extension also belongs
 * to the detected type, so {@code evil.html} containing text, or {@code photo.png} containing a PDF,
 * is refused.
 */
final class FileTypeSniffer {

    record DetectedType(String mimeType, Set<String> extensions) {
    }

    private static final DetectedType PNG = new DetectedType("image/png", Set.of("png"));
    private static final DetectedType JPEG = new DetectedType("image/jpeg", Set.of("jpg", "jpeg"));
    private static final DetectedType GIF = new DetectedType("image/gif", Set.of("gif"));
    private static final DetectedType PDF = new DetectedType("application/pdf", Set.of("pdf"));
    private static final DetectedType TEXT = new DetectedType("text/plain", Set.of("txt", "md", "csv", "log"));

    private FileTypeSniffer() {
    }

    static Optional<DetectedType> detect(byte[] content) {
        if (startsWith(content, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        if (startsWith(content, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (startsWith(content, 'G', 'I', 'F', '8') && content.length > 5 && (content[4] == '7' || content[4] == '9')
                && content[5] == 'a') {
            return Optional.of(GIF);
        }
        if (startsWith(content, '%', 'P', 'D', 'F', '-')) {
            return Optional.of(PDF);
        }
        if (looksLikeText(content)) {
            return Optional.of(TEXT);
        }
        return Optional.empty();
    }

    static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean startsWith(byte[] content, int... prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((content[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /** Text = valid UTF-8 with no NUL bytes (binary files nearly always contain NULs). */
    private static boolean looksLikeText(byte[] content) {
        for (byte b : content) {
            if (b == 0) {
                return false;
            }
        }
        try {
            StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
