package pro.webhm.playerinteractions.client;

import net.minecraftforge.fml.common.Mod;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.Sound.Type;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import pro.webhm.playerinteractions.Actions;
import pro.webhm.playerinteractions.PlayerInteractions;
import pro.webhm.playerinteractions.SessionState;
import pro.webhm.playerinteractions.anim.Clip;
import pro.webhm.playerinteractions.anim.FeaturedAnimations;

@Mod.EventBusSubscriber(
   modid = "aot_player_interactions",
   value = {Dist.CLIENT}
)
public final class SceneCues {
   private static final Map<String, SoundInstance> VOICES = new HashMap<>();
   private static final List<SceneCues.Caption> CAPTIONS = new ArrayList<>();

   public static Component caption(int action, Clip.Cue cue) {
      if (FeaturedAnimations.index(action) == 1) {
         if (cue.text.equals("давай посмотрим на звезды")) {
            return InteractionClient.text("caption.stars.invite");
         }

         if (cue.text.equals("ты красивее любой звезды~")) {
            return InteractionClient.text("caption.stars.compliment");
         }
      }

      return Component.literal(cue.text);
   }

   @SubscribeEvent
   public static void tick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      HashSet<String> active = new HashSet<>();
      CAPTIONS.clear();
      if (mc.player != null && mc.level != null) {
         for (SessionState state : InteractionClient.SESSIONS.values()) {
            if (Actions.scene(state.action)) {
               Player receiver = mc.level.getPlayerByUUID(state.recipient().id);
               if (receiver != null && !(receiver.distanceToSqr(mc.player) > 1024.0)) {
                  double time = state.time(mc.level.getGameTime(), 0.0F) - 12.0;
                  int index = 0;

                  for (Clip.Cue cue : Clip.get(state.action, false).cues) {
                     String id = state.id + ":" + (state.loop == null ? 0 : state.loop.pass) + ":" + index++;
                     if (cue.active(time)) {
                        if (cue.kind.equals("text")) {
                           if (CAPTIONS.size() < 4) {
                              CAPTIONS.add(new SceneCues.Caption(cue, caption(state.action, cue)));
                           }
                        } else if (!state.paused && !state.waitingForJoin && !state.joining() && !state.loopHeld() && !mc.isPaused()) {
                           active.add(id);
                           if (!VOICES.containsKey(id)) {
                              SoundInstance sound = new SceneCues.BundledSound(cue.source, cue.volume, (double)cue.offset + time - (double)cue.tick);
                              VOICES.put(id, sound);
                              mc.getSoundManager().play(sound);
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      VOICES.entrySet().removeIf(entry -> {
         if (active.contains(entry.getKey())) {
            return false;
         } else {
            mc.getSoundManager().stop(entry.getValue());
            return true;
         }
      });
   }

   @SubscribeEvent
   public static void captions(net.minecraftforge.client.event.RenderGuiEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      if (!mc.options.hideGui && mc.screen == null) {
         GuiGraphics g = event.getGuiGraphics();
         int row = 0;

         for (SceneCues.Caption caption : CAPTIONS) {
            Clip.Cue cue = caption.cue();
            List<FormattedCharSequence> lines = mc.font.split(caption.text(), Math.min(360, g.guiWidth() - 30));
            int y = Math.min(g.guiHeight() - 85 - lines.size() * 10, g.guiHeight() * cue.screenY / 100 + row);

            for (FormattedCharSequence line : lines) {
               int w = mc.font.width(line);
               g.fill((g.guiWidth() - w) / 2 - 4, y - 2, (g.guiWidth() + w) / 2 + 4, y + 10, -1340073685);
               g.drawString(mc.font, line, (g.guiWidth() - w) / 2, y, 0xFF000000 | cue.color);
               y += 10;
            }

            row += lines.size() * 10 + 3;
         }
      }
   }

   private SceneCues() {
   }

   private static final class BundledSound extends AbstractSoundInstance {
      private final String asset;
      private final double offset;

      BundledSound(String asset, float gain, double offset) {
         super(PlayerInteractions.id("media/" + asset), SoundSource.RECORDS, SoundInstance.createUnseededRandom());
         this.asset = asset;
         this.offset = Math.max(0.0, offset);
         this.volume = gain;
         this.relative = true;
         this.attenuation = Attenuation.NONE;
      }

      public WeighedSoundEvents resolve(SoundManager manager) {
         this.sound = new Sound(this.location, ConstantFloat.of(1.0F), ConstantFloat.of(1.0F), 1, Type.FILE, true, false, 16);
         WeighedSoundEvents events = new WeighedSoundEvents(this.location, null);
         events.addSound(this.sound);
         return events;
      }

      public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean loop) {
         return CompletableFuture.supplyAsync(() -> {
            AudioStream stream = null;

            try {
               InputStream input = SceneCues.class.getResourceAsStream("/assets/aot_player_interactions/media/" + this.asset);
               if (input == null) {
                  throw new IOException("Missing bundled scene audio: " + this.asset);
               } else {
                  stream = new Mp3AudioStream(input);
                  long skip = (long)(this.offset / 20.0 * (double)stream.getFormat().getFrameRate()) * (long)stream.getFormat().getFrameSize();

                  while (skip > 0L) {
                     ByteBuffer data = stream.read((int)Math.min(skip, 65536L));
                     if (!data.hasRemaining()) {
                        break;
                     }

                     skip -= (long)data.remaining();
                  }

                  return stream;
               }
            } catch (IOException var7) {
               if (stream != null) {
                  try {
                     stream.close();
                  } catch (IOException var6) {
                  }
               }

               throw new CompletionException(var7);
            }
         });
      }
   }

   private static record Caption(Clip.Cue cue, Component text) {
   }
}
