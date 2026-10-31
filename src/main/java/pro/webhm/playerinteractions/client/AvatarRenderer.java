package pro.webhm.playerinteractions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin.Model;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.EntityRenderersEvent.AddLayers;
import net.minecraftforge.client.event.RenderPlayerEvent.Pre;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import pro.webhm.playerinteractions.Actions;
import pro.webhm.playerinteractions.SessionState;
import pro.webhm.playerinteractions.TouchFacing;
import pro.webhm.playerinteractions.npc.BoneTransform;

public final class AvatarRenderer {
   private static AvatarRig wide;
   private static AvatarRig slim;

   private static AvatarRig model(AbstractClientPlayer p) {
      if (wide == null) {
         wide = new AvatarRig(false);
         slim = new AvatarRig(true);
      }

      return p.getSkin().model() == Model.SLIM ? slim : wide;
   }

   public static boolean usesRig(AbstractClientPlayer p) {
      SessionState s = InteractionClient.forPlayer(p.getUUID());
      return !p.isSpectator()
         && (
            s != null && (s.sceneActive(p.getUUID()) || Actions.together(s.action) || s.action == 4 && s.recipient().id.equals(p.getUUID()))
               || PlayerTouchReaction.active(p)
               || PlayerAnimation.fading(p)
         );
   }

   public static boolean fullBody(AbstractClientPlayer p) {
      SessionState s = InteractionClient.forPlayer(p.getUUID());
      return usesRig(p) && (s == null || s.action != 5);
   }

   public static void walkHand(RenderHandEvent event) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer p = mc.player;
      SessionState s = InteractionClient.local();
      if (p != null && s != null && s.action == 5 && !p.isInvisible() && mc.options.getCameraType().isFirstPerson()) {
         boolean left = s.recipient().id.equals(p.getUUID());
         HumanoidArm held = left ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
         HumanoidArm shown = event.getHand() == InteractionHand.MAIN_HAND ? p.getMainArm() : p.getMainArm().getOpposite();
         if (shown == held) {
            event.setCanceled(true);
         }

         if (event.getHand() == InteractionHand.MAIN_HAND) {
            float partial = event.getPartialTick();
            PlayerRenderer renderer = (PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(p);
            Map<String, BoneTransform> pose = TogetherClient.pose(
               s, p, ordinaryPose(p, (PlayerModel<AbstractClientPlayer>)renderer.getModel(), partial), partial
            );
            AvatarRig rig = model(p);
            rig.prepare(p, pose, partial, false);
            Camera camera = mc.gameRenderer.getMainCamera();
            PoseStack stack = new PoseStack();
            stack.mulPose(new Quaternionf(camera.rotation()).conjugate());
            Vec3 offset = TogetherClient.position(s, p, partial).subtract(camera.getPosition());
            stack.translate(offset.x, offset.y, offset.z);
            worldTransform(stack, TogetherClient.yaw(s, p, partial), 0.9375F);
            rig.renderHeldArm(
               stack,
               event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(p.getSkin().texture())),
               event.getPackedLight(),
               OverlayTexture.NO_OVERLAY,
               left
            );
         }
      }
   }

   static void worldTransform(PoseStack stack, float yaw, float scale) {
      stack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
      stack.scale(-scale, -scale, scale);
      stack.translate(0.0, -1.501, 0.0);
   }

   private static AvatarRig prepare(AbstractClientPlayer p, SessionState s, double time, float partial) {
      SessionState.Actor a = s.actor(p.getUUID());
      Map<String, BoneTransform> pose = s.rigPose(a, time, s.joinTime(p.level().getGameTime(), partial));
      pose = ScenePetClient.pose(p.getUUID(), pose, partial);
      AvatarRig m = model(p);
      m.prepare(p, pose, partial, a.slot == 1);
      return m;
   }

   private static float scale(SessionState s, AbstractClientPlayer p) {
      return s != null && Actions.scene(s.action) && s.actor(p.getUUID()).slot == 1 ? AvatarRig.sceneScale() : 0.9375F;
   }

   private static Map<String, BoneTransform> ordinaryPose(AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial) {
      model.attackTime = p.getAttackAnim(partial);
      model.riding = p.isPassenger();
      model.young = false;
      model.crouching = p.isCrouching();
      model.prepareMobModel(p, p.walkAnimation.position(partial), p.walkAnimation.speed(partial), partial);
      model.setupAnim(
         p,
         p.walkAnimation.position(partial),
         p.walkAnimation.speed(partial),
         (float)p.tickCount + partial,
         TouchFacing.relative(Mth.rotLerp(partial, p.yHeadRotO, p.yHeadRot), Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot)),
         p.getViewXRot(partial)
      );
      HashMap<String, BoneTransform> pose = new HashMap<>();
      put(pose, "head", model.head);
      put(pose, "body", model.body);
      put(pose, "right_arm", model.rightArm);
      put(pose, "left_arm", model.leftArm);
      put(pose, "right_leg", model.rightLeg);
      put(pose, "left_leg", model.leftLeg);
      SessionState s = InteractionClient.forPlayer(p.getUUID());
      if (s != null && s.receivingTouch(p.getUUID(), p.level().getGameTime())) {
         BoneTransform head = pose.get("head");
         head.ry = TouchFacing.relative(s.touchHeadYaw, s.touchBodyYaw);
         head.rx = s.touchPitch;
      }

      return pose;
   }

   private static void put(Map<String, BoneTransform> pose, String key, ModelPart part) {
      BoneTransform t = new BoneTransform();
      float degrees = 180.0F / (float)Math.PI;
      t.rx = part.xRot * degrees;
      t.ry = part.yRot * degrees;
      t.rz = part.zRot * degrees;
      pose.put(key, t);
   }

   public static boolean render(Pre event) {
      if (event.getEntity() instanceof AbstractClientPlayer p && usesRig(p)) {
         event.setCanceled(true);
         if (p.isInvisible()) {
            return true;
         }

         SessionState s = InteractionClient.forPlayer(p.getUUID());
         float partial = event.getPartialTick();
         boolean scene = s != null && Actions.scene(s.action);
         double time = scene ? s.time(p.level().getGameTime(), partial) : 0.0;
         AvatarRig rig = model(p);
         PoseStack stack = event.getPoseStack();
         stack.pushPose();
         float yaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
         if (s != null && s.receivingTouch(p.getUUID(), p.level().getGameTime())) {
            yaw = s.touchBodyYaw;
         }

         if (scene) {
            SessionState.Actor a = s.actor(p.getUUID());
            rig = prepare(p, s, time, partial);
            rig.contact(ScenePetClient.contact(p.getUUID(), partial), p.getId());
            double approach = s.joinTime(p.level().getGameTime(), partial);
            Vec3 delta = s.target(a, time, approach).subtract(renderPosition(p, partial));
            stack.translate(delta.x, delta.y, delta.z);
            yaw = s.heading(a, time, approach);
         } else {
            Map<String, BoneTransform> base = ordinaryPose(p, (PlayerModel<AbstractClientPlayer>)event.getRenderer().getModel(), partial);
            base = PlayerTouchReaction.apply(p, base, partial);
            base = PlayerAnimation.touchPose(p, base, partial);
            if (s != null && Actions.together(s.action)) {
               base = TogetherClient.pose(s, p, base, partial);
               yaw = TogetherClient.yaw(s, p, partial);
               Vec3 delta = TogetherClient.position(s, p, partial).subtract(renderPosition(p, partial));
               stack.translate(delta.x, delta.y, delta.z);
            }

            rig.prepare(p, base, partial, false);
            if (p.isCrouching() && (s == null || !Actions.together(s.action))) {
               stack.translate(0.0, -0.1171875, 0.0);
            }
         }

         worldTransform(stack, yaw, scale(s, p));
         boolean own = p == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType().isFirstPerson();
         rig.hideHead(own);

         try {
            rig.renderToBuffer(
               stack,
               event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(p.getSkin().texture())),
               event.getPackedLight(),
               OverlayTexture.NO_OVERLAY,
               -1
            );
         } finally {
            rig.hideHead(false);
            stack.popPose();
         }

         return true;
      }

      return false;
   }

   static Vec3 renderPosition(AbstractClientPlayer p, float partial) {
      return new Vec3(Mth.lerp((double)partial, p.xOld, p.getX()), Mth.lerp((double)partial, p.yOld, p.getY()), Mth.lerp((double)partial, p.zOld, p.getZ()));
   }

   public static AvatarRenderer.Eye touchEye(AbstractClientPlayer p, float partial) {
      PlayerRenderer renderer = (PlayerRenderer)Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(p);
      Map<String, BoneTransform> pose = PlayerTouchReaction.apply(p, ordinaryPose(p, (PlayerModel<AbstractClientPlayer>)renderer.getModel(), partial), partial);
      AvatarRig rig = model(p);
      rig.prepare(p, pose, partial, false);
      SessionState s = InteractionClient.forPlayer(p.getUUID());
      float yaw = s != null && s.receivingTouch(p.getUUID(), p.level().getGameTime()) ? s.touchBodyYaw : Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
      PoseStack stack = new PoseStack();
      if (p.isCrouching()) {
         stack.translate(0.0, -0.1171875, 0.0);
      }

      worldTransform(stack, yaw, 0.9375F);
      stack.mulPose(rig.headMatrix());
      Matrix4f matrix = stack.last().pose();
      Vector3f eye = matrix.transformPosition(0.0F, -0.25F, 0.0F, new Vector3f());
      Quaternionf orientation = matrix.getUnnormalizedRotation(new Quaternionf()).rotateZ((float) Math.PI);
      return new AvatarRenderer.Eye(renderPosition(p, partial).add((double)eye.x, (double)eye.y, (double)eye.z), SceneViewRotation.angles(orientation));
   }

   public static AvatarRenderer.Eye eye(AbstractClientPlayer p, SessionState s, float partial) {
      double time = s.time(p.level().getGameTime(), partial);
      SessionState.Actor a = s.actor(p.getUUID());
      AvatarRig rig = prepare(p, s, time, partial);
      rig.contact(ScenePetClient.contact(p.getUUID(), partial), p.getId());
      double approach = s.joinTime(p.level().getGameTime(), partial);
      PoseStack stack = new PoseStack();
      worldTransform(stack, s.heading(a, time, approach), scale(s, p));
      stack.mulPose(rig.headMatrix());
      Matrix4f matrix = stack.last().pose();
      Vector3f eye = matrix.transformPosition(0.0F, -0.25F, 0.0F, new Vector3f());
      Quaternionf orientation = matrix.getUnnormalizedRotation(new Quaternionf()).rotateZ((float) Math.PI);
      return new AvatarRenderer.Eye(s.target(a, time, approach).add((double)eye.x, (double)eye.y, (double)eye.z), SceneViewRotation.angles(orientation));
   }

   public static AvatarRenderer.Eye togetherEye(AbstractClientPlayer p, SessionState s, float partial) {
      PlayerRenderer renderer = (PlayerRenderer)Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(p);
      Map<String, BoneTransform> pose = TogetherClient.pose(s, p, ordinaryPose(p, (PlayerModel<AbstractClientPlayer>)renderer.getModel(), partial), partial);
      AvatarRig rig = model(p);
      rig.prepare(p, pose, partial, false);
      PoseStack stack = new PoseStack();
      worldTransform(stack, TogetherClient.yaw(s, p, partial), 0.9375F);
      stack.mulPose(rig.headMatrix());
      Matrix4f matrix = stack.last().pose();
      Vector3f eye = matrix.transformPosition(0.0F, -0.25F, 0.0F, new Vector3f());
      Quaternionf orientation = matrix.getUnnormalizedRotation(new Quaternionf()).rotateZ((float) Math.PI);
      return new AvatarRenderer.Eye(
         TogetherClient.position(s, p, partial).add((double)eye.x, (double)eye.y, (double)eye.z), SceneViewRotation.angles(orientation)
      );
   }

   public static void register(AddLayers event) {
      slim = null;
      wide = null;

      for (Model skin : event.getSkins()) {
         PlayerRenderer renderer = (PlayerRenderer)event.getSkin(skin);
         if (renderer != null) {
            renderer.addLayer(new AvatarRenderer.BreastLayer(renderer));
         }
      }
   }

   private AvatarRenderer() {
   }

   private static final class BreastLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
      BreastLayer(PlayerRenderer renderer) {
         super(renderer);
      }

      public void render(
         PoseStack stack,
         MultiBufferSource buffers,
         int light,
         AbstractClientPlayer p,
         float limb,
         float speed,
         float partial,
         float age,
         float yaw,
         float pitch
      ) {
         if (!AvatarRenderer.usesRig(p) && !p.isInvisible() && !p.isSpectator() && AppearanceClient.get(p.getUUID()).enabled) {
            AvatarRig rig = AvatarRenderer.model(p);
            rig.prepare(p, Map.of(), partial, false);
            rig.setPlayerTorso(((PlayerModel)this.getParentModel()).body);
            stack.pushPose();
            ((PlayerModel)this.getParentModel()).body.translateAndRotate(stack);
            rig.renderBreasts(stack, buffers.getBuffer(RenderType.entityTranslucent(p.getSkin().texture())), light, OverlayTexture.NO_OVERLAY);
            stack.popPose();
         }
      }
   }

   public static record Eye(Vec3 position, Vector3f angles) {
   }
}
