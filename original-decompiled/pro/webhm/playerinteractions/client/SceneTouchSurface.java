package pro.webhm.playerinteractions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import pro.webhm.playerinteractions.SessionState;
import pro.webhm.playerinteractions.adornments.AdornmentProject;
import pro.webhm.playerinteractions.anim.ScenePetPoint;
import pro.webhm.playerinteractions.body.SurfaceDent;
import pro.webhm.playerinteractions.npc.BodyData;
import pro.webhm.playerinteractions.npc.BoneTransform;

public final class SceneTouchSurface {
   public final SurfaceSnapshot mesh = new SurfaceSnapshot();
   final SurfaceSnapshot[] volumes = new SurfaceSnapshot[]{new SurfaceSnapshot(), new SurfaceSnapshot()};
   final Matrix4f[] volumeFrames = new Matrix4f[]{new Matrix4f(), new Matrix4f()};
   boolean hasVolumes;
   public final Matrix4f world = new Matrix4f();
   public final Matrix4f inverse = new Matrix4f();
   public Vec3 origin = Vec3.ZERO;
   private AvatarRig model;
   private float width = -1.0F;
   private double tick = Double.NaN;
   private Object data;
   private final PoseStack stack = new PoseStack();
   private final Matrix4f bone = new Matrix4f();
   private final Vector3f a = new Vector3f();
   private final Vector3f b = new Vector3f();
   private final SurfaceSnapshot.Hit seed = new SurfaceSnapshot.Hit();
   private SurfaceDent contact;
   private SurfaceDent otherContact;
   private final Vector3f collisionEnd = new Vector3f();
   private final SurfaceSnapshot.HitFilter collisionFilter = this::blocksArm;

   void contactPatch(SurfaceDent dent) {
      this.contact = dent;
      this.otherContact = null;
   }

   void contactPatches(SurfaceDent first, SurfaceDent second) {
      this.contact = first;
      this.otherContact = second;
   }

   private boolean blocksArm(float x, float y, float z, float nx, float ny, float nz) {
      return this.blocksPatch(this.contact, x, y, z, nx, ny, nz) && this.blocksPatch(this.otherContact, x, y, z, nx, ny, nz);
   }

   private boolean blocksPatch(SurfaceDent contact, float x, float y, float z, float nx, float ny, float nz) {
      if (contact != null && !(this.collisionEnd.distanceSquared(x, y, z) > 0.0576F)) {
         float dx = x - contact.center.x;
         float dy = y - contact.center.y;
         float dz = z - contact.center.z;
         float h = dx * contact.normal.x + dy * contact.normal.y + dz * contact.normal.z;
         float radius = contact.radius + 0.025F;
         return Math.abs(h) > contact.depth + 0.025F
            || dx * dx + dy * dy + dz * dz - h * h > radius * radius
            || nx * contact.normal.x + ny * contact.normal.y + nz * contact.normal.z < (this.otherContact != null ? -0.05F : 0.5F);
      } else {
         return true;
      }
   }

   public void sample(AbstractClientPlayer npc, float partial, SurfaceDent dent) {
      BodyData d = ScenePetClient.body(npc);
      if (this.model == null || this.width != d.armWidthValue()) {
         this.width = d.armWidthValue();
         this.model = new AvatarRig(d.slim);
         this.tick = Double.NaN;
      }

      SessionState session = InteractionClient.forPlayer(npc.getUUID());
      if (session != null) {
         SessionState.Actor actor = session.actor(npc.getUUID());
         double age = session.time(npc.level().getGameTime(), partial);
         this.origin = actor.target(session.action, age);
         float yaw = actor.heading(session.action, age);
         float scale = AvatarRig.sceneScale();
         this.world.identity().rotateY((180.0F - yaw) * (float) Math.PI / 180.0F).scale(-scale, -scale, scale).translate(0.0F, -1.501F, 0.0F);
         this.inverse.set(this.world).invert();
         double time = (double)npc.level().getGameTime() + (double)partial;
         if (this.tick != time) {
            this.tick = time;
            Map<String, BoneTransform> pose = actor.rigPose(session.action, age, session.duration());
            this.model.prepare(npc, pose, partial, true);
            SurfaceSnapshot.Alpha alpha = (u, v) -> true;
            this.mesh.reset(alpha);
            this.model.renderToBuffer(this.stack, this.mesh, 0, 0, -1);
            this.model.protectStrokeJoints(dent);
            this.captureVolumes(alpha);
         }
      }
   }

   void captureVolumes(SurfaceSnapshot.Alpha alpha) {
      this.hasVolumes = this.model != null
         && this.model.currentBreastData != null
         && this.model.currentBreastData.enabled
         && this.model.rightBreast.visible
         && this.model.leftBreast.visible
         && !this.model.rightBreast.skipDraw
         && !this.model.leftBreast.skipDraw;
      if (this.hasVolumes) {
         for (int i = 0; i < 2; i++) {
            ModelPart part = i == 0 ? this.model.rightBreast : this.model.leftBreast;
            ModelPart coat = i == 0 ? this.model.rightBreastJacket : this.model.leftBreastJacket;
            this.frame(i == 0 ? "right_breast" : "left_breast", this.volumeFrames[i]);
            this.volumes[i].reset(alpha);
            this.model
               .renderBreastHalf(
                  this.stack,
                  this.volumes[i],
                  0,
                  0,
                  -1,
                  this.model.bones.get("body"),
                  this.model.bones.get("chest"),
                  part,
                  i == 0,
                  coat.visible && !coat.skipDraw
               );
            if (this.model.currentSkinLayers3d && coat.visible && !coat.skipDraw) {
               this.model.renderBreastSkinHalf3D(this.stack, this.volumes[i], 0, 0, -1, part, i == 0, this.model.currentSkinScale);
            }
         }
      }
   }

   public Matrix4f frame(String id, Matrix4f out) {
      this.stack.pushPose();
      this.model.touchBone(id, this.stack);
      out.set(this.stack.last().pose());
      this.stack.popPose();
      return out;
   }

   public Vector3f anchor(ScenePetPoint point, Vector3f out) {
      this.stack.pushPose();
      this.model.touchBone(point.bone, this.stack);
      if (point.anchored) {
         out.set(point.anchorX, point.anchorY, point.anchorZ).div(16.0F);
      } else {
         float y = -6.0F + point.v * 6.0F;
         out.set((point.u - 0.5F) * 8.0F, y, -2.25F);
         if (point.face == 1) {
            out.z = 2.25F;
         } else if (point.face > 1) {
            out.set(point.face == 2 ? -4.25F : 4.25F, y, (point.u - 0.5F) * 4.0F);
         }

         out.div(16.0F);
      }

      this.stack.last().pose().transformPosition(out);
      this.stack.popPose();
      return out;
   }

   public void bind(ScenePetPoint point, Vector3fc position) {
      String nearest = "body";
      float best = Float.POSITIVE_INFINITY;

      for (String id : AdornmentProject.BONES) {
         this.stack.pushPose();
         this.model.touchBone(id, this.stack);
         this.bone.set(this.stack.last().pose()).invert();
         this.bone.transformPosition(this.a.set(position)).mul(16.0F);
         this.stack.popPose();
         float y = id.equals("head") ? -4.0F : (!id.equals("body") && !id.equals("chest") ? (id.endsWith("breast") ? 0.0F : 3.0F) : -3.0F);
         float score = this.a.distanceSquared(0.0F, y, 0.0F);
         if (score < best) {
            best = score;
            nearest = id;
         }
      }

      point.bone = nearest;
      this.stack.pushPose();
      this.model.touchBone(nearest, this.stack);
      this.bone.set(this.stack.last().pose()).invert();
      this.bone.transformPosition(this.a.set(position)).mul(16.0F);
      this.stack.popPose();
      point.anchorX = this.a.x;
      point.anchorY = this.a.y;
      point.anchorZ = this.a.z;
      point.anchored = true;
   }

   public boolean clear(Vector3fc from, Vector3fc to, float inset) {
      this.inverse.transformPosition(this.a.set(from));
      this.inverse.transformPosition(this.b.set(to));
      this.collisionEnd.set(this.b);
      this.b.sub(this.a);
      float length = this.b.length();
      return length < inset || !this.mesh.ray(this.a, this.b, length - inset, this.seed, false, this.collisionFilter);
   }
}
