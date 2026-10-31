# LovsPlayer: NeoForge 1.21.1 → Forge 1.20.1 port

Status: **scaffold + decompiled source + mechanical pass 1. NOT yet compiled.**
The sandbox this was prepared in cannot reach maven.minecraftforge.net / Mojang, so nothing here has been built.
The first GitHub Actions run will produce the real error list; fix in the order below.

Original: `pro.webhm.playerinteractions`, mod id `aot_player_interactions`, author meinshelfff234,
license text in the original `neoforge.mods.toml`: "None, free to use just as the Minecraft TOS states."
Pristine decompile of the 1.21.1 jar is in `original-decompiled/` for diffing. It is not compiled.

## What is already done
- Gradle project for Forge 1.20.1 (ForgeGradle 6, MixinGradle, JarJar for jlayer, Java 17).
- `mods.toml`, `pack.mcmeta` (pack_format 15), mixin config (JAVA_17 + refmap, `MixinConfigs` manifest entry).
- All assets copied (Cyrillic scene filenames preserved).
- `tools/pass1_mechanical.py` applied: `net.neoforged.*` → `net.minecraftforge.*` for the 1:1 classes,
  `ResourceLocation.fromNamespaceAndPath/parse` → `new ResourceLocation`, `@EventBusSubscriber` → `@Mod.EventBusSubscriber`.
- One Vineflower artifact fixed (`AnimationScreen`, `<unrepresentable>` cast).

## What is left, in the order to attack it

### 1. Networking (5 files, small) — `Network`, `AppearanceServer`, `InteractionServer`, `AnimationCatalog`, `client/AppearanceClient`, `client/InteractionClient`
NeoForge payloads (`CustomPacketPayload`, `StreamCodec`, `PayloadRegistrar`, `PacketDistributor.sendToPlayer/sendToServer`)
do not exist in 1.20.1. Replace with `SimpleChannel`:
```java
public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(
    new ResourceLocation(ID, "main"), () -> "3", "3"::equals, "3"::equals);
CH.registerMessage(0, CatalogData.class, CatalogData::encode, CatalogData::decode, CatalogData::handle,
    Optional.of(NetworkDirection.PLAY_TO_CLIENT));
CH.send(PacketDistributor.PLAYER.with(() -> serverPlayer), msg);   // was PacketDistributor.sendToPlayer
CH.sendToServer(msg);                                              // was PacketDistributor.sendToServer
```
Each record keeps its `writeUtf/writeVarInt/writeByteArray` bodies; they become `encode(FriendlyByteBuf)` / `decode(FriendlyByteBuf)`.
Handlers: `ctx.get().enqueueWork(...)`, `ctx.get().setPacketHandled(true)`, sender = `ctx.get().getSender()`.
Registration happens in `FMLCommonSetupEvent` (or static init), not in a `RegisterPayloadHandlersEvent`.
Messages: `AppearanceChange`, `CatalogData`, `Command`, `Update`.

### 2. Events (4 files, small)
| 1.21.1 (NeoForge) | 1.20.1 (Forge) |
|---|---|
| `ClientTickEvent.Post` | `TickEvent.ClientTickEvent` and `if (e.phase != TickEvent.Phase.END) return;` |
| `ServerTickEvent.Post` | `TickEvent.ServerTickEvent` + same phase check |
| `LivingIncomingDamageEvent` | `LivingHurtEvent` (or `LivingAttackEvent`; the mod only needs "this player took damage") |
| `VanillaGuiLayers.CROSSHAIR` + `RenderGuiLayerEvent` | `RegisterGuiOverlaysEvent` / `RenderGuiOverlayEvent.Pre`, compare `event.getOverlay() == VanillaGuiOverlay.CROSSHAIR.type()` |
| `MovementInputUpdateEvent`, `RenderHandEvent`, `ComputeCameraAngles`, `MouseButton.Pre`, `MouseScrollingEvent` | same names exist in Forge 1.20.1 (already import-renamed); check getter names |
| `FMLClientSetupEvent` etc. | same |
`PlayerInteractions` constructor takes `IEventBus bus`; in Forge use `FMLJavaModLoadingContext.get().getModEventBus()`.

### 3. PlayerSkin (8 files, medium)
`net.minecraft.client.resources.PlayerSkin` (record with `.texture()`, `.model()` WIDE/SLIM) was added in 1.20.2.
In 1.20.1: `AbstractClientPlayer.getSkinTextureLocation()` (ResourceLocation) and `getModelName()` ("default" / "slim").
Easiest: define `enum ArmModel { WIDE, SLIM }` + small helper `ArmModel of(AbstractClientPlayer)`, replace
`PlayerSkin.Model` everywhere, and `player.getSkin().texture()` → `player.getSkinTextureLocation()`.
`DefaultPlayerSkin.getDefaultTexture()` → `DefaultPlayerSkin.getDefaultSkin()`.
`mixin/PlayerSkinMixin` targets `getSkin`, which does not exist in 1.20.1: retarget to `getModelName()` (return "slim"/"default"
per `AppearanceClient.arms(uuid)`), and `getSkinTextureLocation()` if needed.

### 4. Rendering / vertex API (12 files, **the bulk of the work**)
`SharedBodyModel` (2067 lines), `SharedHeadRig`, `SharedSkinLayer3D`, `ContinuousLimbMesh`, `JointSeamMesh`, `TorsoTouchMesh`,
`SurfaceDentMesh`, `SurfaceSnapshot`, `BlushRig`, `AvatarRig`, `facial/FacialMesh`, `facial/FacialHandMesh`.
1.21 changed the vertex API; 1.20.1 uses the old one:
| 1.21.1 | 1.20.1 |
|---|---|
| `buf.addVertex(x,y,z)` | `buf.vertex(x,y,z)` |
| `.setColor(int r,g,b,a)` / `.setColor(int argb)` | `.color(int r,g,b,a)` (0–255); there is no packed-ARGB overload on the builder chain |
| `.setUv(u,v)` | `.uv(u,v)` |
| `.setOverlay(int)` | `.overlayCoords(int)` |
| `.setLight(int)` | `.uv2(int)` |
| `.setNormal(x,y,z)` | `.normal(x,y,z)` then `.endVertex()` (**new in 1.20.1: must call `endVertex()` after each vertex**) |
| `consumer.addVertex(x,y,z,argb,u,v,overlay,light,nx,ny,nz)` (11-arg) | `consumer.vertex(x,y,z, r,g,b,a, u,v, overlay, light, nx,ny,nz)` (floats for r,g,b,a) |
| `Tesselator.begin(mode, format)` returns a `BufferBuilder` | `BufferBuilder b = Tesselator.getInstance().getBuilder(); b.begin(mode, format);` … `BufferUploader.drawWithShader(b.end())` |
| `BufferUploader.drawWithShader(b.buildOrThrow())` | `BufferUploader.drawWithShader(b.end())` |
| `ModelPart.render(pose, vc, light, overlay, int argb)` | `render(pose, vc, light, overlay, float r, g, b, a)` |
| `TorsoTouchMesh implements VertexConsumer` (custom impl) | the 1.20.1 `VertexConsumer` interface has different abstract methods (`vertex`, `color`, `uv`, `overlayCoords`, `uv2`, `normal`, `endVertex`, `defaultColor`, `unsetDefaultColor`): rewrite that class's overrides |
A `compat/Vtx.java` helper (`vertex(buf, x,y,z, argb, u,v, overlay, light, nx,ny,nz)`) that does the 1.20.1 call chain
will let you fix most call sites with a find/replace instead of hand-editing 130 lines.
PoseStack, `Matrix4f`/`Matrix3f` (JOML), `Mth`, `Vec3` are the same.

### 5. GUI (2 files)
`new Button(x, y, w, h, Component, OnPress, CreateNarration)` anonymous subclass (`AnimationScreen`, `UiArt`): the protected
constructor exists in 1.20.1 with the same shape; `renderWidget(GuiGraphics,…)` also exists. Probably compiles as is.
`GuiGraphics` exists in 1.20.1.

### 6. Audio (2 files)
`Mp3AudioStream implements AudioStream`, `SceneCues` uses `SoundManager`/`SoundBufferLibrary`/`WeighedSoundEvents`.
These internals moved between versions; check `SoundBufferLibrary` and `SoundInstance` constructors against 1.20.1 mappings.

### 7. Mixins (7 classes)
Targets: `Camera.setup`, `Entity.push(Entity)`, `AbstractClientPlayer.getSkin` (see #3), `Player.travel`,
`LivingEntityRenderer.render`, `ServerGamePacketListenerImpl.handleMovePlayer`, `MouseHandler.turnPlayer`.
Check each signature against 1.20.1 official mappings (the refmap handles the SRG remap).
- `TouchMouseMixin`: the 1.21.1 handler is `touch(double seconds, CallbackInfo ci)` because `MouseHandler.turnPlayer(double)` takes an argument there. In 1.20.1 `turnPlayer()` takes none, so change the handler to `touch(CallbackInfo ci)`.
- `CameraMixin`: `Camera.setup(BlockGetter, Entity, boolean, boolean, float)` already matches 1.20.1.
`@Shadow` fields may be renamed.

### 8. Player Animator (1 file: `client/PlayerAnimation.java`)
The 1.21.1 jar bundled player-animation-lib **2.0.4**. 1.20.1 has **1.0.x**. The classes used here
(`PlayerAnimationAccess.REGISTER_ANIMATION_EVENT`, `IAnimation`, `TransformType`, `Vec3f`, `FirstPersonMode`,
`FirstPersonConfiguration`) all exist in 1.x, but signatures may differ slightly; fix by compiler error.
Required-mod entry in `mods.toml` is `playeranimator`. Players must install it (or add it to JarJar if licensing allows;
the original jar bundled it).

### 9. Misc
- `compatibilityLevel` is JAVA_17 now; decompiled code compiled under `--release 17` parsed clean apart from the one artifact fixed.
  If you find Java 21-only syntax (switch pattern matching, record patterns), rewrite it.
- `FMLPaths.CONFIGDIR` is the same.
- `Player`/`LivingEntity` method renames: `getEntity()` on events is fine; `player.level()` is fine in 1.20.1; `Entity#getBbHeight` fine.
- `ServerGamePacketListenerImpl` `handleMovePlayer(ServerboundMovePlayerPacket)` same in both.
- `BedSceneOrigin`/`BedBlock`/`BedPart` unchanged.

## Testing checklist once it compiles
1. Single-player: open the interaction wheel (default key), start a paired scene against a second client (LAN or two instances).
2. Check the consent/invite dialog, cancel, decline, expiry.
3. Check first-person camera and mouse look during a scene (`CameraMixin`, `TouchMouseMixin`).
4. Check skin arm-model switch (`PlayerSkinMixin` replacement).
5. Check the MP3 cue audio plays.
