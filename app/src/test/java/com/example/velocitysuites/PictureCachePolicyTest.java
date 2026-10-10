package com.example.velocitysuites;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Pull-to-refresh must not re-download every picture. The server stores a replaced picture under a NEW file name
 * (Admin room / room-type / promotion / announcement controllers: store() a new file, delete the old one), so a
 * changed picture already has a new URL and Glide's normal URL-keyed cache fetches exactly that one. This guards
 * the choice: no code may bypass or re-key Glide's cache, so only pictures whose URL changed are ever downloaded
 * - on a pull and on the silent 30-second refresh alike.
 */
public class PictureCachePolicyTest {

    private static final String[] FORBIDDEN = {
            "skipMemoryCache", "DiskCacheStrategy.NONE", "onlyRetrieveFromCache", ".signature(", "ImageFreshness"
    };

    private static File mainSources() {
        for (String candidate : new String[]{"src/main/java", "app/src/main/java"}) {
            File dir = new File(candidate);
            if (dir.isDirectory()) return dir;
        }
        throw new AssertionError("main sources not found from " + new File(".").getAbsolutePath());
    }

    @Test
    public void noCodeBypassesOrReKeysTheGlideCache() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<java.nio.file.Path> files = Files.walk(mainSources().toPath())) {
            for (java.nio.file.Path path : (Iterable<java.nio.file.Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                for (String word : FORBIDDEN) {
                    if (text.contains(word)) offenders.add(path.getFileName() + " uses " + word);
                }
            }
        }
        assertTrue("pictures must use Glide's plain URL-keyed cache: " + offenders, offenders.isEmpty());
    }
}
