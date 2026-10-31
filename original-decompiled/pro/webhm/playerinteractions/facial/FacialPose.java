package pro.webhm.playerinteractions.facial;

public record FacialPose(
   float open, float width, float smile, float brow, float squint, float jaw, FacialActing acting, float cornerLift, float cornerAsymmetry, LipContact contact
) {
   public FacialPose(float open, float width, float smile, float brow, float squint, float jaw, FacialActing acting, float lift, float asymmetry) {
      this(open, width, smile, brow, squint, jaw, acting, lift, asymmetry, LipContact.NONE);
   }

   public FacialPose(float open, float width, float smile, float brow, float squint, float jaw, FacialActing acting) {
      this(open, width, smile, brow, squint, jaw, acting, 0.0F, 0.0F);
   }

   public FacialPose(float open, float width, float smile, float brow, float squint, float jaw) {
      this(open, width, smile, brow, squint, jaw, FacialActing.NONE);
   }

   public FacialPose(float open, float width, float smile, float brow, float squint) {
      this(open, width, smile, brow, squint, open);
   }

   public static FacialPose rest(FacialData d) {
      return new FacialPose(d.open, 1.0F, d.smile, d.brow, d.squint);
   }

   public FacialPose expression(float smile, float brow, float squint) {
      return new FacialPose(this.open, this.width, smile, brow, squint, this.jaw, this.acting, this.cornerLift, this.cornerAsymmetry, this.contact);
   }

   public FacialPose acting(FacialActing value) {
      return new FacialPose(this.open, this.width, this.smile, this.brow, this.squint, this.jaw, value, this.cornerLift, this.cornerAsymmetry, this.contact);
   }

   public FacialPose corners(float lift, float asymmetry) {
      return new FacialPose(this.open, this.width, this.smile, this.brow, this.squint, this.jaw, this.acting, lift, asymmetry, this.contact);
   }

   public FacialPose contact(LipContact value) {
      return new FacialPose(this.open, this.width, this.smile, this.brow, this.squint, this.jaw, this.acting, this.cornerLift, this.cornerAsymmetry, value);
   }

   public FacialPose mouthFrom(FacialPose source) {
      FacialActing a = new FacialActing(
         this.acting.gazeX(),
         this.acting.gazeY(),
         this.acting.gazeWeight(),
         this.acting.blinkLeft(),
         this.acting.blinkRight(),
         this.acting.headYaw(),
         this.acting.headPitch(),
         this.acting.headRoll(),
         this.acting.blush(),
         source.acting().asymmetry(),
         this.acting.eyeWide()
      );
      return new FacialPose(
         source.open, source.width, source.smile, this.brow, this.squint, source.jaw, a, source.cornerLift, source.cornerAsymmetry, source.contact
      );
   }
}
