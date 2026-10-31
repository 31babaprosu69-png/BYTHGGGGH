package pro.webhm.playerinteractions.client;

import java.util.UUID;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import pro.webhm.playerinteractions.SessionState;

public final class SceneCameraControl {
   private UUID session;
   private float yaw;
   private float pitch;

   private static boolean active(SessionState state, UUID player) {
      return state != null && player != null && state.sceneActive(player);
   }

   private void bind(SessionState state, float baseYaw, float basePitch) {
      if (!state.id.equals(this.session)) {
         this.session = state.id;
         this.yaw = Mth.wrapDegrees(baseYaw);
         this.pitch = Mth.clamp(basePitch, -90.0F, 90.0F);
      }
   }

   public boolean mouse(SessionState state, UUID player, boolean firstPerson, float baseYaw, float basePitch, double dx, double dy) {
      if (!active(state, player)) {
         return false;
      } else {
         if (!firstPerson) {
            this.bind(state, baseYaw, basePitch);
            if (Double.isFinite(dx) && Double.isFinite(dy)) {
               this.yaw = Mth.wrapDegrees(this.yaw + (float)dx * 0.13F);
               this.pitch = Mth.clamp(this.pitch + (float)dy * 0.13F, -90.0F, 90.0F);
            }
         }

         return true;
      }
   }

   public Vector3f angles(SessionState state, UUID player, boolean firstPerson, Vector3f authored, float baseYaw, float basePitch) {
      if (!active(state, player)) {
         return null;
      } else if (firstPerson) {
         return new Vector3f(authored);
      } else {
         this.bind(state, baseYaw, basePitch);
         return new Vector3f(this.yaw, this.pitch, 0.0F);
      }
   }

   public void clear() {
      this.session = null;
      this.yaw = this.pitch = 0.0F;
   }
}
