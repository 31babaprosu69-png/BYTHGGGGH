package pro.webhm.playerinteractions.client;

import java.util.Map;
import org.joml.Vector3f;
import pro.webhm.playerinteractions.npc.BoneTransform;

final class SceneArmPair {
   private static final float[] OFFSETS = new float[]{0.0F, 15.0F, -15.0F, 30.0F, -30.0F, 45.0F, -45.0F, 60.0F, -60.0F, 90.0F, -90.0F};
   private final Vector3f[] goals = new Vector3f[]{new Vector3f(), new Vector3f()};
   private final Vector3f[] start = new Vector3f[]{new Vector3f(), new Vector3f()};
   private final Vector3f trial = new Vector3f();
   private final BoneTransform[] bestArm = new BoneTransform[2];
   private final BoneTransform[] bestFore = new BoneTransform[2];
   private float route;
   private int slide;

   void reset() {
      this.route = 0.0F;
      this.slide = 0;
   }

   // $VF: Could not verify finally blocks. A semaphore variable has been added to preserve control flow.
   // Please report this to the Vineflower issue tracker, at https://github.com/Vineflower/vineflower/issues with a copy of the class file (if you have the rights to distribute it!)
   void solve(
      SceneTouchSurface surface,
      SceneTouchArm[] arms,
      Vector3f[] contacts,
      Vector3f[] normals,
      boolean valid,
      float limit,
      float weight,
      BoneTransform[] previousArm,
      BoneTransform[] previousFore
   ) {
      for (int i = 0; i < 2; i++) {
         arms[i].hold(previousArm[i], previousFore[i]);
         this.start[i].set(arms[i].palm);
         this.bestArm[i] = previousArm[i].copy();
         this.bestFore[i] = previousFore[i].copy();
      }

      if (valid) {
         boolean var28 = false /* VF: Semaphore variable */;

         label247: {
            label248: {
               try {
                  var28 = true;

                  for (float offset : OFFSETS) {
                     float candidate = this.route + offset;
                     if (!(Math.abs(candidate) > 45.0F)) {
                        for (int step = 0; step < 4; step++) {
                           int candidateSlide = (this.slide + step) % 4;
                           boolean fits = true;

                           for (int i = 0; i < 2; i++) {
                              arms[i].hold(previousArm[i], previousFore[i]);
                              arms[i].route(candidate, candidateSlide, true);
                              fits &= arms[i].fit(surface, contacts[i], normals[i], limit, this.goals[i]);
                           }

                           if (fits) {
                              this.route = candidate;
                              this.slide = candidateSlide;

                              for (int i = 0; i < 2; i++) {
                                 arms[i].solve(this.goals[i], limit, weight);
                              }

                              var28 = false;
                              break label247;
                           }
                        }
                     }
                  }

                  for (int i = 0; i < 2; i++) {
                     arms[i].route(this.route, this.slide, true);
                     arms[i].hold(previousArm[i], previousFore[i]);
                     this.goals[i].set(contacts[i]).fma(arms[i].clearance(normals[i]), normals[i]);
                  }

                  if (this.at(surface, arms, limit, 1.0F)) {
                     var28 = false;
                     break label248;
                  }

                  float lo = 0.0F;
                  float hi = 1.0F;

                  for (int step = 0; step < 7; step++) {
                     float fraction = (lo + hi) * 0.5F;
                     if (this.at(surface, arms, limit, fraction)) {
                        lo = fraction;
                        this.save(arms);
                     } else {
                        hi = fraction;
                     }
                  }

                  for (int i = 0; i < 2; i++) {
                     arms[i].hold(this.bestArm[i], this.bestFore[i]);
                  }

                  var28 = false;
               } finally {
                  if (var28) {
                     for (SceneTouchArm arm : arms) {
                        arm.route(this.route, this.slide, false);
                     }
                  }
               }

               for (SceneTouchArm arm : arms) {
                  arm.route(this.route, this.slide, false);
               }

               return;
            }

            for (SceneTouchArm arm : arms) {
               arm.route(this.route, this.slide, false);
            }

            return;
         }

         for (SceneTouchArm arm : arms) {
            arm.route(this.route, this.slide, false);
         }
      }
   }

   private boolean at(SceneTouchSurface surface, SceneTouchArm[] arms, float limit, float fraction) {
      boolean clear = true;

      for (int i = 0; i < 2; i++) {
         arms[i].solve(this.trial.set(this.start[i]).lerp(this.goals[i], fraction), limit, 1.0F);
         clear &= arms[i].clear(surface);
      }

      return clear;
   }

   private void save(SceneTouchArm[] arms) {
      for (int i = 0; i < 2; i++) {
         Map<String, BoneTransform> pose = arms[i].current();
         this.bestArm[i] = pose.get(i == 0 ? "right_arm" : "left_arm").copy();
         this.bestFore[i] = pose.get(i == 0 ? "right_forearm" : "left_forearm").copy();
      }
   }
}
