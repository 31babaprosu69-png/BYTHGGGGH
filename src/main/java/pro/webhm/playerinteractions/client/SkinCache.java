package pro.webhm.playerinteractions.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.fml.loading.FMLPaths;
import pro.webhm.playerinteractions.body.BodyRuntime;
import pro.webhm.playerinteractions.util.NaturalOrder;

public final class SkinCache {
   private static final Map<String, ResourceLocation> CACHE = new ConcurrentHashMap<>();
   private static final Map<String, SkinCache.WaistProfile> WAIST_PROFILES = new ConcurrentHashMap<>();
   private static final Set<String> WAIST_HOLES = ConcurrentHashMap.newKeySet();
   private static final Map<String, Integer> SKIN_SCALES = new ConcurrentHashMap<>();
   private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
   private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
   private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
      Thread t = new Thread(r, "aotnpc-skin-loader");
      t.setDaemon(true);
      return t;
   });
   private static final int MAX_BYTES = 1048576;
   public static final String JAR_PREFIX = "jar:";
   public static final String RESOURCE_PREFIX = "res:";
   public static final String JAR_DIR = "textures/npc_skins";
   private static final String BUNDLED_INDEX = "/assets/aotnpc/npc_skins_index.txt";

   public static ResourceLocation get(String key) {
      if (key != null && !key.isBlank()) {
         ResourceLocation rl = CACHE.get(key);
         if (rl != null) {
            return rl;
         } else if (FAILED.contains(key)) {
            return DefaultPlayerSkin.getDefaultSkin();
         } else if (key.startsWith("configskin:")) {
            return loadConfigSkin(key);
         } else if (key.startsWith("jar:")) {
            String relative = key.substring("jar:".length());
            String resourcePath = "textures/npc_skins/" + relative + ".png";
            if (!resourcePath.matches("[a-z0-9/._-]+")) {
               return loadBundledDirect(key, relative);
            } else {
               ResourceLocation loc = BodyRuntime.id(resourcePath);
               return loadBundled(key, loc);
            }
         } else if (key.startsWith("res:")) {
            ResourceLocation loc = ResourceLocation.tryParse(key.substring("res:".length()));
            if (loc == null) {
               FAILED.add(key);
               return DefaultPlayerSkin.getDefaultSkin();
            } else {
               return loadBundled(key, loc);
            }
         } else {
            if (PENDING.add(key)) {
               POOL.submit(() -> load(key));
            }

            return DefaultPlayerSkin.getDefaultSkin();
         }
      } else {
         return DefaultPlayerSkin.getDefaultSkin();
      }
   }

   public static List<String> listBundled() {
      List<String> out = new ArrayList<>();

      try {
         Map<ResourceLocation, Resource> found = Minecraft.getInstance()
            .getResourceManager()
            .listResources("textures/npc_skins", rlx -> rlx.getPath().endsWith(".png"));

         for (ResourceLocation rl : found.keySet()) {
            if ("aot_player_interactions".equals(rl.getNamespace())) {
               String path = rl.getPath();
               String prefix = "textures/npc_skins/";
               if (path.startsWith(prefix) && path.length() > prefix.length() + 4) {
                  String name = path.substring(prefix.length(), path.length() - 4);
                  if (!out.contains(name)) {
                     out.add(name);
                  }
               }
            }
         }
      } catch (Exception var11) {
         BodyRuntime.LOG.warn("Не удалось перечислить встроенные скины: {}", var11.toString());
      }

      try (InputStream in = SkinCache.class.getResourceAsStream("/assets/aotnpc/npc_skins_index.txt")) {
         if (in != null) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            for (String line : text.split("\\R")) {
               String name = line.trim();
               if (!name.isBlank() && !name.startsWith("#") && !out.contains(name)) {
                  out.add(name);
               }
            }
         }
      } catch (Exception var10) {
         BodyRuntime.LOG.warn("Не удалось прочитать индекс встроенных скинов: {}", var10.toString());
      }

      out.sort(NaturalOrder.INSTANCE);
      return out;
   }

   public static void forget(String url) {
      if (url != null) {
         CACHE.remove(url);
         WAIST_PROFILES.remove(url);
         WAIST_HOLES.remove(url);
         SKIN_SCALES.remove(url);
         FAILED.remove(url);
         PENDING.remove(url);
      }
   }

   public static SkinCache.WaistProfile waistProfile(String key) {
      return key == null ? null : WAIST_PROFILES.get(key);
   }

   public static boolean waistHasHoles(String key) {
      return key != null && WAIST_HOLES.contains(key);
   }

   public static int skinScale(String key) {
      return key == null ? 1 : Math.max(1, SKIN_SCALES.getOrDefault(key, 1));
   }

   public static NativeImage skinImage(String key) {
      ResourceLocation location = get(key);
      return Minecraft.getInstance().getTextureManager().getTexture(location) instanceof DynamicTexture dynamic ? dynamic.getPixels() : null;
   }

   private static void load(String url) {
      try {
         if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("bad scheme");
         }

         URL u = URI.create(url).toURL();
         HttpURLConnection conn = (HttpURLConnection)u.openConnection();
         conn.setConnectTimeout(8000);
         conn.setReadTimeout(8000);
         conn.setInstanceFollowRedirects(true);
         conn.setRequestProperty("User-Agent", "AoTNpc/1.0 (Minecraft mod)");

         byte[] bytes;
         try (
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
         ) {
            byte[] buf = new byte[8192];
            int total = 0;

            int read;
            while ((read = in.read(buf)) > 0) {
               total += read;
               if (total > 1048576) {
                  throw new IllegalStateException("skin too large");
               }

               out.write(buf, 0, read);
            }

            bytes = out.toByteArray();
         }

         NativeImage raw = NativeImage.read(new ByteArrayInputStream(bytes));
         NativeImage image = normalize(raw);
         SKIN_SCALES.put(url, Math.max(1, image.getWidth() / 64));
         rememberWaistProfile(url, image);
         sealHdTorsoHoles(image);
         ResourceLocation loc = BodyRuntime.id("skins/" + sha1(url));
         Minecraft.getInstance().execute(() -> {
            try {
               Minecraft.getInstance().getTextureManager().register(loc, new DynamicTexture(image));
               CACHE.put(url, loc);
            } catch (Exception var7x) {
               WAIST_PROFILES.remove(url);
               WAIST_HOLES.remove(url);
               SKIN_SCALES.remove(url);
               FAILED.add(url);
               BodyRuntime.LOG.warn("Не удалось зарегистрировать текстуру скина {}", url, var7x);
            } finally {
               PENDING.remove(url);
            }
         });
      } catch (Exception var13) {
         FAILED.add(url);
         PENDING.remove(url);
         BodyRuntime.LOG.warn("Не удалось загрузить скин {}: {}", url, var13.toString());
      }
   }

   private static NativeImage normalize(NativeImage src) {
      int w = src.getWidth();
      int h = src.getHeight();
      if (w == h) {
         if (w % 64 != 0) {
            throw new IllegalStateException("skin side must be a multiple of 64, got " + w);
         } else {
            return src;
         }
      } else if (w == h * 2 && w % 64 == 0) {
         int s = w / 64;
         NativeImage out = new NativeImage(w, w, true);
         out.fillRect(0, 0, w, w, 0);

         for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
               out.setPixelRGBA(x, y, src.getPixelRGBA(x, y));
            }
         }

         copyMirrored(out, 44 * s, 20 * s, 12 * s, 12 * s, 36 * s, 52 * s);
         copyMirrored(out, 4 * s, 20 * s, 12 * s, 12 * s, 20 * s, 52 * s);
         copyMirrored(out, 40 * s, 16 * s, 4 * s, 4 * s, 36 * s, 48 * s);
         copyMirrored(out, 0, 16 * s, 4 * s, 4 * s, 20 * s, 48 * s);
         src.close();
         return out;
      } else {
         throw new IllegalStateException("unsupported skin size " + w + "x" + h);
      }
   }

   private static ResourceLocation loadConfigSkin(String key) {
      try {
         Path root = FMLPaths.CONFIGDIR.get().toRealPath();
         Path path = root.resolve(key.substring("configskin:".length())).normalize();
         if (path.startsWith(root) && path.toRealPath().startsWith(root) && path.toString().endsWith(".png") && Files.size(path) <= 1048576L) {
            ResourceLocation var6;
            try (InputStream in = Files.newInputStream(path)) {
               NativeImage image = normalize(NativeImage.read(in));
               SKIN_SCALES.put(key, Math.max(1, image.getWidth() / 64));
               rememberWaistProfile(key, image);
               sealHdTorsoHoles(image);
               ResourceLocation generated = BodyRuntime.id("skins/config_" + sha1(key));
               Minecraft.getInstance().getTextureManager().register(generated, new DynamicTexture(image));
               CACHE.put(key, generated);
               var6 = generated;
            }

            return var6;
         } else {
            throw new IllegalArgumentException("Invalid body skin path");
         }
      } catch (Exception var9) {
         FAILED.add(key);
         BodyRuntime.LOG.warn("Cannot load character skin {}: {}", key, var9.toString());
         return DefaultPlayerSkin.getDefaultSkin();
      }
   }

   private static ResourceLocation loadBundled(String key, ResourceLocation source) {
      try {
         Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(source);
         if (resource.isEmpty()) {
            throw new IllegalStateException("skin resource not found");
         } else {
            ResourceLocation var7;
            try (InputStream in = resource.get().open()) {
               NativeImage raw = NativeImage.read(in);
               NativeImage image = normalize(raw);
               SKIN_SCALES.put(key, Math.max(1, image.getWidth() / 64));
               rememberWaistProfile(key, image);
               sealHdTorsoHoles(image);
               ResourceLocation generated = BodyRuntime.id("skins/bundled_" + sha1(key));
               Minecraft.getInstance().getTextureManager().register(generated, new DynamicTexture(image));
               CACHE.put(key, generated);
               var7 = generated;
            }

            return var7;
         }
      } catch (Exception var10) {
         FAILED.add(key);
         BodyRuntime.LOG.warn("Не удалось загрузить встроенный скин {}: {}", key, var10.toString());
         return DefaultPlayerSkin.getDefaultSkin();
      }
   }

   private static ResourceLocation loadBundledDirect(String key, String relative) {
      String classpath = "/assets/aot_player_interactions/textures/npc_skins/" + relative + ".png";

      try {
         ResourceLocation var6;
         try (InputStream in = SkinCache.class.getResourceAsStream(classpath)) {
            if (in == null) {
               throw new IllegalStateException("skin resource not found");
            }

            NativeImage image = normalize(NativeImage.read(in));
            SKIN_SCALES.put(key, Math.max(1, image.getWidth() / 64));
            rememberWaistProfile(key, image);
            sealHdTorsoHoles(image);
            ResourceLocation generated = BodyRuntime.id("skins/bundled_" + sha1(key));
            Minecraft.getInstance().getTextureManager().register(generated, new DynamicTexture(image));
            CACHE.put(key, generated);
            var6 = generated;
         }

         return var6;
      } catch (Exception var9) {
         FAILED.add(key);
         BodyRuntime.LOG.warn("Не удалось напрямую загрузить встроенный скин {}: {}", key, var9.toString());
         return DefaultPlayerSkin.getDefaultSkin();
      }
   }

   private static void sealHdTorsoHoles(NativeImage image) {
      if (image != null && image.getWidth() > 64 && image.getWidth() == image.getHeight()) {
         int scale = image.getWidth() / 64;
         fillTransparentFace(image, 20, 16, 28, 20, scale);
         fillTransparentFace(image, 28, 16, 36, 20, scale);
         fillTransparentFace(image, 16, 20, 20, 32, scale);
         fillTransparentFace(image, 20, 20, 28, 32, scale);
         fillTransparentFace(image, 28, 20, 32, 32, scale);
         fillTransparentFace(image, 32, 20, 40, 32, scale);
      }
   }

   private static void fillTransparentFace(NativeImage image, int x1, int y1, int x2, int y2, int scale) {
      int minX = x1 * scale;
      int minY = y1 * scale;
      int width = (x2 - x1) * scale;
      int height = (y2 - y1) * scale;
      int[] source = new int[width * height];

      for (int y = 0; y < height; y++) {
         for (int x = 0; x < width; x++) {
            source[y * width + x] = image.getPixelRGBA(minX + x, minY + y);
         }
      }

      for (int y = 0; y < height; y++) {
         for (int x = 0; x < width; x++) {
            if (transparent(source[y * width + x])) {
               int nearest = nearestOpaque(source, width, height, x, y);
               if (!transparent(nearest)) {
                  image.setPixelRGBA(minX + x, minY + y, nearest | 0xFF000000);
               }
            }
         }
      }
   }

   private static int nearestOpaque(int[] pixels, int width, int height, int x, int y) {
      int limit = Math.max(width, height);

      for (int radius = 1; radius <= limit; radius++) {
         int best = 0;
         int bestDistance = Integer.MAX_VALUE;
         int minY = Math.max(0, y - radius);
         int maxY = Math.min(height - 1, y + radius);
         int minX = Math.max(0, x - radius);
         int maxX = Math.min(width - 1, x + radius);

         for (int sy = minY; sy <= maxY; sy++) {
            for (int sx = minX; sx <= maxX; sx++) {
               if (Math.max(Math.abs(sx - x), Math.abs(sy - y)) == radius) {
                  int candidate = pixels[sy * width + sx];
                  if (!transparent(candidate)) {
                     int distance = Math.abs(sx - x) + Math.abs(sy - y);
                     if (distance < bestDistance) {
                        best = candidate;
                        bestDistance = distance;
                     }
                  }
               }
            }
         }

         if (bestDistance != Integer.MAX_VALUE) {
            return best;
         }
      }

      return 0;
   }

   private static void rememberWaistProfile(String key, NativeImage image) {
      WAIST_PROFILES.remove(key);
      WAIST_HOLES.remove(key);
      if (key != null && image != null && image.getWidth() > 64 && image.getWidth() == image.getHeight()) {
         int scale = image.getWidth() / 64;
         int rows = 12 * scale;
         float[] positiveX = new float[rows];
         float[] negativeX = new float[rows];
         float[] positiveU = new float[rows];
         float[] negativeU = new float[rows];
         float[] sampleV = new float[rows];
         int[] firstPixel = new int[rows];
         int[] lastPixel = new int[rows];
         float[][] fillU = new float[rows][8 * scale];
         boolean upperCut = false;
         boolean lowerCut = false;
         boolean hasRealHole = false;
         int u0 = 20 * scale;
         int u1 = 28 * scale;
         int v0 = 20 * scale;

         for (int row = 0; row < rows; row++) {
            int y = v0 + row;
            int first = u0;

            while (first < u1 && transparent(image.getPixelRGBA(first, y))) {
               first++;
            }

            int last = u1 - 1;

            while (last >= u0 && transparent(image.getPixelRGBA(last, y))) {
               last--;
            }

            if (first < u1 && last >= u0) {
               int lowCut = first - u0;
               int highCut = u1 - 1 - last;
               firstPixel[row] = lowCut;
               lastPixel[row] = 8 * scale - 1 - highCut;
               positiveX[row] = 4.0F - (float)lowCut / (float)scale;
               negativeX[row] = -4.0F + (float)highCut / (float)scale;
               positiveU[row] = ((float)first + 0.5F) / (float)scale;
               negativeU[row] = ((float)last + 0.5F) / (float)scale;
               if (lowCut > 0 || highCut > 0) {
                  if (row < 6 * scale) {
                     upperCut = true;
                  } else {
                     lowerCut = true;
                  }
               }

               for (int px = firstPixel[row]; px <= lastPixel[row]; px++) {
                  int source = u0 + px;
                  if (transparent(image.getPixelRGBA(source, y))) {
                     hasRealHole = true;
                     int distance = 1;

                     while (
                        source - distance >= first
                           && source + distance <= last
                           && transparent(image.getPixelRGBA(source - distance, y))
                           && transparent(image.getPixelRGBA(source + distance, y))
                     ) {
                        distance++;
                     }

                     if (source - distance >= first && !transparent(image.getPixelRGBA(source - distance, y))) {
                        source -= distance;
                     } else if (source + distance <= last) {
                        source += distance;
                     }
                  }

                  fillU[row][px] = ((float)source + 0.5F) / (float)scale;
               }
            } else {
               hasRealHole = true;
               firstPixel[row] = 8 * scale;
               lastPixel[row] = -1;
               positiveX[row] = 4.0F;
               negativeX[row] = -4.0F;
               positiveU[row] = 20.0F + 0.5F / (float)scale;
               negativeU[row] = 28.0F - 0.5F / (float)scale;
               if (row < 6 * scale) {
                  upperCut = true;
               } else {
                  lowerCut = true;
               }
            }

            sampleV[row] = ((float)y + 0.5F) / (float)scale;
         }

         if (hasRealHole) {
            WAIST_HOLES.add(key);
         } else {
            if (upperCut || lowerCut) {
               WAIST_PROFILES.put(
                  key, new SkinCache.WaistProfile(scale, positiveX, negativeX, positiveU, negativeU, sampleV, firstPixel, lastPixel, fillU, upperCut, lowerCut)
               );
            }
         }
      }
   }

   private static boolean transparent(int rgba) {
      return (rgba >>> 24 & 0xFF) < 16;
   }

   private static void copyMirrored(NativeImage img, int sx, int sy, int w, int h, int dx, int dy) {
      for (int x = 0; x < w; x++) {
         for (int y = 0; y < h; y++) {
            img.setPixelRGBA(dx + (w - 1 - x), dy + y, img.getPixelRGBA(sx + x, sy + y));
         }
      }
   }

   private static String sha1(String s) {
      try {
         MessageDigest md = MessageDigest.getInstance("SHA-1");
         byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
         StringBuilder sb = new StringBuilder();

         for (byte b : d) {
            sb.append(String.format("%02x", b));
         }

         return sb.toString();
      } catch (Exception var8) {
         return Integer.toHexString(s.hashCode());
      }
   }

   private SkinCache() {
   }

   public static record WaistProfile(
      int scale,
      float[] positiveX,
      float[] negativeX,
      float[] positiveU,
      float[] negativeU,
      float[] sampleV,
      int[] firstPixel,
      int[] lastPixel,
      float[][] fillU,
      boolean upperCut,
      boolean lowerCut
   ) {
   }
}
