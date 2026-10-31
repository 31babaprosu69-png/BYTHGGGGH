package pro.webhm.playerinteractions.facial;

public final class ActingTransition {
   private boolean initialized;
   private boolean enabled;
   private double changed;
   private double last = Double.NEGATIVE_INFINITY;
   private SpeechPerformance.Gesture hands = SpeechPerformance.Gesture.NONE;
   private SpeechPerformance.Gesture fromHands = this.hands;
   private FacialActing head = FacialActing.NONE;
   private FacialActing fromHead = this.head;

   public void sample(double now, boolean allowed, SpeechPerformance.Gesture targetHands, FacialActing targetHead) {
      if (Double.isFinite(now) && !(now <= this.last)) {
         if (!this.initialized || now - this.last > 20.0) {
            this.initialized = true;
            this.enabled = false;
            this.hands = this.fromHands = SpeechPerformance.Gesture.NONE;
            this.head = this.fromHead = FacialActing.NONE;
         }

         if (allowed != this.enabled) {
            this.fromHands = this.hands;
            this.fromHead = this.head;
            this.changed = now;
            this.enabled = allowed;
         }

         float w = FacialExpression.ease((now - this.changed) / 8.0);
         this.hands = this.fromHands.mix(this.enabled ? targetHands : SpeechPerformance.Gesture.NONE, w);
         this.head = this.fromHead.blend(this.enabled ? targetHead : FacialActing.NONE, w);
         this.last = now;
      }
   }

   public SpeechPerformance.Gesture hands() {
      return this.hands;
   }

   public FacialActing head() {
      return this.head;
   }
}
