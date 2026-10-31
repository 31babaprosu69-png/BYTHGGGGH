package pro.webhm.playerinteractions;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import pro.webhm.playerinteractions.anim.Clip;
import pro.webhm.playerinteractions.npc.BodyData;
import pro.webhm.playerinteractions.npc.BreastData;
import pro.webhm.playerinteractions.npc.ModelShapeData;

public final class Appearance {
   private static final BreastData PRESET = read("astrid_preset_1.json", BreastData.class);

   public static int arms(int value) {
      return value >= 0 && value <= 2 ? value : 0;
   }

   public static boolean slim(int choice, GameProfile profile) {
      if (choice != 1 && choice != 2) {
         for (Property property : profile.getProperties().get("textures")) {
            try {
               JsonObject json = JsonParser.parseString(new String(Base64.getDecoder().decode(property.value()), StandardCharsets.UTF_8)).getAsJsonObject();
               JsonObject textures = json.getAsJsonObject("textures");
               if (textures != null && textures.has("SKIN")) {
                  JsonObject skin = textures.getAsJsonObject("SKIN");
                  JsonObject metadata = skin.getAsJsonObject("metadata");
                  return metadata != null && metadata.has("model") && "slim".equals(metadata.get("model").getAsString());
               }
            } catch (RuntimeException var8) {
            }
         }

         return Math.floorMod(profile.getId().hashCode(), 18) < 9;
      } else {
         return choice == 2;
      }
   }

   public static Appearance.Settings decode(String json) {
      if (json != null && !json.isBlank()) {
         JsonObject object = JsonParser.parseString(json).getAsJsonObject();
         if (!object.has("breast")) {
            return new Appearance.Settings(sanitize((BreastData)Clip.JSON.fromJson(object, BreastData.class)), 0);
         } else {
            Appearance.Settings s = (Appearance.Settings)Clip.JSON.fromJson(object, Appearance.Settings.class);
            return new Appearance.Settings(sanitize(s.breast), arms(s.arms));
         }
      } else {
         return new Appearance.Settings(defaults(), 0);
      }
   }

   public static BreastData preset() {
      return copy(PRESET);
   }

   public static BreastData selected(boolean enabled) {
      BreastData b = preset();
      b.enabled = enabled;
      return b;
   }

   public static BodyData sceneRig() {
      BodyData data = read("scene_rig.json", BodyData.class);
      data.modelShape = new ModelShapeData();
      return data;
   }

   public static BodyData reactionRig() {
      return read("touch_reactions.json", BodyData.class);
   }

   private static <T> T read(String file, Class<T> type) {
      try {
         Object var3;
         try (InputStream in = Appearance.class.getResourceAsStream("/assets/aot_player_interactions/appearance/" + file)) {
            if (in == null) {
               throw new IllegalStateException(file);
            }

            var3 = Clip.JSON.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), type);
         }

         return (T)var3;
      } catch (IOException var7) {
         throw new IllegalStateException(var7);
      }
   }

   public static BreastData defaults() {
      return selected(false);
   }

   public static BreastData copy(BreastData b) {
      BreastData result = new BreastData();

      try {
         for (Field field : BreastData.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
               field.set(result, field.get(b));
            }
         }

         return result;
      } catch (IllegalAccessException var6) {
         throw new IllegalStateException(var6);
      }
   }

   public static BreastData sanitize(BreastData source) {
      return selected(source != null && source.enabled);
   }

   private Appearance() {
   }

   public static record Settings(BreastData breast, int arms) {
   }

   public static record Snapshot(UUID player, BreastData breast, int arms) {
   }
}
