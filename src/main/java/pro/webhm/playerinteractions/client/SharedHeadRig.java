package pro.webhm.playerinteractions.client;

import pro.webhm.playerinteractions.compat.Vtx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.AxisAngle4f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import pro.webhm.playerinteractions.body.HeadFrame;
import pro.webhm.playerinteractions.facial.EyeLids;
import pro.webhm.playerinteractions.npc.CheekData;
import pro.webhm.playerinteractions.npc.HeadDynamicsData;

public final class SharedHeadRig {
   private static final float OUTER_MIN = -4.5F;
   private static final float OUTER_MAX = 4.5F;
   private static final float OUTER_TOP = -8.5F;
   private static final float OUTER_BOTTOM = 0.5F;

   public static void render(
      PoseStack stack, VertexConsumer out, int light, HeadFrame frame, HeadDynamicsData data, float partial, boolean gaze, boolean eyelids
   ) {
      renderCheek(stack, out, light, frame.cheek, data);
      if (gaze) {
         renderGaze(stack, out, light, frame, data, partial);
      }

      if (eyelids) {
         renderEyelidRig(stack, out, light, frame, data, partial);
      }
   }

   private static void renderCheek(PoseStack s, VertexConsumer out, int light, float cheek, HeadDynamicsData data) {
      CheekData settings = data.cheek(cheek < 0.0F ? -1 : 1);
      float top = settings.top(data);
      float bottom = settings.bottom(data);
      if (settings.enabled && !(Math.abs(cheek) < 0.001F) && !(bottom - top < 0.15F)) {
         float halfWidth = Math.min(settings.width / 2.0F, 3.95F - settings.x);
         float halfHeight = (bottom - top) / 2.0F;
         float centerY = (top + bottom) / 2.0F - 8.0F;
         float center = Math.signum(cheek) * settings.x;
         float amount = Math.min(1.0F, Math.abs(cheek));
         int resolution = 24;

         for (int row = 0; row < resolution; row++) {
            for (int col = 0; col < resolution; col++) {
               int[][] corners = new int[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}};

               for (int[] corner : corners) {
                  float dx = -1.0F + 2.0F * (float)(col + corner[0]) / (float)resolution;
                  float dy = -1.0F + 2.0F * (float)(row + corner[1]) / (float)resolution;
                  Vector3f point = cheekPoint(data, settings, center, centerY, halfWidth, halfHeight, dx, dy, amount);
                  Vector3f tangentX = cheekPoint(data, settings, center, centerY, halfWidth, halfHeight, dx + 0.001F, dy, amount)
                     .sub(cheekPoint(data, settings, center, centerY, halfWidth, halfHeight, dx - 0.001F, dy, amount));
                  Vector3f tangentY = cheekPoint(data, settings, center, centerY, halfWidth, halfHeight, dx, dy + 0.001F, amount)
                     .sub(cheekPoint(data, settings, center, centerY, halfWidth, halfHeight, dx, dy - 0.001F, amount));
                  Vector3f normal = tangentY.cross(tangentX).normalize();
                  vertex(
                     out,
                     s.last(),
                     light,
                     point.x,
                     point.y,
                     point.z,
                     12.0F + center + dx * halfWidth,
                     16.0F + centerY + dy * halfHeight,
                     normal.x,
                     normal.y,
                     normal.z
                  );
               }
            }
         }
      }
   }

   private static Vector3f cheekPoint(
      HeadDynamicsData data, CheekData settings, float center, float centerY, float hw, float hh, float dx, float dy, float amount
   ) {
      float x = center + dx * hw;
      float y = centerY + dy * hh;
      float fall = (float)Math.pow((double)Math.max(0.0F, 1.0F - dx * dx - dy * dy), (double)Math.max(2.0F, settings.softness));
      float clearance = 1.0F;

      for (int sign : new int[]{-1, 1}) {
         float ex = Math.max(0.0F, Math.abs(x - (float)sign * data.eyeSpacing) - data.eyeWidth / 2.0F - 0.12F);
         float ey = Math.max(0.0F, Math.abs(y + 8.0F - data.eyeY - data.eyeHeight / 2.0F) - data.eyeHeight / 2.0F - 0.12F);
         float t = Math.min(1.0F, (float)Math.hypot((double)ex, (double)ey) / 0.65F);
         clearance = Math.min(clearance, t * t * (3.0F - 2.0F * t));
      }

      fall *= clearance;
      float ox = Math.signum(center) * amount * settings.sideways * fall;
      float oy = amount * Math.min(hh * 0.4F, Math.max(-hh * 0.4F, settings.vertical)) * fall;
      if (data.overlapsEye(x + ox, y + oy + 8.0F, x + ox + 0.001F, y + oy + 8.0F + 0.001F)) {
         fall = 0.0F;
         ox = 0.0F;
         oy = 0.0F;
      }

      return new Vector3f(x + ox, y + oy, -4.0002F - amount * settings.pull * fall);
   }

   public static void renderHairRig(PoseStack stack, VertexConsumer out, int light, HeadFrame entity, HeadDynamicsData data, float partialTick) {
      float split = -8.5F + 9.0F * data.hairAxisY / 8.0F;
      float splitTextureV = 8.0F + data.hairAxisY;
      Pose fixed = stack.last();
      shellSides(out, fixed, light, -8.5F, split, 8.0F, splitTextureV);
      topFace(out, fixed, light);
      bottomFace(out, fixed, light);
      float phase = ((float)entity.tickCount + partialTick + (float)entity.id * 11.0F) * 0.11F;
      float speed = Math.min(1.0F, (float)entity.horizontalSpeed * 8.0F);
      int bones = data.hairBones;

      for (int i = 0; i < bones; i++) {
         float t0 = (float)i / (float)bones;
         float t1 = ((float)i + 1.0F) / (float)bones;
         float inertia = entity.hair;
         float a = strandAngle(data, phase, speed, i, 0.0F) + inertia;
         float x0 = mix(-4.5F, 4.5F, t0);
         float x1 = mix(-4.5F, 4.5F, t1);
         float u0 = mix(40.0F, 48.0F, t0);
         float u1 = mix(40.0F, 48.0F, t1);
         stack.pushPose();
         safeStrand(stack, entity, (x0 + x1) * 0.5F, split, -4.5F, -a, true, x0, x1);
         hairQuad(
            out, stack.last(), light, x0, split, -4.5F, x1, split, -4.5F, x1, 0.5F, -4.5F, x0, 0.5F, -4.5F, u0, splitTextureV, u1, 16.0F, 0.0F, 0.0F, -1.0F
         );
         stack.popPose();
         float backA = strandAngle(data, phase, speed, i, 1.9F) - inertia;
         stack.pushPose();
         safeStrand(stack, entity, (x0 + x1) * 0.5F, split, 4.5F, backA, true, x0, x1);
         hairQuad(
            out,
            stack.last(),
            light,
            x1,
            split,
            4.5F,
            x0,
            split,
            4.5F,
            x0,
            0.5F,
            4.5F,
            x1,
            0.5F,
            4.5F,
            mix(56.0F, 64.0F, t0),
            splitTextureV,
            mix(56.0F, 64.0F, t1),
            16.0F,
            0.0F,
            0.0F,
            1.0F
         );
         stack.popPose();
         float z0 = mix(-4.5F, 4.5F, t0);
         float z1 = mix(-4.5F, 4.5F, t1);
         float sideA = strandAngle(data, phase, speed, i, 3.6F) * 0.68F + inertia;
         stack.pushPose();
         safeStrand(stack, entity, -4.5F, split, (z0 + z1) * 0.5F, sideA, false, z0, z1);
         hairQuad(
            out,
            stack.last(),
            light,
            -4.5F,
            split,
            z1,
            -4.5F,
            split,
            z0,
            -4.5F,
            0.5F,
            z0,
            -4.5F,
            0.5F,
            z1,
            mix(32.0F, 40.0F, t0),
            splitTextureV,
            mix(32.0F, 40.0F, t1),
            16.0F,
            -1.0F,
            0.0F,
            0.0F
         );
         stack.popPose();
         float rightA = strandAngle(data, phase, speed, i, 5.2F) * 0.68F - inertia;
         stack.pushPose();
         safeStrand(stack, entity, 4.5F, split, (z0 + z1) * 0.5F, -rightA, false, z0, z1);
         hairQuad(
            out,
            stack.last(),
            light,
            4.5F,
            split,
            z0,
            4.5F,
            split,
            z1,
            4.5F,
            0.5F,
            z1,
            4.5F,
            0.5F,
            z0,
            mix(48.0F, 56.0F, t0),
            splitTextureV,
            mix(48.0F, 56.0F, t1),
            16.0F,
            1.0F,
            0.0F,
            0.0F
         );
         stack.popPose();
      }
   }

   private static void safeStrand(PoseStack stack, HeadFrame frame, float x, float y, float z, float angle, boolean front, float lo, float hi) {
      float gain = 1.0F;
      if (!strandClear(frame, x, y, z, angle, front, lo, hi, gain)) {
         float low = 0.0F;
         float high = 1.0F;

         for (int i = 0; i < 14; i++) {
            float middle = (low + high) * 0.5F;
            if (strandClear(frame, x, y, z, angle, front, lo, hi, middle)) {
               low = middle;
            } else {
               high = middle;
            }
         }

         gain = low;
      }

      stack.translate(x / 16.0F, y / 16.0F, z / 16.0F);
      stack.mulPose(strandRotation(frame, angle, front, gain));
      stack.translate(-x / 16.0F, -y / 16.0F, -z / 16.0F);
   }

   private static Quaternionf strandRotation(HeadFrame frame, float angle, boolean front, float gain) {
      return new Quaternionf()
         .rotationXYZ((float)Math.toRadians((double)(frame.hairPitch * gain)), 0.0F, (float)Math.toRadians((double)(frame.hairRoll * gain)))
         .mul(new Quaternionf().rotationAxis((float)Math.toRadians((double)(angle * gain)), front ? 1.0F : 0.0F, 0.0F, front ? 0.0F : 1.0F));
   }

   private static boolean strandClear(HeadFrame frame, float x, float y, float z, float angle, boolean front, float lo, float hi, float gain) {
      Quaternionf q = strandRotation(frame, angle, front, gain);
      float sign = Math.signum(front ? z : x);

      for (float along : new float[]{lo, hi}) {
         for (float height : new float[]{y, 0.5F}) {
            Vector3f v = new Vector3f(front ? along : x, height, front ? z : along).sub(x, y, z).rotate(q).add(x, y, z);
            if (!Float.isFinite(v.x) || sign * (front ? v.z : v.x) < 4.08F) {
               return false;
            }
         }
      }

      return true;
   }

   private static float strandAngle(HeadDynamicsData data, float phase, float speed, int index, float phaseOffset) {
      float strandPhase = phase + phaseOffset + (float)index * 0.73F;
      float idle = (float)Math.sin((double)strandPhase) * data.hairMotion * 0.18F;
      float moving = (float)Math.sin((double)(strandPhase * 1.73F)) * speed * data.hairMotion * 0.45F;
      return data.hairAngle + idle + moving;
   }

   private static void rotateAt(PoseStack stack, float x, float y, float z, float degrees, float ax, float ay, float az) {
      stack.translate(x / 16.0F, y / 16.0F, z / 16.0F);
      stack.mulPose(new Quaternionf(new AxisAngle4f((float)Math.toRadians((double)degrees), ax, ay, az)));
      stack.translate(-x / 16.0F, -y / 16.0F, -z / 16.0F);
   }

   private static float mix(float a, float b, float t) {
      return a + (b - a) * t;
   }

   private static void renderEyelidRig(PoseStack stack, VertexConsumer out, int light, HeadFrame entity, HeadDynamicsData data, float partialTick) {
      float closed = entity.blink;
      if (!data.blinkEnabled) {
         closed = 0.0F;
      }

      float top = -8.0F + data.eyeY;
      float textureV = Math.max(8.0F, 8.0F + data.eyeY - data.eyeHeight);

      for (int sign : new int[]{-1, 1}) {
         float wink = sign > 0 ? entity.winkLeft : entity.winkRight;
         HeadFrame.Face facial = entity.face;
         float amount = Math.max(facial == null ? closed : (sign > 0 ? facial.left : facial.right), wink);
         if (!(amount <= 0.001F) || entity.lidSlope != 0.0F || entity.lidCurve != 0.0F || entity.lowerLid != 0.0F) {
            float center = (float)sign * data.eyeSpacing;
            float x0 = center - data.eyeWidth * 0.5F;
            float x1 = center + data.eyeWidth * 0.5F;
            float textureU = 12.0F + x0;
            int strips = entity.lidSlope == 0.0F && entity.lidCurve == 0.0F && entity.lowerLid == 0.0F ? 1 : 8;

            for (int i = 0; i < strips; i++) {
               float u = -1.0F + 2.0F * (float)i / (float)strips;
               float v = -1.0F + 2.0F * (float)(i + 1) / (float)strips;
               float a = center + u * data.eyeWidth / 2.0F;
               float b = center + v * data.eyeWidth / 2.0F;
               float ua = EyeLids.upper(amount, entity.lidSlope, entity.lidCurve, sign, u);
               float ub = EyeLids.upper(amount, entity.lidSlope, entity.lidCurve, sign, v);
               float la = Math.max(ua, EyeLids.lower(amount, entity.lowerLid, u));
               float lb = Math.max(ub, EyeLids.lower(amount, entity.lowerLid, v));
               if (ua + ub > 1.0E-4F) {
                  quad(
                     out,
                     stack.last(),
                     light,
                     a,
                     top,
                     -4.1F,
                     b,
                     top,
                     -4.1F,
                     b,
                     top + data.eyeHeight * ub,
                     -4.1F,
                     a,
                     top + data.eyeHeight * ua,
                     -4.1F,
                     data.eyelidU,
                     data.eyelidV,
                     data.eyelidU + 0.5F,
                     data.eyelidV + 0.5F,
                     0.0F,
                     0.0F,
                     -1.0F
                  );
               }

               if (la + lb < 1.9999F) {
                  quad(
                     out,
                     stack.last(),
                     light,
                     a,
                     top + data.eyeHeight * la,
                     -4.1F,
                     b,
                     top + data.eyeHeight * lb,
                     -4.1F,
                     b,
                     top + data.eyeHeight,
                     -4.1F,
                     a,
                     top + data.eyeHeight,
                     -4.1F,
                     data.eyelidU,
                     data.eyelidV,
                     data.eyelidU + 0.5F,
                     data.eyelidV + 0.5F,
                     0.0F,
                     0.0F,
                     -1.0F
                  );
               }
            }
         }
      }
   }

   private static void renderGaze(PoseStack stack, VertexConsumer out, int light, HeadFrame entity, HeadDynamicsData d, float partial) {
      renderGazePass(stack, out, light, entity, d, partial, false, 1.0F);
   }

   public static void renderPupilGlow(PoseStack stack, VertexConsumer out, HeadFrame entity, HeadDynamicsData d, float partial, float alpha) {
      if (alpha > 0.001F) {
         renderGazePass(stack, out, 15728880, entity, d, partial, true, alpha);
      }
   }

   public static void renderScenePupilGlow(PoseStack stack, VertexConsumer out, HeadFrame entity, HeadDynamicsData d, float partial, float alpha) {
      if (alpha > 0.001F) {
         renderGazePass(stack, out, 15728880, entity, d, partial, true, alpha, true);
      }
   }

   private static void renderGazePass(
      PoseStack stack, VertexConsumer out, int light, HeadFrame entity, HeadDynamicsData d, float partial, boolean glow, float alpha
   ) {
      renderGazePass(stack, out, light, entity, d, partial, glow, alpha, false);
   }

   private static void renderGazePass(
      PoseStack stack, VertexConsumer out, int light, HeadFrame entity, HeadDynamicsData d, float partial, boolean glow, float alpha, boolean sceneLight
   ) {
      if (d.gazeEnabled || sceneLight) {
         float uw = Math.min(d.eyeWidth, d.pupilWidth);
         float uh = Math.min(d.eyeHeight, d.pupilHeight);
         float dilation = Mth.clamp(entity.pupilScale, 1.0F, 1.4F);
         float pw = Math.min(d.eyeWidth, uw * dilation);
         float ph = Math.min(d.eyeHeight, uh * dilation);
         float dx = Mth.clamp(entity.gazeX, -(d.eyeWidth - pw) / 2.0F, (d.eyeWidth - pw) / 2.0F);
         float dy = Mth.clamp(entity.gazeY, -(d.eyeHeight - ph) / 2.0F, (d.eyeHeight - ph) / 2.0F);
         if (d.portraitEyes) {
            dx = 0.0F;
            dy = 0.0F;
         }

         HeadFrame.Face facial = entity.face;
         if (facial != null) {
            dx = Mth.clamp(facial.x, -1.0F, 1.0F) * (d.eyeWidth - pw) / 2.0F;
            dy = Mth.clamp(facial.y, -1.0F, 1.0F) * (d.eyeHeight - ph) / 2.0F;
         }

         for (int sign : new int[]{-1, 1}) {
            float center = (float)sign * d.eyeSpacing;
            float top = -8.0F + d.eyeY;
            float x0 = center - d.eyeWidth / 2.0F;
            float x1 = center + d.eyeWidth / 2.0F;
            if (!glow) {
               quad(
                  out,
                  stack.last(),
                  light,
                  x0,
                  top,
                  -4.04F,
                  x1,
                  top,
                  -4.04F,
                  x1,
                  top + d.eyeHeight,
                  -4.04F,
                  x0,
                  top + d.eyeHeight,
                  -4.04F,
                  d.eyeWhiteU,
                  d.eyeWhiteV,
                  d.eyeWhiteU + 0.5F,
                  d.eyeWhiteV + 0.5F,
                  0.0F,
                  0.0F,
                  -1.0F
               );
            }

            float px = center - pw / 2.0F + dx;
            float py = top + (d.eyeHeight - ph) / 2.0F + dy;
            float u = 12.0F + center - uw / 2.0F;
            float v = 8.0F + d.eyeY + (d.eyeHeight - uh) / 2.0F;
            float selectedU = sign < 0 ? d.pupilRightU : d.pupilLeftU;
            float selectedV = sign < 0 ? d.pupilRightV : d.pupilLeftV;
            if (selectedU >= 0.0F && selectedV >= 0.0F) {
               u = selectedU;
               v = selectedV;
            }

            float sampleW = pw / dilation;
            float sampleH = ph / dilation;
            u += (uw - sampleW) / 2.0F;
            v += (uh - sampleH) / 2.0F;
            if (glow) {
               float closed = Math.max(
                  sign > 0 ? entity.winkLeft : entity.winkRight,
                  facial != null ? (sign > 0 ? facial.left : facial.right) : (d.blinkEnabled ? entity.blink : 0.0F)
               );
               int color = Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F) << 24 | 16777215;
               int strips = entity.lidSlope == 0.0F && entity.lidCurve == 0.0F && entity.lowerLid == 0.0F ? 1 : 8;

               for (int i = 0; i < strips; i++) {
                  float a = Math.max(px, x0 + d.eyeWidth * (float)i / (float)strips);
                  float b = Math.min(px + pw, x0 + d.eyeWidth * (float)(i + 1) / (float)strips);
                  if (!(b <= a)) {
                     float ta = visibleLidY(a, center, top, closed, sign, entity, d, false);
                     float tb = visibleLidY(b, center, top, closed, sign, entity, d, false);
                     float ba = visibleLidY(a, center, top, closed, sign, entity, d, true);
                     float bb = visibleLidY(b, center, top, closed, sign, entity, d, true);
                     float start = Math.max(clipStart(ta - py - ph, tb - py - ph), Math.max(clipStart(py - ba, py - bb), clipStart(ta - ba, tb - bb)));
                     float end = Math.min(clipEnd(ta - py - ph, tb - py - ph), Math.min(clipEnd(py - ba, py - bb), clipEnd(ta - ba, tb - bb)));
                     if (!(end <= start)) {
                        float var52 = mix(a, b, start);
                        b = mix(a, b, end);
                        float var54 = mix(ta, tb, start);
                        tb = mix(ta, tb, end);
                        float var58 = mix(ba, bb, start);
                        bb = mix(ba, bb, end);
                        ta = Mth.clamp(var54, py, py + ph);
                        tb = Mth.clamp(tb, py, py + ph);
                        ba = Math.max(ta, Math.min(py + ph, var58));
                        bb = Math.max(tb, Math.min(py + ph, bb));
                        if (!(ba - ta + bb - tb < 1.0E-5F)) {
                           float au = u + sampleW * (var52 - px) / pw;
                           float bu = u + sampleW * (b - px) / pw;
                           vertex(out, stack.last(), light, var52, ta, -4.065F, au, v + (ta - py) / ph * sampleH, 0.0F, 0.0F, -1.0F, color);
                           vertex(out, stack.last(), light, b, tb, -4.065F, bu, v + (tb - py) / ph * sampleH, 0.0F, 0.0F, -1.0F, color);
                           vertex(out, stack.last(), light, b, bb, -4.065F, bu, v + (bb - py) / ph * sampleH, 0.0F, 0.0F, -1.0F, color);
                           vertex(out, stack.last(), light, var52, ba, -4.065F, au, v + (ba - py) / ph * sampleH, 0.0F, 0.0F, -1.0F, color);
                        }
                     }
                  }
               }
            } else {
               quad(
                  out,
                  stack.last(),
                  light,
                  px,
                  py,
                  -4.06F,
                  px + pw,
                  py,
                  -4.06F,
                  px + pw,
                  py + ph,
                  -4.06F,
                  px,
                  py + ph,
                  -4.06F,
                  u,
                  v,
                  u + sampleW,
                  v + sampleH,
                  0.0F,
                  0.0F,
                  -1.0F
               );
            }
         }
      }
   }

   private static void shellSides(VertexConsumer b, Pose p, int light, float y0, float y1, float v0, float v1) {
      if (!(y1 - y0 < 0.001F)) {
         hairQuad(b, p, light, -4.5F, y0, -4.5F, 4.5F, y0, -4.5F, 4.5F, y1, -4.5F, -4.5F, y1, -4.5F, 40.0F, v0, 48.0F, v1, 0.0F, 0.0F, -1.0F);
         hairQuad(b, p, light, 4.5F, y0, 4.5F, -4.5F, y0, 4.5F, -4.5F, y1, 4.5F, 4.5F, y1, 4.5F, 56.0F, v0, 64.0F, v1, 0.0F, 0.0F, 1.0F);
         hairQuad(b, p, light, -4.5F, y0, 4.5F, -4.5F, y0, -4.5F, -4.5F, y1, -4.5F, -4.5F, y1, 4.5F, 32.0F, v0, 40.0F, v1, -1.0F, 0.0F, 0.0F);
         hairQuad(b, p, light, 4.5F, y0, -4.5F, 4.5F, y0, 4.5F, 4.5F, y1, 4.5F, 4.5F, y1, -4.5F, 48.0F, v0, 56.0F, v1, 1.0F, 0.0F, 0.0F);
      }
   }

   private static void topFace(VertexConsumer b, Pose p, int light) {
      hairQuad(b, p, light, -4.5F, -8.5F, 4.5F, 4.5F, -8.5F, 4.5F, 4.5F, -8.5F, -4.5F, -4.5F, -8.5F, -4.5F, 40.0F, 0.0F, 48.0F, 8.0F, 0.0F, -1.0F, 0.0F);
   }

   private static void bottomFace(VertexConsumer b, Pose p, int light) {
      hairQuad(b, p, light, -4.5F, 0.5F, -4.5F, 4.5F, 0.5F, -4.5F, 4.5F, 0.5F, 4.5F, -4.5F, 0.5F, 4.5F, 48.0F, 0.0F, 56.0F, 8.0F, 0.0F, 1.0F, 0.0F);
   }

   private static void renderEars(PoseStack stack, VertexConsumer out, int light, HeadFrame entity, HeadDynamicsData data, float partialTick) {
      float flutter = (float)Math.sin((double)(((float)entity.tickCount + partialTick + (float)entity.id * 5.0F) * 0.16F)) * data.earMotion;

      for (int sign : new int[]{-1, 1}) {
         float angle = (float)Math.toRadians((double)(data.earAngle + flutter * (float)sign * 0.35F));
         float rootX = (float)sign * 4.05F;
         float tipX = (float)sign * (4.05F + (float)Math.cos((double)angle) * data.earLength);
         float rootY = -5.2F;
         float tipY = rootY - (float)Math.sin((double)angle) * data.earLength;
         float u = sign < 0 ? 32.0F : 48.0F;
         triangle(out, stack.last(), light, rootX, rootY - 0.65F, -0.7F, rootX, rootY + 0.65F, -0.7F, tipX, tipY, -0.15F, u, 10.0F);
         triangle(out, stack.last(), light, rootX, rootY + 0.65F, 0.7F, rootX, rootY - 0.65F, 0.7F, tipX, tipY, 0.15F, u, 10.0F);
      }
   }

   private static void triangle(
      VertexConsumer b, Pose p, int light, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float u, float v
   ) {
      Vector3f n = new Vector3f(bx - ax, by - ay, bz - az).cross(cx - ax, cy - ay, cz - az).normalize();
      vertex(b, p, light, ax, ay, az, u, v, n.x, n.y, n.z);
      vertex(b, p, light, bx, by, bz, u, v + 1.0F, n.x, n.y, n.z);
      vertex(b, p, light, cx, cy, cz, u + 2.0F, v + 0.5F, n.x, n.y, n.z);
      vertex(b, p, light, cx, cy, cz, u + 2.0F, v + 0.5F, n.x, n.y, n.z);
   }

   private static void hairQuad(
      VertexConsumer b,
      Pose p,
      int light,
      float ax,
      float ay,
      float az,
      float bx,
      float by,
      float bz,
      float cx,
      float cy,
      float cz,
      float dx,
      float dy,
      float dz,
      float u0,
      float v0,
      float u1,
      float v1,
      float nx,
      float ny,
      float nz
   ) {
      vertex(b, p, light, ax, ay, az, u0, v0, nx, ny, nz);
      vertex(b, p, light, dx, dy, dz, u0, v1, nx, ny, nz);
      vertex(b, p, light, cx, cy, cz, u1, v1, nx, ny, nz);
      vertex(b, p, light, bx, by, bz, u1, v0, nx, ny, nz);
      vertex(b, p, light, ax, ay, az, u0, v0, -nx, -ny, -nz);
      vertex(b, p, light, bx, by, bz, u1, v0, -nx, -ny, -nz);
      vertex(b, p, light, cx, cy, cz, u1, v1, -nx, -ny, -nz);
      vertex(b, p, light, dx, dy, dz, u0, v1, -nx, -ny, -nz);
   }

   private static void quad(
      VertexConsumer b,
      Pose p,
      int light,
      float ax,
      float ay,
      float az,
      float bx,
      float by,
      float bz,
      float cx,
      float cy,
      float cz,
      float dx,
      float dy,
      float dz,
      float u0,
      float v0,
      float u1,
      float v1,
      float nx,
      float ny,
      float nz
   ) {
      vertex(b, p, light, ax, ay, az, u0, v0, nx, ny, nz);
      vertex(b, p, light, bx, by, bz, u1, v0, nx, ny, nz);
      vertex(b, p, light, cx, cy, cz, u1, v1, nx, ny, nz);
      vertex(b, p, light, dx, dy, dz, u0, v1, nx, ny, nz);
   }

   private static void vertex(VertexConsumer b, Pose pose, int light, float x, float y, float z, float u, float v, float nx, float ny, float nz) {
      vertex(b, pose, light, x, y, z, u, v, nx, ny, nz, -1);
   }

   private static float visibleLidY(float x, float center, float top, float closed, int sign, HeadFrame frame, HeadDynamicsData data, boolean lower) {
      float u = Mth.clamp((x - center) * 2.0F / data.eyeWidth, -1.0F, 1.0F);
      float cell = (u + 1.0F) * 4.0F;
      float a = -1.0F + (float)Math.floor((double)cell) / 4.0F;
      float b = Math.min(1.0F, a + 0.25F);
      float t = cell - (float)Math.floor((double)cell);
      float v0 = lower ? EyeLids.lower(closed, frame.lowerLid, a) : EyeLids.upper(closed, frame.lidSlope, frame.lidCurve, sign, a);
      float v1 = lower ? EyeLids.lower(closed, frame.lowerLid, b) : EyeLids.upper(closed, frame.lidSlope, frame.lidCurve, sign, b);
      return top + data.eyeHeight * (v0 + (v1 - v0) * t);
   }

   private static float clipStart(float a, float b) {
      return a <= 0.0F ? 0.0F : (b >= a ? 1.0F : Math.min(1.0F, a / (a - b)));
   }

   private static float clipEnd(float a, float b) {
      return b <= 0.0F ? 1.0F : (a >= b ? 0.0F : Math.max(0.0F, a / (a - b)));
   }

   private static void vertex(VertexConsumer b, Pose pose, int light, float x, float y, float z, float u, float v, float nx, float ny, float nz, int color) {
      Matrix4f m = pose.pose();
      Vector3f normal = pose.normal().transform(nx, ny, nz, new Vector3f());
      float sx = x / 16.0F;
      float sy = y / 16.0F;
      float sz = z / 16.0F;
      float px = m.m00() * sx + m.m10() * sy + m.m20() * sz + m.m30();
      float py = m.m01() * sx + m.m11() * sy + m.m21() * sz + m.m31();
      float pz = m.m02() * sx + m.m12() * sy + m.m22() * sz + m.m32();
      Vtx.vertex(b, px, py, pz, color, u / 64.0F, v / 64.0F, OverlayTexture.NO_OVERLAY, light, normal.x(), normal.y(), normal.z());
   }
}
