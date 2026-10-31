package pro.webhm.playerinteractions.anim;

import java.util.Arrays;
import pro.webhm.playerinteractions.facial.FacialExpression;

public class SceneCue {
   public String kind = "text";
   public boolean speech = true;
   public String expression = "auto";
   public int tick;
   public int length = 60;
   public String text = "";
   public String source = "";
   public String label = "";
   public int offset;
   public float volume = 1.0F;
   public int color = 16777215;
   public int screenY = 75;

   public boolean active(double time) {
      return time >= (double)this.tick && time < (double)((long)this.tick + (long)this.length);
   }

   public void normalize() {
      this.kind = "audio".equals(this.kind) ? "audio" : "text";
      if (this.expression == null || !Arrays.asList(FacialExpression.IDS).contains(this.expression)) {
         this.expression = "auto";
      }

      this.tick = Math.max(0, Math.min(72000, this.tick));
      this.length = Math.max(1, Math.min(72000, this.length));
      this.offset = Math.max(0, Math.min(72000, this.offset));
      this.volume = Float.isFinite(this.volume) ? Math.max(0.0F, Math.min(1.0F, this.volume)) : 1.0F;
      this.screenY = Math.max(5, Math.min(95, this.screenY));
      this.color &= 16777215;
      this.text = this.text == null ? "" : this.text.substring(0, Math.min(1000, this.text.length()));
      this.source = this.source == null ? "" : this.source.substring(0, Math.min(256, this.source.length()));
      this.label = this.label == null ? "" : this.label.substring(0, Math.min(120, this.label.length()));
   }
}
