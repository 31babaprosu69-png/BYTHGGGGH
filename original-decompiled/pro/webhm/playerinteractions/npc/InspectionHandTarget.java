package pro.webhm.playerinteractions.npc;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import pro.webhm.playerinteractions.facial.FacialData;
import pro.webhm.playerinteractions.facial.FacialExpression;
import pro.webhm.playerinteractions.facial.FacialMesh;
import pro.webhm.playerinteractions.facial.FacialPose;

public final class InspectionHandTarget {
   public static float thickness(boolean right, float weight) {
      return 1.0F - (right ? 0.65F : 0.3F) * FacialData.clamp(weight, 0.0F, 1.0F);
   }

   public static float length(boolean right, float weight) {
      return 1.0F + (right ? 0.35F : 0.0F) * FacialData.clamp(weight, 0.0F, 1.0F);
   }

   public static Vec3 bite(Matrix4f head, FacialData face, FacialPose pose, float contact, double thickness) {
      Vec3 support = sample(head, face, pose, 1, 0.0F, thickness);
      float top = FacialMesh.upper(face.x, face, pose);
      float bottom = FacialMesh.lower(face.x, face, pose);
      float scale = head.transformDirection(new Vector3f(0.0F, 0.0F, 1.0F)).length();
      Vector3f target = new Vector3f(face.x, (top + bottom) * 0.5F, -4.12F - (float)(thickness * 2.6 / (double)Math.max(0.05F, scale))).div(16.0F);
      head.transformPosition(target);
      return support.lerp(new Vec3((double)target.x, (double)target.y, (double)target.z), (double)FacialData.clamp(contact, 0.0F, 1.0F));
   }

   public static Vec3 sample(Matrix4f head, FacialData face, FacialPose pose, int side, float contact, double thickness) {
      float w = FacialExpression.ease((double)FacialData.clamp(contact, 0.0F, 1.0F));
      float lip = FacialMesh.lower(face.x, face, pose);
      float radius = face.width * pose.width() * 0.5F;
      Vector3f support = side > 0
         ? new Vector3f(face.x + radius * 0.35F, lip + 0.25F, -4.3F).div(16.0F)
         : new Vector3f((float)side * 4.0F, lip + 0.25F, -2.8F).div(16.0F);
      Vector3f mouth = FacialMesh.contactPoint(face, pose, side).div(16.0F);
      float frontClearance = (float)(0.1171875 * Math.max(0.25, Math.min(1.0, thickness)) * 1.2 + 0.006);
      mouth.z -= frontClearance;
      head.transformPosition(support);
      head.transformPosition(mouth);
      Vector3f lateral = head.transformDirection(new Vector3f((float)side, 0.0F, 0.0F)).normalize();
      float clearance = (float)(0.1171875 * Math.max(0.25, Math.min(1.0, thickness)) + 0.012);
      support.add(lateral.mul(side > 0 ? clearance : 0.035F));
      support.lerp(mouth, w);
      return new Vec3((double)support.x, (double)support.y, (double)support.z);
   }

   private InspectionHandTarget() {
   }
}
