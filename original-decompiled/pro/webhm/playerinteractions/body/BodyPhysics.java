package pro.webhm.playerinteractions.body;

import java.util.Map;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import pro.webhm.playerinteractions.npc.BodyData;
import pro.webhm.playerinteractions.npc.BoneTransform;
import pro.webhm.playerinteractions.npc.BreastData;
import pro.webhm.playerinteractions.npc.NpcBone;

public final class BodyPhysics {
   public float breastBounce;
   public float breastBounceO;
   public float breastSway;
   public float breastSwayO;
   private float breastBounceVel;
   private float breastSwayVel;
   public float breastBounceR;
   public float breastBounceRO;
   public float breastSwayR;
   public float breastSwayRO;
   private float breastBounceRVel;
   private float breastSwayRVel;
   private float prevAnimRoot;
   private float prevAnimLean;
   private float prevAnimArms;
   private float prevAnimSpread;
   private double prevPx;
   private double prevPy;
   private double prevPz;
   private float prevBodyRot;
   private boolean physicsInit = false;

   public void tick(LivingEntity entity, BodyData d, Map<String, BoneTransform> sampled) {
      this.breastBounceO = this.breastBounce;
      this.breastSwayO = this.breastSway;
      this.breastBounceRO = this.breastBounceR;
      this.breastSwayRO = this.breastSwayR;
      BreastData b = d.breast;
      if (!this.physicsInit) {
         this.prevPx = entity.getX();
         this.prevPy = entity.getY();
         this.prevPz = entity.getZ();
         this.prevBodyRot = entity.yBodyRot;
         this.physicsInit = true;
      } else {
         double dx = entity.getX() - this.prevPx;
         double dy = entity.getY() - this.prevPy;
         double dz = entity.getZ() - this.prevPz;
         this.prevPx = entity.getX();
         this.prevPy = entity.getY();
         this.prevPz = entity.getZ();
         float yawDelta = Mth.wrapDegrees(entity.yBodyRot - this.prevBodyRot);
         this.prevBodyRot = entity.yBodyRot;
         if (b != null && b.enabled && b.physics) {
            float horizontal = (float)Math.sqrt(dx * dx + dz * dz);
            float step = horizontal * 26.0F * (float)Math.sin((double)entity.tickCount * 0.62);
            float animBounce = 0.0F;
            float animSway = 0.0F;
            if (sampled != null && !sampled.isEmpty()) {
               BoneTransform rootT = sampled.get(NpcBone.ROOT.id);
               BoneTransform chestT = sampled.get(NpcBone.CHEST.id);
               BoneTransform bodyT = sampled.get(NpcBone.BODY.id);
               BoneTransform rArmT = sampled.get(NpcBone.RIGHT_ARM.id);
               BoneTransform lArmT = sampled.get(NpcBone.LEFT_ARM.id);
               float rootH = rootT == null ? 0.0F : rootT.height;
               float lean = (chestT == null ? 0.0F : chestT.rx) + (bodyT == null ? 0.0F : bodyT.rx);
               float twist = (chestT == null ? 0.0F : chestT.rz) + (bodyT == null ? 0.0F : bodyT.rz);
               float arms = ((rArmT == null ? 0.0F : rArmT.rx) + (lArmT == null ? 0.0F : lArmT.rx)) * 0.5F;
               float spread = ((rArmT == null ? 0.0F : rArmT.rz) - (lArmT == null ? 0.0F : lArmT.rz)) * 0.5F;
               animBounce = -(rootH - this.prevAnimRoot) * 9.0F + (lean - this.prevAnimLean) * 1.4F + (arms - this.prevAnimArms) * 0.55F;
               animSway = twist * 0.35F + (spread - this.prevAnimSpread) * 0.3F;
               this.prevAnimRoot = rootH;
               this.prevAnimLean = lean;
               this.prevAnimArms = arms;
               this.prevAnimSpread = spread;
            } else {
               this.prevAnimRoot = 0.0F;
               this.prevAnimLean = 0.0F;
               this.prevAnimArms = 0.0F;
               this.prevAnimSpread = 0.0F;
            }

            float baseBounce = (float)(-dy * 60.0) + step + animBounce;
            float baseSway = -yawDelta * 0.55F + (float)(dx + dz) * 6.0F + animSway;
            float phase = b.dualPhysics
               ? horizontal * 26.0F * (float)(Math.sin((double)entity.tickCount * 0.62 + 0.9) - Math.sin((double)entity.tickCount * 0.62))
               : 0.0F;
            float split = b.dualPhysics ? yawDelta * 0.25F : 0.0F;
            float targetBounceL = Mth.clamp(baseBounce, -38.0F, 38.0F);
            float targetBounceR = Mth.clamp(baseBounce + phase, -38.0F, 38.0F);
            float targetSwayL = Mth.clamp(baseSway + split, -32.0F, 32.0F);
            float targetSwayR = Mth.clamp(baseSway - split, -32.0F, 32.0F);
            float stiffness = 0.14F * Mth.clamp(b.bounce, 0.05F, 3.0F);
            float damping = 0.3F;
            this.breastBounceVel = this.breastBounceVel + ((targetBounceL - this.breastBounce) * stiffness - this.breastBounceVel * damping);
            this.breastSwayVel = this.breastSwayVel + ((targetSwayL - this.breastSway) * stiffness - this.breastSwayVel * damping);
            this.breastBounce = Mth.clamp(this.breastBounce + this.breastBounceVel, -45.0F, 45.0F);
            this.breastSway = Mth.clamp(this.breastSway + this.breastSwayVel, -40.0F, 40.0F);
            if (b.dualPhysics) {
               this.breastBounceRVel = this.breastBounceRVel + ((targetBounceR - this.breastBounceR) * stiffness - this.breastBounceRVel * damping);
               this.breastSwayRVel = this.breastSwayRVel + ((targetSwayR - this.breastSwayR) * stiffness - this.breastSwayRVel * damping);
               this.breastBounceR = Mth.clamp(this.breastBounceR + this.breastBounceRVel, -45.0F, 45.0F);
               this.breastSwayR = Mth.clamp(this.breastSwayR + this.breastSwayRVel, -40.0F, 40.0F);
            } else {
               this.breastBounceR = this.breastBounce;
               this.breastSwayR = this.breastSway;
               this.breastBounceRVel = this.breastBounceVel;
               this.breastSwayRVel = this.breastSwayVel;
            }
         } else {
            this.breastBounce *= 0.75F;
            this.breastSway *= 0.75F;
            this.breastBounceR *= 0.75F;
            this.breastSwayR *= 0.75F;
            this.breastBounceVel = this.breastSwayVel = 0.0F;
            this.breastBounceRVel = this.breastSwayRVel = 0.0F;
         }
      }
   }
}
