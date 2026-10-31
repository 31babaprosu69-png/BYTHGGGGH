package pro.webhm.playerinteractions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import pro.webhm.playerinteractions.SessionState;
import pro.webhm.playerinteractions.TouchProfile;
import pro.webhm.playerinteractions.body.HeadFrame;
import pro.webhm.playerinteractions.body.TouchPose;
import pro.webhm.playerinteractions.body.TouchSpring;
import pro.webhm.playerinteractions.facial.FacePerformance;
import pro.webhm.playerinteractions.facial.FacialActing;
import pro.webhm.playerinteractions.facial.FacialData;
import pro.webhm.playerinteractions.facial.FacialExpression;
import pro.webhm.playerinteractions.facial.FacialPose;
import pro.webhm.playerinteractions.facial.FacialTongue;
import pro.webhm.playerinteractions.facial.FacialTouch;
import pro.webhm.playerinteractions.facial.HandResponse;
import pro.webhm.playerinteractions.facial.InspectionAim;
import pro.webhm.playerinteractions.facial.MouthResponse;
import pro.webhm.playerinteractions.npc.BlushData;
import pro.webhm.playerinteractions.npc.BoneTransform;

public final class PlayerTouchReaction {
   private static final Map<AbstractClientPlayer, PlayerTouchReaction.Reaction> ACTIVE = new WeakHashMap<>();
   private static final Map<AbstractClientPlayer, PlayerTouchReaction.Reaction> MOUTHS = new WeakHashMap<>();

   public static boolean active(AbstractClientPlayer npc) {
      PlayerTouchReaction.Reaction r = sample(npc, 0.0F);
      return r != null && (r.expression.x > 0.005F || r.held && (double)npc.level().getGameTime() - r.last <= 12.0);
   }

   public static FacialPose facial(AbstractClientPlayer npc, float partial, FacialPose base) {
      PlayerTouchReaction.Reaction r = sample(npc, partial);
      if (r == null) {
         return base;
      } else {
         double now = (double)npc.level().getGameTime() + (double)partial;
         double age = now - r.start;
         double end = r.held ? r.last + 12.0 : r.last + 18.0;

         FacialExpression.Mood mood = switch (r.zone) {
            case 0, 11 -> FacialExpression.Mood.SHY;
            default -> FacialExpression.Mood.PLAYFUL;
            case 4 -> FacialExpression.Mood.SURPRISE;
            case 7, 8, 9, 10 -> FacialExpression.Mood.WARM;
         };
         if (r.zone == 11) {
            return base;
         } else if (r.zone == 4 && r.refusing) {
            return HandResponse.refusal(base, responseAge(npc, r, partial), r.expression.x, TouchProfile.DATA.facial.emotionStrength, r.side);
         } else if (r.zone != 0 && r.zone != 12 && r.zone != 4) {
            return FacialExpression.perform(mood, age, end - r.start, base, TouchProfile.DATA.facial.emotionStrength, npc.getId() + r.zone);
         } else {
            float movement = Math.min(1.0F, (float)Math.sqrt((double)(r.motion.x * r.motion.x + r.motion.y * r.motion.y + r.motion.z * r.motion.z)));
            return FacialTouch.sample(base, r.zone, r.side, age, r.expression.x, movement, TouchProfile.DATA.facial.emotionStrength);
         }
      }
   }

   public static FacialTongue tongue(AbstractClientPlayer npc, float partial) {
      PlayerTouchReaction.Reaction r = sample(npc, MOUTHS.get(npc), partial);
      if (r == null) {
         return FacialTongue.HIDDEN;
      } else if (!MouthResponse.defensive(r.bond) && !r.bitten) {
         double now = (double)npc.level().getGameTime() + (double)partial;
         if (r.faceFrame != null && r.faceAt == now) {
            return r.faceFrame.tongue();
         } else {
            InspectionAim input = null;
            return FacePerformance.tongue(
               now - r.start,
               r.expression.x,
               input == null ? r.motion.x : input.x() * 0.5F,
               input == null ? r.motion.y : input.y() * 0.5F,
               r.motion.z,
               r.identity
            );
         }
      } else {
         return FacialTongue.HIDDEN;
      }
   }

   public static boolean mouthActive(AbstractClientPlayer npc, float partial) {
      PlayerTouchReaction.Reaction r = sample(npc, MOUTHS.get(npc), partial);
      return r != null && (r.held && (double)npc.level().getGameTime() - r.last <= 12.0 || r.expression.x > 0.005F);
   }

   public static boolean mouthHeld(AbstractClientPlayer npc) {
      PlayerTouchReaction.Reaction r = MOUTHS.get(npc);
      return r != null && r.held && (double)npc.level().getGameTime() - r.last <= 12.0;
   }

   public static float mouthFinger(AbstractClientPlayer npc, float partial) {
      PlayerTouchReaction.Reaction r = sample(npc, MOUTHS.get(npc), partial);
      if (r == null) {
         return 0.0F;
      } else if (r.warningAt >= 0L) {
         return handResponse(npc, partial).bring() * Mth.clamp(r.expression.x, 0.0F, 1.0F);
      } else {
         float ready = FacialExpression.ease(((double)npc.level().getGameTime() + (double)partial - r.start - 7.0) / 18.0);
         return ready * Mth.clamp(r.motion.z, 0.0F, 1.0F) * Mth.clamp(r.expression.x, 0.0F, 1.0F);
      }
   }

   public static FacialPose mouthContact(AbstractClientPlayer npc, float partial, FacialPose base) {
      PlayerTouchReaction.Reaction r = sample(npc, MOUTHS.get(npc), partial);
      if (r == null) {
         return base;
      } else {
         FacialData f = TouchProfile.DATA.facial;
         double now = (double)npc.level().getGameTime() + (double)partial;
         if (r.faceFrame != null && r.faceAt == now && base.equals(r.faceBase)) {
            return r.faceFrame.pose();
         } else {
            r.faceAt = now;
            r.faceBase = base;
            InspectionAim input = null;
            r.faceFrame = FacePerformance.contact(
               base,
               now - r.start,
               r.expression.x,
               input == null ? r.motion.x : input.x() * 0.5F,
               input == null ? r.motion.y : input.y() * 0.5F,
               r.motion.z,
               f.autoExpressions ? f.emotionStrength : 0.0F,
               r.side,
               r.bond,
               r.identity,
               r.warningAt < 0L ? -1.0 : responseAge(npc, r, partial)
            );
            return r.faceFrame.pose();
         }
      }
   }

   private static double responseAge(AbstractClientPlayer npc, PlayerTouchReaction.Reaction r, float partial) {
      double age = (double)npc.level().getGameTime() + (double)partial - r.responseStart - (double)(r.zone == 11 ? Math.max(0L, r.warningAt) : 0L);
      return Double.isNaN(r.stopAge) ? age : Math.min(age, r.stopAge);
   }

   public static HandResponse.Frame handResponse(AbstractClientPlayer npc, float partial) {
      PlayerTouchReaction.Reaction r = sample(npc, partial);
      if (r == null) {
         return HandResponse.Frame.NONE;
      } else if (r.zone == 11 && r.warningAt >= 0L) {
         return HandResponse.bite(responseAge(npc, r, partial));
      } else {
         return r.refusing ? HandResponse.refuse(responseAge(npc, r, partial)) : HandResponse.Frame.NONE;
      }
   }

   public static void grip(AbstractClientPlayer npc, int token, int zone, int side, float x, float y, float z, boolean held, float aimX, float aimY) {
      PlayerTouchReaction.Reaction r = zone == 11 ? MOUTHS.get(npc) : ACTIVE.get(npc);
      if (zone != 11 || r == null || !r.mouthClosing || r.token != token) {
         if (r == null
            || r.token != token && (r.zone != 11 || zone != 11 || !(r.mouthUntil > (double)npc.level().getGameTime()))
            || r.zone != zone && (r.zone != 0 && r.zone != 12 || zone != 0 && zone != 12)) {
            r = new PlayerTouchReaction.Reaction();
            r.start = (double)npc.level().getGameTime();
            r.token = token;
            r.identity = npc.getUUID().hashCode();
            ACTIVE.put(npc, r);
         }

         ACTIVE.put(npc, r);
         if (zone == 11) {
            MOUTHS.put(npc, r);
            if (!held) {
               r.mouthUntil = 0.0;
            } else {
               r.mouthClosing = false;
            }
         } else if (zone != 7 && zone != 0 && zone != 12) {
            MOUTHS.remove(npc);
         }

         if (r.held && !held && r.zone == 0 && Double.isNaN(r.released)) {
            r.released = (double)npc.level().getGameTime();
            r.doubleBlink = npc.getRandom().nextBoolean();
         }

         r.token = token;
         r.zone = zone;
         r.side = side;
         r.last = (double)npc.level().getGameTime();
         r.held = held;
         r.tx = x;
         r.ty = y;
         r.tz = z;
         r.faceAt = Double.NaN;
         r.aimX = aimX;
         r.aimY = aimY;
      }
   }

   private static PlayerTouchReaction.Reaction sample(AbstractClientPlayer npc, float partial) {
      return sample(npc, ACTIVE.get(npc), partial);
   }

   private static PlayerTouchReaction.Reaction sample(AbstractClientPlayer npc, PlayerTouchReaction.Reaction r, float partial) {
      if (r == null) {
         return null;
      } else {
         double now = (double)npc.level().getGameTime() + (double)partial;
         double age = now - r.start;
         if (!(age < 0.0) && !(now - Math.max(r.last, r.mouthUntil) > 90.0)) {
            advance(r, now);
            return r;
         } else {
            ACTIVE.remove(npc, r);
            MOUTHS.remove(npc, r);
            return null;
         }
      }
   }

   static void advance(PlayerTouchReaction.Reaction r, double now) {
      if (Double.isNaN(r.sampled) || !(now <= r.sampled)) {
         r.sampled = now;
         double age = now - r.start;
         boolean held = r.held && now - r.last <= 12.0;
         float pulse = age < 18.0 ? (float)(Math.sin(Math.PI * age / 18.0) * Math.exp(-age / 18.0)) : 0.0F;
         r.motion
            .sample(
               now,
               held ? r.tx : 0.0F,
               held ? r.ty : 0.0F,
               (!held && r.zone != 11 ? 0.0F : r.tz) + (r.zone != 0 && r.zone != 11 && r.zone != 12 ? pulse * 0.24F : 0.0F)
            );
         r.expression.sample(now, r.mouthClosing ? 0.0F : (!held && (r.zone != 11 || !(now < r.mouthUntil)) ? pulse : 1.0F), 0.0F, 0.0F);
      }
   }

   public static Map<String, BoneTransform> apply(AbstractClientPlayer npc, Map<String, BoneTransform> original, float partial) {
      PlayerTouchReaction.Reaction r = sample(npc, partial);
      return r == null ? original : body(r, original, (double)npc.level().getGameTime() + (double)partial);
   }

   static Map<String, BoneTransform> body(PlayerTouchReaction.Reaction r, Map<String, BoneTransform> original, double now) {
      if (r.zone == 11 || r.zone == 12) {
         return original;
      } else if (r.zone == 7) {
         return TouchPose.pet(original, r.expression.x, r.motion.x, r.motion.y);
      } else if (r.zone == 4) {
         double age = now - r.start;
         if (r.refusing) {
            HandResponse.Frame frame = HandResponse.refuse(Double.isNaN(r.stopAge) ? now - r.responseStart : r.stopAge);
            HashMap<String, BoneTransform> result = new HashMap<>(original);
            BoneTransform body = result.getOrDefault("body", new BoneTransform()).copy();
            body.rx = body.rx - 5.0F * frame.weight() * r.expression.x;
            body.ry = body.ry + (float)(r.side * 5) * frame.withdraw() * frame.weight() * r.expression.x;
            result.put("body", body);
            return result;
         } else {
            float flinch = FacialExpression.ease(age / 4.0) * (1.0F - FacialExpression.ease((age - 8.0) / 12.0));
            return TouchPose.startled(original, r.expression.x, flinch);
         }
      } else {
         return r.zone == 0 ? original : TouchPose.apply(original, r.zone, r.side, r.motion.x, r.motion.y, r.motion.z);
      }
   }

   public static void head(AbstractClientPlayer npc, ModelPart head, float partial, FacialPose pose) {
      if (head != null) {
         if (TouchProfile.DATA.facial.enabled && mouthActive(npc, partial)) {
            FacialData f = TouchProfile.DATA.facial;
            if (f.autoExpressions) {
               PlayerTouchReaction.Reaction mouth = sample(npc, MOUTHS.get(npc), partial);
               FacialActing acting = InspectionAim.pose(mouth.motion.x * 2.0F, mouth.motion.y * 2.0F);
               float rad = (float) (Math.PI / 180.0) * f.headMotion * Math.max(0.0F, Math.min(1.0F, mouth.expression.x));
               head.yRot = head.yRot + acting.headYaw() * rad;
               head.xRot = head.xRot + acting.headPitch() * rad;
               head.zRot = head.zRot + acting.headRoll() * rad;
            }
         } else {
            PlayerTouchReaction.Reaction r = sample(npc, partial);
            if (r != null && (r.zone == 0 || r.zone == 4 || r.zone == 7 || r.zone == 11 || r.zone == 12) && head != null) {
               if (r.zone == 4) {
                  FacialData f = TouchProfile.DATA.facial;
                  if (f.autoExpressions) {
                     FacialActing a = r.refusing
                        ? HandResponse.refusal(FacialPose.rest(f), responseAge(npc, r, partial), r.expression.x, f.emotionStrength, r.side).acting()
                        : FacialTouch.sample(
                              FacialPose.rest(f),
                              4,
                              r.side,
                              (double)npc.level().getGameTime() + (double)partial - r.start,
                              r.expression.x,
                              0.0F,
                              f.emotionStrength
                           )
                           .acting();
                     float rad = (float) (Math.PI / 180.0) * f.headMotion;
                     head.xRot = head.xRot + a.headPitch() * rad;
                     head.yRot = head.yRot + a.headYaw() * rad;
                     head.zRot = head.zRot + a.headRoll() * rad;
                  }
               } else if (r.zone == 11) {
                  FacialData f = TouchProfile.DATA.facial;
                  FacialActing a = mouthContact(npc, partial, FacialPose.rest(f)).acting();
                  float rad = (float) (Math.PI / 180.0) * f.headMotion;
                  head.yRot = head.yRot + a.headYaw() * rad;
                  head.xRot = head.xRot + a.headPitch() * rad;
                  head.zRot = head.zRot + a.headRoll() * rad;
               } else {
                  float weight = Math.min(1.0F, r.expression.x);
                  if (r.zone == 12) {
                     head.zRot = head.zRot + (float)Math.toRadians((double)(((float)r.side * 3.5F + r.motion.x * 1.5F) * weight));
                     head.xRot = head.xRot + (float)Math.toRadians((double)((2.0F + r.motion.y) * weight));
                  } else if (r.zone == 7) {
                     head.xRot = head.xRot + (float)Math.toRadians((double)(9.0F * weight + r.motion.y * 2.0F));
                     head.zRot = head.zRot + (float)Math.toRadians((double)(-5.0F * weight + r.motion.x * 4.0F));
                  } else {
                     if (Float.isNaN(r.cheekHeadYaw)) {
                        r.cheekHeadYaw = head.yRot;
                        r.cheekHeadPitch = head.xRot;
                        r.cheekHeadRoll = head.zRot;
                     }

                     float drag = Math.min(1.0F, (float)Math.sqrt((double)(r.motion.x * r.motion.x + r.motion.y * r.motion.y + r.motion.z * r.motion.z)));
                     float target = r.cheekHeadYaw - (float)Math.toRadians(3.0) * Math.signum((float)r.side) * drag;
                     weight = Math.max(0.0F, weight);
                     head.yRot = head.yRot + angleDelta(target - head.yRot) * weight;
                     head.xRot = head.xRot + angleDelta(r.cheekHeadPitch - head.xRot) * weight;
                     head.zRot = head.zRot + angleDelta(r.cheekHeadRoll - head.zRot) * weight;
                  }
               }
            }
         }
      }
   }

   private static float angleDelta(float radians) {
      return (float)Math.atan2(Math.sin((double)radians), Math.cos((double)radians));
   }

   public static void breast(AbstractClientPlayer npc, ModelPart left, ModelPart right, float partial) {
      PlayerTouchReaction.Reaction r = sample(npc, partial);
      if (r != null && r.zone == 4) {
         if (!r.refusing) {
            ModelPart part = r.side < 0 ? right : left;
            part.xRot = part.xRot + (float)Math.toRadians((double)(r.motion.y * 2.0F - r.motion.z * 2.0F));
            part.zRot = part.zRot + (float)Math.toRadians((double)(-r.motion.x * 2.0F));
         }
      }
   }

   public static boolean eyes(AbstractClientPlayer npc, HeadFrame frame, float partial) {
      PlayerTouchReaction.Reaction r = sample(npc, partial);
      if (r == null) {
         return false;
      } else {
         float weight = Math.max(0.0F, Math.min(1.0F, r.expression.x));
         if (r.zone != 11 && r.zone != 12 && r.zone != 4) {
            if (r.zone == 0) {
               float drag = Math.min(1.0F, (float)Math.sqrt((double)(r.motion.x * r.motion.x + r.motion.y * r.motion.y + r.motion.z * r.motion.z)));
               frame.cheek = (float)r.side * weight * (0.25F + 0.75F * drag);
            }

            double now = (double)npc.level().getGameTime() + (double)partial;
            float afterBlink = 0.0F;
            if (r.zone == 0 || r.zone == 8 || r.zone == 9 || r.zone == 10) {
               float growth = (float)(1.0 - Math.exp(-Math.max(0.0, now - r.start) / 7.0));
               double end = Double.isNaN(r.released) ? r.last + 12.0 : r.released;
               if (now > end) {
                  growth = (float)((1.0 - Math.exp(-Math.max(0.0, end - r.start) / 7.0)) * Math.exp(-(now - end) / 6.0));
               }

               frame.pupilScale = 1.0F + 0.4F * growth;
               if (r.doubleBlink) {
                  double age = now - r.released;
                  if (age >= 0.0 && age < 19.0) {
                     frame.blink = 0.0F;
                  }

                  afterBlink = blinkPulse(age - 7.0) + blinkPulse(age - 14.0);
               }
            }

            frame.winkLeft = Math.max(
               frame.winkLeft, weight * (r.zone == 7 ? 0.68F : (r.zone == 0 ? (r.side > 0 ? 1.0F : 0.35F) : (r.zone == 4 ? 0.4F : 0.36F)))
            );
            frame.winkRight = Math.max(
               frame.winkRight, weight * (r.zone == 7 ? 0.68F : (r.zone == 0 ? (r.side < 0 ? 1.0F : 0.35F) : (r.zone == 4 ? 0.4F : 0.36F)))
            );
            frame.winkLeft = Math.max(frame.winkLeft, afterBlink);
            frame.winkRight = Math.max(frame.winkRight, afterBlink);
            frame.gazeX = frame.gazeX * (1.0F - weight * 0.7F) + r.aimX * 0.55F * weight + r.motion.x * 0.1F;
            frame.gazeY = frame.gazeY * (1.0F - weight * 0.7F) + r.aimY * 0.3F * weight + r.motion.y * 0.1F;
            if (frame.face != null) {
               frame.face.x = frame.face.x * (1.0F - weight * 0.7F) + frame.gazeX * weight;
               frame.face.y = frame.face.y * (1.0F - weight * 0.7F) + frame.gazeY * weight;
            }

            return weight > 0.01F || afterBlink > 0.0F || frame.pupilScale > 1.001F;
         } else {
            return false;
         }
      }
   }

   public static float blushTarget(AbstractClientPlayer npc, float partial) {
      PlayerTouchReaction.Reaction r = sample(npc, partial);
      if (r == null) {
         return 0.0F;
      } else {
         BlushData d = TouchProfile.DATA.headDynamics.blush;
         if (r.refusing) {
            return 0.0F;
         } else if (r.zone == 11) {
            return !MouthResponse.defensive(r.bond) && !r.bitten ? mouthFinger(npc, partial) * 0.45F : 0.0F;
         } else {
            return !d.respondsTo(r.zone) && r.zone != 10 ? 0.0F : Math.max(0.0F, Math.min(1.0F, r.expression.x));
         }
      }
   }

   public static float pupilGlowTarget(AbstractClientPlayer npc, float partial) {
      PlayerTouchReaction.Reaction mouth = sample(npc, MOUTHS.get(npc), partial);
      if (mouthHeld(npc)) {
         return !MouthResponse.defensive(mouth.bond) && !mouth.bitten ? Math.max(0.0F, Math.min(1.0F, mouth.expression.x)) * 0.8F : 0.0F;
      } else {
         PlayerTouchReaction.Reaction r = sample(npc, partial);
         if (r != null && r.refusing) {
            return 0.0F;
         } else if (r != null && r.zone == 11) {
            return 0.0F;
         } else {
            return r == null || r.zone != 7 && r.zone != 4 && r.zone != 10 ? 0.0F : Math.max(0.0F, Math.min(1.0F, r.expression.x));
         }
      }
   }

   private static float blinkPulse(double age) {
      return !(age <= 0.0) && !(age >= 4.0) ? (float)Math.pow(Math.sin(Math.PI * age / 4.0), 2.0) : 0.0F;
   }

   public static void receive(SessionState state) {
      Minecraft mc = Minecraft.getInstance();
      if (state.action == 4 && state.zone >= 0 && state.zone != 11 && mc.level != null) {
         if (mc.level.getPlayerByUUID(state.recipient().id) instanceof AbstractClientPlayer player) {
            int var4 = state.reactionZone();
            grip(
               player,
               31 * state.id.hashCode() + state.touchSerial,
               var4,
               state.side,
               state.dragX,
               state.dragY,
               state.dragZ,
               state.touching,
               Math.max(-1.0F, Math.min(1.0F, state.aimX / 4.0F)),
               var4 != 0 && var4 != 12 ? 0.0F : Math.max(-1.0F, Math.min(1.0F, (state.aimY + 2.75F) / 2.25F))
            );
            input(ACTIVE.get(player), state, (double)mc.level.getGameTime());
         }
      }
   }

   static void input(PlayerTouchReaction.Reaction r, SessionState state, double now) {
      r.zone = state.reactionZone();
      r.side = state.side;
      r.tx = state.dragX;
      r.ty = state.dragY;
      r.tz = state.dragZ;
      r.held = state.touching;
      r.last = now;
      double elapsed = (double)Math.max(0L, state.clock - state.touchStarted);
      if (!r.contextSet) {
         r.start = r.responseStart = now - elapsed;
         r.contextSet = true;
      }

      if (!r.held && Double.isNaN(r.stopAge)) {
         r.stopAge = elapsed;
      }
   }

   public static void release(SessionState state) {
      if (state != null && state.action == 4) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null) {
            if (mc.level.getPlayerByUUID(state.recipient().id) instanceof AbstractClientPlayer p) {
               PlayerTouchReaction.Reaction r = ACTIVE.get(p);
               if (r != null) {
                  grip(p, r.token, r.zone, r.side, r.tx, r.ty, r.tz, false, r.aimX, r.aimY);
               }
            }
         }
      }
   }

   public static void clear() {
      ACTIVE.clear();
      MOUTHS.clear();
   }

   public static void hearts(AbstractClientPlayer player, PoseStack head, MultiBufferSource buffers, float partial) {
      PlayerTouchReaction.Reaction r = sample(player, partial);
      if (r != null && r.zone == 7) {
         double now = (double)player.level().getGameTime() + (double)partial;
         PetHeartEffect.render(head, buffers, now - r.start, (r.held ? r.last + 12.0 : r.last) - r.start, player.getId(), 0.9375F, 1.0F, 1.0F);
      }
   }

   static final class Reaction {
      int zone;
      int side;
      int token;
      double start;
      double last;
      boolean held;
      float tx;
      float ty;
      float tz;
      float aimX;
      float aimY;
      int bond = 100;
      int identity;
      int contextToken;
      long warningAt = -1L;
      double responseStart;
      double stopAge = Double.NaN;
      boolean contextSet;
      boolean bitten;
      boolean refusing;
      double mouthUntil;
      double sampled = Double.NaN;
      double released = Double.NaN;
      boolean doubleBlink;
      boolean mouthClosing;
      float cheekHeadYaw = Float.NaN;
      float cheekHeadPitch;
      float cheekHeadRoll;
      double gaitTick = Double.NaN;
      double walkDistance;
      float walkSpeed;
      double faceAt = Double.NaN;
      FacialPose faceBase;
      FacePerformance.Frame faceFrame;
      Vec3 walkPosition;
      final TouchSpring motion = new TouchSpring();
      final TouchSpring expression = new TouchSpring();
   }
}
