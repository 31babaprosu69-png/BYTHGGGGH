package pro.webhm.playerinteractions.anim;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

public record BedSceneOrigin(double x, double y, double z, float yaw) {
   public static BedSceneOrigin nearest(Level level, Vec3 centre) {
      BlockPos base = BlockPos.containing(centre);
      BlockPos best = null;
      double distance = 64.0001;

      for (BlockPos pos : BlockPos.betweenClosed(base.offset(-8, -4, -8), base.offset(8, 4, 8))) {
         BlockState state = level.getBlockState(pos);
         if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.FOOT) {
            double d = centre.distanceToSqr(Vec3.atCenterOf(pos));
            if (d < distance) {
               distance = d;
               best = pos.immutable();
            }
         }
      }

      return best == null ? null : at(level, best);
   }

   public static BedSceneOrigin at(Level level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      if (!(state.getBlock() instanceof BedBlock)) {
         return null;
      } else {
         if (state.getValue(BedBlock.PART) == BedPart.HEAD) {
            pos = pos.relative(((Direction)state.getValue(BedBlock.FACING)).getOpposite());
         }

         state = level.getBlockState(pos);
         return state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.FOOT
            ? new BedSceneOrigin(
               (double)pos.getX() + 0.5,
               (double)pos.getY() + state.getShape(level, pos).max(Axis.Y),
               (double)pos.getZ() + 0.5,
               ((Direction)state.getValue(BedBlock.FACING)).toYRot()
            )
            : null;
      }
   }
}
