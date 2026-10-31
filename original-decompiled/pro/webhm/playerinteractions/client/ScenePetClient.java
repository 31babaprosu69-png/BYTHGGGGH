package pro.webhm.playerinteractions.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin.Model;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import pro.webhm.playerinteractions.Actions;
import pro.webhm.playerinteractions.Appearance;
import pro.webhm.playerinteractions.SessionState;
import pro.webhm.playerinteractions.anim.Clip;
import pro.webhm.playerinteractions.anim.ScenePetPoint;
import pro.webhm.playerinteractions.anim.SceneTouchMotion;
import pro.webhm.playerinteractions.npc.BodyData;
import pro.webhm.playerinteractions.npc.BoneTransform;

public final class ScenePetClient {
   private static final Map<UUID, ScenePetClient.State> STATES = new HashMap<>();
   private static final Map<UUID, BodyData> BODIES = new HashMap<>();

   private static ScenePetClient.State state(SessionState session, float partial) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && session != null && Actions.scene(session.action)) {
         ScenePetClient.State s = STATES.get(session.id);
         int pass = session.loop == null ? 0 : session.loop.pass;
         if (s != null && session.paused && (s.release >= 0L || s.pass != pass)) {
            STATES.remove(session.id);
            s = null;
         }

         if (s == null) {
            if (!session.paused) {
               return null;
            }

            Player target = mc.level.getPlayerByUUID(session.recipient().id);
            if (target == null) {
               return null;
            }

            s = new ScenePetClient.State();
            s.owner = session.initiator().id;
            s.session = session.id;
            s.npc = target.getId();
            s.pass = pass;
            s.point = Clip.get(session.action, false)
               .original
               .touchPoints()
               .stream()
               .filter(p -> p.tick == Clip.get(session.action, false).pauseTick())
               .findFirst()
               .orElseThrow()
               .copy();
            s.point.normalize(Integer.MAX_VALUE);
            s.cursorX = session.dragX;
            s.cursorY = session.dragY;
            STATES.put(session.id, s);
         }

         if (!session.paused && s.release < 0L) {
            s.release = mc.level.getGameTime();
         }

         boolean own = mc.player != null && s.owner.equals(mc.player.getUUID());
         s.pressure = session.pressure;
         if (own && mc.screen == null) {
            s.pressure = GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), 0) == 1;
         }

         s.advance(mc.level.getGameTime(), partial, own ? s.cursorX : session.dragX, own ? s.cursorY : session.dragY, s.pressure, s.release);
         return s;
      } else {
         return null;
      }
   }

   public static Map<String, BoneTransform> pose(UUID owner, Map<String, BoneTransform> original, float partial) {
      SessionState session = InteractionClient.forPlayer(owner);
      if (session != null && session.initiator().id.equals(owner)) {
         ScenePetClient.State s = state(session, partial);
         if (s != null && !(s.weight <= 0.0F)) {
            Map<String, BoneTransform> result = s.touch.update(s, partial);
            return result.isEmpty() ? original : result;
         } else {
            return original;
         }
      } else {
         return original;
      }
   }

   public static SceneTouchRuntime contact(UUID target, float partial) {
      SessionState session = InteractionClient.forPlayer(target);
      if (session != null && session.recipient().id.equals(target)) {
         ScenePetClient.State s = state(session, partial);
         if (s == null) {
            return null;
         } else {
            s.touch.update(s, partial);
            return s.touch;
         }
      } else {
         return null;
      }
   }

   public static float weight(UUID owner, float partial) {
      SessionState session = InteractionClient.forPlayer(owner);
      if (session != null && session.initiator().id.equals(owner)) {
         ScenePetClient.State s = state(session, partial);
         return s == null ? 0.0F : s.weight;
      } else {
         return 0.0F;
      }
   }

   public static ScenePetClient.TouchActor touchActor(UUID owner, float partial) {
      Minecraft mc = Minecraft.getInstance();
      SessionState session = InteractionClient.forPlayer(owner);
      if (session != null && Actions.scene(session.action) && mc.level != null) {
         SessionState.Actor actor = session.actor(owner);
         if (mc.level.getPlayerByUUID(owner) instanceof AbstractClientPlayer p) {
            double time = session.time(mc.level.getGameTime(), partial);
            Map pose = actor.rigPose(session.action, time, session.duration());
            return new ScenePetClient.TouchActor(
               pose, actor.target(session.action, time), actor.heading(session.action, time), p.getSkin().model() == Model.SLIM
            );
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public static BodyData body(AbstractClientPlayer player) {
      BodyData d = BODIES.computeIfAbsent(player.getUUID(), id -> Appearance.sceneRig());
      d.slim = player.getSkin().model() == Model.SLIM;
      d.breast = AppearanceClient.get(player.getUUID());
      return d;
   }

   public static boolean mouse(double dx, double dy) {
      SessionState session = InteractionClient.local();
      Minecraft mc = Minecraft.getInstance();
      if (session != null && session.paused && session.initiator().id.equals(mc.player.getUUID())) {
         ScenePetClient.State s = state(session, 0.0F);
         if (s == null) {
            return false;
         } else {
            s.touch.move(s, dx / mc.getWindow().getGuiScale() * 0.012, dy / mc.getWindow().getGuiScale() * 0.012);
            return true;
         }
      } else {
         return false;
      }
   }

   public static void input(SessionState session) {
      Minecraft mc = Minecraft.getInstance();
      ScenePetClient.State s = state(session, 0.0F);
      if (s != null) {
         boolean focus = mc.screen == null && mc.isWindowActive();
         int flags = (focus && PlayerKeys.OPEN.isDown() ? 1 : 0) | (focus && GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), 0) == 1 ? 2 : 0);
         InteractionClient.send(6, session.id, 0, flags, s.cursorX, s.cursorY);
      }
   }

   public static void clear() {
      STATES.clear();
      BODIES.clear();
   }

   public static void prune() {
      STATES.keySet().removeIf(id -> !InteractionClient.SESSIONS.containsKey(id));
   }

   private ScenePetClient() {
   }

   public static final class State extends SceneTouchMotion {
      public UUID owner;
      public UUID session;
      public int npc;
      public int pass;
      public ScenePetPoint point;
      public float cursorX;
      public float cursorY;
      public boolean preview;
      public boolean pressure;
      public long release = -1L;
      public final SceneTouchRuntime touch = new SceneTouchRuntime();
   }

   public static record TouchActor(Map<String, BoneTransform> pose, Vec3 position, float yaw, boolean slim) {
   }
}
