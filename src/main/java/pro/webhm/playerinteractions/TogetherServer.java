package pro.webhm.playerinteractions;

import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import pro.webhm.playerinteractions.anim.Clip;
import pro.webhm.playerinteractions.body.CarryPose;

final class TogetherServer {
   static TogetherServer.Run start(ServerPlayer a, ServerPlayer b, SessionState s) {
      if (a.onGround() && b.onGround() && !a.getAbilities().flying && !b.getAbilities().flying && !a.isInWaterOrBubble() && !b.isInWaterOrBubble()) {
         TogetherServer.Run r = new TogetherServer.Run(a, b, s.action == 6);
         Vec3 goal = goal(a.position(), r.heading, r.carry);
         if (r.carry && !carrySpace(b, goal)) {
            return null;
         } else {
            s.pairYaw = r.heading;
            s.side = 1;
            return r;
         }
      } else {
         return null;
      }
   }

   static Vec3 goal(Vec3 leader, float heading, boolean carry) {
      if (carry) {
         return leader.add(CarryPose.offset(heading, 0.9375F, 0.0F));
      } else {
         double a = Math.toRadians((double)heading);
         return leader.add(-Math.cos(a) * 0.76, 0.0, -Math.sin(a) * 0.76);
      }
   }

   static String tick(TogetherServer.Run r, SessionState s) {
      ServerPlayer a = r.leader;
      ServerPlayer b = r.follower;
      if (!a.getAbilities().flying && !b.getAbilities().flying && !a.isInWaterOrBubble() && !b.isInWaterOrBubble()) {
         Vec3 travel = a.position().subtract(r.previous);
         if (!(travel.lengthSqr() > 9.0) && !(a.distanceToSqr(b) > 25.0)) {
            r.previous = a.position();
            a.setSprinting(false);
            b.setSprinting(false);
            r.heading = r.heading + Mth.clamp(Mth.wrapDegrees(a.getYRot() - r.heading), -12.0F, 12.0F);
            Vec3 goal = goal(a.position(), r.heading, r.carry);
            Vec3 before = b.position();
            if (r.carry) {
               Vec3 put = ground(a.position(), b);
               if (put != null) {
                  r.safe = put;
               }

               if (!carrySpace(b, goal)) {
                  return "no_space";
               }

               float entry = Clip.smooth(s.elapsed / 12.0);
               Vec3 at = s.recipient().from().lerp(goal, (double)entry);
               AABB sweep = CarryPlacement.swept(before, at);
               if (!loaded(b, sweep) || b.level().getBlockCollisions(b, sweep).iterator().hasNext()) {
                  return "no_space";
               }

               b.setPos(at.x, at.y, at.z);
               b.fallDistance = 0.0F;
            } else {
               Vec3 delta = goal.subtract(before);
               double max = 0.28;
               if (delta.length() > max) {
                  delta = delta.normalize().scale(max);
               }

               b.move(MoverType.SELF, delta);
               double gap = b.position().distanceToSqr(goal);
               r.blocked = gap > 0.3 ? r.blocked + 1 : 0;
               if (r.blocked > 20) {
                  return "no_space";
               }
            }

            b.setDeltaMovement(Vec3.ZERO);
            float yaw = r.heading + (float)(r.carry ? 90 : 0);
            if (r.carry) {
               b.setYRot(yaw);
            }

            b.setYHeadRot(b.getYRot());
            b.yBodyRot = yaw;
            s.pairYaw = r.heading;
            s.pairDistance = s.pairDistance + b.position().subtract(before).horizontalDistance();
            s.pairSpeed = Mth.lerp(0.25F, s.pairSpeed, (float)Math.min(1.0, travel.horizontalDistance() * 6.0));
            SessionState.Actor actor = s.recipient();
            actor.x = b.getX();
            actor.y = b.getY();
            actor.z = b.getZ();
            actor.yaw = yaw;
            return null;
         } else {
            return "too_far";
         }
      } else {
         return "stopped";
      }
   }

   static void stop(TogetherServer.Run r) {
      if (r != null) {
         ServerPlayer b = r.follower;
         if (b.isAlive() && b.level().dimension().equals(r.dimension)) {
            Vec3 at = b.position();
            if (r.carry) {
               Vec3 put = r.leader.level() == b.level() ? ground(r.leader.position(), b) : null;
               if (put == null) {
                  put = ground(r.safe, b);
               }

               if (put == null) {
                  put = CarryPlacement.free(b.position(), b.getDimensions(Pose.STANDING), box -> clear(b, box));
               }

               if (put != null) {
                  at = put;
               }
            }

            b.setDeltaMovement(Vec3.ZERO);
            b.fallDistance = 0.0F;
            if (b.hasDisconnected()) {
               b.setPos(at.x, at.y, at.z);
            } else {
               b.connection.teleport(at.x, at.y, at.z, b.getYRot(), b.getXRot());
            }
         }
      }
   }

   private static boolean carrySpace(ServerPlayer b, Vec3 at) {
      AABB box = CarryPlacement.body(at);
      return loaded(b, box) && !b.level().getBlockCollisions(b, box).iterator().hasNext();
   }

   private static boolean loaded(ServerPlayer b, AABB box) {
      Level level = b.level();
      return box.minY >= (double)level.getMinBuildHeight()
         && box.maxY <= (double)level.getMaxBuildHeight()
         && level.getWorldBorder().isWithinBounds(box)
         && level.hasChunkAt(BlockPos.containing(box.minX, box.minY, box.minZ))
         && level.hasChunkAt(BlockPos.containing(box.minX, box.minY, box.maxZ))
         && level.hasChunkAt(BlockPos.containing(box.maxX, box.minY, box.minZ))
         && level.hasChunkAt(BlockPos.containing(box.maxX, box.minY, box.maxZ));
   }

   private static boolean clear(ServerPlayer b, AABB box) {
      return loaded(b, box)
         && b.level().noCollision(b, box)
         && !b.level().containsAnyLiquid(box)
         && b.level().players().stream().noneMatch(p -> p != b && p.isAlive() && !p.isSpectator() && p.getBoundingBox().intersects(box));
   }

   private static Vec3 ground(Vec3 near, ServerPlayer b) {
      return CarryPlacement.ground(
         near,
         b.getDimensions(Pose.STANDING),
         box -> {
            ArrayList<AABB> shapes = new ArrayList<>();
            if (!(box.maxY <= (double)b.level().getMinBuildHeight()) && !(box.minY >= (double)b.level().getMaxBuildHeight())) {
               AABB query = new AABB(
                  box.minX,
                  Math.max(box.minY, (double)b.level().getMinBuildHeight()),
                  box.minZ,
                  box.maxX,
                  Math.min(box.maxY, (double)b.level().getMaxBuildHeight()),
                  box.maxZ
               );
               if (loaded(b, query)) {
                  for (VoxelShape shape : b.level().getBlockCollisions(b, query)) {
                     shapes.addAll(shape.toAabbs());
                  }
               }

               return shapes;
            } else {
               return shapes;
            }
         },
         box -> clear(b, box)
      );
   }

   private TogetherServer() {
   }

   static final class Run {
      final ServerPlayer leader;
      final ServerPlayer follower;
      final boolean carry;
      final ResourceKey<Level> dimension;
      Vec3 previous;
      Vec3 safe;
      float heading;
      int blocked;

      Run(ServerPlayer a, ServerPlayer b, boolean carry) {
         this.leader = a;
         this.follower = b;
         this.carry = carry;
         this.dimension = b.level().dimension();
         this.previous = a.position();
         this.safe = b.position();
         this.heading = a.getYRot();
      }
   }
}
