package pro.webhm.playerinteractions;

import net.minecraftforge.fml.common.Mod;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.StartTracking;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent;
import pro.webhm.playerinteractions.anim.BedSceneOrigin;
import pro.webhm.playerinteractions.anim.Clip;

@Mod.EventBusSubscriber(
   modid = "aot_player_interactions"
)
public final class InteractionServer {
   private static final ConsentBook CONSENT = new ConsentBook();
   private static final Map<UUID, InteractionServer.Running> RUNNING = new LinkedHashMap<>();
   private static final Map<UUID, Long> LAST_COMMAND = new HashMap<>();

   public static boolean locked(UUID player) {
      UUID id = CONSENT.reserved(player);
      InteractionServer.Running run = id == null ? null : RUNNING.get(id);
      return run != null && run.state.controlsMovement(player);
   }

   public static boolean busy() {
      return !RUNNING.isEmpty() || !CONSENT.invitations().isEmpty();
   }

   public static SessionState session(UUID player) {
      UUID id = CONSENT.reserved(player);
      InteractionServer.Running run = id == null ? null : RUNNING.get(id);
      return run == null ? null : run.state;
   }

   public static boolean walkingLook(UUID player) {
      UUID id = CONSENT.reserved(player);
      InteractionServer.Running run = id == null ? null : RUNNING.get(id);
      return run != null && run.state.action == 5 && run.state.recipient().id.equals(player);
   }

   private static long now(ServerPlayer p) {
      return p.server.overworld().getGameTime();
   }

   private static boolean available(ServerPlayer p) {
      return p != null && p.isAlive() && !p.isSpectator() && !p.isSleeping() && !p.isPassenger() && !p.isFallFlying() && !p.isSwimming();
   }

   private static boolean nearby(ServerPlayer a, ServerPlayer b, double radius) {
      return available(a) && available(b) && a.level() == b.level() && a.distanceToSqr(b) <= radius * radius && a.hasLineOfSight(b);
   }

   private static void message(ServerPlayer p, String key) {
      if (p != null) {
         p.displayClientMessage(Component.translatable("aot_players." + key), true);
      }
   }

   private static void send(ServerPlayer p, String kind, Object value) {
      if (p != null) {
         Network.toPlayer(p, new Network.Update(kind, Clip.JSON.toJson(value)));
      }
   }

   private static void publish(MinecraftServer server, SessionState state) {
      ServerPlayer actor = server.getPlayerList().getPlayer(state.initiator().id);
      if (actor != null) {
         for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (viewer.level() == actor.level()) {
               send(viewer, "session", state);
            }
         }
      }
   }

   public static void command(ServerPlayer sender, Network.Command command) {
      if (command.op() == 8) {
         AnimationCatalog.request(sender, command.value() == 1);
      } else {
         long tick = now(sender);
         UUID actor = sender.getUUID();
         if (command.op() == 0) {
            if (tick - LAST_COMMAND.getOrDefault(actor, -100L) >= 10L) {
               LAST_COMMAND.put(actor, tick);
               if (!(sender.level().getEntity(command.target()) instanceof ServerPlayer target) || !nearby(sender, target, 4.0)) {
                  message(sender, "too_far");
                  return;
               }

               if (!AnimationCatalog.allowed(command.value())) {
                  message(sender, "catalog.missing");
               } else if (Actions.needsBed(command.value()) && BedSceneOrigin.nearest(sender.level(), target.position()) == null) {
                  message(sender, "need_bed");
               } else {
                  ConsentBook.Invite invite = CONSENT.offer(actor, target.getUUID(), command.value(), tick);
                  if (invite == null) {
                     message(sender, "busy");
                  } else {
                     send(
                        sender, "invite", new InteractionServer.Invitation(invite, sender.getGameProfile().getName(), target.getGameProfile().getName(), false)
                     );
                     send(
                        target, "invite", new InteractionServer.Invitation(invite, sender.getGameProfile().getName(), target.getGameProfile().getName(), true)
                     );
                  }
               }
            }
         } else if (command.op() == 1 || command.op() == 2) {
            ConsentBook.Invite invite = CONSENT.get(command.token());
            if (invite != null && invite.recipient().equals(actor)) {
               ServerPlayer initiator = sender.server.getPlayerList().getPlayer(invite.initiator());
               if (tick < invite.expires() && nearby(initiator, sender, 4.0)) {
                  ConsentBook.Invite accepted = CONSENT.answer(actor, invite.id(), command.op() == 1, tick);
                  if (accepted != null) {
                     clearInvite(sender.server, invite);
                     if (command.op() == 2) {
                        message(initiator, "declined");
                     } else {
                        start(initiator, sender, invite, tick);
                     }
                  }
               } else {
                  cancelInvite(sender.server, invite, "expired");
               }
            }
         } else if (CONSENT.participant(actor, command.token())) {
            if (command.op() == 3) {
               ConsentBook.Invite invitation = CONSENT.get(command.token());
               if (invitation != null) {
                  cancelInvite(sender.server, invitation, "cancelled");
               } else {
                  InteractionServer.Running active = RUNNING.get(command.token());
                  if (active != null
                     && active.state.loop != null
                     && active.state.loop.continuous
                     && !active.state.paused
                     && !active.state.waitingForJoin
                     && !active.state.joining()) {
                     active.state.loop.requestExit();
                     publish(sender.server, active.state);
                     message(sender, "loop.finishing");
                  } else {
                     stop(sender.server, command.token(), "stopped");
                  }
               }
            } else {
               InteractionServer.Running run = RUNNING.get(command.token());
               if (run != null && run.state.initiator().id.equals(actor)) {
                  if (command.op() == 9) {
                     if (run.state.repeat(actor, command.value())) {
                        run.state.clock = tick;
                        publish(sender.server, run.state);
                     }
                  } else if (command.op() == 7) {
                     ServerPlayer recipient = sender.server.getPlayerList().getPlayer(run.state.recipient().id);
                     if (!nearby(sender, recipient, 6.0)) {
                        message(sender, "join_near");
                     } else if (run.state.beginJoin(actor, sender.position(), sender.getYRot())) {
                        for (int i = 0; i < run.saved.size(); i++) {
                           InteractionServer.Saved saved = run.saved.get(i);
                           if (saved.player == sender) {
                              run.saved
                                 .set(
                                    i,
                                    new InteractionServer.Saved(
                                       sender, sender.level().dimension(), sender.position(), sender.getYRot(), sender.getXRot(), saved.gravity, saved.physics
                                    )
                                 );
                           }
                        }

                        sender.noPhysics = true;
                        sender.setNoGravity(true);
                        sender.setDeltaMovement(Vec3.ZERO);
                        sender.fallDistance = 0.0F;
                        run.state.clock = tick;
                        publish(sender.server, run.state);
                     }
                  } else if (command.op() == 6) {
                     if (run.state.paused && command.value() >= 0 && command.value() <= 3) {
                        run.petDown = (command.value() & 1) != 0;
                        run.state.pressure = (command.value() & 2) != 0;
                        run.state.dragX = command.x();
                        run.state.dragY = command.y();
                        run.lastPet = tick;
                     }
                  } else if (run.state.action == 4) {
                     if (command.op() == 5) {
                        if (!run.state.touching || run.state.zone == 0 || run.state.zone == 12) {
                           run.state.mode = command.value() == 1 ? 1 : 0;
                           run.state.dragX = run.state.dragY = run.state.dragZ = 0.0F;
                           publish(sender.server, run.state);
                        }
                     } else if (command.op() == 4) {
                        if (command.value() == 0) {
                           releaseTouch(sender.server, run, tick);
                        } else if (command.value() == 1 || command.value() == 2) {
                           if ((command.value() != 2 || run.state.touching) && (command.value() != 1 || !run.state.touching)) {
                              if (command.value() != 2 || tick - run.lastTouch >= 3L) {
                                 ServerPlayer target = sender.server.getPlayerList().getPlayer(run.state.recipient().id);
                                 if (!nearby(sender, target, 3.0)) {
                                    stop(sender.server, command.token(), "too_far");
                                 } else if (!sender.getMainHandItem().isEmpty()) {
                                    message(sender, "empty_hand");
                                    releaseTouch(sender.server, run, tick);
                                 } else {
                                    if (!run.state.touching) {
                                       TouchZones.Hit hit = TouchZones.pick(
                                          target,
                                          Map.of(),
                                          sender.getEyePosition(),
                                          sender.getLookAngle(),
                                          Appearance.slim(AppearanceServer.get(target).arms(), target.getGameProfile())
                                       );
                                       if (hit == null) {
                                          releaseTouch(sender.server, run, tick);
                                          return;
                                       }

                                       BlockHitResult block = sender.level()
                                          .clip(new ClipContext(sender.getEyePosition(), hit.point(), Block.COLLIDER, Fluid.NONE, sender));
                                       if (block.getType() != Type.MISS && block.getLocation().distanceTo(sender.getEyePosition()) + 0.01 < hit.distance()) {
                                          releaseTouch(sender.server, run, tick);
                                          return;
                                       }

                                       run.state.touchSerial++;
                                       run.state.touchBodyYaw = Mth.wrapDegrees(target.getYRot());
                                       run.state.touchHeadYaw = run.state.touchBodyYaw;
                                       run.state.touchPitch = Mth.clamp(target.getXRot(), -65.0F, 65.0F);
                                       run.state.zone = hit.zone();
                                       run.state.side = hit.side();
                                       run.state.aimX = hit.x();
                                       run.state.aimY = hit.y();
                                       run.state.touchStarted = tick;
                                       run.contact = new TouchContact(
                                          hit.point(),
                                          hit.distance(),
                                          tick,
                                          run.state.touchBodyYaw,
                                          TouchFacing.relative(run.state.touchHeadYaw, run.state.touchBodyYaw),
                                          run.state.touchPitch
                                       );
                                       run.state.dragX = run.state.dragY = run.state.dragZ = 0.0F;
                                       Vec3 local = hit.point().subtract(target.position()).yRot((float)Math.toRadians((double)target.yBodyRot));
                                       run.state.contactX = local.x;
                                       run.state.contactY = local.y;
                                       run.state.contactZ = local.z;
                                    } else {
                                       Vec3 force = run.contact == null
                                          ? null
                                          : run.contact.force(sender.getEyePosition(), sender.getLookAngle(), run.state.reactionZone(), tick);
                                       if (force == null || !clearTouch(sender, run.contact.point())) {
                                          releaseTouch(sender.server, run, tick);
                                          return;
                                       }

                                       run.state.dragX = (float)force.x;
                                       run.state.dragY = (float)force.y;
                                       run.state.dragZ = (float)force.z;
                                    }

                                    run.state.touching = true;
                                    run.state.clock = tick;
                                    run.lastTouch = tick;
                                    publish(sender.server, run.state);
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean clearTouch(ServerPlayer player, Vec3 point) {
      return player.level().clip(new ClipContext(player.getEyePosition(), point, Block.COLLIDER, Fluid.NONE, player)).getType() == Type.MISS;
   }

   private static void releaseTouch(MinecraftServer server, InteractionServer.Running run, long tick) {
      run.state.touching = false;
      run.state.clock = tick;
      run.lastTouch = tick;
      run.contact = null;
      publish(server, run.state);
   }

   private static void clearInvite(MinecraftServer server, ConsentBook.Invite invite) {
      send(server.getPlayerList().getPlayer(invite.initiator()), "clear_invite", invite.id());
      send(server.getPlayerList().getPlayer(invite.recipient()), "clear_invite", invite.id());
   }

   private static void cancelInvite(MinecraftServer server, ConsentBook.Invite invite, String reason) {
      CONSENT.release(invite.id());
      clearInvite(server, invite);
      message(server.getPlayerList().getPlayer(invite.initiator()), reason);
      message(server.getPlayerList().getPlayer(invite.recipient()), reason);
   }

   private static void start(ServerPlayer initiator, ServerPlayer recipient, ConsentBook.Invite invite, long tick) {
      SessionState state = new SessionState();
      state.id = invite.id();
      state.action = invite.action();
      state.clock = tick;
      state.initializeScene();
      BedSceneOrigin bed = Actions.needsBed(state.action) ? BedSceneOrigin.nearest(recipient.level(), recipient.position()) : null;
      if (!Actions.needsBed(state.action) || bed != null && !(new Vec3(bed.x(), bed.y(), bed.z()).distanceTo(recipient.position()) > 6.0)) {
         for (int slot = 0; slot < 2; slot++) {
            ServerPlayer player = slot == 0 ? initiator : recipient;
            Vec3 origin = player.position();
            float yaw = player.getYRot();
            if (Actions.scene(state.action)) {
               origin = bed != null ? new Vec3(bed.x(), bed.y(), bed.z()) : recipient.position();
               yaw = bed != null ? bed.yaw() : recipient.getYRot();
               if (slot == 0) {
                  Clip clip = Clip.get(state.action, true);
                  double a = Math.toRadians((double)yaw);
                  origin = origin.add(
                     (double)clip.sceneOffsetX * Math.cos(a) - (double)clip.sceneOffsetZ * Math.sin(a),
                     (double)clip.sceneOffsetY,
                     (double)clip.sceneOffsetZ * Math.cos(a) + (double)clip.sceneOffsetX * Math.sin(a)
                  );
                  yaw += clip.sceneOffsetYaw;
               }
            }

            state.actors
               .add(new SessionState.Actor(player.getUUID(), player.getGameProfile().getName(), slot, origin, yaw, player.position(), player.getYRot()));
         }

         if (Actions.scene(state.action) && !routeFits(initiator, state)) {
            CONSENT.release(invite.id());
            message(initiator, "no_space");
            message(recipient, "no_space");
         } else {
            InteractionServer.Running run = new InteractionServer.Running();
            run.state = state;
            run.born = tick;
            run.lastTouch = tick;
            if (Actions.together(state.action)) {
               run.together = TogetherServer.start(initiator, recipient, state);
               if (run.together == null) {
                  CONSENT.release(state.id);
                  message(initiator, "no_space");
                  message(recipient, "no_space");
                  return;
               }
            }

            for (ServerPlayer player : List.of(initiator, recipient)) {
               run.saved
                  .add(
                     new InteractionServer.Saved(
                        player, player.level().dimension(), player.position(), player.getYRot(), player.getXRot(), player.isNoGravity(), player.noPhysics
                     )
                  );
               if (state.sceneActive(player.getUUID()) || state.action == 6 && player == recipient) {
                  player.setNoGravity(true);
                  player.noPhysics = true;
                  player.setDeltaMovement(Vec3.ZERO);
                  player.fallDistance = 0.0F;
               }
            }

            RUNNING.put(state.id, run);
            publish(initiator.server, state);
         }
      } else {
         CONSENT.release(invite.id());
         message(initiator, "need_bed");
         message(recipient, "need_bed");
      }
   }

   private static boolean routeFits(ServerPlayer player, SessionState state) {
      for (SessionState.Actor actor : state.actors) {
         for (int tick = 12; tick <= 12 + state.duration(); tick += 5) {
            Vec3 at = actor.target(state.action, (double)tick);
            BlockPos pos = BlockPos.containing(at);
            if (!player.level().hasChunkAt(pos) || !player.level().getWorldBorder().isWithinBounds(pos) || Math.abs(at.y - actor.fromY) > 4.0) {
               return false;
            }

            AABB box = new AABB(at.x - 0.25, at.y + 0.6, at.z - 0.25, at.x + 0.25, at.y + 1.1, at.z + 0.25);
            if (!player.level().noCollision(player, box)) {
               return false;
            }
         }
      }

      return true;
   }

   @SubscribeEvent
   public static void tick(TickEvent.ServerTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }
      MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
      if (server == null) {
         return;
      }
      long tick = server.overworld().getGameTime();

      for (ConsentBook.Invite invite : CONSENT.invitations()) {
         ServerPlayer a = server.getPlayerList().getPlayer(invite.initiator());
         ServerPlayer b = server.getPlayerList().getPlayer(invite.recipient());
         if (tick >= invite.expires() || !nearby(a, b, 5.0)) {
            cancelInvite(server, invite, "expired");
         }
      }

      for (InteractionServer.Running run : new ArrayList<>(RUNNING.values())) {
         SessionState state = run.state;
         ServerPlayer a = server.getPlayerList().getPlayer(state.initiator().id);
         ServerPlayer b = server.getPlayerList().getPlayer(state.recipient().id);
         if (!available(a) || !available(b) || a.level() != b.level() || tick - run.born > state.maximumTicks()) {
            stop(server, state.id, "stopped");
         } else if (Actions.together(state.action)) {
            state.elapsed++;
            state.clock = tick;
            String failure = TogetherServer.tick(run.together, state);
            if (failure != null) {
               stop(server, state.id, failure);
            } else if (tick - run.lastUpdate >= 2L) {
               run.lastUpdate = tick;
               publish(server, state);
            }
         } else if (state.action == 4) {
            if (!nearby(a, b, 3.5)) {
               stop(server, state.id, "too_far");
            } else {
               if (state.touching
                  && (
                     tick - run.lastTouch > 12L || run.contact == null || !run.contact.inRange(a.getEyePosition(), tick) || !clearTouch(a, run.contact.point())
                  )) {
                  releaseTouch(server, run, tick);
               }

               if (state.receivingTouch(b.getUUID(), tick)) {
                  b.setYRot(state.touchHeadYaw);
                  b.setYHeadRot(state.touchHeadYaw);
                  b.yBodyRot = state.touchBodyYaw;
                  b.setXRot(state.touchPitch);
               }
            }
         } else {
            int loopPhase = state.loop == null ? -1 : state.loop.phase;
            int loopPass = state.loop == null ? 0 : state.loop.pass;
            int pause = Clip.get(state.action, false).pauseTick();
            if (!state.paused && pause >= 0 && !state.pauseCompleted && state.elapsed >= (double)(12 + pause)) {
               state.elapsed = (double)(12 + pause);
               state.paused = true;
               state.progress = 0;
            }

            if (state.paused) {
               if (tick - run.lastPet > 6L) {
                  state.pressure = false;
               }

               if (run.petDown && tick - run.lastPet <= 6L) {
                  state.progress++;
               } else {
                  state.progress = 0;
               }

               if (state.progress >= 100) {
                  state.paused = false;
                  state.pauseCompleted = true;
                  run.petDown = false;
               }
            } else {
               state.advanceScene(tick);
            }

            state.clock = tick;
            if (state.elapsed > (double)(12 + state.duration() + 12)) {
               stop(server, state.id, "finished");
            } else {
               boolean invalid = false;

               for (SessionState.Actor actor : state.actors) {
                  if (state.sceneActive(actor.id)) {
                     ServerPlayer player = server.getPlayerList().getPlayer(actor.id);
                     Vec3 target = state.target(actor, state.elapsed, state.joinTime(tick, 0.0F));
                     if (player.position().distanceTo(target) > 8.0) {
                        invalid = true;
                        break;
                     }

                     player.setPos(target.x, target.y, target.z);
                     float yaw = state.heading(actor, state.elapsed, state.joinTime(tick, 0.0F));
                     player.setYRot(yaw);
                     player.setYHeadRot(yaw);
                     player.yBodyRot = yaw;
                     player.setDeltaMovement(Vec3.ZERO);
                     player.fallDistance = 0.0F;
                  }
               }

               if (invalid) {
                  stop(server, state.id, "stopped");
               } else if (tick - run.lastUpdate >= 4L || state.loop != null && (state.loop.phase != loopPhase || state.loop.pass != loopPass)) {
                  run.lastUpdate = tick;
                  publish(server, state);
               }
            }
         }
      }
   }

   private static void stop(MinecraftServer server, UUID id, String reason) {
      InteractionServer.Running run = RUNNING.remove(id);
      CONSENT.release(id);
      if (run != null) {
         for (InteractionServer.Saved saved : run.saved) {
            ServerPlayer player = saved.player;
            player.setNoGravity(saved.gravity);
            player.noPhysics = saved.physics;
            player.fallDistance = 0.0F;
            if (run.state.sceneActive(player.getUUID())
               && player.isAlive()
               && server.getPlayerList().getPlayer(player.getUUID()) == player
               && player.level().dimension().equals(saved.dimension)
               && player.level().noCollision(player, player.getBoundingBox().move(saved.position.subtract(player.position())))) {
               player.connection.teleport(saved.position.x, saved.position.y, saved.position.z, saved.yaw, saved.pitch);
            }

            message(player, reason);
         }

         for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            send(viewer, "stop", id);
         }

         TogetherServer.stop(run.together);
      }
   }

   private static void remove(ServerPlayer player) {
      UUID id = CONSENT.reserved(player.getUUID());
      if (id != null) {
         ConsentBook.Invite invite = CONSENT.get(id);
         if (invite != null) {
            cancelInvite(player.server, invite, "cancelled");
         } else {
            stop(player.server, id, "stopped");
         }
      }

      CONSENT.removePlayer(player.getUUID());
      LAST_COMMAND.remove(player.getUUID());
   }

   @SubscribeEvent
   public static void logout(PlayerLoggedOutEvent event) {
      if (event.getEntity() instanceof ServerPlayer p) {
         remove(p);
      }
   }

   @SubscribeEvent
   public static void dimension(PlayerChangedDimensionEvent event) {
      if (event.getEntity() instanceof ServerPlayer p) {
         remove(p);
      }
   }

   @SubscribeEvent
   public static void damage(LivingHurtEvent event) {
      if (event.getEntity() instanceof ServerPlayer p) {
         remove(p);
      }
   }

   @SubscribeEvent
   public static void tracking(StartTracking event) {
      if (event.getEntity() instanceof ServerPlayer p && event.getTarget() instanceof ServerPlayer target) {
         UUID id = CONSENT.reserved(target.getUUID());
         InteractionServer.Running run = id == null ? null : RUNNING.get(id);
         if (run != null) {
            send(p, "session", run.state);
         }
      }
   }

   @SubscribeEvent
   public static void shutdown(ServerStoppedEvent event) {
      for (InteractionServer.Running run : RUNNING.values()) {
         for (InteractionServer.Saved s : run.saved) {
            s.player.setNoGravity(s.gravity);
            s.player.noPhysics = s.physics;
         }

         TogetherServer.stop(run.together);
      }

      RUNNING.clear();
      CONSENT.clear();
      LAST_COMMAND.clear();
   }

   private InteractionServer() {
   }

   public static record Invitation(ConsentBook.Invite invite, String fromName, String toName, boolean incoming) {
   }

   private static final class Running {
      SessionState state;
      List<InteractionServer.Saved> saved = new ArrayList<>();
      long born;
      long lastTouch;
      long lastPet;
      long lastUpdate;
      boolean petDown;
      TogetherServer.Run together;
      TouchContact contact;
   }

   private static record Saved(ServerPlayer player, ResourceKey<Level> dimension, Vec3 position, float yaw, float pitch, boolean gravity, boolean physics) {
   }
}
