package pro.webhm.playerinteractions;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import pro.webhm.playerinteractions.client.AnimationLibrary;
import pro.webhm.playerinteractions.client.InteractionClient;

public final class Network {
   public static final int REQUEST = 0;
   public static final int ACCEPT = 1;
   public static final int DECLINE = 2;
   public static final int STOP = 3;
   public static final int TOUCH = 4;
   public static final int MODE = 5;
   public static final int PET = 6;
   public static final int JOIN = 7;
   public static final int CATALOG = 8;
   public static final int REPEAT = 9;
   public static final UUID NONE = new UUID(0L, 0L);

   public static void register(RegisterPayloadHandlersEvent event) {
      PayloadRegistrar r = event.registrar("3");
      r.playToClient(Network.CatalogData.TYPE, Network.CatalogData.CODEC, (p, c) -> {
         if (FMLEnvironment.dist.isClient()) {
            c.enqueueWork(() -> AnimationLibrary.receive(p));
         }
      });
      r.playToServer(Network.AppearanceChange.TYPE, Network.AppearanceChange.CODEC, (p, c) -> c.enqueueWork(() -> {
            if (c.player() instanceof ServerPlayer player) {
               AppearanceServer.set(player, p.json());
            }
         }));
      r.playToServer(Network.Command.TYPE, Network.Command.CODEC, (p, c) -> c.enqueueWork(() -> {
            if (p.valid() && c.player() instanceof ServerPlayer player) {
               InteractionServer.command(player, p);
            }
         }));
      r.playToClient(Network.Update.TYPE, Network.Update.CODEC, (p, c) -> {
         if (FMLEnvironment.dist.isClient()) {
            c.enqueueWork(() -> InteractionClient.receive(p));
         }
      });
   }

   private Network() {
   }

   public static record AppearanceChange(String json) implements CustomPacketPayload {
      public static final Type<Network.AppearanceChange> TYPE = new Type(PlayerInteractions.id("appearance_change"));
      public static final StreamCodec<RegistryFriendlyByteBuf, Network.AppearanceChange> CODEC = StreamCodec.of(
         (b, p) -> b.writeUtf(p.json, 4096), b -> new Network.AppearanceChange(b.readUtf(4096))
      );

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public static record CatalogData(int generation, int action, byte[] json) implements CustomPacketPayload {
      public static final Type<Network.CatalogData> TYPE = new Type(PlayerInteractions.id("catalog_data"));
      public static final StreamCodec<RegistryFriendlyByteBuf, Network.CatalogData> CODEC = StreamCodec.of((b, p) -> {
         if (p.json.length > 524288) {
            throw new IllegalArgumentException("Catalogue packet too large");
         } else {
            b.writeVarInt(p.generation);
            b.writeVarInt(p.action);
            b.writeByteArray(p.json);
         }
      }, b -> new Network.CatalogData(b.readVarInt(), b.readVarInt(), b.readByteArray(524288)));

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public static record Command(int op, UUID token, int target, int value, float x, float y) implements CustomPacketPayload {
      public static final Type<Network.Command> TYPE = new Type(PlayerInteractions.id("command"));
      public static final StreamCodec<RegistryFriendlyByteBuf, Network.Command> CODEC = StreamCodec.of((b, p) -> {
         b.writeVarInt(p.op);
         b.writeUUID(p.token);
         b.writeVarInt(p.target);
         b.writeVarInt(p.value);
         b.writeFloat(p.x);
         b.writeFloat(p.y);
      }, b -> new Network.Command(b.readVarInt(), b.readUUID(), b.readVarInt(), b.readVarInt(), b.readFloat(), b.readFloat()));

      public boolean valid() {
         return this.op >= 0 && this.op <= 9 && Float.isFinite(this.x) && Float.isFinite(this.y) && Math.abs(this.x) <= 1.0F && Math.abs(this.y) <= 1.0F;
      }

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public static record Update(String kind, String json) implements CustomPacketPayload {
      public static final Type<Network.Update> TYPE = new Type(PlayerInteractions.id("update"));
      public static final StreamCodec<RegistryFriendlyByteBuf, Network.Update> CODEC = StreamCodec.of((b, p) -> {
         b.writeUtf(p.kind, 16);
         b.writeUtf(p.json, 30000);
      }, b -> new Network.Update(b.readUtf(16), b.readUtf(30000)));

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
