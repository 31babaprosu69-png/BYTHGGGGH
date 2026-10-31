package pro.webhm.playerinteractions.npc;

public enum RootPivot {
   CENTER("center", "центр", 0.0F, 0.0F, 0.0F, false),
   PELVIS("pelvis", "таз", 0.0F, 12.0F, 0.0F, false),
   RIGHT_HAND("right_hand", "правая кисть", 0.0F, 0.0F, 0.0F, true),
   LEFT_HAND("left_hand", "левая кисть", 0.0F, 0.0F, 0.0F, true),
   RIGHT_SHOULDER("right_shoulder", "правое плечо", -5.0F, -4.0F, 0.0F, false),
   LEFT_SHOULDER("left_shoulder", "левое плечо", 5.0F, -4.0F, 0.0F, false),
   HEAD("head", "голова", 0.0F, -12.0F, 0.0F, false),
   FEET("feet", "ступни", 0.0F, 12.0F, 0.0F, false);

   public final String id;
   public final String label;
   public final float x;
   public final float y;
   public final float z;
   public final boolean dynamic;

   private RootPivot(String id, String label, float x, float y, float z, boolean dynamic) {
      this.id = id;
      this.label = label;
      this.x = x;
      this.y = y;
      this.z = z;
      this.dynamic = dynamic;
   }

   public static RootPivot byId(String id) {
      for (RootPivot p : values()) {
         if (p.id.equals(id)) {
            return p;
         }
      }

      return CENTER;
   }

   public RootPivot next() {
      RootPivot[] all = values();
      return all[(this.ordinal() + 1) % all.length];
   }
}
