package pro.webhm.playerinteractions.anim;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class AnimationFiles {
   public static final int MAX_COUNT = 64;
   public static final int MAX_TOTAL = 8388608;

   public static AnimationFiles.Result load(Path directory) throws IOException {
      return load(directory, List.of());
   }

   public static AnimationFiles.Result load(Path directory, List<AnimationBundle> first) throws IOException {
      Files.createDirectories(directory);
      ArrayList<AnimationBundle> animations = new ArrayList<>(first);
      ArrayList<String> errors = new ArrayList<>();

      List<Path> files;
      try (Stream<Path> stream = Files.list(directory)) {
         files = stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
            .sorted(Comparator.comparing(p -> p.getFileName().toString()))
            .limit(257L)
            .toList();
      }

      int total = 0;
      HashSet<Integer> ids = new HashSet<>();

      for (AnimationBundle entry : first) {
         if (!ids.add(entry.action())) {
            throw new IOException("Duplicate packaged animation");
         }

         total += entry.json().getBytes(StandardCharsets.UTF_8).length;
      }

      if (first.size() <= 64 && total <= 8388608) {
         for (Path file : files.subList(0, Math.min(256, files.size()))) {
            try {
               if (animations.size() >= 64) {
                  throw new IOException("Limit: 64 animations");
               }

               if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                  throw new IOException("Expected a regular file, not a link");
               }

               if (Files.size(file) > 524288L) {
                  throw new IOException("File exceeds 512 KiB");
               }

               byte[] bytes;
               try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                  bytes = in.readNBytes(524289);
               }

               if (bytes.length > 524288) {
                  throw new IOException("File exceeds 512 KiB");
               }

               if (total + bytes.length > 8388608) {
                  throw new IOException("Catalogue exceeds 8 MiB");
               }

               AnimationBundle bundle = AnimationBundle.parse(
                  new String(bytes, StandardCharsets.UTF_8), file.getFileName().toString().replaceFirst("(?i)\\.json$", "")
               );
               int transferSize = bundle.json().getBytes(StandardCharsets.UTF_8).length;
               if (total + transferSize > 8388608) {
                  throw new IOException("Normalized catalogue exceeds 8 MiB");
               }

               if (!ids.add(bundle.action())) {
                  throw new IOException("Duplicate animation or ID collision");
               }

               animations.add(bundle);
               total += transferSize;
            } catch (RuntimeException | IOException var17) {
               errors.add(file.getFileName() + ": " + var17.getMessage());
            }
         }

         if (files.size() == 257) {
            errors.add("Only the first 256 file names are scanned; remove excess files");
         }

         return new AnimationFiles.Result(List.copyOf(animations), List.copyOf(errors));
      } else {
         throw new IOException("Packaged catalogue exceeds bounds");
      }
   }

   private AnimationFiles() {
   }

   public static record Result(List<AnimationBundle> animations, List<String> errors) {
   }
}
