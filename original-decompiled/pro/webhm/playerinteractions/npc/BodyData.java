package pro.webhm.playerinteractions.npc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import pro.webhm.playerinteractions.adornments.AdornmentCodec;
import pro.webhm.playerinteractions.adornments.AdornmentProject;
import pro.webhm.playerinteractions.facial.FacialData;

public class BodyData {
   private static final Gson BODY_JSON = new GsonBuilder()
      .registerTypeAdapter(BoneTransform.class, new BoneTransformAdapter())
      .registerTypeAdapter(AdornmentProject.class, AdornmentCodec.ADAPTER)
      .create();
   public String skinUrl = "";
   public boolean slim = true;
   public float armWidth = 0.0F;
   public ModelShapeData modelShape = new ModelShapeData();
   public boolean skinLayers3d = false;
   public String blockbenchModel = "";
   public float scale = 1.0F;
   public boolean child = false;
   public float posOffsetY = 0.0F;
   public BreastData breast = new BreastData();
   public List<HeadDecorationData> headDecorations = new ArrayList<>();
   public AdornmentProject adornments = new AdornmentProject();
   public PortraitData portrait = new PortraitData();
   public FaceData face = new FaceData();
   public FacialData facial = new FacialData();
   public HeadDynamicsData headDynamics = new HeadDynamicsData();
   public NpcEquipment gear = new NpcEquipment();
   public Map<String, BoneTransform> pose = new HashMap<>();
   public String rootPivot = "center";
   public float pivotX;
   public float pivotY;
   public float pivotZ;

   public BoneTransform bone(NpcBone bone) {
      return this.pose.computeIfAbsent(bone.id, k -> new BoneTransform());
   }

   public float armWidthValue() {
      float value = this.armWidth > 0.0F ? this.armWidth : (this.slim ? 3.0F : 4.0F);
      return Math.max(2.0F, Math.min(4.0F, value));
   }

   public String bodyJson() {
      return BODY_JSON.toJson(this, BodyData.class);
   }

   public static BodyData fromBodyJson(String json) {
      BodyData body = (BodyData)BODY_JSON.fromJson(json, BodyData.class);
      if (body == null) {
         throw new IllegalArgumentException("Empty body preset");
      } else {
         body.normalizeBody();
         return body;
      }
   }

   public void normalizeAdultRig() {
      if (this.child && this.scale < 1.0F) {
         this.scale = 1.0F;
      }

      this.child = false;
   }

   public void normalizeBody() {
      if (this.adornments == null) {
         this.adornments = new AdornmentProject();
      }

      this.normalizeAdultRig();
      if (this.pose == null) {
         this.pose = new HashMap<>();
      }

      if (this.breast == null) {
         this.breast = new BreastData();
      }

      this.breast.normalizeRotations();
      if (this.modelShape == null) {
         this.modelShape = new ModelShapeData();
      }

      this.modelShape.normalize();
      if (this.face == null) {
         this.face = new FaceData();
      }

      this.face.normalize();
      if (this.facial == null) {
         this.facial = new FacialData();
      }

      this.facial.normalize();
      if (this.headDynamics == null) {
         this.headDynamics = new HeadDynamicsData();
      }

      this.headDynamics.normalize();
      if (this.headDecorations == null) {
         this.headDecorations = new ArrayList<>();
      }

      this.headDecorations.removeIf(Objects::isNull);
      if (this.headDecorations.size() > 64) {
         this.headDecorations = new ArrayList<>(this.headDecorations.subList(0, 64));
      }

      this.headDecorations.forEach(HeadDecorationData::normalize);
      if (this.gear == null) {
         this.gear = new NpcEquipment();
      }

      if (this.portrait == null) {
         this.portrait = new PortraitData();
      }

      if (this.blockbenchModel == null) {
         this.blockbenchModel = "";
      }

      if (this.skinUrl == null) {
         this.skinUrl = "";
      }
   }
}
