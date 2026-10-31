package pro.webhm.playerinteractions.npc;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

public class BoneTransformAdapter implements JsonSerializer<BoneTransform>, JsonDeserializer<BoneTransform> {
   public JsonElement serialize(BoneTransform t, Type type, JsonSerializationContext ctx) {
      float[] v = new float[]{t.rx, t.ry, t.rz, t.height, t.shiftX, t.shiftZ};
      int last = v.length;

      while (last > 0 && v[last - 1] == 0.0F) {
         last--;
      }

      JsonArray arr = new JsonArray();

      for (int i = 0; i < last; i++) {
         arr.add(round(v[i]));
      }

      return arr;
   }

   public BoneTransform deserialize(JsonElement el, Type type, JsonDeserializationContext ctx) {
      BoneTransform t = new BoneTransform();
      if (el.isJsonArray()) {
         JsonArray a = el.getAsJsonArray();
         if (a.size() > 0) {
            t.rx = a.get(0).getAsFloat();
         }

         if (a.size() > 1) {
            t.ry = a.get(1).getAsFloat();
         }

         if (a.size() > 2) {
            t.rz = a.get(2).getAsFloat();
         }

         if (a.size() >= 9) {
            t.height = -a.get(4).getAsFloat();
         } else {
            if (a.size() > 3) {
               t.height = a.get(3).getAsFloat();
            }

            if (a.size() > 4) {
               t.shiftX = a.get(4).getAsFloat();
            }

            if (a.size() > 5) {
               t.shiftZ = a.get(5).getAsFloat();
            }
         }
      } else if (el.isJsonObject()) {
         JsonObject o = el.getAsJsonObject();
         t.rx = f(o, "rx");
         t.ry = f(o, "ry");
         t.rz = f(o, "rz");
         t.height = o.has("height") ? o.get("height").getAsFloat() : -f(o, "oy");
      }

      return t;
   }

   private static float f(JsonObject o, String k) {
      return o.has(k) ? o.get(k).getAsFloat() : 0.0F;
   }

   private static float round(float v) {
      return (float)Math.round(v * 1000.0F) / 1000.0F;
   }
}
