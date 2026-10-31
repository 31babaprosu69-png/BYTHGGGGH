#!/usr/bin/env python3
"""Pass 1: purely mechanical NeoForge 1.21.1 -> Forge 1.20.1 renames.

Only renames that are 1:1 and certain are done here. Everything that needs
a real code change (networking, tick events, rendering/vertex API, PlayerSkin,
GUI layers, damage event) is listed in PORTING.md and left for compiler-driven
fixing.

Usage:  python3 -I tools/pass1_mechanical.py src/main/java
"""
import re
import sys
from pathlib import Path

IMPORTS = {
    "net.neoforged.fml.common.Mod": "net.minecraftforge.fml.common.Mod",
    "net.neoforged.bus.api.IEventBus": "net.minecraftforge.eventbus.api.IEventBus",
    "net.neoforged.bus.api.SubscribeEvent": "net.minecraftforge.eventbus.api.SubscribeEvent",
    "net.neoforged.api.distmarker.Dist": "net.minecraftforge.api.distmarker.Dist",
    "net.neoforged.fml.loading.FMLPaths": "net.minecraftforge.fml.loading.FMLPaths",
    "net.neoforged.fml.loading.FMLEnvironment": "net.minecraftforge.fml.loading.FMLEnvironment",
    "net.neoforged.fml.event.lifecycle.FMLClientSetupEvent": "net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent",
}
PREFIXES = [  # prefix rewrites for whole packages whose class names did not change
    ("net.neoforged.neoforge.client.event.", "net.minecraftforge.client.event."),
    ("net.neoforged.neoforge.event.entity.player.", "net.minecraftforge.event.entity.player."),
    ("net.neoforged.neoforge.event.server.", "net.minecraftforge.event.server."),
]

def convert(text: str) -> str:
    # --- imports
    NOT_ONE_TO_ONE = ("net.neoforged.neoforge.client.event.ClientTickEvent",)  # becomes TickEvent.ClientTickEvent + phase check

    def imp(m):
        name = m.group(2)
        if name.startswith(NOT_ONE_TO_ONE):
            return m.group(0)
        if name in IMPORTS:
            return f"{m.group(1)}{IMPORTS[name]};"
        for old, new in PREFIXES:
            if name.startswith(old):
                return f"{m.group(1)}{new}{name[len(old):]};"
        return m.group(0)
    text = re.sub(r"(import )([\w.]+);", imp, text)

    # --- ResourceLocation factories (constructors are public again in 1.20.1)
    text = re.sub(r"ResourceLocation\.fromNamespaceAndPath\(", "new ResourceLocation(", text)
    text = re.sub(r"ResourceLocation\.parse\(", "new ResourceLocation(", text)

    # --- @EventBusSubscriber -> @Mod.EventBusSubscriber
    if "net.neoforged.fml.common.EventBusSubscriber;" in text:
        text = text.replace("import net.neoforged.fml.common.EventBusSubscriber.Bus;\n", "")
        text = text.replace("import net.neoforged.fml.common.EventBusSubscriber;\n", "")
        text = re.sub(r"@EventBusSubscriber\(", "@Mod.EventBusSubscriber(", text)
        text = text.replace("bus = Bus.MOD", "bus = Mod.EventBusSubscriber.Bus.MOD")
        if "import net.minecraftforge.fml.common.Mod;" not in text:
            text = re.sub(r"(package [\w.]+;\n)", r"\1\nimport net.minecraftforge.fml.common.Mod;", text, count=1)
    return text

def main(root: str):
    changed = 0
    for p in Path(root).rglob("*.java"):
        old = p.read_text(encoding="utf-8")
        new = convert(old)
        if new != old:
            p.write_text(new, encoding="utf-8")
            changed += 1
    print(f"pass 1 changed {changed} files")

if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "src/main/java")
