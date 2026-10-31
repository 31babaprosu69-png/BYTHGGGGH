package pro.webhm.playerinteractions.client;

import net.minecraftforge.fml.common.Mod;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.InputEvent.MouseScrollingEvent;
import net.minecraftforge.client.event.InputEvent.MouseButton.Pre;
import net.minecraftforge.client.event.ScreenEvent.Opening;
import net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles;
import org.joml.Vector3f;
import pro.webhm.playerinteractions.Actions;
import pro.webhm.playerinteractions.InteractionServer;
import pro.webhm.playerinteractions.Network;
import pro.webhm.playerinteractions.SessionState;
import pro.webhm.playerinteractions.TouchZones;
import pro.webhm.playerinteractions.anim.Clip;

@Mod.EventBusSubscriber(
   modid = "aot_player_interactions",
   value = {Dist.CLIENT}
)
public final class InteractionClient {
   public static final Map<UUID, SessionState> SESSIONS = new HashMap<>();
   private static final Map<UUID, Network.Update> PENDING_SESSIONS = new HashMap<>();
   public static InteractionServer.Invitation invitation;
   private static boolean pressed;
   private static boolean localLocked;
   private static boolean oldPhysics;
   private static boolean oldGravity;
   private static float viewYaw;
   private static float viewPitch;
   private static final SceneCameraControl SCENE_CAMERA = new SceneCameraControl();
   private static long lastTouch;
   private static long lastPet;
   private static final Map<UUID, float[]> RENDER_FACING = new HashMap<>();

   public static MutableComponent text(String key, Object... args) {
      return Component.translatable("aot_players." + key, args);
   }

   public static Component action(int id) {
      return (Component)(id >= 7 ? AnimationLibrary.label(id) : text("action." + id));
   }

   public static SessionState local() {
      Minecraft mc = Minecraft.getInstance();
      return mc.player == null ? null : forPlayer(mc.player.getUUID());
   }

   public static SessionState forPlayer(UUID id) {
      return SESSIONS.values().stream().filter(s -> s.actor(id) != null).findFirst().orElse(null);
   }

   public static boolean locked() {
      SessionState s = local();
      return s != null && s.controlsMovement(Minecraft.getInstance().player.getUUID());
   }

   public static boolean touchCamera() {
      Minecraft mc = Minecraft.getInstance();
      SessionState s = local();
      return mc.player != null && mc.level != null && s != null && s.receivingTouch(mc.player.getUUID(), mc.level.getGameTime());
   }

   public static void send(int op, UUID token, int target, int value, float x, float y) {
      if (Minecraft.getInstance().getConnection() != null) {
         Network.toServer(new Network.Command(op, token, target, value, x, y));
      }
   }

   public static void request(int target, int action) {
      send(0, Network.NONE, target, action, 0.0F, 0.0F);
   }

   public static void answer(boolean accept) {
      if (invitation != null) {
         send(accept ? 1 : 2, invitation.invite().id(), 0, 0, 0.0F, 0.0F);
      }
   }

   public static void stop() {
      SessionState s = local();
      if (s != null) {
         send(3, s.id, 0, 0, 0.0F, 0.0F);
      } else if (invitation != null) {
         send(3, invitation.invite().id(), 0, 0, 0.0F, 0.0F);
      }
   }

   public static void receive(Network.Update update) {
      String var1 = update.kind();
      switch (var1) {
         case "catalog_begin":
            AnimationLibrary.begin(update.json());
            break;
         case "catalog_end":
            AnimationLibrary.finish(update.json());
            break;
         case "appearance":
            AppearanceClient.receive(update.json());
            break;
         case "invite":
            invitation = (InteractionServer.Invitation)Clip.JSON.fromJson(update.json(), InteractionServer.Invitation.class);
            break;
         case "clear_invite":
            UUID idx = (UUID)Clip.JSON.fromJson(update.json(), UUID.class);
            if (invitation != null && invitation.invite().id().equals(idx)) {
               invitation = null;
               if (Minecraft.getInstance().screen instanceof InvitationScreen) {
                  Minecraft.getInstance().setScreen(null);
               }
            }
            break;
         case "session":
            SessionState state = (SessionState)Clip.JSON.fromJson(update.json(), SessionState.class);
            if (state.action >= 7 && (!AnimationLibrary.ready() || !Clip.hasCustom(state.action))) {
               if (PENDING_SESSIONS.size() < 128) {
                  PENDING_SESSIONS.put(state.id, update);
               }

               return;
            }

            SessionState previous = SESSIONS.get(state.id);
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
               state.bindClient(previous, level.getGameTime());
            }

            SESSIONS.put(state.id, state);
            TogetherClient.receive(state);
            PlayerTouchReaction.receive(state);
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null && state.action == 4 && state.initiator().id.equals(player.getUUID()) && !state.touching) {
               pressed = false;
            }
            break;
         case "stop":
            UUID id = (UUID)Clip.JSON.fromJson(update.json(), UUID.class);
            PENDING_SESSIONS.remove(id);
            PlayerTouchReaction.release(SESSIONS.remove(id));
            TogetherClient.clear(id);
            if (local() == null) {
               pressed = false;
               restore();
            }
      }
   }

   public static void catalogReady() {
      List<Network.Update> pending = List.copyOf(PENDING_SESSIONS.values());
      PENDING_SESSIONS.clear();

      for (Network.Update update : pending) {
         receive(update);
      }
   }

   private static void restore() {
      Minecraft mc = Minecraft.getInstance();
      if (localLocked && mc.player != null) {
         mc.player.noPhysics = oldPhysics;
         mc.player.setNoGravity(oldGravity);
      }

      localLocked = false;
      viewPitch = 0.0F;
      viewYaw = 0.0F;
      SCENE_CAMERA.clear();
   }

   public static boolean mouse(double x, double y) {
      SessionState state = local();
      if (state != null && Minecraft.getInstance().screen == null) {
         Minecraft mc = Minecraft.getInstance();
         if (state.receivingTouch(mc.player.getUUID(), mc.level.getGameTime())) {
            return true;
         } else {
            boolean firstPerson = mc.options.getCameraType().isFirstPerson();
            if (firstPerson && ScenePetClient.mouse(x, y)) {
               return true;
            } else if (state.action == 6 && state.recipient().id.equals(mc.player.getUUID())) {
               viewYaw = Mth.clamp(viewYaw + (float)x * 0.13F, -60.0F, 60.0F);
               viewPitch = Mth.clamp(viewPitch + (float)y * 0.13F, -45.0F, 45.0F);
               return true;
            } else {
               return SCENE_CAMERA.mouse(state, mc.player.getUUID(), firstPerson, mc.player.getYRot(), mc.player.getXRot(), x, y);
            }
         }
      } else {
         return false;
      }
   }

   @SubscribeEvent
   public static void camera(ComputeCameraAngles event) {
      Minecraft mc = Minecraft.getInstance();
      SessionState state = local();
      if (mc.player != null) {
         if (!mc.options.getCameraType().isFirstPerson()) {
            Vector3f angles = SCENE_CAMERA.angles(state, mc.player.getUUID(), false, null, event.getYaw(), event.getPitch());
            if (angles != null) {
               event.setYaw(angles.x);
               event.setPitch(angles.y);
               event.setRoll(0.0F);
            }
         } else if (touchCamera()) {
            Vector3f angles = AvatarRenderer.touchEye(mc.player, (float)event.getPartialTick()).angles();
            event.setYaw(angles.x);
            event.setPitch(angles.y);
            event.setRoll(angles.z);
         } else if (state != null && state.action != 4 && state.action != 5) {
            if (state.action != 6 || state.recipient().id.equals(mc.player.getUUID())) {
               if (!Actions.scene(state.action) || state.sceneActive(mc.player.getUUID())) {
                  float partial = (float)event.getPartialTick();
                  AvatarRenderer.Eye eye = state.action == 6
                     ? AvatarRenderer.togetherEye(mc.player, state, partial)
                     : AvatarRenderer.eye(mc.player, state, partial);
                  Vector3f angles = state.action == 6
                     ? SceneViewRotation.look(eye.angles(), viewYaw, viewPitch)
                     : SCENE_CAMERA.angles(state, mc.player.getUUID(), true, eye.angles(), event.getYaw(), event.getPitch());
                  event.setYaw(angles.x);
                  event.setPitch(angles.y);
                  event.setRoll(angles.z);
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void tick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null) {
         long now = mc.level.getGameTime();
         ScenePetClient.prune();
         if (invitation != null && now >= invitation.invite().expires()) {
            invitation = null;
         }

         SessionState state = local();

         while (PlayerKeys.consumeOpen()) {
            if (mc.screen == null) {
               if (state != null) {
                  if (!state.paused || !state.initiator().id.equals(mc.player.getUUID())) {
                     stop();
                  }
               } else if (invitation != null) {
                  if (invitation.incoming()) {
                     mc.setScreen(new InvitationScreen());
                  } else {
                     stop();
                  }
               } else {
                  AbstractClientPlayer target = target();
                  if (target == null) {
                     mc.player.displayClientMessage(text("look_at_player"), true);
                  } else {
                     mc.setScreen(new WheelScreen(target.getId(), target.getName().getString()));
                  }
               }
            }
         }

         if (state == null) {
            restore();
         } else if (Actions.together(state.action)) {
            mc.player.setSprinting(false);
            mc.options.keySprint.setDown(false);
            if (state.recipient().id.equals(mc.player.getUUID())) {
               if (!localLocked) {
                  localLocked = true;
                  oldPhysics = mc.player.noPhysics;
                  oldGravity = mc.player.isNoGravity();
               }

               mc.player.noPhysics = state.action == 6;
               mc.player.setNoGravity(true);
               mc.player.setDeltaMovement(Vec3.ZERO);
               Vec3 pos = TogetherClient.position(state, mc.player, 0.0F);
               mc.player.setPos(pos.x, pos.y, pos.z);
               mc.player.yBodyRot = TogetherClient.yaw(state, mc.player, 0.0F);
               mc.player.fallDistance = 0.0F;
            }
         } else {
            if (Actions.scene(state.action)) {
               if (!state.sceneActive(mc.player.getUUID())) {
                  restore();
                  return;
               }

               if (!localLocked) {
                  localLocked = true;
                  oldPhysics = mc.player.noPhysics;
                  oldGravity = mc.player.isNoGravity();
               }

               mc.player.noPhysics = true;
               mc.player.setNoGravity(true);
               mc.player.setDeltaMovement(Vec3.ZERO);
               mc.player.fallDistance = 0.0F;
               SessionState.Actor actor = state.actor(mc.player.getUUID());
               double time = state.time(now, 0.0F);
               double approach = state.joinTime(now, 0.0F);
               Vec3 target = state.target(actor, time, approach);
               mc.player.setPos(target.x, target.y, target.z);
               float yaw = state.heading(actor, time, approach);
               mc.player.setYRot(yaw);
               mc.player.yBodyRot = yaw;
               mc.player.setYHeadRot(yaw);
               if (state.paused && state.initiator().id.equals(mc.player.getUUID()) && now - lastPet >= 2L) {
                  lastPet = now;
                  ScenePetClient.input(state);
               }
            } else {
               if (state.receivingTouch(mc.player.getUUID(), now)) {
                  mc.player.setYRot(state.touchHeadYaw);
                  mc.player.setYHeadRot(state.touchHeadYaw);
                  mc.player.yBodyRot = state.touchBodyYaw;
                  mc.player.setXRot(state.touchPitch);
               }

               if (pressed && (mc.screen != null || !mc.isWindowActive())) {
                  pressed = false;
                  send(4, state.id, 0, 0, 0.0F, 0.0F);
               }

               if (pressed && state.touching && now - lastTouch >= 3L) {
                  lastTouch = now;
                  send(4, state.id, 0, 2, 0.0F, 0.0F);
               }
            }
         }
      } else {
         restore();
         SESSIONS.clear();
         invitation = null;
         pressed = false;
      }
   }

   private static AbstractClientPlayer target() {
      Minecraft mc = Minecraft.getInstance();
      Vec3 eye = mc.player.getEyePosition();
      Vec3 end = eye.add(mc.player.getLookAngle().scale(4.0));
      BlockHitResult block = mc.level.clip(new ClipContext(eye, end, Block.COLLIDER, Fluid.NONE, mc.player));
      double distance = eye.distanceToSqr(block.getLocation());
      AbstractClientPlayer best = null;

      for (AbstractClientPlayer player : mc.level.players()) {
         if (player != mc.player && player.isAlive() && !player.isSpectator()) {
            Optional<Vec3> hit = player.getBoundingBox().inflate(0.1).clip(eye, end);
            if (hit.isPresent() && eye.distanceToSqr(hit.get()) < distance) {
               best = player;
               distance = eye.distanceToSqr(hit.get());
            }
         }
      }

      return best;
   }

   public static TouchZones.Hit touchHit() {
      Minecraft mc = Minecraft.getInstance();
      SessionState state = local();
      if (state != null && state.action == 4 && state.initiator().id.equals(mc.player.getUUID())) {
         Player target = mc.level.getPlayerByUUID(state.recipient().id);
         return target instanceof AbstractClientPlayer player
            ? TouchZones.pick(target, Map.of(), mc.player.getEyePosition(), mc.player.getLookAngle(), SkinCompat.slim(player))
            : null;
      } else {
         return null;
      }
   }

   @SubscribeEvent
   public static void click(Pre event) {
      Minecraft mc = Minecraft.getInstance();
      SessionState state = local();
      if (state != null && mc.screen == null) {
         if (!Actions.scene(state.action) && !Actions.together(state.action)) {
            if (state.initiator().id.equals(mc.player.getUUID()) && mc.options.keyAttack.matchesMouse(event.getButton())) {
               event.setCanceled(true);
               if (event.getAction() == 1 && mc.player.getMainHandItem().isEmpty() && touchHit() != null) {
                  pressed = true;
                  send(4, state.id, 0, 1, 0.0F, 0.0F);
               } else if (event.getAction() == 0) {
                  pressed = false;
                  send(4, state.id, 0, 0, 0.0F, 0.0F);
               }
            }
         } else {
            event.setCanceled(true);
            if (state.waitingForJoin && state.initiator().id.equals(mc.player.getUUID()) && event.getButton() == 1 && event.getAction() == 1) {
               send(7, state.id, 0, 0, 0.0F, 0.0F);
            }

            if (event.getButton() == 0 && event.getAction() == 1 && state.canRepeat(mc.player.getUUID())) {
               send(9, state.id, 0, state.loop.pass, 0.0F, 0.0F);
            }
         }
      }
   }

   @SubscribeEvent
   public static void scroll(MouseScrollingEvent event) {
      SessionState state = local();
      Minecraft mc = Minecraft.getInstance();
      if (state != null && state.action == 4 && mc.screen == null && state.initiator().id.equals(mc.player.getUUID())) {
         event.setCanceled(true);
         TouchZones.Hit hit = touchHit();
         int zone = state.touching ? state.zone : (hit == null ? -1 : hit.zone());
         if ((zone == 0 || zone == 12) && event.getScrollDeltaY() != 0.0) {
            send(5, state.id, 0, 1 - state.mode, 0.0F, 0.0F);
         }
      }
   }

   @SubscribeEvent
   public static void input(MovementInputUpdateEvent event) {
      SessionState s = local();
      if (s != null) {
         Input i = event.getInput();
         if (locked()) {
            i.leftImpulse = i.forwardImpulse = 0.0F;
            i.up = i.down = i.left = i.right = i.jumping = i.shiftKeyDown = false;
         } else if (Actions.together(s.action)) {
            i.leftImpulse *= 0.75F;
            i.forwardImpulse *= 0.75F;
         }
      }
   }

   @SubscribeEvent
   public static void renderPlayer(net.minecraftforge.client.event.RenderPlayerEvent.Pre event) {
      if (!AvatarRenderer.render(event)) {
         Player player = event.getEntity();
         SessionState s = forPlayer(player.getUUID());
         if (s != null && s.sceneActive(player.getUUID())) {
            RENDER_FACING.put(player.getUUID(), new float[]{player.yBodyRot, player.yBodyRotO, player.yHeadRot, player.yHeadRotO});
            float yaw = s.actor(player.getUUID()).heading(s.action, s.time(player.level().getGameTime(), event.getPartialTick()));
            player.yBodyRot = player.yBodyRotO = player.yHeadRot = player.yHeadRotO = yaw;
         }
      }
   }

   @SubscribeEvent
   public static void renderedPlayer(net.minecraftforge.client.event.RenderPlayerEvent.Post event) {
      Player player = event.getEntity();
      float[] previous = RENDER_FACING.remove(player.getUUID());
      if (previous != null) {
         player.yBodyRot = previous[0];
         player.yBodyRotO = previous[1];
         player.yHeadRot = previous[2];
         player.yHeadRotO = previous[3];
      }
   }

   @SubscribeEvent
   public static void screen(Opening event) {
      if (local() != null && event.getNewScreen() instanceof PauseScreen) {
         stop();
      }
   }

   @SubscribeEvent
   public static void logout(LoggingOut event) {
      restore();
      SESSIONS.clear();
      PENDING_SESSIONS.clear();
      AnimationLibrary.clear();
      RENDER_FACING.clear();
      TogetherClient.clear();
      invitation = null;
      pressed = false;
      PlayerAnimation.clear();
      AppearanceClient.clear();
      AvatarRig.clear();
   }

   @SubscribeEvent
   public static void hud(net.minecraftforge.client.event.RenderGuiEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && !mc.options.hideGui && mc.screen == null) {
         InteractionHud.render(event.getGuiGraphics());
         SceneStaminaHud.render(event.getGuiGraphics(), event.getPartialTick());
      }
   }

   @SubscribeEvent
   public static void hand(RenderHandEvent event) {
      AvatarRenderer.walkHand(event);
   }

   @SubscribeEvent
   public static void crosshair(net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre event) {
      SessionState s = local();
      Minecraft mc = Minecraft.getInstance();
      if (s != null && s.action == 4 && s.initiator().id.equals(mc.player.getUUID()) && event.getOverlay().equals(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.CROSSHAIR.type())) {
         event.setCanceled(true);
      }
   }

   private InteractionClient() {
   }
}
