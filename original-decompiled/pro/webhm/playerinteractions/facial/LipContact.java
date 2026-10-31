package pro.webhm.playerinteractions.facial;

public record LipContact(float weight, float x, float vertical, float compression) {
   public static final LipContact NONE = new LipContact(0.0F, 0.0F, 0.0F, 0.0F);

   public LipContact(float weight, float x, float vertical, float compression) {
      weight = FacialData.clamp(weight, 0.0F, 1.0F);
      x = FacialData.clamp(x, -0.85F, 0.85F);
      vertical = FacialData.clamp(vertical, -0.5F, 0.5F);
      compression = FacialData.clamp(compression, 0.0F, 1.0F);
      this.weight = weight;
      this.x = x;
      this.vertical = vertical;
      this.compression = compression;
   }

   public float influence(float u) {
      float t = Math.min(1.0F, Math.abs(u - this.x) / 0.95F);
      float bell = 1.0F - t * t;
      return this.weight * bell * bell * FacialData.clamp(1.0F - u * u, 0.0F, 1.0F);
   }

   public LipContact mix(LipContact b, float t) {
      return new LipContact(
         this.weight + (b.weight - this.weight) * t,
         this.x + (b.x - this.x) * t,
         this.vertical + (b.vertical - this.vertical) * t,
         this.compression + (b.compression - this.compression) * t
      );
   }
}
