package pro.webhm.playerinteractions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

public final class CatalogInstructions {
   private static final Set<String> LEGACY = Set.of(
      "a8dadba1017698c3621086f3ac3caa2464cc749477aaaba26df363822c6bc1e6",
      "af7e54dc1077dd4f5d5089a3506e758b2cc51fcb8071a2f204cd8471f4b3d9ff",
      "c0b55e798e93d6119ebe272c29e615c30b8f9eb529146fe21d07312986b63aef"
   );

   public static void prepare(Path directory) throws IOException {
      Files.createDirectories(directory);

      for (String name : new String[]{"README.txt", "README-ru.txt", "example.json.example"}) {
         try (InputStream in = CatalogInstructions.class.getResourceAsStream("/assets/aot_player_interactions/custom/" + name)) {
            if (in == null) {
               throw new IOException("Missing bundled instructions: " + name);
            }

            byte[] current = in.readAllBytes();
            Path file = directory.resolve(name);
            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
               Files.write(file, current, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } else if (name.equals("README.txt") && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) <= 65536L) {
               String old = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
               if (isLegacy(old)) {
                  Files.write(file, current, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS);
               }
            }
         }
      }
   }

   private static boolean isLegacy(String text) {
      try {
         return LEGACY.contains(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))));
      } catch (NoSuchAlgorithmException var2) {
         throw new IllegalStateException(var2);
      }
   }

   private CatalogInstructions() {
   }
}
