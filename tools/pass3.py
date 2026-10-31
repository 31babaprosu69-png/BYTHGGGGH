#!/usr/bin/env python3
"""Pass 3: vertex / model / misc 1.21 -> 1.20.1 API differences."""
import re, sys
from pathlib import Path
root = Path(sys.argv[1] if len(sys.argv) > 1 else "src/main/java")
P = root / "pro/webhm/playerinteractions"

def edit(rel, fn):
    p = P / rel
    t = o = p.read_text(encoding="utf-8")
    t = fn(t)
    if t == o:
        print("WARNING no change:", rel)
    p.write_text(t, encoding="utf-8")

def lines_fix(rel, targets):
    """targets: {line_no: (regex, repl)} applied to the 1-based line of the ORIGINAL file."""
    p = P / rel
    ls = p.read_text(encoding="utf-8").split("\n")
    for n, (rx, rep) in targets.items():
        new = re.sub(rx, rep, ls[n - 1])
        if new == ls[n - 1]:
            print("WARNING line unchanged:", rel, n)
        ls[n - 1] = new
    p.write_text("\n".join(ls), encoding="utf-8")

# ---- Java 21 SequencedCollection getFirst/getLast on Lists
FIRST = (r"([\w.]+)\.getFirst\(\)", r"\1.get(0)")
LAST = (r"([\w.]+)\.getLast\(\)", r"\1.get(\1.size() - 1)")
lines_fix("facial/FacialMesh.java", {53: LAST})
lines_fix("adornments/AdornmentProject.java", {67: LAST})
lines_fix("SessionState.java", {248: FIRST})
lines_fix("anim/NpcAnimation.java", {162: FIRST, 649: FIRST, 720: FIRST})
lines_fix("anim/SceneScaleKey.java", {48: FIRST, 49: FIRST})
lines_fix("anim/AnimationEntry.java", {17: FIRST})
lines_fix("adornments/AdornmentFrame.java", {156: FIRST})

# ---- model-part render with packed color (only the 4 reported sites)
lines_fix("client/SharedBodyModel.java", {
    808: (r"this\.root\.render\(", "Vtx.render(this.root, "),
    915: (r"this\.root\.render\(", "Vtx.render(this.root, "),
    928: (r"part\.render\(", "Vtx.render(part, "),
    1030: (r"this\.root\.render\(", "Vtx.render(this.root, "),
})

# ---- global textual fixes
IMPORT = "import pro.webhm.playerinteractions.compat.Vtx;"
for p in root.rglob("*.java"):
    if p.name == "Vtx.java":
        continue
    t = o = p.read_text(encoding="utf-8")
    t = t.replace(".transformNormal(", ".normal().transform(")
    # chained builder calls (line starts with '.')
    t = re.sub(r"(?m)^(\s+)\.addVertex\(", r"\1.vertex(", t)
    t = re.sub(r"\b(out|buffer|b)\.addVertex\(", r"Vtx.vertex(\1, ", t)
    t = t.replace(".setColor(", ".color(").replace(".setUv1(", ".overlayCoords(").replace(".setUv2(", ".uv2(").replace(".setUv(", ".uv(")
    t = re.sub(r"\.setNormal\(([^;]*)\);", r".normal(\1).endVertex();", t)
    t = re.sub(r"(\w+(?:\.getInstance\(\))?)\.getTimer\(\)\.getGameTimeDeltaPartialTick\((?:true|false)\)", r"\1.getFrameTime()", t)
    t = t.replace("event.getScrollDeltaY()", "event.getScroll" + "Delta()")
    t = re.sub(r"(?<!\(Object\))\((Camera|Entity|Player|LivingEntityRenderer)\)this\b", r"(\1)(Object)this", t) if "/mixin/" in str(p).replace("\\", "/") else t
    t = t.replace("stack.mulPose(rig.headMatrix())", "Vtx.mulPose(stack, rig.headMatrix())")
    t = t.replace("dimensions.width()", "dimensions.width").replace("dimensions.height()", "dimensions.height")
    t = t.replace("property.value()", "property.getValue()")
    t = t.replace("new Sound(this.location,", "new Sound(this.location.toString(),")
    if "Vtx." in t and "package pro.webhm.playerinteractions.compat;" not in t and IMPORT not in t:
        t = re.sub(r"(package [\w.]+;\n)", r"\1\n" + IMPORT, t, count=1)
    if t != o:
        p.write_text(t, encoding="utf-8")

# ---- custom VertexConsumer implementations: rename to 1.20.1 interface
SIG = [
    ("public VertexConsumer addVertex(float x, float y, float z) {",
     "public VertexConsumer vertex(double x0, double y0, double z0) {\n      float x = (float)x0;\n      float y = (float)y0;\n      float z = (float)z0;"),
    ("public VertexConsumer setColor(", "public VertexConsumer color("),
    ("public VertexConsumer setUv(", "public VertexConsumer uv("),
    ("public VertexConsumer setUv1(", "public VertexConsumer overlayCoords("),
    ("public VertexConsumer setUv2(", "public VertexConsumer uv2("),
    ("public VertexConsumer setNormal(", "public VertexConsumer normal("),
]
EXTRA = """
   public void endVertex() {
   }

   public void defaultColor(int r, int g, int b, int a) {
   }

   public void unsetDefaultColor() {
   }
}
"""
for rel in ("client/SurfaceSnapshot.java", "client/SurfaceDentMesh.java", "client/TorsoTouchMesh.java"):
    def f(t):
        for a, b in SIG:
            if a not in t:
                print("WARNING sig missing:", rel, a)
            t = t.replace(a, b)
        i = t.rstrip().rfind("}")
        return t[:i].rstrip("\n") + "\n" + EXTRA
    edit(rel, f)

# ---- SharedBodyModel / AvatarRig: packed-color renderToBuffer becomes renderToBufferArgb + 8-arg override
def sbm(t):
    t = t.replace("public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color) {",
                  "public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, float r, float g, float b, float a) {\n"
                  "      this.renderToBufferArgb(poseStack, buffer, light, overlay, Vtx.argb(r, g, b, a));\n   }\n\n"
                  "   public void renderToBufferArgb(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color) {")
    return t
edit("client/SharedBodyModel.java", sbm)
edit("client/AvatarRig.java", lambda t: t.replace("public void renderToBuffer(PoseStack stack, VertexConsumer out, int light, int overlay, int color) {\n      super.renderToBuffer(",
      "public void renderToBufferArgb(PoseStack stack, VertexConsumer out, int light, int overlay, int color) {\n      super.renderToBufferArgb("))
edit("client/AvatarRenderer.java", lambda t: t.replace("rig.renderToBuffer(", "rig.renderToBufferArgb("))
edit("client/SceneTouchSurface.java", lambda t: t.replace("this.model.renderToBuffer(", "this.model.renderToBufferArgb("))

# ---- GUI
edit("client/AnimationScreen.java", lambda t: t.replace("public boolean mouseScrolled(double mx, double my, double dx, double dy) {", "public boolean mouseScrolled(double mx, double my, double dy) {")
                                               .replace("return super.mouseScrolled(mx, my, dx, dy);", "return super.mouseScrolled(mx, my, dy);"))
def hud(t):
    t = t.replace("mc.gui.leftHeight, mc.gui.rightHeight", "leftHeight(mc), rightHeight(mc)")
    helper = ("   private static int leftHeight(Minecraft mc) {\n"
              "      return mc.gui instanceof net.minecraftforge.client.gui.overlay.ForgeGui g ? g.leftHeight : 39;\n   }\n\n"
              "   private static int rightHeight(Minecraft mc) {\n"
              "      return mc.gui instanceof net.minecraftforge.client.gui.overlay.ForgeGui g ? g.rightHeight : 39;\n   }\n\n")
    return t.replace("   static int bottom(int height", helper + "   static int bottom(int height", 1)
edit("client/InteractionHud.java", hud)
def appscreen(t):
    old = ("InventoryScreen.renderEntityInInventoryFollowsMouse(\n"
           "               g, this.left + 12, this.top + 28, this.left + 90, this.top + this.panelHeight - 40, 32, 0.0625F, (float)mx, (float)my, this.minecraft.player\n"
           "            );")
    new = ("int previewX = this.left + 51;\n"
           "            int previewY = this.top + 28 + (this.panelHeight - 68) / 2 + 29;\n"
           "            InventoryScreen.renderEntityInInventoryFollowsMouse(\n"
           "               g, previewX, previewY, 32, (float)(previewX - mx), (float)(previewY - 50 - my), this.minecraft.player\n"
           "            );")
    assert old in t, "AppearanceScreen call not found"
    return t.replace(old, new)
edit("client/AppearanceScreen.java", appscreen)
print("pass 3 done")
