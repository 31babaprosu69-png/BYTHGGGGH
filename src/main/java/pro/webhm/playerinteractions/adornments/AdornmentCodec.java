package pro.webhm.playerinteractions.adornments;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class AdornmentCodec {
   public static final String PREFIX = "AOTDEC1:";
   public static final int MAX_JSON = 180000;
   public static final int MAX_CODE = 24000;
   private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();
   private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
   public static final TypeAdapter<AdornmentProject> ADAPTER = new TypeAdapter<AdornmentProject>() {
      public void write(JsonWriter out, AdornmentProject p) throws IOException {
         if (p != null && !p.rigs.isEmpty()) {
            out.value(AdornmentCodec.code(p));
         } else {
            out.nullValue();
         }
      }

      public AdornmentProject read(JsonReader in) throws IOException {
         JsonElement element = JsonParser.parseReader(in);
         if (element.isJsonNull()) {
            return new AdornmentProject();
         } else {
            try {
               return AdornmentCodec.parse(element.isJsonPrimitive() ? element.getAsString() : element.toString());
            } catch (RuntimeException var4) {
               return new AdornmentProject();
            }
         }
      }
   };

   public static String json(AdornmentProject p) {
      p.validate();
      return PRETTY.toJson(p);
   }

   public static String contentKey(AdornmentProject p) {
      return JSON.toJson(p);
   }

   public static AdornmentProject copy(AdornmentProject p) {
      return parse(JSON.toJson(p));
   }

   public static String code(AdornmentProject p) {
      p.validate();
      byte[] raw = JSON.toJson(p).getBytes(StandardCharsets.UTF_8);
      if (raw.length > 180000) {
         throw new IllegalArgumentException("Проект слишком велик");
      } else {
         try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            GZIPOutputStream out = new GZIPOutputStream(bytes);

            try {
               out.write(raw);
            } catch (Throwable var7) {
               try {
                  out.close();
               } catch (Throwable var6) {
                  var7.addSuppressed(var6);
               }

               throw var7;
            }

            out.close();
            String result = "AOTDEC1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
            if (result.length() > 24000) {
               throw new IllegalArgumentException("Код проекта слишком велик");
            } else {
               return result;
            }
         } catch (IOException var8) {
            throw new IllegalArgumentException("Не удалось упаковать проект", var8);
         }
      }
   }

   public static AdornmentProject parse(String input) {
      if (input == null || input.length() > 180000) {
         throw new IllegalArgumentException("Файл слишком велик");
      }

      String raw = input.strip();
      if (raw.startsWith("AOTDEC1:")) {
         if (raw.length() > 24000) {
            throw new IllegalArgumentException("Код слишком велик");
         }

         try (GZIPInputStream stream = new GZIPInputStream(new ByteArrayInputStream(Base64.getUrlDecoder().decode(raw.substring("AOTDEC1:".length()))))) {
            byte[] bytes = stream.readNBytes(180001);
            if (bytes.length > 180000) {
               throw new IllegalArgumentException("Распакованный проект слишком велик");
            }

            raw = new String(bytes, StandardCharsets.UTF_8);
         } catch (IllegalArgumentException | IOException var10) {
            throw new IllegalArgumentException("Повреждённый код украшения", var10);
         }
      }

      int depth = 0;
      boolean quoted = false;
      boolean escape = false;

      for (int i = 0; i < raw.length(); i++) {
         char c = raw.charAt(i);
         if (escape) {
            escape = false;
         } else if (quoted && c == '\\') {
            escape = true;
         } else if (c == '"') {
            quoted = !quoted;
         } else if (!quoted) {
            if (c == '{' || c == '[') {
               if (++depth > 12) {
                  throw new IllegalArgumentException("Слишком сложная структура проекта");
               }
            }

            if (c == '}' || c == ']') {
               depth--;
            }
         }
      }

      try {
         JsonElement tree = JsonParser.parseString(raw);
         if (tree.isJsonObject() && tree.getAsJsonObject().has("version")) {
            AdornmentProject p = (AdornmentProject)JSON.fromJson(tree, AdornmentProject.class);
            p.validate();
            return p;
         } else {
            throw new IllegalArgumentException("Это не файл украшений");
         }
      } catch (JsonParseException var8) {
         throw new IllegalArgumentException("Повреждённый файл украшений", var8);
      }
   }

   private AdornmentCodec() {
   }
}
