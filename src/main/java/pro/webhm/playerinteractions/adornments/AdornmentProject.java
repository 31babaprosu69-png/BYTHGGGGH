package pro.webhm.playerinteractions.adornments;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AdornmentProject {
   public static final int VERSION = 1;
   public static final int MAX_RIGS = 16;
   public static final int MAX_LINKS = 32;
   public static final int MAX_TOTAL = 128;
   public static final String[] BONES = new String[]{
      "head",
      "chest",
      "body",
      "right_arm",
      "right_forearm",
      "left_arm",
      "left_forearm",
      "right_leg",
      "right_shin",
      "left_leg",
      "left_shin",
      "right_breast",
      "left_breast"
   };
   public int version = 1;
   public String name = "Украшения";
   public List<AdornmentProject.Rig> rigs = new ArrayList<>();

   public AdornmentProject.Rig find(String id) {
      for (AdornmentProject.Rig r : this.rigs) {
         if (r.id.equals(id)) {
            return r;
         }
      }

      return null;
   }

   public int count() {
      int n = 0;

      for (AdornmentProject.Rig r : this.rigs) {
         n += r.links.size();
      }

      return n;
   }

   public static String id() {
      return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
   }

   public static AdornmentProject.Rig chain() {
      AdornmentProject.Rig r = new AdornmentProject.Rig();

      for (int i = 0; i < 8; i++) {
         AdornmentProject.Link l = new AdornmentProject.Link();
         l.yaw = i == 0 ? 0.0F : 90.0F;
         r.links.add(l);
      }

      r.links.get(r.links.size() - 1).grip = true;
      return r;
   }

   public void validate() {
      if (this.version != 1) {
         fail("Неподдерживаемая версия украшений: " + this.version);
      }

      text(this.name, 80);
      if (this.rigs == null || this.rigs.size() > 16) {
         fail("Допустимо до 16 украшений");
      }

      HashSet<String> ids = new HashSet<>();
      int total = 0;

      for (AdornmentProject.Rig r : this.rigs) {
         if (r == null) {
            fail("Пустое украшение");
         }

         key(r.id, ids);
         text(r.name, 80);
         if (!Arrays.asList(BONES).contains(r.bone)) {
            fail("Неизвестная точка крепления");
         }

         range(r.x, -32.0F, 32.0F);
         range(r.y, -32.0F, 32.0F);
         range(r.z, -32.0F, 32.0F);
         angles(r.pitch, r.yaw, r.roll);
         range(r.damping, 0.1F, 20.0F);
         range(r.gravity, 0.0F, 3.0F);
         range((float)r.plane, 0.0F, 2.0F);
         range(r.flex, 5.0F, 90.0F);
         text(r.grabText, 240);
         text(r.pullText, 240);
         text(r.releaseText, 240);
         if (r.links == null || r.links.isEmpty() || r.links.size() > 32) {
            fail("В украшении нужно 1–32 звена");
         }

         total += r.links.size();

         for (AdornmentProject.Link l : r.links) {
            if (l == null) {
               fail("Пустое звено");
            }

            key(l.id, ids);
            range(l.length, 0.25F, 12.0F);
            range(l.width, 0.1F, 8.0F);
            range(l.depth, 0.1F, 8.0F);
            angles(l.pitch, l.yaw, l.roll);
            range(l.limit, 0.0F, 170.0F);
            range(l.stiffness, 0.0F, 10.0F);
            range((float)l.joint, 0.0F, 3.0F);
            range((float)l.shape, 0.0F, 2.0F);
            range(l.wire, 0.05F, 2.0F);
            if (l.rgb < 0 || l.rgb > 16777215) {
               fail("Недопустимый цвет");
            }

            range((float)l.u, 0.0F, 63.0F);
            range((float)l.v, 0.0F, 63.0F);
            range(l.gripAt, 0.0F, 1.0F);
            range(l.gripX, -8.0F, 8.0F);
            range(l.gripZ, -8.0F, 8.0F);
         }

         if (r.length() > 96.0F) {
            fail("Длина украшения не должна превышать 96 пикселей");
         }
      }

      if (total > 128) {
         fail("Допустимо до 128 звеньев на NPC");
      }
   }

   private static void key(String s, Set<String> seen) {
      if (s == null || !s.matches("[a-zA-Z0-9_-]{1,40}") || !seen.add(s)) {
         fail("Повторяющийся или неверный ID");
      }
   }

   private static void text(String s, int max) {
      if (s == null || s.length() > max) {
         fail("Слишком длинное или пустое поле текста");
      }
   }

   private static void angles(float a, float b, float c) {
      range(a, -180.0F, 180.0F);
      range(b, -180.0F, 180.0F);
      range(c, -180.0F, 180.0F);
   }

   private static void range(float n, float lo, float hi) {
      if (!Float.isFinite(n) || n < lo || n > hi) {
         fail("Число вне допустимого диапазона");
      }
   }

   private static void fail(String message) {
      throw new IllegalArgumentException(message);
   }

   public static final class Link {
      public String id = AdornmentProject.id();
      public float length = 1.5F;
      public float width = 1.0F;
      public float depth = 0.28F;
      public float pitch;
      public float yaw;
      public float roll;
      public int joint = 1;
      public float limit = 85.0F;
      public float stiffness = 0.1F;
      public int shape = 1;
      public float wire = 0.18F;
      public boolean solid = true;
      public boolean grip;
      public int rgb = 13939050;
      public int u = 8;
      public int v = 8;
      public float gripAt = 1.0F;
      public float gripX;
      public float gripZ;

      public float radius() {
         return (float)Math.sqrt((double)(this.width * this.width + this.depth * this.depth)) * 0.5F + 0.04F;
      }
   }

   public static final class Rig {
      public String id = AdornmentProject.id();
      public String name = "Цепь";
      public String bone = "right_forearm";
      public float x = -4.2F;
      public float y = 4.0F;
      public float z = 0.0F;
      public float pitch;
      public float yaw;
      public float roll;
      public float damping = 3.0F;
      public float gravity = 1.0F;
      public boolean simpleChain = true;
      public boolean preciseAnchor;
      public boolean limitBends;
      public float flex = 35.0F;
      public int plane;
      public boolean enabled = true;
      public String grabText = "Только осторожно с украшением.";
      public String pullText = "Полегче, не тяни так сильно.";
      public String releaseText = "";
      public List<AdornmentProject.Link> links = new ArrayList<>();

      public float length() {
         float n = 0.0F;

         for (AdornmentProject.Link l : this.links) {
            n += l.length;
         }

         return n;
      }

      public int index(String key) {
         for (int i = 0; i < this.links.size(); i++) {
            if (this.links.get(i).id.equals(key)) {
               return i;
            }
         }

         return -1;
      }

      public boolean linked() {
         if (this.simpleChain && !this.links.isEmpty()) {
            for (AdornmentProject.Link l : this.links) {
               if (l.shape != 1) {
                  return false;
               }
            }

            return true;
         } else {
            return false;
         }
      }

      public int boneIndex() {
         for (int i = 0; i < AdornmentProject.BONES.length; i++) {
            if (AdornmentProject.BONES[i].equals(this.bone)) {
               return i;
            }
         }

         return 0;
      }
   }
}
