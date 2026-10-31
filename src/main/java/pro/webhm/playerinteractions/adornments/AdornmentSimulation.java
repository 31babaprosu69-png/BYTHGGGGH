package pro.webhm.playerinteractions.adornments;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class AdornmentSimulation {
   public static final float STEP = 0.008333334F;
   public static final float SOCKET_INSET = 0.75F;
   private static final Vector3f DOWN = new Vector3f(0.0F, 1.0F, 0.0F);
   public final AdornmentProject.Rig rig;
   public final Vector3f[] points;
   public final Vector3f[] velocity;
   public final Quaternionf[] rotation;
   public final boolean[] blocked;
   private final Vector3f[] predicted;
   private final Vector3f[] old;
   private final Quaternionf[] rest;
   private final Vector3f root = new Vector3f();
   private final Vector3f previousRoot = new Vector3f();
   private final Vector3f target = new Vector3f();
   private final Vector3f aim = new Vector3f();
   private final Vector3f local = new Vector3f();
   private final Vector3f candidate = new Vector3f();
   private final Vector3f endpoint = new Vector3f();
   private final Vector3f best = new Vector3f();
   private final Vector3f gripError = new Vector3f();
   private final Vector3f searchDesired = new Vector3f();
   private final Quaternionf parent = new Quaternionf();
   private final Quaternionf basis = new Quaternionf();
   private final Quaternionf turn = new Quaternionf();
   private final Quaternionf rootRotation = new Quaternionf();
   private final Matrix4f anchor = new Matrix4f();
   private final Quaternionf rootRest;
   private final Vector3f gripGoal = new Vector3f();
   private final Vector3f fabrikDirection = new Vector3f();
   private final Vector3f filteredTarget = new Vector3f();
   private boolean targetReady;
   private final Vector3f sweepA = new Vector3f();
   private final Vector3f sweepB = new Vector3f();
   private final Vector3f planeLocal = new Vector3f();
   private final Vector3f allowedDirection = new Vector3f();
   private final AdornmentFrame lastFrame = new AdornmentFrame();
   private final AdornmentFrame stepFrame = new AdornmentFrame();
   private final Quaternionf previousRotation = new Quaternionf();
   private boolean initialized;
   private boolean sweepEnabled;
   private int gripIndex = -1;
   private int supportBone = -1;
   private int supportLink = -1;
   private float scale = 1.0F;
   private int quietSteps;
   private final boolean linked;
   private int jointIndex;
   private float visualTwist;
   private final Vector3f outwardHint = new Vector3f();
   private final Vector3f oldDirection = new Vector3f();
   private final Vector3f pivotVelocity = new Vector3f();
   private final Vector3f relativeVelocity = new Vector3f();
   private final AdornmentContact contact = new AdornmentContact();
   private final AdornmentContact socketContact = new AdornmentContact();
   private final AdornmentContact nextContact = new AdornmentContact();
   private final Quaternionf contactRotation = new Quaternionf();
   private final Quaternionf contactTurn = new Quaternionf();
   private final Quaternionf nextRotation = new Quaternionf();
   private final Quaternionf contactPhysics = new Quaternionf();
   private final Vector3f contactLocal = new Vector3f();
   private final Vector3f nextDirection = new Vector3f();
   private final Vector3f contactCorrection = new Vector3f();
   private final Vector3f tangentA = new Vector3f();
   private final Vector3f tangentB = new Vector3f();
   public boolean sleeping;
   public float tension;

   public void supportGrip(int bone, int link) {
      this.supportBone = bone;
      this.supportLink = link;
   }

   public AdornmentSimulation(AdornmentProject.Rig rig) {
      this.rig = rig;
      this.linked = rig.linked();
      this.rootRest = angles(rig.pitch, rig.yaw, rig.roll);
      int n = rig.links.size();
      this.points = vecs(n + 1);
      this.velocity = vecs(n + 1);
      this.old = vecs(n + 1);
      this.predicted = vecs(n + 1);
      this.rotation = new Quaternionf[n];
      this.rest = new Quaternionf[n];
      this.blocked = new boolean[n];

      for (int i = 0; i < n; i++) {
         this.rotation[i] = new Quaternionf();
         AdornmentProject.Link l = rig.links.get(i);
         this.rest[i] = this.linked ? new Quaternionf() : angles(l.pitch, l.yaw, l.roll);
      }
   }

   private static Vector3f[] vecs(int n) {
      Vector3f[] a = new Vector3f[n];

      for (int i = 0; i < n; i++) {
         a[i] = new Vector3f();
      }

      return a;
   }

   public static Quaternionf angles(float x, float y, float z) {
      return new Quaternionf().rotationZYX((float)Math.toRadians((double)z), (float)Math.toRadians((double)y), (float)Math.toRadians((double)x));
   }

   public void reset() {
      this.initialized = false;
      this.targetReady = false;
      this.quietSteps = 0;
      this.sleeping = false;
   }

   public void grip(int index, Vector3fc point) {
      int next = index >= 0 && index < this.rotation.length && point.isFinite() ? index : -1;
      if (next != this.gripIndex) {
         this.targetReady = false;
      }

      this.gripIndex = next;
      if (next >= 0) {
         this.target.set(point);
      }

      this.sleeping = false;
   }

   public void release() {
      if (this.gripIndex >= 0) {
         for (Vector3f v : this.velocity) {
            float speed = v.length();
            if (speed > 48.0F * this.scale) {
               v.mul(48.0F * this.scale / speed);
            }
         }

         this.sleeping = false;
      }

      this.gripIndex = -1;
      this.targetReady = false;
      this.tension = 0.0F;
   }

   public Vector3f gripPoint(int index, Vector3f out) {
      AdornmentProject.Link l = this.rig.links.get(index);
      return this.rotation[index].transform(out.set(l.gripX, l.length * l.gripAt, l.gripZ).mul(this.scale)).add(this.points[index]);
   }

   public void rebase(float x, float y, float z) {
      if (Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z) && !(x * x + y * y + z * z > 4096.0F)) {
         for (Vector3f p : this.points) {
            p.add(x, y, z);
         }

         this.previousRoot.add(x, y, z);
         if (this.targetReady) {
            this.filteredTarget.add(x, y, z);
         }

         if (this.initialized) {
            this.lastFrame.translate(x, y, z);
         }
      } else {
         this.reset();
      }
   }

   private int boneIndex() {
      for (int i = 0; i < AdornmentProject.BONES.length; i++) {
         if (AdornmentProject.BONES[i].equals(this.rig.bone)) {
            return i;
         }
      }

      return 0;
   }

   private void anchor(AdornmentFrame frame) {
      this.scale = frame.scale;
      this.anchor.set(frame.bones[this.boneIndex()]).translate(this.rig.x, this.rig.y, this.rig.z);
      this.anchor.getTranslation(this.root);
      this.anchor.getUnnormalizedRotation(this.rootRotation).normalize();
      if (this.linked) {
         frame.attachment(this.rig, this.root);
      }

      if (this.linked) {
         frame.bones[this.boneIndex()].getTranslation(this.outwardHint);
         this.outwardHint.set(this.root.x - this.outwardHint.x, this.root.y - this.outwardHint.y, this.root.z - this.outwardHint.z);
         if (this.outwardHint.lengthSquared() > 1.0E-8F) {
            this.outwardHint.normalize();
         }
      }

      this.rootRotation.mul(this.rootRest);
   }

   public void update(AdornmentFrame frame, float seconds) {
      AdornmentFrame supplied = frame;
      boolean interpolate = this.initialized;
      this.anchor(frame);
      if (this.root.isFinite() && Float.isFinite(this.scale) && !(this.scale <= 0.0F)) {
         if (!this.initialized || this.previousRoot.distanceSquared(this.root) > 1024.0F * this.scale * this.scale) {
            interpolate = false;
            this.points[0].set(this.root);
            this.parent.set(this.rootRotation);
            this.visualTwist = 0.0F;

            for (int i = 0; i < this.rotation.length; i++) {
               this.rotation[i].set(this.parent).mul(this.rest[i]);
               this.rotation[i].transform(DOWN, this.aim).mul(this.rig.links.get(i).length * this.scale);
               this.points[i + 1].set(this.points[i]).add(this.aim);
               this.velocity[i + 1].zero();
               this.parent.set(this.rotation[i]);
               if (this.linked) {
                  this.visualTwist = this.visualTwist + this.rig.links.get(i).yaw;
                  this.rotation[i].rotateY((float)Math.toRadians((double)this.visualTwist));
               }
            }

            this.initialized = true;
            this.previousRoot.set(this.root);
            this.sleeping = false;
         }

         this.sweepEnabled = interpolate;
         float dt = Math.max(0.0F, Math.min(0.1F, Float.isFinite(seconds) ? seconds : 0.0F));
         int steps = Math.max(1, (int)Math.ceil((double)(dt / 0.008333334F)));
         float h = dt / (float)steps;
         boolean moving = this.root.distanceSquared(this.previousRoot) > 1.0E-6F
            || Math.abs(this.rootRotation.dot(this.previousRotation)) < 0.999999F
            || !frame.samePose(this.lastFrame);
         if (!interpolate || dt != 0.0F || moving) {
            if (!this.sleeping || moving || this.gripIndex >= 0) {
               for (int step = 0; step < steps; step++) {
                  float fraction = ((float)step + 1.0F) / (float)steps;

                  for (int ix = 0; ix < this.points.length; ix++) {
                     this.old[ix].set(this.points[ix]);
                     this.predicted[ix].set(this.points[ix]);
                  }

                  if (interpolate && moving && step < steps - 1) {
                     this.stepFrame.interpolate(this.lastFrame, supplied, fraction);
                     frame = this.stepFrame;
                  } else {
                     frame = supplied;
                  }

                  this.anchor(frame);
                  this.points[0].set(this.root);
                  this.predicted[0].set(this.root);
                  if (h > 0.0F) {
                     this.velocity[0].set(this.root).sub(this.old[0]).div(h);
                  } else {
                     this.velocity[0].zero();
                  }

                  for (int ix = 1; ix < this.points.length; ix++) {
                     if (h > 0.0F) {
                        this.velocity[ix].y = this.velocity[ix].y - 156.8F * this.rig.gravity * this.scale * h;
                        this.velocity[ix].mul((float)Math.exp((double)(-(this.rig.damping + (float)(this.linked ? 6 : 0)) * h)));
                        this.predicted[ix].fma(h, this.velocity[ix]);
                     }
                  }

                  if (this.gripIndex >= 0) {
                     this.gripPoint(this.gripIndex, this.endpoint);
                     this.gripError.set(this.target).sub(this.endpoint);
                     this.tension = Math.min(1.0F, this.gripError.length() / Math.max(this.scale, this.rig.length() * this.scale * 0.25F));
                     if (!this.targetReady) {
                        this.filteredTarget.set(this.endpoint);
                        this.targetReady = true;
                     }

                     this.gripError.set(this.target).sub(this.filteredTarget);
                     float travel = this.gripError.length();
                     if (travel > 0.0F && h > 0.0F) {
                        this.filteredTarget.fma(Math.min(1.0F, 240.0F * this.scale * h / travel), this.gripError);
                     }

                     AdornmentProject.Link held = this.rig.links.get(this.gripIndex);
                     this.rotation[this.gripIndex].transform(this.gripGoal.set(-held.gripX, held.length * (1.0F - held.gripAt), -held.gripZ).mul(this.scale));
                     this.gripGoal.add(this.filteredTarget);
                     if (this.rig.plane != 0) {
                        this.planeLocal.set(this.gripGoal).sub(this.root);
                        this.rootRotation.transformInverse(this.planeLocal);
                        if (this.rig.plane == 1) {
                           this.planeLocal.x = 0.0F;
                        } else {
                           this.planeLocal.z = 0.0F;
                        }

                        this.rootRotation.transform(this.planeLocal, this.gripGoal).add(this.root);
                     }

                     for (int iteration = 0; iteration < 6; iteration++) {
                        this.predicted[this.gripIndex + 1].set(this.gripGoal);

                        for (int ixx = this.gripIndex; ixx >= 0; ixx--) {
                           this.fabrikDirection.set(this.predicted[ixx]).sub(this.predicted[ixx + 1]);
                           if (this.fabrikDirection.lengthSquared() < 1.0E-8F) {
                              this.fabrikDirection.set(0.0F, 1.0F, 0.0F);
                           } else {
                              this.fabrikDirection.normalize();
                           }

                           this.predicted[ixx].set(this.predicted[ixx + 1]).fma(this.rig.links.get(ixx).length * this.scale, this.fabrikDirection);
                        }

                        this.predicted[0].set(this.points[0]);

                        for (int ixx = 0; ixx <= this.gripIndex; ixx++) {
                           this.fabrikDirection.set(this.predicted[ixx + 1]).sub(this.predicted[ixx]);
                           if (this.fabrikDirection.lengthSquared() < 1.0E-8F) {
                              this.fabrikDirection.set(0.0F, -1.0F, 0.0F);
                           } else {
                              this.fabrikDirection.normalize();
                           }

                           this.predicted[ixx + 1].set(this.predicted[ixx]).fma(this.rig.links.get(ixx).length * this.scale, this.fabrikDirection);
                        }
                     }
                  } else {
                     this.tension = 0.0F;
                     if (this.linked && !this.rig.limitBends && h > 0.0F) {
                        for (int pass = 0; pass < 8; pass++) {
                           for (int ixx = this.rotation.length - 1; ixx >= 0; ixx--) {
                              this.relaxLength(ixx);
                           }

                           for (int ixx = 0; ixx < this.rotation.length; ixx++) {
                              this.relaxLength(ixx);
                           }

                           this.relaxContacts(frame);
                        }
                     }
                  }

                  this.parent.set(this.rootRotation);
                  float maxSpeed = 0.0F;
                  float maxMotion = 0.0F;
                  this.visualTwist = 0.0F;

                  for (int ixx = 0; ixx < this.rotation.length; ixx++) {
                     this.jointIndex = ixx;
                     AdornmentProject.Link link = this.rig.links.get(ixx);
                     this.basis.set(this.parent).mul(this.rest[ixx]);
                     this.aim.set(this.predicted[ixx + 1]).sub(this.points[ixx]);
                     if (this.aim.lengthSquared() < 1.0E-10F) {
                        this.basis.transform(DOWN, this.aim);
                     } else {
                        this.aim.normalize();
                     }

                     this.basis.transform(DOWN, this.candidate);
                     if (!this.linked || this.rig.limitBends) {
                        this.aim.lerp(this.candidate, Math.min(1.0F, link.stiffness * h)).normalize();
                     }

                     this.constrain(link, this.aim, this.candidate);
                     float length = link.length * this.scale;
                     this.blocked[ixx] = !this.choose(frame, ixx, link, length, this.candidate);
                     this.points[ixx + 1].set(this.points[ixx]).fma(length, this.best);
                     this.basis.transformInverse(this.best, this.local);
                     this.turn.rotationTo(DOWN, this.local);
                     this.rotation[ixx].set(this.basis).mul(this.turn).normalize();
                     this.parent.set(this.rotation[ixx]);
                     if (this.linked) {
                        this.visualTwist = this.visualTwist + link.yaw;
                        this.rotation[ixx].rotateY((float)Math.toRadians((double)this.visualTwist));
                     }

                     if (h > 0.0F) {
                        float allowedSpeed = this.velocity[ixx + 1].length() + this.old[ixx].distance(this.points[ixx]) / h;
                        if (this.linked) {
                           this.pivotVelocity.set(this.velocity[ixx]);
                           this.relativeVelocity.set(this.velocity[ixx + 1]).sub(this.pivotVelocity);
                           this.relativeVelocity.fma(-this.relativeVelocity.dot(this.best), this.best);
                           this.velocity[ixx + 1].set(this.pivotVelocity).add(this.relativeVelocity);
                           this.gripError.set(this.points[ixx + 1]).sub(this.predicted[ixx + 1]);
                           float correction = this.gripError.lengthSquared();
                           float into = this.velocity[ixx + 1].dot(this.gripError);
                           if (correction > 1.0E-8F && into < 0.0F) {
                              this.velocity[ixx + 1].fma(-into / correction, this.gripError);
                           }
                        } else {
                           this.velocity[ixx + 1].set(this.points[ixx + 1]).sub(this.old[ixx + 1]).div(h);
                        }

                        if (this.gripIndex >= 0) {
                           this.velocity[ixx + 1].mul(0.3F);
                        }

                        float cap = this.gripIndex >= 0 ? 80.0F * this.scale : Math.min(160.0F * this.scale, allowedSpeed);
                        float speed = this.velocity[ixx + 1].length();
                        if (speed > cap && speed > 0.0F) {
                           this.velocity[ixx + 1].mul(cap / speed);
                        }

                        if (this.blocked[ixx]) {
                           this.velocity[ixx + 1].zero();
                        }

                        maxSpeed = Math.max(maxSpeed, this.velocity[ixx + 1].lengthSquared());
                        maxMotion = Math.max(maxMotion, this.points[ixx + 1].distanceSquared(this.old[ixx + 1]) / (h * h));
                     }
                  }

                  float restSpeed = 0.25F * Math.min(1.0F, this.rig.gravity > 0.0F ? this.rig.gravity : 1.0F);
                  boolean quiet = this.linked && !this.rig.limitBends
                     ? maxMotion < restSpeed * restSpeed * this.scale * this.scale && this.valid()
                     : maxSpeed < 0.02F * this.scale * this.scale;
                  if (!moving && this.gripIndex < 0 && quiet) {
                     this.quietSteps++;
                  } else {
                     this.quietSteps = 0;
                  }
               }

               this.sleeping = this.quietSteps > 120;
               if (this.sleeping) {
                  for (Vector3f v : this.velocity) {
                     v.zero();
                  }
               }

               this.previousRoot.set(this.root);
               this.previousRotation.set(this.rootRotation);
               if (moving || !interpolate) {
                  this.lastFrame.set(supplied);
               }
            }
         }
      } else {
         this.reset();
      }
   }

   private float limit(AdornmentProject.Link l) {
      return this.linked ? (this.jointIndex == 0 ? 170.0F : (this.rig.limitBends ? this.rig.flex : 90.0F)) : l.limit;
   }

   private void relaxLength(int i) {
      this.fabrikDirection.set(this.predicted[i + 1]).sub(this.predicted[i]);
      float length = this.fabrikDirection.length();
      if (!(length < 1.0E-7F)) {
         this.fabrikDirection.mul((length - this.rig.links.get(i).length * this.scale) / length * (i == 0 ? 1.0F : 0.5F));
         if (i > 0) {
            this.predicted[i].add(this.fabrikDirection);
         }

         this.predicted[i + 1].sub(this.fabrikDirection);
      }
   }

   private void relaxContacts(AdornmentFrame frame) {
      this.parent.set(this.rootRotation);
      float twist = 0.0F;

      for (int i = 0; i < this.rotation.length; i++) {
         AdornmentProject.Link link = this.rig.links.get(i);
         float length = link.length * this.scale;
         this.aim.set(this.predicted[i + 1]).sub(this.predicted[i]);
         if (!(this.aim.lengthSquared() < 1.0E-10F)) {
            this.aim.normalize();
            this.parent.transformInverse(this.aim, this.local);
            this.turn.rotationTo(DOWN, this.local);
            this.parent.mul(this.turn).normalize();
            twist += link.yaw;
            this.contactRotation.set(this.parent).rotateY((float)Math.toRadians((double)twist));

            for (int b = 0; b < frame.count; b++) {
               AdornmentFrame.Box box = frame.boxes[b];
               float inset = i == 0 && this.rig.preciseAnchor && box.intersects(this.root, this.root, 0.75F * this.scale) ? 0.75F * this.scale : 0.0F;
               this.contact.set(this.predicted[i], this.contactRotation, link.width * this.scale, length, link.depth * this.scale, inset);
               if (this.contact
                  .separation(box, this.supportBone >= 0 && this.supportLink >= 0 && box.bone == this.supportBone ? 3.0F : box.hi.y, this.contactCorrection)) {
                  if (this.rig.plane != 0) {
                     this.rootRotation.transformInverse(this.contactCorrection);
                     if (this.rig.plane == 1) {
                        this.contactCorrection.x = 0.0F;
                     } else {
                        this.contactCorrection.z = 0.0F;
                     }

                     this.rootRotation.transform(this.contactCorrection);
                  }

                  if (i > 0) {
                     this.stopIntoContact(this.velocity[i]);
                  }

                  this.stopIntoContact(this.velocity[i + 1]);
                  if (i > 0) {
                     this.predicted[i].add(this.contactCorrection);
                     this.predicted[i + 1].add(this.contactCorrection);
                  } else {
                     this.predicted[i + 1].fma(2.0F, this.contactCorrection);
                  }
               }
            }
         }
      }
   }

   private void stopIntoContact(Vector3f v) {
      float size = this.contactCorrection.lengthSquared();
      float into = v.dot(this.contactCorrection);
      if (size > 1.0E-14F && into < 0.0F) {
         v.fma(-into / size, this.contactCorrection);
      }
   }

   private int joint(AdornmentProject.Link l) {
      return this.linked ? 1 : l.joint;
   }

   private void constrain(AdornmentProject.Link l, Vector3fc world, Vector3f out) {
      this.basis.transformInverse(world, this.local);
      float limit = (float)Math.toRadians((double)this.limit(l));
      int joint = this.joint(l);
      if (joint == 0) {
         this.local.set(DOWN);
      } else if (joint == 2) {
         float a = clamp((float)Math.atan2((double)this.local.z, (double)this.local.y), -limit, limit);
         this.local.set(0.0F, (float)Math.cos((double)a), (float)Math.sin((double)a));
      } else if (joint == 3) {
         float a = clamp((float)Math.atan2((double)this.local.x, (double)this.local.y), -limit, limit);
         this.local.set((float)Math.sin((double)a), (float)Math.cos((double)a), 0.0F);
      } else {
         float y = clamp(this.local.y, -1.0F, 1.0F);
         float angle = (float)Math.acos((double)y);
         if (angle > limit) {
            float r = (float)Math.hypot((double)this.local.x, (double)this.local.z);
            if (r < 1.0E-6F) {
               this.local.set((float)Math.sin((double)limit), (float)Math.cos((double)limit), 0.0F);
            } else {
               this.local
                  .set(this.local.x / r * (float)Math.sin((double)limit), (float)Math.cos((double)limit), this.local.z / r * (float)Math.sin((double)limit));
            }
         }
      }

      this.basis.transform(this.local, out).normalize();
   }

   private boolean clear(AdornmentFrame frame, int i, float length, Vector3fc direction, float radius) {
      this.endpoint.set(this.points[i]).fma(length, direction);
      if (this.linked) {
         AdornmentProject.Link link = this.rig.links.get(i);
         this.basis.transformInverse(direction, this.contactLocal);
         this.contactTurn.rotationTo(DOWN, this.contactLocal);
         this.contactPhysics.set(this.basis).mul(this.contactTurn).normalize();
         this.contactRotation.set(this.contactPhysics).rotateY((float)Math.toRadians((double)(this.visualTwist + link.yaw)));
         this.contact.set(this.points[i], this.contactRotation, link.width * this.scale, length, link.depth * this.scale, 0.0F);
         if (i == 0 && this.rig.preciseAnchor) {
            this.socketContact.set(this.points[i], this.contactRotation, link.width * this.scale, length, link.depth * this.scale, 0.75F * this.scale);
         }

         for (int k = 0; k < frame.count; k++) {
            AdornmentFrame.Box box = frame.boxes[k];
            if (this.touches(
               i == 0 && this.rig.preciseAnchor && box.intersects(this.points[0], this.points[0], 0.75F * this.scale) ? this.socketContact : this.contact,
               box,
               i
            )) {
               return false;
            }
         }

         return this.nextJointClear(frame, i);
      } else {
         for (int kx = 0; kx < frame.count; kx++) {
            AdornmentFrame.Box box = frame.boxes[kx];
            if (box.intersects(this.points[i], this.endpoint, radius)) {
               return false;
            }

            if (!this.linked && this.sweepEnabled) {
               float distance = Math.max(this.old[i].distance(this.points[i]), this.old[i + 1].distance(this.endpoint));
               if (!(distance < 0.001F) && !box.intersects(this.old[i], this.old[i + 1], radius)) {
                  int samples = Math.min(32, Math.max(1, (int)Math.ceil((double)(distance / Math.max(0.04F, radius * 0.5F)))));
                  float margin = distance / (float)(2 * samples);
                  if (!box.intersects(this.old[i], this.old[i + 1], radius + margin)) {
                     for (int t = 0; t < samples; t++) {
                        float alpha = ((float)t + 0.5F) / (float)samples;
                        this.sweepA.set(this.old[i]).lerp(this.points[i], alpha);
                        this.sweepB.set(this.old[i + 1]).lerp(this.endpoint, alpha);
                        if (box.intersects(this.sweepA, this.sweepB, radius + margin)) {
                           return false;
                        }
                     }
                  }
               }
            }
         }

         if (this.linked) {
            return true;
         } else {
            for (int kx = 0; kx < i - 2; kx++) {
               float rr = radius + this.rig.links.get(kx).radius() * this.scale;
               if (segmentDistanceSquared(this.points[i], this.endpoint, this.points[kx], this.points[kx + 1]) < rr * rr) {
                  return false;
               }
            }

            return true;
         }
      }
   }

   private boolean touches(AdornmentContact section, AdornmentFrame.Box box, int link) {
      return section.intersects(box, this.supportBone >= 0 && this.supportLink >= 0 && box.bone == this.supportBone ? 3.0F : box.hi.y);
   }

   private boolean nextJointClear(AdornmentFrame frame, int i) {
      if (i + 1 >= this.rig.links.size()) {
         return true;
      } else {
         AdornmentProject.Link next = this.rig.links.get(i + 1);
         float twist = (float)Math.toRadians((double)(this.visualTwist + this.rig.links.get(i).yaw + next.yaw));
         float planeAngle = 0.0F;
         if (this.rig.plane != 0) {
            this.contactPhysics.transform(DOWN, this.nextDirection);
            this.rootRotation.transformInverse(this.nextDirection);
            planeAngle = (float)Math.atan2(this.rig.plane == 1 ? (double)this.nextDirection.z : (double)this.nextDirection.x, (double)this.nextDirection.y);
         }

         for (int sample = 0; sample < (this.rig.plane == 0 ? 49 : 361); sample++) {
            if (this.rig.plane != 0) {
               float a = planeAngle + (float)Math.toRadians((double)((sample + 1) / 2 * (sample % 2 == 1 ? 1 : -1)));
               this.nextDirection
                  .set(
                     this.rig.plane == 2 ? (float)Math.sin((double)a) : 0.0F,
                     (float)Math.cos((double)a),
                     this.rig.plane == 1 ? (float)Math.sin((double)a) : 0.0F
                  );
               this.rootRotation.transform(this.nextDirection);
               this.contactPhysics.transformInverse(this.nextDirection);
               this.contactTurn.rotationTo(DOWN, this.nextDirection);
               this.nextRotation.set(this.contactPhysics).mul(this.contactTurn).normalize();
            } else {
               this.nextRotation.set(this.contactPhysics);
               if (sample > 0) {
                  float phi = (float)((sample - 1) % 8) * (float) Math.PI / 4.0F;
                  float bend = this.rig.limitBends ? this.rig.flex : 35.0F;
                  float theta = (float)Math.toRadians(
                     sample <= 8
                        ? (double)(bend * 0.5F)
                        : (sample <= 16 ? (double)bend : (sample <= 24 ? 42.5 : (sample <= 32 ? 85.0 : (sample <= 40 ? 127.5 : 170.0))))
                  );
                  this.nextRotation.rotateAxis(theta, (float)Math.cos((double)phi), 0.0F, (float)Math.sin((double)phi));
               }
            }

            this.nextRotation.rotateY(twist);
            this.nextContact.set(this.endpoint, this.nextRotation, next.width * this.scale, next.length * this.scale, next.depth * this.scale, 0.0F);
            boolean clear = true;

            for (int b = 0; b < frame.count; b++) {
               if (this.touches(this.nextContact, frame.boxes[b], i + 1)) {
                  clear = false;
                  break;
               }
            }

            if (clear) {
               return true;
            }
         }

         return false;
      }
   }

   private boolean choose(AdornmentFrame f, int i, AdornmentProject.Link l, float length, Vector3fc desired) {
      Vector3fc var18 = this.searchDesired.set(desired);
      this.projectPlane(this.searchDesired);
      this.best.set(var18);
      float radius = l.radius() * this.scale;
      if (this.allowed(l, this.best) && this.clear(f, i, length, this.best, radius)) {
         return true;
      } else if (this.joint(l) == 0) {
         return false;
      } else {
         float score = -Float.MAX_VALUE;
         float limit = (float)Math.toRadians(this.linked && !this.rig.limitBends && i > 0 ? 35.0 : (double)this.limit(l));
         boolean relaxed = false;
         this.oldDirection.set(this.old[i + 1]).sub(this.old[i]);
         if (this.oldDirection.lengthSquared() > 1.0E-8F) {
            this.oldDirection.normalize();
         }

         if (this.linked && this.oldDirection.lengthSquared() > 1.0E-8F) {
            this.candidate.set(this.oldDirection);
            this.projectPlane(this.candidate);
            if (this.clear(f, i, length, this.candidate, radius)) {
               score = this.candidate.dot(var18) + 0.004F * this.candidate.dot(this.outwardHint) + 0.02F * this.candidate.dot(this.oldDirection);
               this.best.set(this.candidate);
               this.basis.transformInverse(this.candidate, this.local);
               relaxed = this.local.y < (float)Math.cos((double)limit) - 1.0E-6F;
            }
         }

         if (this.linked && score == -Float.MAX_VALUE && this.oldDirection.lengthSquared() > 1.0E-8F) {
            this.tangentA
               .set(Math.abs(this.oldDirection.y) < 0.9F ? 0.0F : 1.0F, Math.abs(this.oldDirection.y) < 0.9F ? 1.0F : 0.0F, 0.0F)
               .cross(this.oldDirection)
               .normalize();
            this.oldDirection.cross(this.tangentA, this.tangentB).normalize();

            for (int ring = 0; ring < 6 && score == -Float.MAX_VALUE; ring++) {
               float theta = (float)Math.toRadians((double)(0.25F * (float)(1 << ring)));
               float sin = (float)Math.sin((double)theta);
               float cos = (float)Math.cos((double)theta);

               for (int j = 0; j < 16; j++) {
                  float phi = (float)j * (float) Math.PI / 8.0F;
                  this.candidate
                     .set(this.oldDirection)
                     .mul(cos)
                     .fma(sin * (float)Math.cos((double)phi), this.tangentA)
                     .fma(sin * (float)Math.sin((double)phi), this.tangentB);
                  this.projectPlane(this.candidate);
                  float s = this.candidate.dot(var18) + 0.004F * this.candidate.dot(this.outwardHint) + 0.02F * this.candidate.dot(this.oldDirection);
                  if (s > score && this.clear(f, i, length, this.candidate, radius)) {
                     score = s;
                     this.best.set(this.candidate);
                     relaxed = true;
                  }
               }
            }
         }

         boolean localContact = this.linked && score != -Float.MAX_VALUE && this.best.dot(var18) > 0.95F;
         int rings = this.rig.plane != 0 ? 0 : (this.linked ? (this.rig.preciseAnchor && i == 0 ? 170 : 4) : (l.joint == 1 ? 10 : 1));
         int around = this.rig.plane != 0 ? (this.linked && !this.rig.preciseAnchor ? 73 : 361) : (this.linked ? 16 : (l.joint == 1 ? 32 : 81));

         for (int pass = 0; pass < (localContact ? 0 : (this.linked ? 2 : 1)); pass++) {
            if (pass > 0) {
               if (score != -Float.MAX_VALUE) {
                  break;
               }

               limit = (float)Math.toRadians(170.0);
               relaxed = true;
            }

            for (int r = 0; r <= rings; r++) {
               for (int jx = 0; jx < around; jx++) {
                  if (this.rig.plane != 0) {
                     float a = (float)((double)jx * Math.PI * 2.0 / (double)around);
                     this.local
                        .set(
                           this.rig.plane == 2 ? (float)Math.sin((double)a) : 0.0F,
                           (float)Math.cos((double)a),
                           this.rig.plane == 1 ? (float)Math.sin((double)a) : 0.0F
                        );
                     this.rootRotation.transform(this.local, this.candidate);
                  } else if (this.joint(l) == 1) {
                     float theta = limit * (float)r / (float)rings;
                     float phi = (float)((double)jx * Math.PI * 2.0 / (double)around);
                     this.local
                        .set(
                           (float)(Math.sin((double)theta) * Math.cos((double)phi)),
                           (float)Math.cos((double)theta),
                           (float)(Math.sin((double)theta) * Math.sin((double)phi))
                        );
                  } else {
                     float a = -limit + 2.0F * limit * (float)jx / ((float)around - 1.0F);
                     this.local
                        .set(l.joint == 3 ? (float)Math.sin((double)a) : 0.0F, (float)Math.cos((double)a), l.joint == 2 ? (float)Math.sin((double)a) : 0.0F);
                  }

                  if (this.rig.plane == 0) {
                     this.basis.transform(this.local, this.candidate);
                  }

                  float s = this.candidate.dot(var18);
                  if (this.linked) {
                     s += 0.004F * this.candidate.dot(this.outwardHint) + 0.02F * this.candidate.dot(this.oldDirection);
                  }

                  if (s > score && this.allowed(l, this.candidate) && this.clear(f, i, length, this.candidate, radius)) {
                     score = s;
                     this.best.set(this.candidate);
                  }
               }
            }
         }

         if (score == -Float.MAX_VALUE) {
            return false;
         } else {
            float lo = 0.0F;
            float hi = 1.0F;
            this.aim.set(this.best);

            for (int k = 0; k < (this.linked ? 7 : 10); k++) {
               float t = (lo + hi) * 0.5F;
               this.candidate.set(this.aim).lerp(var18, t).normalize();
               if (!relaxed) {
                  this.constrain(l, this.candidate, this.candidate);
               }

               this.projectPlane(this.candidate);
               if (this.allowed(l, this.candidate) && this.clear(f, i, length, this.candidate, radius)) {
                  lo = t;
                  this.best.set(this.candidate);
               } else {
                  hi = t;
               }
            }

            return true;
         }
      }
   }

   private void projectPlane(Vector3f direction) {
      if (this.rig.plane != 0) {
         this.rootRotation.transformInverse(direction, this.planeLocal);
         if (this.rig.plane == 1) {
            this.planeLocal.x = 0.0F;
         } else {
            this.planeLocal.z = 0.0F;
         }

         if (this.planeLocal.lengthSquared() < 1.0E-10F) {
            this.planeLocal.set(DOWN);
         } else {
            this.planeLocal.normalize();
         }

         this.rootRotation.transform(this.planeLocal, direction);
      }
   }

   private boolean allowed(AdornmentProject.Link link, Vector3fc direction) {
      if (this.rig.plane != 0 && !this.linked) {
         this.constrain(link, direction, this.allowedDirection);
         return this.allowedDirection.dot(direction) > 0.999999F;
      } else {
         return true;
      }
   }

   public boolean valid() {
      for (boolean b : this.blocked) {
         if (b) {
            return false;
         }
      }

      return true;
   }

   private static float clamp(float v, float a, float b) {
      return Math.max(a, Math.min(b, v));
   }

   public static float segmentDistanceSquared(Vector3fc p, Vector3fc q, Vector3fc a, Vector3fc b) {
      float ux = q.x() - p.x();
      float uy = q.y() - p.y();
      float uz = q.z() - p.z();
      float vx = b.x() - a.x();
      float vy = b.y() - a.y();
      float vz = b.z() - a.z();
      float wx = p.x() - a.x();
      float wy = p.y() - a.y();
      float wz = p.z() - a.z();
      float aa = ux * ux + uy * uy + uz * uz;
      float bb = ux * vx + uy * vy + uz * vz;
      float cc = vx * vx + vy * vy + vz * vz;
      float dd = ux * wx + uy * wy + uz * wz;
      float ee = vx * wx + vy * wy + vz * wz;
      float s = aa < 1.0E-9F ? 0.0F : clamp((bb * ee - cc * dd) / Math.max(1.0E-9F, aa * cc - bb * bb), 0.0F, 1.0F);
      float t = cc < 1.0E-9F ? 0.0F : clamp((bb * s + ee) / cc, 0.0F, 1.0F);
      s = aa < 1.0E-9F ? 0.0F : clamp((bb * t - dd) / aa, 0.0F, 1.0F);
      t = cc < 1.0E-9F ? 0.0F : clamp((bb * s + ee) / cc, 0.0F, 1.0F);
      float dx = wx + s * ux - t * vx;
      float dy = wy + s * uy - t * vy;
      float dz = wz + s * uz - t * vz;
      return dx * dx + dy * dy + dz * dz;
   }
}
