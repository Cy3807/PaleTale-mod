package cn.paletale.companion;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.GZIPInputStream;

/**
 * Minecraft writes its log in the platform's charset: UTF-8 on most systems, GBK on a Chinese Windows without UTF-8 enabled.
 * Read as UTF-8, a GBK log turns every Chinese word into garbage (including the "已转移至另一个服务器" line the import needs),
 * so the file is checked first: valid UTF-8 from start to end means UTF-8, anything else means GB18030 (a superset of GBK).
 */
final class LogCharset {
    private static final Charset GB18030 = Charset.forName("GB18030");
    private static final long LIMIT = 512L * 1024 * 1024;
    private LogCharset() {}

    static Charset of(Path file) {
        try (InputStream raw = Files.newInputStream(file); InputStream input = file.getFileName().toString().endsWith(".gz") ? new GZIPInputStream(raw) : raw) {
            return validUtf8(input) ? StandardCharsets.UTF_8 : GB18030;
        } catch (IOException unreadable) {
            // A log cut off in the middle (still being written) is judged by what could be read.
            return StandardCharsets.UTF_8;
        }
    }

    /** True when every byte sequence is well-formed UTF-8; an incomplete character at the very end (a cut-off file) does not count against it. */
    static boolean validUtf8(InputStream input) throws IOException {
        byte[] buffer = new byte[65536];
        int need = 0, count;
        long total = 0;
        while ((count = input.read(buffer)) > 0) {
            total += count;
            for (int i = 0; i < count; i++) {
                int b = buffer[i] & 0xFF;
                if (need > 0) { if ((b & 0xC0) != 0x80) return false; need--; }
                else if (b < 0x80) continue;
                else if (b >= 0xC2 && b <= 0xDF) need = 1;
                else if ((b & 0xF0) == 0xE0) need = 2;
                else if (b >= 0xF0 && b <= 0xF4) need = 3;
                else return false;
            }
            if (total > LIMIT) return true;
        }
        return true;
    }
}
