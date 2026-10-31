package pro.webhm.playerinteractions.facial;

public final class FacialContact {
   public static FacialPose sample(FacialPose base, double age, float contact, float dragX, float dragY, float finger, float strength, int side) {
      return sample(base, age, contact, dragX, dragY, finger, strength, side, 100, 0, -1.0);
   }

   public static FacialPose sample(
      FacialPose base, double age, float contact, float dragX, float dragY, float finger, float strength, int side, int bond, int identity, double warningAge
   ) {
      return FacePerformance.contact(base, age, contact, dragX, dragY, finger, strength, side, bond, identity, warningAge).pose();
   }

   private FacialContact() {
   }
}
