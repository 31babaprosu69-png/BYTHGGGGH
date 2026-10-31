package pro.webhm.playerinteractions;

import java.util.UUID;
import java.util.function.Function;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class PairCollision {
   public static Function<UUID, SessionState> clientSession = id -> null;

   public static boolean paired(SessionState state, UUID a, UUID b) {
      return state != null && state.action != 4 && !a.equals(b) && state.actor(a) != null && state.actor(b) != null;
   }

   public static boolean suppress(Entity a, Entity b) {
      if (a instanceof Player && b instanceof Player && a.level() == b.level()) {
         SessionState state = a.level().isClientSide ? clientSession.apply(a.getUUID()) : InteractionServer.session(a.getUUID());
         return paired(state, a.getUUID(), b.getUUID());
      } else {
         return false;
      }
   }

   private PairCollision() {
   }
}
