package pro.webhm.playerinteractions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin.Model;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.joml.Matrix4f;
import pro.webhm.playerinteractions.Actions;
import pro.webhm.playerinteractions.Appearance;
import pro.webhm.playerinteractions.SessionState;
import pro.webhm.playerinteractions.TouchProfile;
import pro.webhm.playerinteractions.anim.Clip;
import pro.webhm.playerinteractions.anim.NpcAnimation;
import pro.webhm.playerinteractions.body.BodyPhysics;
import pro.webhm.playerinteractions.facial.FacialPose;
import pro.webhm.playerinteractions.npc.BodyData;
import pro.webhm.playerinteractions.npc.BoneTransform;
import pro.webhm.playerinteractions.npc.BreastData;
import pro.webhm.playerinteractions.npc.ModelShapeData;
import pro.webhm.playerinteractions.npc.NpcBone;
import pro.webhm.playerinteractions.npc.RootPivot;

public final class AvatarRig extends SharedBodyModel<AbstractClientPlayer> {
   private static final BodyData SCENE = Appearance.sceneRig();
   private static final Map<UUID, AvatarRig.PhysicsFrame> PHYSICS = new HashMap<>();
   FacialPose facialPose;

   public AvatarRig(boolean slim) {
      super(createLayer(slim).bakeRoot());
   }

   @Override
   public void renderToBuffer(PoseStack stack, VertexConsumer out, int light, int overlay, int color) {
      super.renderToBuffer(stack, this.strokeBuffer(stack, out), light, overlay, color);
   }

   public void setupAnim(AbstractClientPlayer entity, float limb, float amount, float age, float yaw, float pitch) {
   }

   public static float sceneScale() {
      return SCENE.scale;
   }

   public static void clear() {
      PHYSICS.clear();
   }

   public void prepare(AbstractClientPlayer player, Map<String, BoneTransform> pose, float partial, boolean scene) {
      BodyData data = new BodyData();
      data.breast = AppearanceClient.get(player.getUUID());
      data.slim = player.getSkin().model() == Model.SLIM;
      data.modelShape = scene ? SCENE.modelShape : new ModelShapeData();
      data.rootPivot = scene ? SCENE.rootPivot : "center";
      data.pivotX = SCENE.pivotX;
      data.pivotY = SCENE.pivotY;
      data.pivotZ = SCENE.pivotZ;
      AvatarRig.PhysicsFrame frame = PHYSICS.computeIfAbsent(player.getUUID(), id -> new AvatarRig.PhysicsFrame());
      if (frame.tick != player.tickCount) {
         frame.tick = player.tickCount;
         frame.value.tick(player, data, pose);
      }

      this.configure(data, pose, partial, frame.value, player.isModelPartShown(PlayerModelPart.HAT), player.isModelPartShown(PlayerModelPart.JACKET));
      SessionState session = InteractionClient.forPlayer(player.getUUID());
      if (session != null && TouchProfile.DATA.facial.enabled) {
         this.facialPose = FacialPose.rest(TouchProfile.DATA.facial);
      }

      if (session != null && Actions.scene(session.action)) {
         SessionState.Actor actor = session.actor(player.getUUID());
         float time = (float)Math.max(0.0, session.time(player.level().getGameTime(), partial) - 12.0);
         NpcAnimation.FaceKey key = actor.clip(session.action).original.sampleFace(time, false);
         if (key != null) {
            if (this.facialPose != null && key.facialKey) {
               this.facialPose = this.facialPose.expression(key.smile, key.brow, key.squint);
               if (key.manualMouth) {
                  this.facialPose = new FacialPose(key.open, 1.0F, this.facialPose.smile(), this.facialPose.brow(), this.facialPose.squint());
               }
            }

            if (key.aimHead) {
               float entry = actor.slot == 0 && session.joining() ? Clip.smooth(session.joinTime(player.level().getGameTime(), partial) / 12.0) : 1.0F;
               this.bones.get("head").yRot = key.headYaw * (float) (Math.PI / 180.0) * entry;
               this.bones.get("head").xRot = key.headPitch * (float) (Math.PI / 180.0) * entry;
            }
         }
      }

      if (PlayerTouchReaction.active(player)) {
         FacialPose rest = FacialPose.rest(TouchProfile.DATA.facial);
         this.facialPose = PlayerTouchReaction.mouthContact(player, partial, PlayerTouchReaction.facial(player, partial, rest));
         PlayerTouchReaction.head(player, this.bones.get("head"), partial, this.facialPose);
         PlayerTouchReaction.breast(player, this.leftBreast, this.rightBreast, partial);
      }

      if (this.facialPose != null) {
         this.facialPose = this.facialPose
            .expression(
               this.facialPose.smile(),
               this.facialPose.brow(),
               Math.max(this.facialPose.squint(), Math.max(0.0F, this.facialPose.smile()) * TouchProfile.DATA.facial.expressionLink * 0.24F)
            );
      }
   }

   void configure(BodyData data, Map<String, BoneTransform> pose, float partial, BodyPhysics physics, boolean hat, boolean jacket) {
      this.strokeDent = null;
      this.facialPose = null;
      this.renderPartialTick = partial;
      this.currentSkinKey = "";
      this.currentSkinScale = 1;
      this.currentSkinLayers3d = false;
      this.currentModelShape = data.modelShape;
      this.currentBreastData = data.breast;
      this.continuousLimbs = true;
      this.continuousArmWidth = data.slim ? 3.0F : 4.0F;
      this.continuousArmTextureWidth = this.continuousArmWidth;
      this.setBuiltInGeometryHidden(false);
      this.waistJointAllowed = !this.currentModelShape.hasCustomTorso();
      this.bodyBase.visible = this.chestBase.visible = !this.currentModelShape.hasCustomTorso();
      this.flatOuterParts.forEach(p -> p.visible = true);
      this.headOuter.visible = hat;
      this.bodyOuter.visible = this.chestOuter.visible = false;
      this.adaptiveChestOuter = data.breast.enabled && data.breast.jacket && jacket;
      this.applyPose(pose, false, RootPivot.byId(data.rootPivot), data.pivotX, data.pivotY, data.pivotZ);
      this.applyModelShape(data);
      this.breasts(data.breast, physics, partial, jacket);
      this.applyJointBridges();
   }

   private void breasts(BreastData b, BodyPhysics physics, float partial, boolean jacket) {
      this.rightBreast.visible = this.leftBreast.visible = b.enabled;
      this.rightBreastJacket.visible = this.leftBreastJacket.visible = b.jacket && jacket;
      float bounceL = b.physics ? Mth.lerp(partial, physics.breastBounceO, physics.breastBounce) : 0.0F;
      float bounceR = b.physics ? Mth.lerp(partial, physics.breastBounceRO, physics.breastBounceR) : 0.0F;
      float swayL = b.physics ? Mth.lerp(partial, physics.breastSwayO, physics.breastSway) : 0.0F;
      float swayR = b.physics ? Mth.lerp(partial, physics.breastSwayRO, physics.breastSwayR) : 0.0F;
      float width = Mth.lerp(0.25F, this.currentModelShape.torsoWidth, this.currentModelShape.waistWidth) / 8.0F;
      this.rightBreast.x = -b.separation * width;
      this.leftBreast.x = b.separation * width;
      this.rightBreast.y = this.leftBreast.y = -4.5F - b.height;
      this.rightBreast.z = this.leftBreast.z = -2.0F - b.depth;
      this.rightBreast.xRot = (-b.rotation + b.rightPitch + bounceR) * (float) (Math.PI / 180.0);
      this.leftBreast.xRot = (-b.rotation + b.leftPitch + bounceL) * (float) (Math.PI / 180.0);
      this.rightBreast.yRot = b.rightYaw * (float) (Math.PI / 180.0);
      this.leftBreast.yRot = b.leftYaw * (float) (Math.PI / 180.0);
      this.rightBreast.zRot = (b.rightRoll + swayR) * (float) (Math.PI / 180.0);
      this.leftBreast.zRot = (b.leftRoll + swayL) * (float) (Math.PI / 180.0);
      this.rightBreast.xScale = this.leftBreast.xScale = width;
      this.rightBreast.yScale = this.leftBreast.yScale = 1.0F;
      this.rightBreast.zScale = this.leftBreast.zScale = b.depthScale() / 4.0F;
   }

   public void renderBreasts(PoseStack stack, VertexConsumer out, int light, int overlay) {
      this.renderBreastGeometry(stack, out, light, overlay, -1);
   }

   public void renderHeldArm(PoseStack stack, VertexConsumer out, int light, int overlay, boolean left) {
      this.renderContinuousLimb(
         stack,
         out,
         left ? NpcBone.LEFT_ARM : NpcBone.RIGHT_ARM,
         left ? NpcBone.LEFT_FOREARM : NpcBone.RIGHT_FOREARM,
         true,
         !left,
         left ? 32 : 40,
         left ? 48 : 16,
         left ? 48 : 40,
         left ? 48 : 32,
         light,
         overlay,
         -1
      );
   }

   public void hideHead(boolean hidden) {
      this.bones.get("head").visible = !hidden;
   }

   public void contact(SceneTouchRuntime contact, int seed) {
      if (contact != null) {
         this.strokeDent = contact.stroke;
         contact.reaction.apply(this.rightBreast, this.leftBreast);
         if (TouchProfile.DATA.facial.autoExpressions) {
            contact.reaction.head(this.bones.get("head"), TouchProfile.DATA.facial.headMotion, seed);
            if (this.facialPose != null) {
               this.facialPose = contact.reaction.face(this.facialPose, TouchProfile.DATA.facial.emotionStrength, seed);
            }
         }
      }
   }

   public Matrix4f headMatrix() {
      PoseStack s = new PoseStack();
      this.translateToBone(NpcBone.HEAD, s);
      return new Matrix4f(s.last().pose());
   }

   public void setPlayerTorso(ModelPart body) {
      this.root.x = this.root.y = this.root.z = this.root.xRot = this.root.yRot = this.root.zRot = 0.0F;
      ModelPart torso = this.bones.get("body");
      torso.xRot = torso.yRot = torso.zRot = 0.0F;
      ModelPart chest = this.bones.get("chest");
      chest.xRot = chest.yRot = chest.zRot = 0.0F;
   }

   private static final class PhysicsFrame {
      final BodyPhysics value = new BodyPhysics();
      int tick = Integer.MIN_VALUE;
   }
}
