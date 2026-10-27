package com.worksphere.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FileTypeSnifferTest {

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    @Test
    void recognisesTheAcceptedFormatsByTheirFirstBytes() {
        assertThat(mime(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0))).isEqualTo("image/png");
        assertThat(mime(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10))).isEqualTo("image/jpeg");
        assertThat(mime("GIF89a....".getBytes(StandardCharsets.US_ASCII))).isEqualTo("image/gif");
        assertThat(mime("GIF87a....".getBytes(StandardCharsets.US_ASCII))).isEqualTo("image/gif");
        assertThat(mime("%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII))).isEqualTo("application/pdf");
        assertThat(mime("Just some notes, with ünïcode.\n".getBytes(StandardCharsets.UTF_8))).isEqualTo("text/plain");
    }

    @Test
    void rejectsExecutablesArchivesAndOtherBinaryContent() {
        assertThat(FileTypeSniffer.detect(bytes('M', 'Z', 0x90, 0, 3, 0, 0, 0))).as("Windows executable").isEmpty();
        assertThat(FileTypeSniffer.detect(bytes('P', 'K', 3, 4, 0, 0, 0, 0))).as("zip").isEmpty();
        assertThat(FileTypeSniffer.detect(bytes(0x7F, 'E', 'L', 'F', 2, 1, 1, 0))).as("ELF binary").isEmpty();
        assertThat(FileTypeSniffer.detect(bytes(0xC3, 0x28, 0xA0, 0xA1))).as("invalid UTF-8").isEmpty();
    }

    @Test
    void aGifHeaderMustBeComplete() {
        assertThat(FileTypeSniffer.detect("GIF8".getBytes(StandardCharsets.US_ASCII)))
                .as("just the first four bytes is plain text, not an image")
                .hasValueSatisfying(type -> assertThat(type.mimeType()).isEqualTo("text/plain"));
    }

    @Test
    void textFilesAreOnlyAcceptedWithTextExtensions() {
        FileTypeSniffer.DetectedType text = FileTypeSniffer.detect("<script>alert(1)</script>".getBytes()).orElseThrow();

        assertThat(text.extensions()).contains("txt", "md", "csv", "log");
        assertThat(text.extensions()).doesNotContain("html", "htm", "svg", "js");
    }

    @Test
    void readsTheLastExtensionInLowerCase() {
        assertThat(FileTypeSniffer.extensionOf("Report.FINAL.PDF")).isEqualTo("pdf");
        assertThat(FileTypeSniffer.extensionOf("no-extension")).isEmpty();
    }

    @Test
    void filenamesLoseTheirPathsAndControlCharacters() {
        assertThat(FileService.sanitizeFilename("../../etc/passwd.txt")).isEqualTo("passwd.txt");
        assertThat(FileService.sanitizeFilename("C:\\Users\\me\\notes.txt")).isEqualTo("notes.txt");
        assertThat(FileService.sanitizeFilename("bad\u0000na\nme.txt")).isEqualTo("badname.txt");
        assertThat(FileService.sanitizeFilename(null)).isEqualTo("file");
        assertThat(FileService.sanitizeFilename("   ")).isEqualTo("file");
        assertThat(FileService.sanitizeFilename("a".repeat(500) + ".txt")).hasSizeLessThanOrEqualTo(200).endsWith(".txt");
    }

    private static String mime(byte[] content) {
        return FileTypeSniffer.detect(content).orElseThrow().mimeType();
    }
}
