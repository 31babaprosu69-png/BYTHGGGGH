package pro.webhm.playerinteractions;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import pro.webhm.playerinteractions.client.AnimationLibrary;
import pro.webhm.playerinteractions.client.InteractionClient;

/** Forge 1.20.1 SimpleChannel replacement for the NeoForge 1.21.1 payload registrar. */
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

   private static final String PROTOCOL = "3";
   public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
      new ResourceLocation(PlayerInteractions.ID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals
   );
   private static boolean registered;

   public static synchronized void register() {
      if (registered) {
         return;
      }
      registered = true;
      int id = 0;
      CHANNEL.registerMessage(id++, CatalogData.class, CatalogData::encode, CatalogData::decode, CatalogData::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
      CHANNEL.registerMessage(id++, AppearanceChange.class, AppearanceChange::encode, AppearanceChange::decode, AppearanceChange::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
      CHANNEL.registerMessage(id++, Command.class, Command::encode, Command::decode, Command::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
      CHANNEL.registerMessage(id++, Update.class, Update::encode, Update::decode, Update::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
   }

   // ---- send helpers (replace PacketDistributor.sendToPlayer / sendToServer / sendToPlayersTrackingEntityAndSelf)
   public static void toPlayer(ServerPlayer player, Object message) {
      CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
   }

   public static void toTrackingAndSelf(ServerPlayer player, Object message) {
      CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), message);
   }

   public static void toServer(Object message) {
      CHANNEL.sendToServer(message);
   }

   private Network() {
   }

   public static record AppearanceChange(String json) {
      public static void encode(AppearanceChange p, FriendlyByteBuf b) {
         b.writeUtf(p.json, 4096);
      }

      public static AppearanceChange decode(FriendlyByteBuf b) {
         return new AppearanceChange(b.readUtf(4096));
      }

      public static void handle(AppearanceChange p, Supplier<NetworkEvent.Context> ctx) {
         NetworkEvent.Context c = ctx.get();
         c.enqueueWork(() -> {
            ServerPlayer player = c.getSender();
            if (player != null) {
               AppearanceServer.set(player, p.json());
            }
         });
         c.setPacketHandled(true);
      }
   }

   public static record CatalogData(int generation, int action, byte[] json) {
      public static void encode(CatalogData p, FriendlyByteBuf b) {
         if (p.json.length > 524288) {
            throw new IllegalArgumentException("Catalogue packet too large");
         }
         b.writeVarInt(p.generation);
         b.writeVarInt(p.action);
         b.writeByteArray(p.json);
      }

      public static CatalogData decode(FriendlyByteBuf b) {
         return new CatalogData(b.readVarInt(), b.readVarInt(), b.readByteArray(524288));
      }

      public static void handle(CatalogData p, Supplier<NetworkEvent.Context> ctx) {
         NetworkEvent.Context c = ctx.get();
         if (FMLEnvironment.dist.isClient()) {
            c.enqueueWork(() -> AnimationLibrary.receive(p));
         }
         c.setPacketHandled(true);
      }
   }

   public static record Command(int op, UUID token, int target, int value, float x, float y) {
      public static void encode(Command p, FriendlyByteBuf b) {
         b.writeVarInt(p.op);
         b.writeUUID(p.token);
         b.writeVarInt(p.target);
         b.writeVarInt(p.value);
         b.writeFloat(p.x);
         b.writeFloat(p.y);
      }

      public static Command decode(FriendlyByteBuf b) {
         return new Command(b.readVarInt(), b.readUUID(), b.readVarInt(), b.readVarInt(), b.readFloat(), b.readFloat());
      }

      public static void handle(Command p, Supplier<NetworkEvent.Context> ctx) {
         NetworkEvent.Context c = ctx.get();
         c.enqueueWork(() -> {
            ServerPlayer player = c.getSender();
            if (p.valid() && player != null) {
               InteractionServer.command(player, p);
            }
         });
         c.setPacketHandled(true);
      }

      public boolean valid() {
         return this.op >= 0 && this.op <= 9 && Float.isFinite(this.x) && Float.isFinite(this.y) && Math.abs(this.x) <= 1.0F && Math.abs(this.y) <= 1.0F;
      }
   }

   public static record Update(String kind, String json) {
      public static void encode(Update p, FriendlyByteBuf b) {
         b.writeUtf(p.kind, 16);
         b.writeUtf(p.json, 30000);
      }

      public static Update decode(FriendlyByteBuf b) {
         return new Update(b.readUtf(16), b.readUtf(30000));
      }

      public static void handle(Update p, Supplier<NetworkEvent.Context> ctx) {
         NetworkEvent.Context c = ctx.get();
         if (FMLEnvironment.dist.isClient()) {
            c.enqueueWork(() -> InteractionClient.receive(p));
         }
         c.setPacketHandled(true);
      }
   }
}
