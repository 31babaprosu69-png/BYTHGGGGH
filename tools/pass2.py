#!/usr/bin/env python3
"""Pass 2: networking call sites, tick/GUI/damage events, PlayerSkin -> SkinCompat."""
import re, sys
from pathlib import Path
root = Path(sys.argv[1] if len(sys.argv) > 1 else "src/main/java")
for p in root.rglob("*.java"):
    if p.name in ("Network.java", "SkinCompat.java", "PlayerSkinMixin.java"):
        continue
    t = o = p.read_text(encoding="utf-8")
    # networking
    t = re.sub(r",\s*new CustomPacketPayload\[0\]", "", t)
    t = t.replace("PacketDistributor.sendToPlayersTrackingEntityAndSelf(", "Network.toTrackingAndSelf(")
    t = t.replace("PacketDistributor.sendToPlayer(", "Network.toPlayer(")
    t = t.replace("PacketDistributor.sendToServer(", "Network.toServer(")
    for imp in ("net.minecraft.network.protocol.common.custom.CustomPacketPayload",
                "net.neoforged.neoforge.network.PacketDistributor"):
        t = t.replace(f"import {imp};\n", "")
    if "Network." in t and p.parent.name == "client" and "import pro.webhm.playerinteractions.Network;" not in t:
        t = re.sub(r"(package [\w.]+;\n)", r"\1\nimport pro.webhm.playerinteractions.Network;", t, count=1)
    # tick events
    t = t.replace("import net.neoforged.neoforge.client.event.ClientTickEvent.Post;", "import net.minecraftforge.event.TickEvent;")
    t = t.replace("import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;", "import net.minecraftforge.event.TickEvent;")
    if "net.minecraftforge.event.TickEvent;" in t:
        kind = "ServerTickEvent" if p.name == "InteractionServer.java" else "ClientTickEvent"
        t = t.replace("public static void tick(Post event) {",
                      f"public static void tick(TickEvent.{kind} event) {{\n      if (event.phase != TickEvent.Phase.END) {{\n         return;\n      }}")
    # server tick: no getServer() guarantee in 1.20.1 -> lifecycle hooks
    t = t.replace("MinecraftServer server = event.getServer();",
                  "MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();\n      if (server == null) {\n         return;\n      }")
    # damage
    t = t.replace("import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;", "import net.minecraftforge.event.entity.living.LivingHurtEvent;")
    t = t.replace("damage(LivingIncomingDamageEvent event)", "damage(LivingHurtEvent event)")
    # fully-qualified GUI / render events
    t = t.replace("net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre", "net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre")
    t = t.replace("net.neoforged.neoforge.client.event.", "net.minecraftforge.client.event.")
    t = t.replace("event.getName().equals(VanillaGuiLayers.CROSSHAIR)", "event.getOverlay().equals(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.CROSSHAIR.type())")
    t = t.replace("import net.neoforged.neoforge.client.gui.VanillaGuiLayers;\n", "")
    t = t.replace("event.getPartialTick().getGameTimeDeltaPartialTick(false)", "event.getPartialTick()")
    # skins
    t = t.replace("import net.minecraft.client.resources.PlayerSkin.Model;\n", "")
    t = re.sub(r"(\w+)\.getSkin\(\)\.model\(\) == Model\.SLIM", r"SkinCompat.slim(\1)", t)
    t = re.sub(r"(\w+)\.getSkin\(\)\.texture\(\)", r"SkinCompat.texture(\1)", t)
    t = t.replace("for (Model skin : event.getSkins())", "for (String skin : event.getSkins())")
    t = t.replace("DefaultPlayerSkin.getDefaultTexture()", "DefaultPlayerSkin.getDefaultSkin()")
    if t != o:
        p.write_text(t, encoding="utf-8")
print("pass 2 done")
