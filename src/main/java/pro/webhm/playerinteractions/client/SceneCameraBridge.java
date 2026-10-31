package pro.webhm.playerinteractions.client;

import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.ToDoubleFunction;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

public final class SceneCameraBridge {
   public static BiPredicate<Double, Double> mouse = (x, y) -> false;
   public static DoubleSupplier inspectionWeight = () -> 0.0;
   public static ToDoubleFunction<UUID> strokeWeight = id -> 0.0;
   public static ToDoubleFunction<UUID> pairedWeight = id -> 0.0;
   private static volatile SceneCameraBridge.PositionProvider provider;
   private static BooleanSupplier fullBody = () -> false;

   public static void installFullBody(BooleanSupplier value) {
      fullBody = value;
   }

   public static boolean fullBody() {
      return fullBody.getAsBoolean();
   }

   public static void install(SceneCameraBridge.PositionProvider value) {
      provider = value;
   }

   public static Vec3 position(Camera camera, float partialTick) {
      SceneCameraBridge.PositionProvider current = provider;
      return current == null ? null : current.position(camera, partialTick);
   }

   private SceneCameraBridge() {
   }

   @FunctionalInterface
   public interface PositionProvider {
      Vec3 position(Camera var1, float var2);
   }
}
