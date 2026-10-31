package pro.webhm.playerinteractions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import pro.webhm.playerinteractions.adornments.AdornmentFrame;
import pro.webhm.playerinteractions.adornments.AdornmentProject;
import pro.webhm.playerinteractions.body.BodyRuntime;
import pro.webhm.playerinteractions.body.SurfaceDent;
import pro.webhm.playerinteractions.body.TorsoContact;
import pro.webhm.playerinteractions.npc.BodyData;
import pro.webhm.playerinteractions.npc.BoneTransform;
import pro.webhm.playerinteractions.npc.BreastData;
import pro.webhm.playerinteractions.npc.HeadDecorationData;
import pro.webhm.playerinteractions.npc.ModelShapeData;
import pro.webhm.playerinteractions.npc.NpcBone;
import pro.webhm.playerinteractions.npc.NpcEquipment;
import pro.webhm.playerinteractions.npc.RootPivot;

public abstract class SharedBodyModel<T extends Entity> extends EntityModel<T> {
   public static final ModelLayerLocation LAYER_WIDE = new ModelLayerLocation(BodyRuntime.id("npc"), "wide");
   public static final ModelLayerLocation LAYER_SLIM = new ModelLayerLocation(BodyRuntime.id("npc"), "slim");
   public static final int ARM_WIDTH_STEPS = 9;
   public static final ModelLayerLocation[] ARM_WIDTH_LAYERS = new ModelLayerLocation[9];
   protected static final BoneTransform IDENTITY;
   protected static final float CHILD_HEAD_SCALE = 1.45F;
   protected NpcEquipment sampledGear;
   protected String currentSkinKey = "";
   protected boolean currentSkinLayers3d;
   protected BreastData currentBreastData;
   protected List<HeadDecorationData> decorations = List.of();
   protected int currentSkinScale = 1;
   protected boolean waistJointAllowed;
   protected ModelShapeData currentModelShape;
   protected float renderPartialTick = 0.0F;
   protected final Vector3f gazeTarget = new Vector3f();
   protected final ModelPart root;
   protected boolean continuousLimbs;
   protected float continuousArmWidth = 3.0F;
   protected float continuousArmTextureWidth = 3.0F;
   protected final Map<String, ModelPart> bones = new HashMap<>();
   protected final List<ModelPart> flatOuterParts = new ArrayList<>();
   public int previewHiddenParts;
   protected final Map<String, float[]> defaults = new HashMap<>();
   protected final ModelPart rightBreast;
   protected final ModelPart leftBreast;
   protected final ModelPart rightBreastJacket;
   protected final ModelPart leftBreastJacket;
   protected final ModelPart waistJoint;
   protected final ModelPart rightElbowJoint;
   protected final ModelPart leftElbowJoint;
   protected final ModelPart rightKneeJoint;
   protected final ModelPart leftKneeJoint;
   protected final ModelPart bodyBase;
   protected final ModelPart bodyOuter;
   protected final ModelPart chestBase;
   protected final ModelPart chestOuter;
   protected final ModelPart headOuter;
   protected boolean adaptiveChestOuter;
   protected static final float BREAST_Y = -4.5F;
   protected static final float BREAST_Z = -2.0F;
   private final Matrix4f adornmentWorld = new Matrix4f();
   private final PoseStack adornmentPose = new PoseStack();
   private long adornmentBreastMask = Long.MIN_VALUE;
   private String adornmentBreastPixels;
   private int adornmentBreastScale;
   private long adornmentBreastOccupied;
   protected TorsoContact sceneTouch;
   protected String sceneTouchBone = "body";
   public SurfaceDent strokeDent;
   private final SurfaceDentMesh strokeMesh = new SurfaceDentMesh();
   private final TorsoTouchMesh sceneTouchMesh = new TorsoTouchMesh();

   public static int armWidthIndex(float width) {
      return Mth.clamp(Math.round((width - 2.0F) * 4.0F), 0, 8);
   }

   public static float armWidthForIndex(int index) {
      return 2.0F + (float)Mth.clamp(index, 0, 8) * 0.25F;
   }

   public void setGazeTarget(Vector3f target) {
      this.gazeTarget.set(target);
   }

   public Vector3f gazeTargetInHead(PoseStack headStack) {
      return new Matrix4f(headStack.last().pose()).invert().transformPosition(new Vector3f(this.gazeTarget));
   }

   public void setRenderPartialTick(float partialTick) {
      this.renderPartialTick = Math.max(0.0F, Math.min(1.0F, partialTick));
   }

   public ModelPart bonePart(NpcBone bone) {
      return this.bones.get(bone.id);
   }

   public boolean previewHidden(String bone) {
      for (int i = 0; i < AdornmentProject.BONES.length; i++) {
         if (AdornmentProject.BONES[i].equals(bone)) {
            return (this.previewHiddenParts & 1 << i) != 0;
         }
      }

      return false;
   }

   public void withPreviewVisibility(Runnable draw) {
      if (this.previewHiddenParts == 0) {
         draw.run();
      } else {
         IdentityHashMap<ModelPart, boolean[]> changed = new IdentityHashMap<>();
         if ((this.previewHiddenParts & 6) != 0) {
            changed.put(this.waistJoint, new boolean[]{this.waistJoint.visible, this.waistJoint.skipDraw});
            this.waistJoint.visible = false;
         }

         for (int i = 0; i < AdornmentProject.BONES.length; i++) {
            if ((this.previewHiddenParts & 1 << i) != 0) {
               String id = AdornmentProject.BONES[i];
               ModelPart part = i == 11 ? this.rightBreast : (i == 12 ? this.leftBreast : this.bonePart(NpcBone.byId(id)));
               changed.put(part, new boolean[]{part.visible, part.skipDraw});
               if (i != 1 && i != 2) {
                  part.visible = false;
               } else {
                  part.skipDraw = true;

                  for (String child : new String[]{"base", "outer"}) {
                     ModelPart p = part.getChild(child);
                     changed.put(p, new boolean[]{p.visible, p.skipDraw});
                     p.skipDraw = true;
                  }
               }
            }
         }

         try {
            draw.run();
         } finally {
            changed.forEach((px, v) -> {
               px.visible = v[0];
               px.skipDraw = v[1];
            });
         }
      }
   }

   public SharedBodyModel(ModelPart root) {
      this.root = root;
      this.bones.put(NpcBone.ROOT.id, root);
      ModelPart body = root.getChild(NpcBone.BODY.id);
      this.bodyBase = body.getChild("base");
      this.bodyOuter = body.getChild("outer");
      ModelPart chest = body.getChild(NpcBone.CHEST.id);
      this.waistJoint = body.getChild("waist_joint");
      this.chestBase = chest.getChild("base");
      this.chestOuter = chest.getChild("outer");
      this.bones.put(NpcBone.BODY.id, body);
      this.bones.put(NpcBone.CHEST.id, chest);
      this.bones.put(NpcBone.HEAD.id, chest.getChild(NpcBone.HEAD.id));
      this.headOuter = this.bones.get(NpcBone.HEAD.id).getChild("outer");
      ModelPart rArm = chest.getChild(NpcBone.RIGHT_ARM.id);
      ModelPart lArm = chest.getChild(NpcBone.LEFT_ARM.id);
      this.rightElbowJoint = rArm.getChild("elbow_joint");
      this.leftElbowJoint = lArm.getChild("elbow_joint");
      this.bones.put(NpcBone.RIGHT_ARM.id, rArm);
      this.bones.put(NpcBone.LEFT_ARM.id, lArm);
      this.bones.put(NpcBone.RIGHT_FOREARM.id, rArm.getChild(NpcBone.RIGHT_FOREARM.id));
      this.bones.put(NpcBone.LEFT_FOREARM.id, lArm.getChild(NpcBone.LEFT_FOREARM.id));
      ModelPart rLeg = root.getChild(NpcBone.RIGHT_LEG.id);
      ModelPart lLeg = root.getChild(NpcBone.LEFT_LEG.id);
      this.rightKneeJoint = rLeg.getChild("knee_joint");
      this.leftKneeJoint = lLeg.getChild("knee_joint");
      this.bones.put(NpcBone.RIGHT_LEG.id, rLeg);
      this.bones.put(NpcBone.LEFT_LEG.id, lLeg);
      this.bones.put(NpcBone.RIGHT_SHIN.id, rLeg.getChild(NpcBone.RIGHT_SHIN.id));
      this.bones.put(NpcBone.LEFT_SHIN.id, lLeg.getChild(NpcBone.LEFT_SHIN.id));
      this.flatOuterParts.add(body.getChild("outer"));
      this.flatOuterParts.add(chest.getChild("outer"));
      this.flatOuterParts.add(this.headOuter);
      this.rightBreast = chest.getChild("right_breast");
      this.leftBreast = chest.getChild("left_breast");
      this.rightBreastJacket = this.rightBreast.getChild("right_breast_jacket");
      this.leftBreastJacket = this.leftBreast.getChild("left_breast_jacket");
      this.bones.forEach((id, part) -> this.defaults.put(id, new float[]{part.x, part.y, part.z}));
   }

   public static LayerDefinition createLayer(boolean slim) {
      return createLayer(slim ? 3.0F : 4.0F);
   }

   public static LayerDefinition createLayer(float requestedArmWidth) {
      MeshDefinition mesh = new MeshDefinition();
      PartDefinition root = mesh.getRoot();
      CubeDeformation none = CubeDeformation.NONE;
      CubeDeformation outer = new CubeDeformation(0.25F);
      CubeDeformation overlap = new CubeDeformation(-0.02F, 0.0F, -0.02F);
      CubeDeformation overlapOuter = new CubeDeformation(0.23F, 0.25F, 0.23F);
      CubeDeformation torsoOuter = new CubeDeformation(0.25F, 0.0F, 0.25F);
      CubeDeformation joint = new CubeDeformation(-0.04F, 0.0F, -0.04F);
      CubeDeformation jointOuter = new CubeDeformation(0.2F, 0.2F, 0.2F);
      EnumSet<Direction> bodyFaces = EnumSet.allOf(Direction.class);
      bodyFaces.remove(Direction.DOWN);
      bodyFaces.remove(Direction.UP);
      EnumSet<Direction> chestFaces = EnumSet.allOf(Direction.class);
      chestFaces.remove(Direction.UP);
      EnumSet<Direction> jointFaces = EnumSet.allOf(Direction.class);
      jointFaces.remove(Direction.UP);
      jointFaces.remove(Direction.DOWN);
      PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, 12.0F, 0.0F));
      body.addOrReplaceChild("base", CubeListBuilder.create().texOffs(16, 22).addBox(-4.0F, -6.0F, -2.0F, 8.0F, 6.0F, 4.0F, bodyFaces), PartPose.ZERO);
      body.addOrReplaceChild("outer", CubeListBuilder.create().texOffs(16, 38).addBox(-4.0F, -6.0F, -2.0F, 8.0F, 6.0F, 4.0F, torsoOuter), PartPose.ZERO);
      PartDefinition waistJoint = body.addOrReplaceChild(
         "waist_joint", CubeListBuilder.create().texOffs(16, 21).addBox(-4.0F, -1.0F, -2.0F, 8.0F, 2.0F, 4.0F, joint), PartPose.offset(0.0F, -6.0F, 0.0F)
      );
      waistJoint.addOrReplaceChild("outer", CubeListBuilder.create().texOffs(16, 37).addBox(-4.0F, -1.0F, -2.0F, 8.0F, 2.0F, 4.0F, jointOuter), PartPose.ZERO);
      PartDefinition chest = body.addOrReplaceChild("chest", CubeListBuilder.create(), PartPose.offset(0.0F, -6.0F, 0.0F));
      chest.addOrReplaceChild("base", CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F, -6.0F, -2.0F, 8.0F, 6.0F, 4.0F, chestFaces), PartPose.ZERO);
      chest.addOrReplaceChild("outer", CubeListBuilder.create().texOffs(16, 32).addBox(-4.0F, -6.0F, -2.0F, 8.0F, 6.0F, 4.0F, torsoOuter), PartPose.ZERO);
      PartDefinition head = chest.addOrReplaceChild(
         "head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, none), PartPose.offset(0.0F, -6.0F, 0.0F)
      );
      head.addOrReplaceChild(
         "outer", CubeListBuilder.create().texOffs(32, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.5F)), PartPose.ZERO
      );
      float armW = Mth.clamp(requestedArmWidth, 2.0F, 4.0F);
      float rArmX = 1.0F - armW;
      float armY = 4.0F - armW * 0.5F;
      PartDefinition rArm = chest.addOrReplaceChild(
         "right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(rArmX, -2.0F, -2.0F, armW, 7.0F, 4.0F, none), PartPose.offset(-5.0F, armY - 6.0F, 0.0F)
      );
      rArm.addOrReplaceChild("outer", CubeListBuilder.create().texOffs(40, 32).addBox(rArmX, -2.0F, -2.0F, armW, 7.0F, 4.0F, outer), PartPose.ZERO);
      rArm.addOrReplaceChild("shoulder_fill", CubeListBuilder.create(), PartPose.ZERO);
      rArm.addOrReplaceChild("shoulder_fill_outer", CubeListBuilder.create(), PartPose.ZERO);
      PartDefinition rElbow = rArm.addOrReplaceChild(
         "elbow_joint",
         CubeListBuilder.create().texOffs(40, 21).addBox(rArmX + 0.2F, -0.55F, -1.55F, armW - 0.4F, 1.1F, 3.1F, jointFaces),
         PartPose.offset(0.0F, 4.0F, 0.0F)
      );
      rElbow.addOrReplaceChild("outer", CubeListBuilder.create(), PartPose.ZERO);
      PartDefinition rForearm = rArm.addOrReplaceChild(
         "right_forearm", CubeListBuilder.create().texOffs(40, 21).addBox(rArmX, -1.0F, -2.0F, armW, 7.0F, 4.0F, overlap), PartPose.offset(0.0F, 4.0F, 0.0F)
      );
      rForearm.addOrReplaceChild("outer", CubeListBuilder.create().texOffs(40, 37).addBox(rArmX, -1.0F, -2.0F, armW, 7.0F, 4.0F, overlapOuter), PartPose.ZERO);
      PartDefinition lArm = chest.addOrReplaceChild(
         "left_arm", CubeListBuilder.create().texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, armW, 7.0F, 4.0F, none), PartPose.offset(5.0F, armY - 6.0F, 0.0F)
      );
      lArm.addOrReplaceChild("outer", CubeListBuilder.create().texOffs(48, 48).addBox(-1.0F, -2.0F, -2.0F, armW, 7.0F, 4.0F, outer), PartPose.ZERO);
      lArm.addOrReplaceChild("shoulder_fill", CubeListBuilder.create(), PartPose.ZERO);
      lArm.addOrReplaceChild("shoulder_fill_outer", CubeListBuilder.create(), PartPose.ZERO);
      PartDefinition lElbow = lArm.addOrReplaceChild(
         "elbow_joint",
         CubeListBuilder.create().texOffs(32, 53).addBox(-0.8F, -0.55F, -1.55F, armW - 0.4F, 1.1F, 3.1F, jointFaces),
         PartPose.offset(0.0F, 4.0F, 0.0F)
      );
      lElbow.addOrReplaceChild("outer", CubeListBuilder.create(), PartPose.ZERO);
      PartDefinition lForearm = lArm.addOrReplaceChild(
         "left_forearm", CubeListBuilder.create().texOffs(32, 53).addBox(-1.0F, -1.0F, -2.0F, armW, 7.0F, 4.0F, overlap), PartPose.offset(0.0F, 4.0F, 0.0F)
      );
      lForearm.addOrReplaceChild("outer", CubeListBuilder.create().texOffs(48, 53).addBox(-1.0F, -1.0F, -2.0F, armW, 7.0F, 4.0F, overlapOuter), PartPose.ZERO);
      PartDefinition rLeg = root.addOrReplaceChild(
         "right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 7.0F, 4.0F, none), PartPose.offset(-1.9F, 12.0F, 0.0F)
      );
      rLeg.addOrReplaceChild("outer", CubeListBuilder.create().texOffs(0, 32).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 7.0F, 4.0F, outer), PartPose.ZERO);
      rLeg.addOrReplaceChild("hip_fill", CubeListBuilder.create(), PartPose.ZERO);
      rLeg.addOrReplaceChild("hip_fill_outer", CubeListBuilder.create(), PartPose.ZERO);
      PartDefinition rKnee = rLeg.addOrReplaceChild(
         "knee_joint", CubeListBuilder.create().texOffs(0, 21).addBox(-1.55F, -0.55F, -1.55F, 3.1F, 1.1F, 3.1F, jointFaces), PartPose.offset(0.0F, 6.0F, 0.0F)
      );
      rKnee.addOrReplaceChild("outer", CubeListBuilder.create(), PartPose.ZERO);
      PartDefinition rShin = rLeg.addOrReplaceChild(
         "right_shin", CubeListBuilder.create().texOffs(0, 21).addBox(-2.0F, -1.0F, -2.0F, 4.0F, 7.0F, 4.0F, overlap), PartPose.offset(0.0F, 6.0F, 0.0F)
      );
      rShin.addOrReplaceChild("outer", CubeListBuilder.create().texOffs(0, 37).addBox(-2.0F, -1.0F, -2.0F, 4.0F, 7.0F, 4.0F, overlapOuter), PartPose.ZERO);
      PartDefinition lLeg = root.addOrReplaceChild(
         "left_leg", CubeListBuilder.create().texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 7.0F, 4.0F, none), PartPose.offset(1.9F, 12.0F, 0.0F)
      );
      lLeg.addOrReplaceChild("outer", CubeListBuilder.create().texOffs(0, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 7.0F, 4.0F, outer), PartPose.ZERO);
      lLeg.addOrReplaceChild("hip_fill", CubeListBuilder.create(), PartPose.ZERO);
      lLeg.addOrReplaceChild("hip_fill_outer", CubeListBuilder.create(), PartPose.ZERO);
      PartDefinition lKnee = lLeg.addOrReplaceChild(
         "knee_joint", CubeListBuilder.create().texOffs(16, 53).addBox(-1.55F, -0.55F, -1.55F, 3.1F, 1.1F, 3.1F, jointFaces), PartPose.offset(0.0F, 6.0F, 0.0F)
      );
      lKnee.addOrReplaceChild("outer", CubeListBuilder.create(), PartPose.ZERO);
      PartDefinition lShin = lLeg.addOrReplaceChild(
         "left_shin", CubeListBuilder.create().texOffs(16, 53).addBox(-2.0F, -1.0F, -2.0F, 4.0F, 7.0F, 4.0F, overlap), PartPose.offset(0.0F, 6.0F, 0.0F)
      );
      lShin.addOrReplaceChild("outer", CubeListBuilder.create().texOffs(0, 53).addBox(-2.0F, -1.0F, -2.0F, 4.0F, 7.0F, 4.0F, overlapOuter), PartPose.ZERO);
      PartDefinition rBreast = chest.addOrReplaceChild("right_breast", CubeListBuilder.create(), PartPose.offset(0.0F, -4.5F, -2.0F));
      rBreast.addOrReplaceChild("right_breast_jacket", CubeListBuilder.create(), PartPose.ZERO);
      PartDefinition lBreast = chest.addOrReplaceChild("left_breast", CubeListBuilder.create(), PartPose.offset(0.0F, -4.5F, -2.0F));
      lBreast.addOrReplaceChild("left_breast_jacket", CubeListBuilder.create(), PartPose.ZERO);
      return LayerDefinition.create(mesh, 64, 64);
   }

   protected void setBuiltInGeometryHidden(boolean hidden) {
      this.bones.values().forEach(partx -> partx.skipDraw = hidden);
      ModelPart[] parts = new ModelPart[]{
         this.bodyBase,
         this.bodyOuter,
         this.chestBase,
         this.chestOuter,
         this.headOuter,
         this.waistJoint,
         this.waistJoint.getChild("outer"),
         this.rightElbowJoint,
         this.rightElbowJoint.getChild("outer"),
         this.leftElbowJoint,
         this.leftElbowJoint.getChild("outer"),
         this.rightKneeJoint,
         this.rightKneeJoint.getChild("outer"),
         this.leftKneeJoint,
         this.leftKneeJoint.getChild("outer"),
         this.rightBreast,
         this.leftBreast,
         this.rightBreastJacket,
         this.leftBreastJacket,
         this.bones.get(NpcBone.RIGHT_ARM.id).getChild("outer"),
         this.bones.get(NpcBone.LEFT_ARM.id).getChild("outer"),
         this.bones.get(NpcBone.RIGHT_ARM.id).getChild("shoulder_fill"),
         this.bones.get(NpcBone.RIGHT_ARM.id).getChild("shoulder_fill_outer"),
         this.bones.get(NpcBone.LEFT_ARM.id).getChild("shoulder_fill"),
         this.bones.get(NpcBone.LEFT_ARM.id).getChild("shoulder_fill_outer"),
         this.bones.get(NpcBone.RIGHT_FOREARM.id).getChild("outer"),
         this.bones.get(NpcBone.LEFT_FOREARM.id).getChild("outer"),
         this.bones.get(NpcBone.RIGHT_LEG.id).getChild("outer"),
         this.bones.get(NpcBone.LEFT_LEG.id).getChild("outer"),
         this.bones.get(NpcBone.RIGHT_LEG.id).getChild("hip_fill"),
         this.bones.get(NpcBone.RIGHT_LEG.id).getChild("hip_fill_outer"),
         this.bones.get(NpcBone.LEFT_LEG.id).getChild("hip_fill"),
         this.bones.get(NpcBone.LEFT_LEG.id).getChild("hip_fill_outer"),
         this.bones.get(NpcBone.RIGHT_SHIN.id).getChild("outer"),
         this.bones.get(NpcBone.LEFT_SHIN.id).getChild("outer")
      };

      for (ModelPart part : parts) {
         if (part != null) {
            part.skipDraw = hidden;
         }
      }
   }

   protected void applyModelShape(BodyData data) {
      if (data.modelShape != null) {
         data.modelShape.normalize();
         float armDepthScale = data.modelShape.armDepth / 4.0F;
         ModelPart rightArm = this.bones.get(NpcBone.RIGHT_ARM.id);
         ModelPart leftArm = this.bones.get(NpcBone.LEFT_ARM.id);
         if (rightArm != null) {
            rightArm.y = rightArm.y - data.modelShape.armPivotHeight;
            float t = Mth.clamp((rightArm.y + 6.0F) / 6.0F, 0.0F, 1.0F);
            float widthAtAxis = lerp(t, data.modelShape.torsoWidth, data.modelShape.waistWidth);
            rightArm.x -= (widthAtAxis - 8.0F) * 0.5F;
            if (!this.continuousLimbs) {
               rightArm.zScale *= armDepthScale;
            }
         }

         if (leftArm != null) {
            leftArm.y = leftArm.y - data.modelShape.armPivotHeight;
            float t = Mth.clamp((leftArm.y + 6.0F) / 6.0F, 0.0F, 1.0F);
            float widthAtAxis = lerp(t, data.modelShape.torsoWidth, data.modelShape.waistWidth);
            leftArm.x += (widthAtAxis - 8.0F) * 0.5F;
            if (!this.continuousLimbs) {
               leftArm.zScale *= armDepthScale;
            }
         }

         ModelPart head = this.bones.get(NpcBone.HEAD.id);
         if (head != null) {
            head.xScale = head.xScale * data.modelShape.headWidth;
            head.yScale = head.yScale * data.modelShape.headHeight;
            head.zScale = head.zScale * data.modelShape.headDepth;
         }
      }
   }

   protected void applyJointBridges() {
      ModelPart chest = this.bones.get(NpcBone.CHEST.id);
      halfRotation(this.waistJoint, chest);
      this.waistJoint.visible = false;
      ModelPart rightForearm = this.bones.get(NpcBone.RIGHT_FOREARM.id);
      ModelPart leftForearm = this.bones.get(NpcBone.LEFT_FOREARM.id);
      ModelPart rightShin = this.bones.get(NpcBone.RIGHT_SHIN.id);
      ModelPart leftShin = this.bones.get(NpcBone.LEFT_SHIN.id);
      halfRotation(this.rightElbowJoint, rightForearm);
      halfRotation(this.leftElbowJoint, leftForearm);
      halfRotation(this.rightKneeJoint, rightShin);
      halfRotation(this.leftKneeJoint, leftShin);
      this.rightElbowJoint.visible = jointIsBent(rightForearm);
      this.leftElbowJoint.visible = jointIsBent(leftForearm);
      this.rightKneeJoint.visible = jointIsBent(rightShin);
      this.leftKneeJoint.visible = jointIsBent(leftShin);
   }

   protected static boolean jointIsBent(ModelPart movingPart) {
      if (movingPart != null && movingPart.visible) {
         float threshold = (float)Math.toRadians(7.0);
         return Math.abs(movingPart.xRot) > threshold || Math.abs(movingPart.yRot) > threshold || Math.abs(movingPart.zRot) > threshold;
      } else {
         return false;
      }
   }

   protected static void halfRotation(ModelPart joint, ModelPart movingPart) {
      joint.xRot = movingPart.xRot * 0.5F;
      joint.yRot = movingPart.yRot * 0.5F;
      joint.zRot = movingPart.zRot * 0.5F;
   }

   protected void applyDoorHand(int door) {
      boolean right = door != 2;
      ModelPart arm = this.bones.get(right ? NpcBone.RIGHT_ARM.id : NpcBone.LEFT_ARM.id);
      ModelPart fore = this.bones.get(right ? NpcBone.RIGHT_FOREARM.id : NpcBone.LEFT_FOREARM.id);
      if (arm != null && fore != null) {
         arm.xRot = (float)Math.toRadians(-74.0);
         arm.yRot = 0.0F;
         arm.zRot = (float)Math.toRadians(right ? 9.0 : -9.0);
         fore.xRot = (float)Math.toRadians(-16.0);
      }
   }

   protected static float lerp(float t, float a, float b) {
      return a + (b - a) * t;
   }

   protected void applyPose(Map<String, BoneTransform> pose, boolean child) {
      this.applyPose(pose, child, RootPivot.CENTER, 0.0F, 0.0F, 0.0F);
   }

   protected void applyPose(Map<String, BoneTransform> pose, boolean child, RootPivot pivot, float px, float py, float pz) {
      float[] anchor = pivot.dynamic ? this.handAnchor(pose, pivot == RootPivot.RIGHT_HAND) : new float[]{pivot.x, pivot.y, pivot.z};
      float ax = anchor[0] + px;
      float ay = anchor[1] + py;
      float az = anchor[2] + pz;

      for (NpcBone bone : NpcBone.values()) {
         ModelPart part = this.bones.get(bone.id);
         if (part != null) {
            float[] d = this.defaults.get(bone.id);
            BoneTransform raw = pose == null ? IDENTITY : pose.getOrDefault(bone.id, IDENTITY);
            BoneTransform t = bone.clamped(raw);
            part.x = bone.hasOffsets() ? d[0] + t.shiftX : d[0];
            part.y = bone.hasOffsets() ? d[1] - t.height : d[1];
            part.z = bone.hasOffsets() ? d[2] + t.shiftZ : d[2];
            if (bone.root) {
               part.x += ax;
               part.y += ay;
               part.z += az;
            } else if (bone == NpcBone.BODY || bone == NpcBone.RIGHT_LEG || bone == NpcBone.LEFT_LEG) {
               part.x -= ax;
               part.y -= ay;
               part.z -= az;
            }

            part.xRot = (float)Math.toRadians((double)t.rx);
            part.yRot = (float)Math.toRadians((double)t.ry);
            part.zRot = (float)Math.toRadians((double)t.rz);
            part.xScale = 1.0F;
            part.yScale = 1.0F;
            part.zScale = 1.0F;
         }
      }

      if (child) {
         ModelPart head = this.bones.get(NpcBone.HEAD.id);
         if (head != null) {
            head.xScale = 1.45F;
            head.yScale = 1.45F;
            head.zScale = 1.45F;
         }
      }
   }

   protected float[] handAnchor(Map<String, BoneTransform> pose, boolean right) {
      NpcBone armBone = right ? NpcBone.RIGHT_ARM : NpcBone.LEFT_ARM;
      NpcBone foreBone = right ? NpcBone.RIGHT_FOREARM : NpcBone.LEFT_FOREARM;
      Matrix4f m = new Matrix4f();
      Vector3f pos = new Vector3f();
      NpcBone[] chain = new NpcBone[]{NpcBone.BODY, NpcBone.CHEST, armBone, foreBone};

      for (NpcBone bone : chain) {
         float[] d = this.defaults.get(bone.id);
         if (d != null) {
            Vector3f step = new Vector3f(d[0], d[1], d[2]);
            m.transformDirection(step);
            pos.add(step);
            BoneTransform raw = pose == null ? IDENTITY : pose.getOrDefault(bone.id, IDENTITY);
            BoneTransform t = bone.clamped(raw);
            m.rotateZ((float)Math.toRadians((double)t.rz));
            m.rotateY((float)Math.toRadians((double)t.ry));
            m.rotateX((float)Math.toRadians((double)t.rx));
         }
      }

      Vector3f hand = new Vector3f(0.0F, 7.0F, 0.0F);
      m.transformDirection(hand);
      pos.add(hand);
      return new float[]{pos.x, pos.y, pos.z};
   }

   protected void applyWalkBob(float limbSwing, float amount) {
      float swing = (float)Math.cos((double)(limbSwing * 0.6662F)) * 1.4F * Math.min(1.0F, amount);
      this.add(NpcBone.RIGHT_LEG, swing, 0.0F, 0.0F);
      this.add(NpcBone.LEFT_LEG, -swing, 0.0F, 0.0F);
      this.add(NpcBone.RIGHT_SHIN, Math.max(0.0F, -swing * 0.6F), 0.0F, 0.0F);
      this.add(NpcBone.LEFT_SHIN, Math.max(0.0F, swing * 0.6F), 0.0F, 0.0F);
      this.add(NpcBone.RIGHT_ARM, -swing * 0.7F, 0.0F, 0.0F);
      this.add(NpcBone.LEFT_ARM, swing * 0.7F, 0.0F, 0.0F);
   }

   protected Vector3f headPivot() {
      Matrix4f rotation = new Matrix4f();
      Vector3f result = new Vector3f();
      String[] chain = new String[]{NpcBone.ROOT.id, NpcBone.BODY.id, NpcBone.CHEST.id, NpcBone.HEAD.id};

      for (int i = 0; i < chain.length; i++) {
         ModelPart part = this.bones.get(chain[i]);
         if (part != null) {
            Vector3f step = new Vector3f(part.x, part.y, part.z);
            rotation.transformDirection(step);
            result.add(step);
            if (i + 1 < chain.length) {
               rotation.rotateZ(part.zRot);
               rotation.rotateY(part.yRot);
               rotation.rotateX(part.xRot);
            }
         }
      }

      return result;
   }

   protected Matrix4f headParentRotation() {
      Matrix4f rotation = new Matrix4f();

      for (String bone : new String[]{NpcBone.ROOT.id, NpcBone.BODY.id, NpcBone.CHEST.id}) {
         ModelPart part = this.bones.get(bone);
         if (part != null) {
            rotation.rotateZ(part.zRot);
            rotation.rotateY(part.yRot);
            rotation.rotateX(part.xRot);
         }
      }

      return rotation;
   }

   protected static float wrap(float deg) {
      deg %= 360.0F;
      if (deg >= 180.0F) {
         deg -= 360.0F;
      }

      if (deg < -180.0F) {
         deg += 360.0F;
      }

      return deg;
   }

   protected void add(NpcBone bone, float x, float y, float z) {
      ModelPart part = this.bones.get(bone.id);
      if (part != null) {
         part.xRot += x;
         part.yRot += y;
         part.zRot += z;
      }
   }

   public void setHighlight(NpcBone bone) {
      for (Entry<String, ModelPart> e : this.bones.entrySet()) {
         e.getValue().skipDraw = !e.getKey().equals(bone.id);
      }

      this.rightBreast.skipDraw = true;
      this.leftBreast.skipDraw = true;
      this.rightBreastJacket.skipDraw = true;
      this.leftBreastJacket.skipDraw = true;
      if (bone.root) {
         ModelPart body = this.bones.get(NpcBone.BODY.id);
         ModelPart chest = this.bones.get(NpcBone.CHEST.id);
         if (body != null) {
            body.skipDraw = false;
         }

         if (chest != null) {
            chest.skipDraw = false;
         }
      } else {
         ModelPart part = this.bones.get(bone.id);
         if (part != null) {
            part.skipDraw = false;
            part.xScale *= 1.06F;
            part.yScale *= 1.06F;
            part.zScale *= 1.06F;
         }
      }
   }

   public void clearHighlight() {
      this.bones.values().forEach(p -> p.skipDraw = false);
      this.rightBreast.skipDraw = false;
      this.leftBreast.skipDraw = false;
      this.rightBreastJacket.skipDraw = false;
      this.leftBreastJacket.skipDraw = false;
   }

   public void translateToBone(NpcBone target, PoseStack pose) {
      NpcBone[] chain = chainTo(target);

      for (NpcBone b : chain) {
         ModelPart part = this.bones.get(b.id);
         if (part != null) {
            part.translateAndRotate(pose);
         }
      }
   }

   public void adornmentFrame(AdornmentFrame frame, float yaw, float scale, float offsetY) {
      frame.clear();
      frame.scale = scale;
      Matrix4f world = this.adornmentWorld
         .identity()
         .translate(0.0F, offsetY * 16.0F, 0.0F)
         .rotateY((float)Math.toRadians((double)(180.0F - yaw)))
         .scale(-scale, -scale, scale)
         .translate(0.0F, -24.016F, 0.0F);
      frame.toModel.set(world).invert();

      for (int i = 0; i < AdornmentProject.BONES.length; i++) {
         String id = AdornmentProject.BONES[i];
         PoseStack p = this.adornmentPose;
         p.pushPose();
         this.translateToBone(id.endsWith("_breast") ? NpcBone.CHEST : NpcBone.byId(id), p);
         if (id.equals("right_breast")) {
            this.rightBreast.translateAndRotate(p);
         }

         if (id.equals("left_breast")) {
            this.leftBreast.translateAndRotate(p);
         }

         frame.bones[i].set(world).scale(16.0F).mul(p.last().pose()).scale(0.0625F);
         p.popPose();
      }

      float outer = 0.55F;
      float arm = this.continuousArmWidth;
      float armDepth = this.currentModelShape == null ? 4.0F : this.currentModelShape.armDepth;

      for (int i = 0; i < 11; i++) {
         String idx = AdornmentProject.BONES[i];
         Matrix4f m = frame.bones[i];
         if (idx.equals("head")) {
            frame.add(m, -4.0F - outer, -8.0F - outer, -4.0F - outer, 4.0F + outer, outer, 4.0F + outer);
         } else if (!idx.equals("chest") && !idx.equals("body")) {
            boolean isArm = idx.contains("arm");
            boolean lower = idx.contains("forearm") || idx.contains("shin");
            float x0 = isArm ? (idx.startsWith("right") ? 1.0F - arm : -1.0F) : -2.0F;
            float x1 = isArm ? x0 + arm : 2.0F;
            float y0 = lower ? -1.0F : (isArm ? -2.0F : 0.0F);
            float y1 = lower ? 6.0F : (isArm ? 5.0F : 7.0F);
            float depth = (isArm ? armDepth : 4.0F) * 0.5F;
            frame.add(m, x0 - outer, y0 - 0.15F, -depth - outer, x1 + outer, y1 + 0.15F, depth + outer);
            if (lower) {
               frame.add(m, x0 - outer, -1.5F, -depth - outer, x1 + outer, 1.5F, depth + outer);
            }
         } else {
            float top = this.currentModelShape == null ? 8.0F : (idx.equals("chest") ? this.currentModelShape.torsoWidth : this.currentModelShape.waistWidth);
            float bottom = this.currentModelShape == null ? 8.0F : (idx.equals("chest") ? this.currentModelShape.waistWidth : this.currentModelShape.hipWidth);

            for (int band = 0; band < 3; band++) {
               float half = Math.max(lerp((float)band / 3.0F, top, bottom), lerp((float)(band + 1) / 3.0F, top, bottom)) * 0.5F + outer;
               float y0 = (float)(-6 + band * 2);
               frame.add(m, -half, y0 - 0.1F, -2.0F - outer, half, y0 + 2.1F, 2.0F + outer);
            }
         }
      }

      if (this.currentBreastData != null && this.currentBreastData.enabled) {
         this.addAdornmentBreast(frame, 11, this.rightBreast.visible);
         this.addAdornmentBreast(frame, 12, this.leftBreast.visible);
      }
   }

   private void addAdornmentBreast(AdornmentFrame frame, int bone, boolean visible) {
      if (visible) {
         BreastData b = this.currentBreastData;
         float edge = 0.025F;
         float front = -4.0F - (b.jacket ? (this.currentSkinLayers3d ? 0.3F : 0.12F) : 0.0F) - edge;
         if (!b.pixelVolume) {
            frame.add(frame.bones[bone], bone == 11 ? -4.0F - edge : -edge, -edge, front, bone == 11 ? edge : 4.0F + edge, 5.01F + edge, edge);
         } else {
            if (this.adornmentBreastMask != b.volumeMask || this.adornmentBreastPixels != b.volumePixels || this.adornmentBreastScale != this.currentSkinScale) {
               b.ensureVolumePixelScale(this.currentSkinScale);
               this.adornmentBreastMask = b.volumeMask;
               this.adornmentBreastPixels = b.volumePixels;
               this.adornmentBreastScale = this.currentSkinScale;
               this.adornmentBreastOccupied = 0L;
               int scale = BreastData.safePixelScale(this.currentSkinScale);

               for (int y = 0; y < 5; y++) {
                  for (int x = 0; x < 8; x++) {
                     boolean occupied = false;

                     for (int v = 0; v < scale && !occupied; v++) {
                        for (int u = 0; u < scale; u++) {
                           if (b.volumePixelAt(x * scale + u, y * scale + v, scale)) {
                              occupied = true;
                              break;
                           }
                        }
                     }

                     if (occupied) {
                        this.adornmentBreastOccupied |= 1L << y * 8 + x;
                     }
                  }
               }
            }

            int first = bone == 11 ? 0 : 4;

            for (int y = 0; y < 5; y++) {
               int x = first;

               while (x < first + 4) {
                  if ((this.adornmentBreastOccupied & 1L << y * 8 + x) == 0L) {
                     x++;
                  } else {
                     int start = x++;

                     while (x < first + 4 && (this.adornmentBreastOccupied & 1L << y * 8 + x) != 0L) {
                        x++;
                     }

                     frame.add(frame.bones[bone], (float)(start - 4) - edge, (float)y - edge, front, (float)(x - 4) + edge, (float)y + 1.01F + edge, edge);
                  }
               }
            }
         }
      }
   }

   public void translateToHand(boolean right, PoseStack pose) {
      this.translateToBone(right ? NpcBone.RIGHT_FOREARM : NpcBone.LEFT_FOREARM, pose);
   }

   protected static NpcBone[] chainTo(NpcBone target) {
      return switch (target) {
         case ROOT -> new NpcBone[]{NpcBone.ROOT};
         case BODY -> new NpcBone[]{NpcBone.ROOT, NpcBone.BODY};
         case CHEST -> new NpcBone[]{NpcBone.ROOT, NpcBone.BODY, NpcBone.CHEST};
         case HEAD -> new NpcBone[]{NpcBone.ROOT, NpcBone.BODY, NpcBone.CHEST, NpcBone.HEAD};
         case RIGHT_ARM -> new NpcBone[]{NpcBone.ROOT, NpcBone.BODY, NpcBone.CHEST, NpcBone.RIGHT_ARM};
         case LEFT_ARM -> new NpcBone[]{NpcBone.ROOT, NpcBone.BODY, NpcBone.CHEST, NpcBone.LEFT_ARM};
         case RIGHT_FOREARM -> new NpcBone[]{NpcBone.ROOT, NpcBone.BODY, NpcBone.CHEST, NpcBone.RIGHT_ARM, NpcBone.RIGHT_FOREARM};
         case LEFT_FOREARM -> new NpcBone[]{NpcBone.ROOT, NpcBone.BODY, NpcBone.CHEST, NpcBone.LEFT_ARM, NpcBone.LEFT_FOREARM};
         case RIGHT_LEG -> new NpcBone[]{NpcBone.ROOT, NpcBone.RIGHT_LEG};
         case LEFT_LEG -> new NpcBone[]{NpcBone.ROOT, NpcBone.LEFT_LEG};
         case RIGHT_SHIN -> new NpcBone[]{NpcBone.ROOT, NpcBone.RIGHT_LEG, NpcBone.RIGHT_SHIN};
         case LEFT_SHIN -> new NpcBone[]{NpcBone.ROOT, NpcBone.LEFT_LEG, NpcBone.LEFT_SHIN};
      };
   }

   public NpcEquipment activeGear(BodyData data) {
      return this.sampledGear != null ? this.sampledGear : data.gear;
   }

   public void renderFaceOverlay(PoseStack poseStack, VertexConsumer buffer, int light, int overlay) {
      ModelPart head = this.bones.get(NpcBone.HEAD.id);
      if (head != null) {
         float sx = head.xScale;
         float sy = head.yScale;
         float sz = head.zScale;

         try {
            this.bones.values().forEach(part -> part.skipDraw = true);
            this.rightBreast.skipDraw = this.leftBreast.skipDraw = true;
            this.rightBreastJacket.skipDraw = this.leftBreastJacket.skipDraw = true;
            head.skipDraw = false;
            head.xScale *= 1.0015F;
            head.yScale *= 1.0015F;
            head.zScale *= 1.0015F;
            this.root.render(poseStack, buffer, light, overlay, -1);
         } finally {
            head.xScale = sx;
            head.yScale = sy;
            head.zScale = sz;
            this.bones.values().forEach(part -> part.skipDraw = false);
            this.rightBreast.skipDraw = this.leftBreast.skipDraw = false;
            this.rightBreastJacket.skipDraw = this.leftBreastJacket.skipDraw = false;
         }
      }
   }

   public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color) {
      this.withPreviewVisibility(() -> this.renderVisibleBody(poseStack, buffer, light, overlay, color));
   }

   public VertexConsumer strokeBuffer(PoseStack stack, VertexConsumer out) {
      return (VertexConsumer)(this.strokeDent != null && (double)this.strokeDent.depth > 1.0E-7
         ? this.strokeMesh.begin(out, stack.last().pose(), this.strokeDent)
         : out);
   }

   public void touchBone(String id, PoseStack stack) {
      this.translateToBone(id.endsWith("_breast") ? NpcBone.CHEST : NpcBone.byId(id), stack);
      if (id.equals("right_breast")) {
         this.rightBreast.translateAndRotate(stack);
      }

      if (id.equals("left_breast")) {
         this.leftBreast.translateAndRotate(stack);
      }
   }

   public void protectStrokeJoints(SurfaceDent dent) {
      dent.clearGuards();
      NpcBone[] upper = new NpcBone[]{NpcBone.RIGHT_ARM, NpcBone.LEFT_ARM, NpcBone.RIGHT_LEG, NpcBone.LEFT_LEG};
      NpcBone[] lower = new NpcBone[]{NpcBone.RIGHT_FOREARM, NpcBone.LEFT_FOREARM, NpcBone.RIGHT_SHIN, NpcBone.LEFT_SHIN};
      PoseStack stack = new PoseStack();
      Vector3f[] ring = new Vector3f[8];

      for (int i = 0; i < 8; i++) {
         ring[i] = new Vector3f();
      }

      for (int joint = 0; joint < 4; joint++) {
         boolean arm = joint < 2;
         float width = arm ? this.continuousArmWidth : 4.0F;
         float depth = arm && this.currentModelShape != null ? this.currentModelShape.armDepth : 4.0F;
         float left = (arm ? (joint == 0 ? 1.0F - width : -1.0F) : -2.0F) - 0.25F;
         float right = left + width + 0.5F;
         float front = -depth * 0.5F - 0.25F;
         float back = -front;

         for (int half = 0; half < 2; half++) {
            stack.pushPose();
            this.translateToBone(half == 0 ? upper[joint] : lower[joint], stack);
            float y = half == 0 ? (float)(arm ? 4 : 6) / 16.0F : 0.0F;

            for (int i = 0; i < 4; i++) {
               Vector3f p = ring[half * 4 + i].set((i < 2 ? left : right) / 16.0F, y, (i % 2 == 0 ? front : back) / 16.0F);
               stack.last().pose().transformPosition(p);
            }

            stack.popPose();
         }

         dent.guardRings(ring);
      }
   }

   public boolean sceneTouchSupported(String bone, int face) {
      return this.continuousLimbs
         && !this.currentSkinLayers3d
         && SkinCache.waistProfile(this.currentSkinKey) == null
         && !SkinCache.waistHasHoles(this.currentSkinKey)
         && (this.currentModelShape == null || !this.currentModelShape.hasCustomTorso() || this.currentModelShape.shapeSecondLayer)
         && (!"chest".equals(bone) || face != 0 || this.currentBreastData == null || !this.currentBreastData.enabled);
   }

   private boolean dentPart(String bone) {
      return this.sceneTouch != null
         && this.sceneTouch.depth > 1.0E-5F
         && this.sceneTouchBone.equals(bone)
         && this.sceneTouchSupported(bone, this.sceneTouch.face);
   }

   private VertexConsumer dentBuffer(PoseStack stack, VertexConsumer out, String bone) {
      return (VertexConsumer)(this.dentPart(bone) ? this.sceneTouchMesh.begin(out, stack.last().pose(), this.sceneTouch) : out);
   }

   private void renderVisibleBody(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color) {
      boolean dentBody = this.dentPart("body");
      boolean dentChest = this.dentPart("chest");
      boolean oldBody = this.bodyBase.skipDraw;
      boolean oldChest = this.chestBase.skipDraw;
      if (dentBody) {
         this.bodyBase.skipDraw = true;
      }

      if (dentChest) {
         this.chestBase.skipDraw = true;
      }

      try {
         if (this.continuousLimbs) {
            this.renderContinuousRig(poseStack, buffer, light, overlay, color);
         } else {
            this.root.render(poseStack, buffer, light, overlay, color);
         }
      } finally {
         this.bodyBase.skipDraw = oldBody;
         this.chestBase.skipDraw = oldChest;
      }

      if (dentBody || dentChest) {
         ModelPart part = dentBody ? this.bodyBase : this.chestBase;
         NpcBone bone = dentBody ? NpcBone.BODY : NpcBone.CHEST;
         if (this.root.visible && this.bonePart(bone).visible && !this.bonePart(bone).skipDraw) {
            poseStack.pushPose();
            this.translateToBone(bone, poseStack);
            part.render(poseStack, this.dentBuffer(poseStack, buffer, bone.id), light, overlay, color);
            poseStack.popPose();
         }
      }

      this.renderShapedTorsoBase(poseStack, buffer, light, overlay, color);
      this.renderWaistBacking(poseStack, buffer, light, overlay, color);
      this.renderPelvisBottom(poseStack, buffer, light, overlay, color);
      this.renderAdaptiveChestOuter(poseStack, buffer, light, overlay, color);
      this.renderWaistConnector(poseStack, buffer, light, overlay, color);
      this.renderBreastGeometry(poseStack, buffer, light, overlay, color);
      this.renderHeadDecorations(poseStack, buffer, light, overlay, color, false);
   }

   public void renderHeadDecorations(PoseStack stack, VertexConsumer out, int light, int overlay, int color, boolean solidPass) {
      if (!this.previewHidden("head")) {
         if (this.decorations != null && !this.decorations.isEmpty()) {
            stack.pushPose();
            this.root.translateAndRotate(stack);
            this.bones.get(NpcBone.BODY.id).translateAndRotate(stack);
            this.bones.get(NpcBone.CHEST.id).translateAndRotate(stack);
            this.bones.get(NpcBone.HEAD.id).translateAndRotate(stack);

            for (HeadDecorationData d : this.decorations) {
               if (d.solidColor == solidPass) {
                  int partColor = solidPass ? color & 0xFF000000 | d.rgb & 16777215 : color;
                  stack.pushPose();
                  stack.translate(d.x / 16.0F, d.y / 16.0F, d.z / 16.0F);
                  stack.mulPose(
                     new Quaternionf()
                        .rotationZYX((float)Math.toRadians((double)d.roll), (float)Math.toRadians((double)d.yaw), (float)Math.toRadians((double)d.pitch))
                  );
                  int steps = d.shape == 0 ? 1 : 5;

                  for (int step = 0; step < steps; step++) {
                     float taper = d.shape == 0 ? 1.0F : (float)(steps - step) / (float)steps;
                     float w = d.width * taper;
                     float depth = d.shape == 1 ? d.depth * taper : d.depth;
                     float x0 = -w / 2.0F;
                     float x1 = w / 2.0F;
                     float y1 = d.height / 2.0F - (float)step * d.height / (float)steps;
                     float y0 = y1 - d.height / (float)steps;
                     float z0 = -depth / 2.0F;
                     float z1 = depth / 2.0F;
                     float u0 = (float)d.u;
                     float v0 = (float)d.v;
                     float u1 = Math.min(64.0F, (float)d.u + d.width);
                     float v1 = Math.min(64.0F, (float)d.v + d.height);
                     Pose p = stack.last();
                     breastFront(out, p, x0, x1, y0, y1, z0, u0, v0, u1, v1, partColor, overlay, light);
                     breastBack(out, p, x0, x1, y0, y1, z1, u0, v0, u1, v1, partColor, overlay, light);
                     breastWest(out, p, x0, y0, y1, z0, z1, u0, v0, u1, v1, partColor, overlay, light);
                     breastEast(out, p, x1, y0, y1, z0, z1, u0, v0, u1, v1, partColor, overlay, light);
                     breastTop(out, p, x0, x1, y0, z0, z1, u0, v0, u1, v1, partColor, overlay, light);
                     breastBottom(out, p, x0, x1, y1, z0, z1, u0, v0, u1, v1, partColor, overlay, light);
                  }

                  stack.popPose();
               }
            }

            stack.popPose();
         }
      }
   }

   // $VF: Could not verify finally blocks. A semaphore variable has been added to preserve control flow.
   // Please report this to the Vineflower issue tracker, at https://github.com/Vineflower/vineflower/issues with a copy of the class file (if you have the rights to distribute it!)
   protected void renderContinuousRig(PoseStack stack, VertexConsumer out, int light, int overlay, int color) {
      NpcBone[] limbBones = new NpcBone[]{
         NpcBone.RIGHT_ARM,
         NpcBone.RIGHT_FOREARM,
         NpcBone.LEFT_ARM,
         NpcBone.LEFT_FOREARM,
         NpcBone.RIGHT_LEG,
         NpcBone.RIGHT_SHIN,
         NpcBone.LEFT_LEG,
         NpcBone.LEFT_SHIN
      };
      List<ModelPart> hidden = new ArrayList<>();

      for (NpcBone id : limbBones) {
         ModelPart part = this.bonePart(id);
         hidden.add(part);
         hidden.add(part.getChild("outer"));
      }

      hidden.add(this.rightElbowJoint);
      hidden.add(this.leftElbowJoint);
      hidden.add(this.rightKneeJoint);
      hidden.add(this.leftKneeJoint);
      boolean[] previous = new boolean[hidden.size()];

      for (int i = 0; i < hidden.size(); i++) {
         previous[i] = hidden.get(i).skipDraw;
         hidden.get(i).skipDraw = true;
      }

      boolean var16 = false /* VF: Semaphore variable */;

      try {
         var16 = true;
         this.root.render(stack, out, light, overlay, color);
         var16 = false;
      } finally {
         if (var16) {
            for (int i = 0; i < hidden.size(); i++) {
               hidden.get(i).skipDraw = previous[i];
            }
         }
      }

      for (int i = 0; i < hidden.size(); i++) {
         hidden.get(i).skipDraw = previous[i];
      }

      this.renderContinuousLimb(stack, out, NpcBone.RIGHT_ARM, NpcBone.RIGHT_FOREARM, true, true, 40, 16, 40, 32, light, overlay, color);
      this.renderContinuousLimb(stack, out, NpcBone.LEFT_ARM, NpcBone.LEFT_FOREARM, true, false, 32, 48, 48, 48, light, overlay, color);
      this.renderContinuousLimb(stack, out, NpcBone.RIGHT_LEG, NpcBone.RIGHT_SHIN, false, true, 0, 16, 0, 32, light, overlay, color);
      this.renderContinuousLimb(stack, out, NpcBone.LEFT_LEG, NpcBone.LEFT_SHIN, false, false, 16, 48, 0, 48, light, overlay, color);
   }

   protected void renderContinuousLimb(
      PoseStack stack,
      VertexConsumer out,
      NpcBone upperId,
      NpcBone lowerId,
      boolean arm,
      boolean right,
      int u,
      int v,
      int outerU,
      int outerV,
      int light,
      int overlay,
      int color
   ) {
      ModelPart upper = this.bonePart(upperId);
      ModelPart lower = this.bonePart(lowerId);
      if (this.root.visible && upper.visible && lower.visible && (!upper.skipDraw || !lower.skipDraw)) {
         if (!arm || this.bonePart(NpcBone.BODY).visible && this.bonePart(NpcBone.CHEST).visible) {
            stack.pushPose();

            try {
               this.root.translateAndRotate(stack);
               if (arm) {
                  this.bonePart(NpcBone.BODY).translateAndRotate(stack);
                  this.bonePart(NpcBone.CHEST).translateAndRotate(stack);
               }

               upper.translateAndRotate(stack);
               float w = arm ? this.continuousArmWidth : 4.0F;
               float x = arm ? (right ? 1.0F - w : -1.0F) : -2.0F;
               float tw = arm ? this.continuousArmTextureWidth : 4.0F;
               float depth = arm && this.currentModelShape != null ? this.currentModelShape.armDepth : 4.0F;
               ContinuousLimbMesh.render(stack, out, lower, x, w, depth, arm ? -2.0F : 0.0F, tw, u, v, 0.0F, light, overlay, color);
               if (upper.getChild("outer").visible && !upper.getChild("outer").skipDraw) {
                  ContinuousLimbMesh.render(stack, out, lower, x, w, depth, arm ? -2.0F : 0.0F, tw, outerU, outerV, 0.25F, light, overlay, color);
               }
            } finally {
               stack.popPose();
            }
         }
      }
   }

   protected void renderWaistConnector(PoseStack stack, VertexConsumer out, int light, int overlay, int color) {
      ModelPart body = this.bones.get(NpcBone.BODY.id);
      ModelPart chest = this.bones.get(NpcBone.CHEST.id);
      if (this.root.visible && body != null && chest != null && !body.skipDraw && !chest.skipDraw && body.visible && chest.visible) {
         float waistHalf = this.currentModelShape != null && this.currentModelShape.hasCustomTorso() ? this.currentModelShape.waistWidth * 0.5F : 4.0F;
         stack.pushPose();
         this.root.translateAndRotate(stack);
         body.translateAndRotate(stack);
         float inset = 0.002F;
         JointSeamMesh.render(stack, out, chest, -6.0F, -waistHalf + 0.002F, waistHalf - 0.002F, -1.998F, 1.998F, 16.0F, 25.5F, 8.0F, light, overlay, color);
         boolean shapedOuter = this.currentModelShape != null && this.currentModelShape.hasCustomTorso() && this.currentModelShape.shapeSecondLayer;
         float outerHalf = (shapedOuter ? waistHalf : 4.0F) + 0.25F;
         JointSeamMesh.render(stack, out, chest, -6.0F, -outerHalf + 0.002F, outerHalf - 0.002F, -2.248F, 2.248F, 16.0F, 41.5F, 8.0F, light, overlay, color);
         stack.popPose();
      }
   }

   protected void renderPelvisBottom(PoseStack stack, VertexConsumer out, int light, int overlay, int color) {
      ModelPart body = this.bonePart(NpcBone.BODY);
      if (this.root.visible && body != null && body.visible && !body.skipDraw) {
         boolean shaped = this.currentModelShape != null && this.currentModelShape.hasCustomTorso();
         if (shaped || this.bodyBase.visible) {
            float half = shaped ? this.currentModelShape.hipWidth * 0.5F : 4.0F;
            stack.pushPose();
            this.root.translateAndRotate(stack);
            body.translateAndRotate(stack);
            breastBottom(out, stack.last(), -half, half, 0.0F, -2.0F, 2.0F, 28.0F, 20.0F, 36.0F, 16.0F, color, overlay, light);
            stack.popPose();
         }
      }
   }

   protected void renderShapedTorsoBase(PoseStack stack, VertexConsumer out, int light, int overlay, int color) {
      if (this.currentModelShape != null && this.currentModelShape.hasCustomTorso()) {
         ModelPart body = this.bones.get(NpcBone.BODY.id);
         ModelPart chest = this.bones.get(NpcBone.CHEST.id);
         if (body != null) {
            if (body.visible && !body.skipDraw) {
               stack.pushPose();
               this.root.translateAndRotate(stack);
               body.translateAndRotate(stack);
               renderTorsoFrustum(
                  this.dentBuffer(stack, out, "body"),
                  stack.last(),
                  light,
                  overlay,
                  color,
                  this.currentModelShape.waistWidth * 0.5F,
                  this.currentModelShape.hipWidth * 0.5F,
                  -6.0F,
                  0.0F,
                  -2.0F,
                  2.0F,
                  20.0F,
                  28.0F,
                  26.0F,
                  32.0F,
                  32.0F,
                  40.0F,
                  16.0F,
                  20.0F,
                  28.0F,
                  32.0F,
                  false,
                  false
               );
               stack.popPose();
            }

            if (chest != null && chest.visible && !chest.skipDraw) {
               stack.pushPose();
               this.root.translateAndRotate(stack);
               body.translateAndRotate(stack);
               chest.translateAndRotate(stack);
               renderTorsoFrustum(
                  this.dentBuffer(stack, out, "chest"),
                  stack.last(),
                  light,
                  overlay,
                  color,
                  this.currentModelShape.torsoWidth * 0.5F,
                  this.currentModelShape.waistWidth * 0.5F,
                  -6.0F,
                  0.0F,
                  -2.0F,
                  2.0F,
                  20.0F,
                  28.0F,
                  20.0F,
                  26.0F,
                  32.0F,
                  40.0F,
                  16.0F,
                  20.0F,
                  28.0F,
                  32.0F,
                  true,
                  false
               );
               stack.popPose();
            }
         }
      }
   }

   protected void renderAdaptiveChestOuter(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color) {
      if (this.currentModelShape != null && this.currentModelShape.hasCustomTorso() && this.currentModelShape.shapeSecondLayer) {
         this.renderShapedTorsoOuter(poseStack, buffer, light, overlay, color);
      } else if (!this.currentSkinLayers3d) {
         ModelPart body = this.bones.get(NpcBone.BODY.id);
         ModelPart chest = this.bones.get(NpcBone.CHEST.id);
         if (body != null && chest != null) {
            if (body.visible && !body.skipDraw) {
               poseStack.pushPose();
               this.root.translateAndRotate(poseStack);
               body.translateAndRotate(poseStack);
               Pose lowerPose = poseStack.last();
               buffer = this.dentBuffer(poseStack, buffer, "body");
               float x0 = -4.25F;
               float x1 = 4.25F;
               float y0 = -6.0F;
               float y1 = 0.0F;
               float z0 = -2.25F;
               float z1 = 2.25F;
               breastFront(buffer, lowerPose, x0, x1, y0, y1, z0, 20.0F, 42.0F, 28.0F, 48.0F, color, overlay, light);
               breastBack(buffer, lowerPose, x0, x1, y0, y1, z1, 32.0F, 42.0F, 40.0F, 48.0F, color, overlay, light);
               breastWest(buffer, lowerPose, x0, y0, y1, z0, z1, 16.0F, 42.0F, 20.0F, 48.0F, color, overlay, light);
               breastEast(buffer, lowerPose, x1, y0, y1, z0, z1, 28.0F, 42.0F, 32.0F, 48.0F, color, overlay, light);
               poseStack.popPose();
            }

            if (chest.visible && !chest.skipDraw) {
               poseStack.pushPose();
               this.root.translateAndRotate(poseStack);
               body.translateAndRotate(poseStack);
               chest.translateAndRotate(poseStack);
               Pose pose = poseStack.last();
               buffer = this.dentBuffer(poseStack, buffer, "chest");
               float x0 = -4.25F;
               float x1 = 4.25F;
               float y0 = -6.0F;
               float y1 = 0.0F;
               float z0 = -2.25F;
               float z1 = 2.25F;
               breastBack(buffer, pose, x0, x1, y0, y1, z1, 32.0F, 36.0F, 40.0F, 42.0F, color, overlay, light);
               breastWest(buffer, pose, x0, y0, y1, z0, z1, 16.0F, 36.0F, 20.0F, 42.0F, color, overlay, light);
               breastEast(buffer, pose, x1, y0, y1, z0, z1, 28.0F, 36.0F, 32.0F, 42.0F, color, overlay, light);
               breastTop(buffer, pose, x0, x1, y0, z0, z1, 20.0F, 32.0F, 28.0F, 36.0F, color, overlay, light);
               int scale = BreastData.safePixelScale(this.currentSkinScale);
               int width = 8 * scale;
               int rows = 6 * scale;
               float step = 1.0F / (float)scale;

               for (int row = 0; row < rows; row++) {
                  for (int px = 0; px < width; px++) {
                     boolean followsBreast = this.adaptiveChestOuter
                        && this.currentBreastData != null
                        && row >= scale
                        && (!this.currentBreastData.pixelVolume || this.currentBreastData.volumePixelAt(px, row - scale, scale));
                     if (!followsBreast) {
                        float cellX0 = -4.0F + (float)px * step;
                        float cellX1 = cellX0 + step;
                        if (px == 0) {
                           cellX0 -= 0.25F;
                        }

                        if (px == width - 1) {
                           cellX1 += 0.25F;
                        }

                        float cellY0 = y0 + (float)row * step;
                        float cellY1 = cellY0 + step;
                        breastFront(
                           buffer,
                           pose,
                           cellX0,
                           cellX1,
                           cellY0,
                           cellY1,
                           z0,
                           20.0F + (float)px * step,
                           36.0F + (float)row * step,
                           20.0F + ((float)px + 1.0F) * step,
                           36.0F + ((float)row + 1.0F) * step,
                           color,
                           overlay,
                           light
                        );
                        if (row == rows - 1) {
                           breastBottom(
                              buffer,
                              pose,
                              cellX0,
                              cellX1,
                              cellY1,
                              z0,
                              -2.0F,
                              20.0F + (float)px * step,
                              36.0F + (float)row * step,
                              20.0F + ((float)px + 1.0F) * step,
                              36.0F + ((float)row + 1.0F) * step,
                              color,
                              overlay,
                              light
                           );
                        }
                     }
                  }
               }

               poseStack.popPose();
            }
         }
      }
   }

   protected void renderShapedTorsoOuter(PoseStack stack, VertexConsumer out, int light, int overlay, int color) {
      ModelPart body = this.bones.get(NpcBone.BODY.id);
      ModelPart chest = this.bones.get(NpcBone.CHEST.id);
      if (body != null && this.currentModelShape != null) {
         if (body.visible && !body.skipDraw) {
            stack.pushPose();
            this.root.translateAndRotate(stack);
            body.translateAndRotate(stack);
            renderTorsoFrustum(
               this.dentBuffer(stack, out, "body"),
               stack.last(),
               light,
               overlay,
               color,
               this.currentModelShape.waistWidth * 0.5F + 0.25F,
               this.currentModelShape.hipWidth * 0.5F + 0.25F,
               -6.0F,
               0.0F,
               -2.25F,
               2.25F,
               20.0F,
               28.0F,
               42.0F,
               48.0F,
               32.0F,
               40.0F,
               16.0F,
               20.0F,
               28.0F,
               32.0F,
               false,
               false
            );
            stack.popPose();
         }

         if (chest != null && chest.visible && !chest.skipDraw) {
            stack.pushPose();
            this.root.translateAndRotate(stack);
            body.translateAndRotate(stack);
            chest.translateAndRotate(stack);
            Pose pose = stack.last();
            float top = this.currentModelShape.torsoWidth * 0.5F + 0.25F;
            float waist = this.currentModelShape.waistWidth * 0.5F + 0.25F;
            out = this.dentBuffer(stack, out, "chest");
            this.renderShapedChestOuterFront(out, pose, light, overlay, color, top, waist);
            renderTorsoFrustum(
               out,
               pose,
               light,
               overlay,
               color,
               top,
               waist,
               -6.0F,
               0.0F,
               -2.25F,
               2.25F,
               20.0F,
               28.0F,
               36.0F,
               42.0F,
               32.0F,
               40.0F,
               16.0F,
               20.0F,
               28.0F,
               32.0F,
               true,
               true
            );
            stack.popPose();
         }
      }
   }

   protected void renderShapedChestOuterFront(VertexConsumer out, Pose pose, int light, int overlay, int color, float topHalf, float bottomHalf) {
      int scale = BreastData.safePixelScale(this.currentSkinScale);
      int width = 8 * scale;
      int rows = 6 * scale;
      float step = 1.0F / (float)scale;

      for (int row = 0; row < rows; row++) {
         float rowT0 = (float)row / (float)rows;
         float rowT1 = ((float)row + 1.0F) / (float)rows;
         float h0 = lerp(rowT0, topHalf, bottomHalf);
         float h1 = lerp(rowT1, topHalf, bottomHalf);
         float y0 = -6.0F + (float)row * step;
         float y1 = y0 + step;

         for (int px = 0; px < width; px++) {
            boolean followsBreast = this.adaptiveChestOuter
               && this.currentBreastData != null
               && row >= scale
               && (!this.currentBreastData.pixelVolume || this.currentBreastData.volumePixelAt(px, row - scale, scale));
            if (!followsBreast) {
               float f0 = (float)px / (float)width;
               float f1 = ((float)px + 1.0F) / (float)width;
               float reference = Math.max(4.25F, Math.max(topHalf, bottomHalf));
               float xt0 = Mth.clamp(lerp(f0, -reference, reference), -h0, h0);
               float xt1 = Mth.clamp(lerp(f1, -reference, reference), -h0, h0);
               float xb0 = Mth.clamp(lerp(f0, -reference, reference), -h1, h1);
               float xb1 = Mth.clamp(lerp(f1, -reference, reference), -h1, h1);
               if (xt0 != xt1 || xb0 != xb1) {
                  torsoFront(
                     out,
                     pose,
                     xt0,
                     xt1,
                     xb0,
                     xb1,
                     y0,
                     y1,
                     -2.25F,
                     20.0F + (float)px * step,
                     20.0F + ((float)px + 1.0F) * step,
                     36.0F + (float)row * step,
                     36.0F + ((float)row + 1.0F) * step,
                     reference,
                     color,
                     overlay,
                     light
                  );
               }
            }
         }
      }
   }

   protected static void renderTorsoFrustum(
      VertexConsumer out,
      Pose pose,
      int light,
      int overlay,
      int color,
      float topHalf,
      float bottomHalf,
      float y0,
      float y1,
      float z0,
      float z1,
      float frontU0,
      float frontU1,
      float v0,
      float v1,
      float backU0,
      float backU1,
      float westU0,
      float westU1,
      float eastU0,
      float eastU1,
      boolean topCap,
      boolean skipFront
   ) {
      if (!skipFront) {
         torsoFront(
            out,
            pose,
            -topHalf,
            topHalf,
            -bottomHalf,
            bottomHalf,
            y0,
            y1,
            z0,
            frontU0,
            frontU1,
            v0,
            v1,
            Math.max(z0 < -2.1F ? 4.25F : 4.0F, Math.max(topHalf, bottomHalf)),
            color,
            overlay,
            light
         );
      }

      torsoBack(
         out,
         pose,
         -topHalf,
         topHalf,
         -bottomHalf,
         bottomHalf,
         y0,
         y1,
         z1,
         backU0,
         backU1,
         v0,
         v1,
         Math.max(z1 > 2.1F ? 4.25F : 4.0F, Math.max(topHalf, bottomHalf)),
         color,
         overlay,
         light
      );
      torsoWest(out, pose, -topHalf, -bottomHalf, y0, y1, z0, z1, westU0, westU1, v0, v1, color, overlay, light);
      torsoEast(out, pose, topHalf, bottomHalf, y0, y1, z0, z1, eastU0, eastU1, v0, v1, color, overlay, light);
      if (topCap) {
         breastTop(out, pose, -topHalf, topHalf, y0, z0, z1, frontU0, v0 - 4.0F, frontU1, v0, color, overlay, light);
      }
   }

   protected static void torsoFront(
      VertexConsumer b,
      Pose p,
      float xt0,
      float xt1,
      float xb0,
      float xb1,
      float y0,
      float y1,
      float z,
      float u0,
      float u1,
      float v0,
      float v1,
      float referenceHalf,
      int color,
      int overlay,
      int light
   ) {
      Vector3f n = p.transformNormal(0.0F, 0.0F, -1.0F, new Vector3f());
      float density = 4.0F / referenceHalf;
      breastEdgeVertex(b, p.pose(), xt1, y0, z, 24.0F + xt1 * density, v0, color, overlay, light, n);
      breastEdgeVertex(b, p.pose(), xt0, y0, z, 24.0F + xt0 * density, v0, color, overlay, light, n);
      breastEdgeVertex(b, p.pose(), xb0, y1, z, 24.0F + xb0 * density, v1, color, overlay, light, n);
      breastEdgeVertex(b, p.pose(), xb1, y1, z, 24.0F + xb1 * density, v1, color, overlay, light, n);
   }

   protected static void torsoBack(
      VertexConsumer b,
      Pose p,
      float xt0,
      float xt1,
      float xb0,
      float xb1,
      float y0,
      float y1,
      float z,
      float u0,
      float u1,
      float v0,
      float v1,
      float referenceHalf,
      int color,
      int overlay,
      int light
   ) {
      Vector3f n = p.transformNormal(0.0F, 0.0F, 1.0F, new Vector3f());
      float density = 4.0F / referenceHalf;
      breastEdgeVertex(b, p.pose(), xt0, y0, z, 36.0F + xt0 * density, v0, color, overlay, light, n);
      breastEdgeVertex(b, p.pose(), xt1, y0, z, 36.0F + xt1 * density, v0, color, overlay, light, n);
      breastEdgeVertex(b, p.pose(), xb1, y1, z, 36.0F + xb1 * density, v1, color, overlay, light, n);
      breastEdgeVertex(b, p.pose(), xb0, y1, z, 36.0F + xb0 * density, v1, color, overlay, light, n);
   }

   protected static void torsoWest(
      VertexConsumer b,
      Pose p,
      float xt,
      float xb,
      float y0,
      float y1,
      float z0,
      float z1,
      float u0,
      float u1,
      float v0,
      float v1,
      int color,
      int overlay,
      int light
   ) {
      Vector3f local = new Vector3f(-(y1 - y0), xb - xt, 0.0F).normalize();
      Vector3f n = p.transformNormal(local.x(), local.y(), 0.0F, new Vector3f());
      breastEdgeVertex(b, p.pose(), xt, y0, z0, u0, v0, color, overlay, light, n);
      breastEdgeVertex(b, p.pose(), xt, y0, z1, u1, v0, color, overlay, light, n);
      breastEdgeVertex(b, p.pose(), xb, y1, z1, u1, v1, color, overlay, light, n);
      breastEdgeVertex(b, p.pose(), xb, y1, z0, u0, v1, color, overlay, light, n);
   }

   protected static void torsoEast(
      VertexConsumer b,
      Pose p,
      float xt,
      float xb,
      float y0,
      float y1,
      float z0,
      float z1,
      float u0,
      float u1,
      float v0,
      float v1,
      int color,
      int overlay,
      int light
   ) {
      Vector3f local = new Vector3f(y1 - y0, -(xb - xt), 0.0F).normalize();
      Vector3f n = p.transformNormal(local.x(), local.y(), 0.0F, new Vector3f());
      breastEdgeVertex(b, p.pose(), xt, y0, z1, u1, v0, color, overlay, light, n);
      breastEdgeVertex(b, p.pose(), xt, y0, z0, u0, v0, color, overlay, light, n);
      breastEdgeVertex(b, p.pose(), xb, y1, z0, u0, v1, color, overlay, light, n);
      breastEdgeVertex(b, p.pose(), xb, y1, z1, u1, v1, color, overlay, light, n);
   }

   protected void renderWaistBacking(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color) {
      if (this.currentModelShape == null || !this.currentModelShape.hasCustomTorso()) {
         SkinCache.WaistProfile profile = SkinCache.waistProfile(this.currentSkinKey);
         if (profile != null) {
            ModelPart body = this.bones.get(NpcBone.BODY.id);
            ModelPart chest = this.bones.get(NpcBone.CHEST.id);
            if (body != null && body.visible) {
               int halfRows = 6 * profile.scale();
               if (profile.lowerCut() && !body.skipDraw) {
                  poseStack.pushPose();
                  this.root.translateAndRotate(poseStack);
                  body.translateAndRotate(poseStack);
                  renderTorsoBlocks(buffer, poseStack.last(), light, overlay, color, profile, halfRows, halfRows);
                  poseStack.popPose();
               }

               if (profile.upperCut() && chest != null && chest.visible && !chest.skipDraw) {
                  poseStack.pushPose();
                  this.root.translateAndRotate(poseStack);
                  body.translateAndRotate(poseStack);
                  chest.translateAndRotate(poseStack);
                  renderTorsoBlocks(buffer, poseStack.last(), light, overlay, color, profile, 0, halfRows);
                  poseStack.popPose();
               }
            }
         }
      }
   }

   protected static void renderTorsoBlocks(
      VertexConsumer buffer, Pose pose, int light, int overlay, int color, SkinCache.WaistProfile profile, int rowStart, int rowCount
   ) {
      int scale = profile.scale();
      int width = 8 * scale;
      float step = 1.0F / (float)scale;
      float[] vs = profile.sampleV();
      int[] first = profile.firstPixel();
      int[] last = profile.lastPixel();
      float[][] fillU = profile.fillU();

      for (int localRow = 0; localRow < rowCount; localRow++) {
         int row = rowStart + localRow;
         if (last[row] >= first[row]) {
            float y0 = -6.0F + (float)localRow * step;
            float y1 = y0 + step;

            for (int px = first[row]; px <= last[row]; px++) {
               float x0 = -4.0F + (float)px * step;
               float x1 = x0 + step;
               float u = fillU[row][px];
               float backU = 40.0F - ((float)px + 0.5F) * step;
               float v = vs[row];
               breastFront(buffer, pose, x0, x1, y0, y1, -2.002F, u, v, u, v, color, overlay, light);
               breastBack(buffer, pose, x0, x1, y0, y1, 2.002F, backU, v, backU, v, color, overlay, light);
               if (px == first[row]) {
                  renderWaistSide(buffer, pose, x0, y0, y1, v, false, scale, color, overlay, light);
               }

               if (px == last[row]) {
                  renderWaistSide(buffer, pose, x1, y0, y1, v, true, scale, color, overlay, light);
               }

               if (localRow == 0 || !waistPixelPresent(profile, row - 1, px)) {
                  breastTop(buffer, pose, x0, x1, y0, -2.0F, 2.0F, u, v, u, v, color, overlay, light);
               }

               if (localRow + 1 == rowCount || !waistPixelPresent(profile, row + 1, px)) {
                  breastBottom(buffer, pose, x0, x1, y1, -2.0F, 2.0F, u, v, u, v, color, overlay, light);
               }
            }
         }
      }
   }

   protected static void renderWaistSide(
      VertexConsumer buffer, Pose pose, float x, float y0, float y1, float v, boolean east, int scale, int color, int overlay, int light
   ) {
      float step = 1.0F / (float)scale;
      int depthPixels = 4 * scale;

      for (int depth = 0; depth < depthPixels; depth++) {
         float z0 = -2.0F + (float)depth * step;
         float z1 = z0 + step;
         float sideU = east ? 28.0F + ((float)depth + 0.5F) * step : 20.0F - ((float)depth + 0.5F) * step;
         if (east) {
            breastEast(buffer, pose, x, y0, y1, z0, z1, sideU, v, sideU, v, color, overlay, light);
         } else {
            breastWest(buffer, pose, x, y0, y1, z0, z1, sideU, v, sideU, v, color, overlay, light);
         }
      }
   }

   protected static boolean waistPixelPresent(SkinCache.WaistProfile profile, int row, int pixel) {
      return row >= 0 && row < profile.firstPixel().length && pixel >= profile.firstPixel()[row] && pixel <= profile.lastPixel()[row];
   }

   protected void renderBreastGeometry(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color) {
      if (this.rightBreast.visible || this.leftBreast.visible) {
         ModelPart body = this.bones.get(NpcBone.BODY.id);
         ModelPart chest = this.bones.get(NpcBone.CHEST.id);
         if (body != null && chest != null) {
            if (this.rightBreast.visible && !this.rightBreast.skipDraw) {
               this.renderBreastHalf(
                  poseStack,
                  buffer,
                  light,
                  overlay,
                  color,
                  body,
                  chest,
                  this.rightBreast,
                  true,
                  this.rightBreastJacket.visible && !this.rightBreastJacket.skipDraw
               );
            }

            if (this.leftBreast.visible && !this.leftBreast.skipDraw) {
               this.renderBreastHalf(
                  poseStack,
                  buffer,
                  light,
                  overlay,
                  color,
                  body,
                  chest,
                  this.leftBreast,
                  false,
                  this.leftBreastJacket.visible && !this.leftBreastJacket.skipDraw
               );
            }
         }
      }
   }

   protected void renderBreastHalf(
      PoseStack poseStack,
      VertexConsumer buffer,
      int light,
      int overlay,
      int color,
      ModelPart body,
      ModelPart chest,
      ModelPart breast,
      boolean right,
      boolean jacket
   ) {
      poseStack.pushPose();
      this.root.translateAndRotate(poseStack);
      body.translateAndRotate(poseStack);
      chest.translateAndRotate(poseStack);
      breast.translateAndRotate(poseStack);
      float x0 = right ? -4.0F : 0.0F;
      float x1 = right ? 0.0F : 4.0F;
      Pose pose = poseStack.last();
      int texU = right ? 16 : 20;
      boolean pixelVolume = this.currentBreastData != null && this.currentBreastData.pixelVolume;
      if (pixelVolume) {
         renderBreastPixels(buffer, pose, light, overlay, color, right, this.currentBreastData, -4.0F, 0.0F, 20.0F, 21.0F, this.currentSkinScale);
      } else {
         renderBreastBase(buffer, pose, light, overlay, color, x0, x1, texU);
      }

      if (jacket && !this.currentSkinLayers3d) {
         if (pixelVolume) {
            renderBreastPixelLayerFlat(buffer, pose, light, overlay, color, right, this.currentBreastData, this.currentSkinScale);
         } else {
            renderBreastJacketFlat(buffer, pose, light, overlay, color, right);
         }
      }

      poseStack.popPose();
   }

   protected static void renderBreastPixelLayerFlat(
      VertexConsumer buffer, Pose pose, int light, int overlay, int color, boolean right, BreastData settings, int textureScale
   ) {
      int scale = BreastData.safePixelScale(textureScale);
      int halfWidth = 4 * scale;
      int startX = right ? 0 : halfWidth;
      float step = 1.0F / (float)scale;
      int height = 5 * scale;

      for (int y = 0; y < height; y++) {
         for (int localX = 0; localX < halfWidth; localX++) {
            int maskX = startX + localX;
            if (settings.volumePixelAt(maskX, y, scale)) {
               float x0 = right ? -4.0F + (float)localX * step : (float)localX * step;
               float y0 = (float)y * step;
               float x1 = x0 + step;
               float y1 = y0 + step;
               float u0 = 20.0F + (float)maskX * step;
               float v0 = 37.0F + (float)y * step;
               float u1 = u0 + step;
               float v1 = v0 + step;
               float zFront = -4.12F;
               float zBack = -4.002F;
               breastFront(buffer, pose, x0, x1, y0, y1, zFront, u0, v0, u1, v1, color, overlay, light);
               if (y == 0 || !settings.volumePixelAt(maskX, y - 1, scale)) {
                  breastTop(buffer, pose, x0, x1, y0, zFront, zBack, u0, v0, u1, v1, color, overlay, light);
               }

               if (y + 1 == height || !settings.volumePixelAt(maskX, y + 1, scale)) {
                  breastBottom(buffer, pose, x0, x1, y1 + 0.01F, zFront, 0.0F, u0, v0, u1, v1, color, overlay, light);
               }

               if (maskX == 0 || !settings.volumePixelAt(maskX - 1, y, scale)) {
                  breastWest(buffer, pose, x0, y0, y1, zFront, zBack, u0, v0, u1, v1, color, overlay, light);
               }

               if (maskX + 1 == 8 * scale || !settings.volumePixelAt(maskX + 1, y, scale)) {
                  breastEast(buffer, pose, x1, y0, y1, zFront, zBack, u0, v0, u1, v1, color, overlay, light);
               }
            }
         }
      }
   }

   protected static void renderBreastPixels(
      VertexConsumer buffer,
      Pose pose,
      int light,
      int overlay,
      int color,
      boolean right,
      BreastData settings,
      float zFront,
      float zBack,
      float textureU,
      float textureV,
      int textureScale
   ) {
      int scale = BreastData.safePixelScale(textureScale);
      int halfWidth = 4 * scale;
      int startX = right ? 0 : halfWidth;
      float step = 1.0F / (float)scale;

      for (int y = 0; y < 5 * scale; y++) {
         for (int localX = 0; localX < halfWidth; localX++) {
            int maskX = startX + localX;
            if (settings.volumePixelAt(maskX, y, scale)) {
               float x0 = right ? -4.0F + (float)localX * step : (float)localX * step;
               float x1 = x0 + step;
               renderBreastVoxel(
                  buffer,
                  pose,
                  light,
                  overlay,
                  color,
                  x0,
                  x1,
                  (float)y * step,
                  ((float)y + 1.0F) * step,
                  zFront,
                  zBack,
                  textureU + (float)maskX * step,
                  textureV + (float)y * step,
                  step
               );
            }
         }
      }
   }

   public void renderBreastSkinLayer3D(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color, int textureScale) {
      int scale = BreastData.safePixelScale(textureScale);
      if (this.rightBreast.visible && this.rightBreastJacket.visible && !this.rightBreast.skipDraw) {
         this.renderBreastSkinHalf3D(poseStack, buffer, light, overlay, color, this.rightBreast, true, scale);
      }

      if (this.leftBreast.visible && this.leftBreastJacket.visible && !this.leftBreast.skipDraw) {
         this.renderBreastSkinHalf3D(poseStack, buffer, light, overlay, color, this.leftBreast, false, scale);
      }
   }

   protected void renderBreastSkinHalf3D(
      PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color, ModelPart breast, boolean right, int scale
   ) {
      ModelPart body = this.bones.get(NpcBone.BODY.id);
      ModelPart chest = this.bones.get(NpcBone.CHEST.id);
      if (body != null && chest != null) {
         poseStack.pushPose();
         this.root.translateAndRotate(poseStack);
         body.translateAndRotate(poseStack);
         chest.translateAndRotate(poseStack);
         breast.translateAndRotate(poseStack);
         Pose pose = poseStack.last();
         float step = 1.0F / (float)scale;
         int pixelW = 4 * scale;
         int pixelH = 5 * scale;
         float baseX = right ? -4.0F : 0.0F;
         float baseU = right ? 20.0F : 24.0F;
         float zFront = -4.24F;
         float zBack = -4.002F;
         BreastData settings = this.currentBreastData;

         for (int py = 0; py < pixelH; py++) {
            float y0 = (float)py * step;
            float y1 = ((float)py + 1.0F) * step + (py == pixelH - 1 ? 0.25F : 0.0F);
            float v = 37.0F + (float)py * step;

            for (int px = 0; px < pixelW; px++) {
               int maskX = (right ? 0 : 4 * scale) + px;
               if (settings == null || !settings.pixelVolume || settings.volumePixelAt(maskX, py, scale)) {
                  float x0 = baseX + (float)px * step;
                  float x1 = baseX + ((float)px + 1.0F) * step;
                  if (right && px == 0) {
                     x0 -= 0.2F;
                  }

                  if (!right && px == pixelW - 1) {
                     x1 += 0.2F;
                  }

                  float u = baseU + (float)px * step;
                  renderBreastVoxel(buffer, pose, light, overlay, color, x0, x1, y0, y1, zFront, zBack, u, v, step);
                  if (py == pixelH - 1 || settings != null && settings.pixelVolume && !settings.volumePixelAt(maskX, py + 1, scale)) {
                     breastBottom(buffer, pose, x0, x1, y1 + 0.01F, zBack, 0.0F, u, v, u + step, v + step, color, overlay, light);
                  }
               }
            }
         }

         poseStack.popPose();
      }
   }

   protected static void renderBreastVoxel(
      VertexConsumer buffer,
      Pose pose,
      int light,
      int overlay,
      int color,
      float x0,
      float x1,
      float y0,
      float y1,
      float zFront,
      float zBack,
      float u,
      float v,
      float texel
   ) {
      breastFront(buffer, pose, x0, x1, y0, y1, zFront, u, v, u + texel, v + texel, color, overlay, light);
      breastTop(buffer, pose, x0, x1, y0, zFront, zBack, u, v, u + texel, v + texel, color, overlay, light);
      breastBottom(buffer, pose, x0, x1, y1, zFront, zBack, u, v, u + texel, v + texel, color, overlay, light);
      breastWest(buffer, pose, x0, y0, y1, zFront, zBack, u, v, u + texel, v + texel, color, overlay, light);
      breastEast(buffer, pose, x1, y0, y1, zFront, zBack, u, v, u + texel, v + texel, color, overlay, light);
   }

   protected static void renderBreastBase(VertexConsumer buffer, Pose pose, int light, int overlay, int color, float x0, float x1, int texU) {
      float y0 = 0.0F;
      float y1 = 5.0F;
      float zFront = -4.0F;
      float zBack = 0.0F;
      breastFront(buffer, pose, x0, x1, y0, y1, zFront, (float)texU + 4.0F, 21.0F, (float)texU + 8.0F, 26.0F, color, overlay, light);
      breastBottom(buffer, pose, x0, x1, y1, zFront, zBack, (float)texU + 4.0F, 25.0F, (float)texU + 8.0F, 26.0F, color, overlay, light);
      breastTop(buffer, pose, x0, x1, y0, zFront, zBack, (float)texU + 4.0F, 25.0F, (float)texU + 8.0F, 27.0F, color, overlay, light);
      breastWest(buffer, pose, x0, y0, y1, zFront, zBack, (float)texU, 21.0F, (float)texU + 4.0F, 26.0F, color, overlay, light);
      breastEast(buffer, pose, x1, y0, y1, zFront, zBack, (float)texU + 8.0F, 21.0F, (float)texU + 12.0F, 26.0F, color, overlay, light);
   }

   protected static void renderBreastJacketFlat(VertexConsumer buffer, Pose pose, int light, int overlay, int color, boolean right) {
      float x0 = right ? -4.0F : 0.0F;
      float x1 = right ? 0.0F : 4.0F;
      float u0 = right ? 20.0F : 24.0F;
      float zFront = -4.12F;
      float zBack = -4.002F;
      breastFront(buffer, pose, x0, x1, 0.0F, 5.0F, zFront, u0, 37.0F, u0 + 4.0F, 42.0F, color, overlay, light);
      breastTop(buffer, pose, x0, x1, 0.0F, zFront, zBack, u0, 37.0F, u0 + 4.0F, 38.0F, color, overlay, light);
      breastBottom(buffer, pose, x0, x1, 5.01F, zFront, 0.0F, u0, 41.0F, u0 + 4.0F, 42.0F, color, overlay, light);
      if (right) {
         breastWest(buffer, pose, x0, 0.0F, 5.0F, zFront, zBack, u0, 37.0F, u0 + 1.0F, 42.0F, color, overlay, light);
      } else {
         breastEast(buffer, pose, x1, 0.0F, 5.0F, zFront, zBack, u0 + 3.0F, 37.0F, u0 + 4.0F, 42.0F, color, overlay, light);
      }
   }

   protected static void breastFront(
      VertexConsumer b, Pose p, float x0, float x1, float y0, float y1, float z, float u0, float v0, float u1, float v1, int color, int overlay, int light
   ) {
      Vector3f n = p.transformNormal(0.0F, 0.0F, -1.0F, new Vector3f());
      breastEdgeVertex(b, p.pose(), x1, y0, z, u1, v0, color, overlay, light, n);
      breastEdgeVertex(b, p.pose(), x0, y0, z, u0, v0, color, overlay, light, n);
      breastEdgeVertex(b, p.pose(), x0, y1, z, u0, v1, color, overlay, light, n);
      breastEdgeVertex(b, p.pose(), x1, y1, z, u1, v1, color, overlay, light, n);
   }

   protected static void breastBack(
      VertexConsumer b, Pose p, float x0, float x1, float y0, float y1, float z, float u0, float v0, float u1, float v1, int color, int overlay, int light
   ) {
      Vector3f n = p.transformNormal(0.0F, 0.0F, 1.0F, new Vector3f());
      breastEdgeVertex(b, p.pose(), x0, y0, z, u0, v0, color, overlay, light, n);
      breastEdgeVertex(b, p.pose(), x1, y0, z, u1, v0, color, overlay, light, n);
      breastEdgeVertex(b, p.pose(), x1, y1, z, u1, v1, color, overlay, light, n);
      breastEdgeVertex(b, p.pose(), x0, y1, z, u0, v1, color, overlay, light, n);
   }

   protected static void breastBottom(
      VertexConsumer b, Pose p, float x0, float x1, float y, float z0, float z1, float u0, float v0, float u1, float v1, int color, int overlay, int light
   ) {
      Vector3f n = p.transformNormal(0.0F, 1.0F, 0.0F, new Vector3f());
      breastEdgeVertex(b, p.pose(), x0, y, z0, u0, v0, color, overlay, light, n);
      breastEdgeVertex(b, p.pose(), x1, y, z0, u1, v0, color, overlay, light, n);
      breastEdgeVertex(b, p.pose(), x1, y, z1, u1, v1, color, overlay, light, n);
      breastEdgeVertex(b, p.pose(), x0, y, z1, u0, v1, color, overlay, light, n);
   }

   protected static void breastTop(
      VertexConsumer b, Pose p, float x0, float x1, float y, float z0, float z1, float u0, float v0, float u1, float v1, int color, int overlay, int light
   ) {
      Vector3f n = p.transformNormal(0.0F, -1.0F, 0.0F, new Vector3f());
      breastEdgeVertex(b, p.pose(), x0, y, z1, u0, v1, color, overlay, light, n);
      breastEdgeVertex(b, p.pose(), x1, y, z1, u1, v1, color, overlay, light, n);
      breastEdgeVertex(b, p.pose(), x1, y, z0, u1, v0, color, overlay, light, n);
      breastEdgeVertex(b, p.pose(), x0, y, z0, u0, v0, color, overlay, light, n);
   }

   protected static void breastWest(
      VertexConsumer b, Pose p, float x, float y0, float y1, float z0, float z1, float u0, float v0, float u1, float v1, int color, int overlay, int light
   ) {
      Vector3f n = p.transformNormal(-1.0F, 0.0F, 0.0F, new Vector3f());
      breastEdgeVertex(b, p.pose(), x, y0, z0, u0, v0, color, overlay, light, n);
      breastEdgeVertex(b, p.pose(), x, y0, z1, u1, v0, color, overlay, light, n);
      breastEdgeVertex(b, p.pose(), x, y1, z1, u1, v1, color, overlay, light, n);
      breastEdgeVertex(b, p.pose(), x, y1, z0, u0, v1, color, overlay, light, n);
   }

   protected static void breastEast(
      VertexConsumer b, Pose p, float x, float y0, float y1, float z0, float z1, float u0, float v0, float u1, float v1, int color, int overlay, int light
   ) {
      Vector3f n = p.transformNormal(1.0F, 0.0F, 0.0F, new Vector3f());
      breastEdgeVertex(b, p.pose(), x, y0, z1, u1, v0, color, overlay, light, n);
      breastEdgeVertex(b, p.pose(), x, y0, z0, u0, v0, color, overlay, light, n);
      breastEdgeVertex(b, p.pose(), x, y1, z0, u0, v1, color, overlay, light, n);
      breastEdgeVertex(b, p.pose(), x, y1, z1, u1, v1, color, overlay, light, n);
   }

   protected static void breastEdgeVertex(
      VertexConsumer buffer, Matrix4f matrix, float x, float y, float z, float u, float v, int color, int overlay, int light, Vector3f normal
   ) {
      Vector3f point = matrix.transformPosition(x / 16.0F, y / 16.0F, z / 16.0F, new Vector3f());
      buffer.addVertex(point.x(), point.y(), point.z(), color, u / 64.0F, v / 64.0F, overlay, light, normal.x(), normal.y(), normal.z());
   }

   static {
      for (int i = 0; i < 9; i++) {
         ARM_WIDTH_LAYERS[i] = new ModelLayerLocation(BodyRuntime.id("npc"), "arm_" + (20 + i * 2));
      }

      IDENTITY = new BoneTransform();
   }
}
