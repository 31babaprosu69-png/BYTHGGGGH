package pro.webhm.playerinteractions.body;

public final class HeadFrame {
   public int tickCount;
   public int id;
   public double horizontalSpeed;
   public float hair;
   public float blink;
   public float gazeX;
   public float gazeY;
   public float winkLeft;
   public float winkRight;
   public float hairPitch;
   public float hairRoll;
   public float cheek;
   public float lidSlope;
   public float lidCurve;
   public float lowerLid;
   public float pupilScale = 1.0F;
   public HeadFrame.Face face;

   public static final class Face {
      public float left;
      public float right;
      public float x;
      public float y;
   }
}
