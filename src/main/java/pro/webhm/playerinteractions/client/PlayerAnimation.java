package pro.webhm.playerinteractions.client;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess.AnimationRegister;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import pro.webhm.playerinteractions.PairCollision;
import pro.webhm.playerinteractions.SessionState;
import pro.webhm.playerinteractions.body.TouchSpring;
import pro.webhm.playerinteractions.npc.BoneTransform;

public final class PlayerAnimation {
   private static final FirstPersonConfiguration FIRST = new FirstPersonConfiguration()
      .setShowRightArm(true)
      .setShowLeftArm(true)
      .setShowRightItem(false)
      .setShowLeftItem(false);
   private static final Map<UUID, PlayerAnimation.TouchBlend> TOUCH = new HashMap<>();

   private static PlayerAnimation.TouchBlend touch(AbstractClientPlayer p) {
      PlayerAnimation.TouchBlend b = TOUCH.computeIfAbsent(p.getUUID(), id -> new PlayerAnimation.TouchBlend());
      b.update(p);
      return b;
   }

   static boolean fading(AbstractClientPlayer p) {
      PlayerAnimation.TouchBlend b = touch(p);
      return b.state != null && b.state.initiator().id.equals(p.getUUID()) && b.weight > 0.002F;
   }

   public static void clear() {
      TOUCH.clear();
      ScenePetClient.clear();
      PlayerTouchReaction.clear();
   }

   static Map<String, BoneTransform> touchPose(AbstractClientPlayer p, Map<String, BoneTransform> base, float partial) {
      PlayerAnimation.TouchBlend b = touch(p);
      SessionState s = b.state;
      if (s != null && !(b.weight <= 0.002F) && s.initiator().id.equals(p.getUUID())) {
         Player target = p.level().getPlayerByUUID(s.recipient().id);
         if (target == null) {
            return base;
         } else {
            Vec3 offset = new Vec3(s.contactX, s.contactY, s.contactZ).yRot(-((float)Math.toRadians((double)target.yBodyRot)));
            Vec3 at = target.getPosition(partial).add(offset);
            Vec3 pos = p.getPosition(partial);
            b.arm.begin(base, (float)pos.x, (float)pos.y, (float)pos.z, p.yBodyRot, SkinCompat.slim(p));
            return new HashMap<>(b.arm.solve(new Vector3f((float)at.x, (float)at.y, (float)at.z), 170.0F, b.weight));
         }
      } else {
         return base;
      }
   }

   public static void register() {
      PairCollision.clientSession = InteractionClient::forPlayer;
      PlayerAnimationAccess.REGISTER_ANIMATION_EVENT
         .register((AnimationRegister)(player, stack) -> stack.addAnimLayer(10100, new PlayerAnimation.Visibility(player)));
      SceneCameraBridge.mouse = InteractionClient::mouse;
      SceneCameraBridge.installFullBody(() -> {
         LocalPlayer p = Minecraft.getInstance().player;
         return p != null && AvatarRenderer.fullBody(p);
      });
      SceneCameraBridge.install((camera, partial) -> {
         Minecraft mc = Minecraft.getInstance();
         SessionState s = InteractionClient.local();
         if (mc.player == null || !mc.options.getCameraType().isFirstPerson()) {
            return null;
         } else if (InteractionClient.touchCamera()) {
            return AvatarRenderer.touchEye(mc.player, partial).position();
         } else if (s != null && s.action == 5 && s.recipient().id.equals(mc.player.getUUID())) {
            return TogetherClient.position(s, mc.player, partial).add(0.0, (double)mc.player.getEyeHeight(), 0.0);
         } else if (s != null && s.action == 6 && s.recipient().id.equals(mc.player.getUUID())) {
            return AvatarRenderer.togetherEye(mc.player, s, partial).position();
         } else {
            return s != null && s.sceneActive(mc.player.getUUID()) ? AvatarRenderer.eye(mc.player, s, partial).position() : null;
         }
      });
   }

   private PlayerAnimation() {
   }

   static final class TouchBlend {
      final TouchSpring spring = new TouchSpring();
      final SceneTouchArm arm = new SceneTouchArm();
      SessionState state;
      double sampled = Double.NaN;
      float weight;

      void update(AbstractClientPlayer player) {
         double now = (double)((float)player.level().getGameTime() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
         if (!Double.isFinite(this.sampled) || !(now <= this.sampled)) {
            this.sampled = now;
            SessionState next = InteractionClient.forPlayer(player.getUUID());
            if (next != null && next.action == 4) {
               this.state = next;
            }

            this.spring.sample(now, next != null && next.action == 4 && next.touching && next.zone == 11 && next.mode == 1 ? 1.0F : 0.0F, 0.0F, 0.0F);
            this.weight = Math.max(0.0F, Math.min(1.0F, this.spring.x));
         }
      }
   }

   private static record Visibility(AbstractClientPlayer player) implements IAnimation {
      public boolean isActive() {
         return AvatarRenderer.fullBody(this.player);
      }

      public void setupAnim(float partial) {
      }

      public Vec3f get3DTransform(String bone, TransformType type, float partial, Vec3f base) {
         return base;
      }

      public FirstPersonMode getFirstPersonMode(float partial) {
         return FirstPersonMode.THIRD_PERSON_MODEL;
      }

      public FirstPersonConfiguration getFirstPersonConfiguration(float partial) {
         return PlayerAnimation.FIRST;
      }
   }
}
