package pro.webhm.playerinteractions;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import pro.webhm.playerinteractions.npc.BoneTransform;

public final class TouchZones {
   public static final List<TouchZones.Region> FACE;
   public static final float MOUTH_X;
   public static final float MOUTH_Y;
   public static final float MOUTH_W;

   private static Matrix4f bone(Matrix4f parent, Map<String, BoneTransform> pose, String name, float x, float y, float z) {
      BoneTransform t = pose.getOrDefault(name, new BoneTransform());
      float rad = (float) (Math.PI / 180.0);
      return new Matrix4f(parent).translate(x + t.shiftX, y - t.height, z + t.shiftZ).rotateZ(t.rz * rad).rotateY(t.ry * rad).rotateX(t.rx * rad);
   }

   public static TouchZones.Hit pick(Player target, Map<String, BoneTransform> pose, Vec3 eye, Vec3 direction, boolean slim) {
      return pick(
         target.position(),
         target.yBodyRot,
         TouchFacing.relative(target.getYHeadRot(), target.yBodyRot),
         target.getXRot(),
         target.isCrouching(),
         slim,
         pose,
         eye,
         direction
      );
   }

   public static TouchZones.Hit pick(
      Vec3 origin, float yaw, float headYaw, float headPitch, boolean crouching, boolean slim, Map<String, BoneTransform> pose, Vec3 eye, Vec3 direction
   ) {
      float rad = (float) (Math.PI / 180.0);
      Matrix4f world = new Matrix4f()
         .translate((float)origin.x, (float)origin.y, (float)origin.z)
         .rotateY((180.0F - yaw) * rad)
         .scale(-0.9375F, -0.9375F, 0.9375F)
         .translate(0.0F, -1.501F + (crouching ? 0.125F : 0.0F), 0.0F)
         .scale(0.0625F);
      Matrix4f root = bone(world, pose, "root", 0.0F, 0.0F, 0.0F);
      Matrix4f body = bone(root, pose, "body", 0.0F, 12.0F, 0.0F);
      Matrix4f chest = bone(body, pose, "chest", 0.0F, -6.0F, 0.0F);
      Matrix4f head = bone(chest, pose, "head", 0.0F, -6.0F, 0.0F).rotateY(headYaw * rad).rotateX(headPitch * rad);
      ArrayList<TouchZones.Part> parts = new ArrayList<>();

      for (TouchZones.Region r : FACE) {
         if (r.enabled && r.reaction != 11) {
            float top = Math.max(-8.0F, r.y - r.height / 2.0F - 8.0F);
            float bottom = Math.min(0.0F, r.y + r.height / 2.0F - 8.0F);
            if (r.reaction == 7) {
               bottom = Math.min(bottom, -5.5F);
            }

            if (bottom > top) {
               parts.add(
                  new TouchZones.Part(
                     head,
                     new AABB(
                        (double)Math.max(-4.0F, r.x - r.width / 2.0F),
                        (double)top,
                        -4.002,
                        (double)Math.min(4.0F, r.x + r.width / 2.0F),
                        (double)bottom,
                        -4.001
                     ),
                     r.reaction,
                     r.x < 0.0F ? -1 : 1
                  )
               );
            }
         }
      }

      parts.add(new TouchZones.Part(head, new AABB(-4.0, -8.0, -4.0, 4.0, 0.0, 4.0), -1, 1));
      parts.add(new TouchZones.Part(head, new AABB(-4.0, -8.15, -4.0, 4.0, -8.0, 4.0), 7, 1));
      parts.add(new TouchZones.Part(body, new AABB(-4.0, -6.0, -2.05, 4.0, 0.0, 2.05), 3, 1));
      parts.add(new TouchZones.Part(chest, new AABB(-4.0, -6.0, -2.1, 4.0, 0.0, 2.1), 4, 1));

      for (int side : new int[]{-1, 1}) {
         String s = side < 0 ? "right_" : "left_";
         float w = slim ? 3.0F : 4.0F;
         Matrix4f arm = bone(chest, pose, s + "arm", (float)(side * 5), -4.0F, 0.0F);
         float lo = side < 0 ? 1.0F - w : -1.0F;
         float hi = side < 0 ? 1.0F : w - 1.0F;
         parts.add(new TouchZones.Part(arm, new AABB((double)lo, -2.0, -2.0, (double)hi, 1.0, 2.0), 1, side));
         parts.add(new TouchZones.Part(arm, new AABB((double)lo, 1.0, -2.0, (double)hi, 4.0, 2.0), 2, side));
         Matrix4f lower = bone(arm, pose, s + "forearm", 0.0F, 4.0F, 0.0F);
         parts.add(new TouchZones.Part(lower, new AABB((double)lo, 0.0, -2.0, (double)hi, 6.0, 2.0), 2, side));
         Matrix4f leg = bone(root, pose, s + "leg", (float)side * 1.9F, 12.0F, 0.0F);
         parts.add(new TouchZones.Part(leg, new AABB(-2.0, 0.0, -2.0, 2.0, 6.0, 2.0), 5, side));
         Matrix4f shin = bone(leg, pose, s + "shin", 0.0F, 6.0F, 0.0F);
         parts.add(new TouchZones.Part(shin, new AABB(-2.0, 0.0, -2.0, 2.0, 4.0, 2.0), 5, side));
         parts.add(new TouchZones.Part(shin, new AABB(-2.0, 4.0, -2.0, 2.0, 6.0, 2.0), 6, side));
      }

      Vec3 end = eye.add(direction.normalize().scale(3.0));
      TouchZones.Hit nearest = null;

      for (TouchZones.Part p : parts) {
         Matrix4f inverse = new Matrix4f(p.matrix).invert();
         Optional<Vec3> hit = p.box.clip(transform(inverse, eye), transform(inverse, end));
         if (!hit.isEmpty()) {
            Vec3 at = hit.get();
            Vec3 point = transform(p.matrix, at);
            double distance = point.distanceTo(eye);
            if (nearest == null || distance < nearest.distance) {
               nearest = new TouchZones.Hit(p.zone, p.zone == 4 ? (at.x < 0.0 ? -1 : 1) : p.side, (float)at.x, (float)at.y, point, distance);
            }
         }
      }

      return nearest != null && nearest.zone >= 0 ? nearest : null;
   }

   private static Vec3 transform(Matrix4f matrix, Vec3 p) {
      Vector3f v = matrix.transformPosition(new Vector3f((float)p.x, (float)p.y, (float)p.z));
      return new Vec3((double)v.x, (double)v.y, (double)v.z);
   }

   public static Vec3 localForce(Vec3 world, int zone, float yaw, float headYaw, float headPitch, Map<String, BoneTransform> pose) {
      Vec3 v = world.yRot((float)Math.toRadians((double)yaw));
      Vector3f local = new Vector3f((float)v.x, (float)(-v.y), (float)(-v.z));
      Matrix4f frame = bone(new Matrix4f(), pose, "root", 0.0F, 0.0F, 0.0F);
      if (zone != 3 && (zone < 5 || zone == 7 || zone == 11 || zone == 12)) {
         frame = bone(frame, pose, "body", 0.0F, 0.0F, 0.0F);
         frame = bone(frame, pose, "chest", 0.0F, 0.0F, 0.0F);
      }

      if (zone == 0 || zone == 11 || zone == 12) {
         frame = bone(frame, pose, "head", 0.0F, 0.0F, 0.0F).rotateY((float)Math.toRadians((double)headYaw)).rotateX((float)Math.toRadians((double)headPitch));
      }

      frame.invert().transformDirection(local);
      return new Vec3((double)local.x, (double)local.y, (double)local.z).scale(17.066668F);
   }

   private TouchZones() {
   }

   static {
      try {
         try (InputStream input = TouchZones.class.getResourceAsStream("/assets/aot_player_interactions/touch_defaults.json")) {
            JsonObject json = JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(input), StandardCharsets.UTF_8)).getAsJsonObject();
            FACE = new ArrayList<>();
            Gson gson = new Gson();
            json.getAsJsonObject("touchAreas")
               .entrySet()
               .forEach(ex -> FACE.add((TouchZones.Region)gson.fromJson((JsonElement)ex.getValue(), TouchZones.Region.class)));
            JsonObject mouth = json.getAsJsonObject("mouth");
            MOUTH_X = mouth.get("x").getAsFloat();
            MOUTH_Y = mouth.get("y").getAsFloat();
            MOUTH_W = mouth.get("width").getAsFloat();
         }
      } catch (IOException var6) {
         throw new ExceptionInInitializerError(var6);
      }
   }

   public static record Hit(int zone, int side, float x, float y, Vec3 point, double distance) {
   }

   private static record Part(Matrix4f matrix, AABB box, int zone, int side) {
   }

   public static record Region(boolean enabled, float x, float y, float width, float height, int reaction) {
   }
}
