package com.gargin.cavenoise;

import com.gargin.cavenoise.entity.ModEntityTypes;
import com.gargin.cavenoise.entity.client.CaveDwellerRenderer;
import com.gargin.cavenoise.entity.custom.CaveDwellerEntity;
import com.gargin.cavenoise.item.ModItems;
import com.gargin.cavenoise.sound.ModSounds;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CreativeModeTabEvent.BuildContents;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import software.bernie.geckolib.GeckoLib;

@Mod("cavenoise")
public class CaveNoise {
   public static final String MODID = "cavenoise";
   private static final Logger LOGGER = LogUtils.getLogger();
   private boolean USING_FAST_TIMERS = false;
   private int ticksCalmResetMin;
   private int ticksCalmResetMax;
   private int ticksCalmResetCooldown;
   private int ticksNoiseResetMin;
   private int ticksNoiseResetMax;
   private int calmTimer;
   private int noiseTimer;
   private boolean canSpawn = false;
   private double chanceToSpawnPerTick = 0.005;
   private double chanceToCooldown = 0.4;
   private boolean anySpelunkers = false;
   private List<Player> spelunkers = new ArrayList();
   private List<ServerPlayer> players = new ArrayList();

   public CaveNoise() {
      IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
      modEventBus.addListener(this::commonSetup);
      ModItems.register(modEventBus);
      ModEntityTypes.register(modEventBus);
      MinecraftForge.EVENT_BUS.register(this);
      modEventBus.addListener(this::addCreative);
      GeckoLib.initialize();
      ModSounds.register(modEventBus);
      if (!this.USING_FAST_TIMERS) {
         this.ticksCalmResetMin = 9600;
         this.ticksCalmResetMax = 12000;
         this.ticksCalmResetCooldown = 16000;
         this.ticksNoiseResetMin = 2000;
         this.ticksNoiseResetMax = 1600;
         this.calmTimer = 24000;
      } else {
         this.ticksCalmResetMin = 960;
         this.ticksCalmResetMax = 1200;
         this.ticksCalmResetCooldown = 1600;
         this.ticksNoiseResetMin = 1000;
         this.ticksNoiseResetMax = 800;
         this.calmTimer = 20;
      }

      this.noiseTimer = 4800;
   }

   private void commonSetup(FMLCommonSetupEvent event) {
      LOGGER.info("HELLO FROM COMMON SETUP");
      LOGGER.info("DIRT BLOCK >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));
   }

   private void addCreative(BuildContents event) {
      if (event.getTab() != CreativeModeTabs.INGREDIENTS && event.getTab() == CreativeModeTabs.SPAWN_EGGS) {
         event.accept(ModItems.CAVE_DWELLER_SPAWN_EGG);
      }
   }

   @SubscribeEvent
   public void onServerStarting(ServerStartingEvent event) {
      LOGGER.info("HELLO from server starting");
      this.resetCalmTimer();
   }

   @SubscribeEvent
   public void serverTick(ServerTickEvent event) {
      Iterable<Entity> entities = event.getServer().getLevel(Level.OVERWORLD).getEntities().getAll();
      AtomicBoolean dwellerExists = new AtomicBoolean(false);
      entities.forEach(entity -> {
         if (entity instanceof CaveDwellerEntity) {
            dwellerExists.set(true);
            this.resetCalmTimer();
         }
      });
      --this.noiseTimer;
      if (this.noiseTimer <= 0 && (dwellerExists.get() || this.calmTimer <= 6000)) {
         event.getServer().getLevel(Level.OVERWORLD).getPlayers(this::playCaveSoundToSpelunkers);
      }

      if (this.calmTimer <= 0) {
         this.canSpawn = true;
      } else {
         this.canSpawn = false;
      }

      --this.calmTimer;
      if (this.canSpawn && !dwellerExists.get()) {
         Random rand = new Random();
         if (rand.nextDouble() <= this.chanceToSpawnPerTick) {
            this.spelunkers.clear();
            this.anySpelunkers = false;
            event.getServer().getLevel(Level.OVERWORLD).getPlayers(this::listSpelunkers);
            if (this.anySpelunkers) {
               Player victim = this.spelunkers.get(rand.nextInt(this.spelunkers.size()));
               event.getServer().getLevel(Level.OVERWORLD).getPlayers(this::playCaveSoundToSpelunkers);
               CaveDwellerEntity cavedweller = new CaveDwellerEntity(
                       ModEntityTypes.CAVE_DWELLER.get(), event.getServer().getLevel(Level.OVERWORLD)
               );
               cavedweller.setInvisible(true);
               System.out.println("SPAWNED CD");
               cavedweller.setPos(cavedweller.generatePos(victim));
               System.out.println("POS: " + cavedweller.position());
               System.out.println("ADDED SUCCESSFULLY: " + event.getServer().getLevel(Level.OVERWORLD).addFreshEntity(cavedweller));
               this.resetCalmTimer();
            }
         }
      }
   }

   public boolean listSpelunkers(ServerPlayer player) {
      if (this.checkIfPlayerIsSpelunker(player)) {
         this.anySpelunkers = true;
         this.spelunkers.add(player);
      }

      return true;
   }

   public boolean playCaveSoundToSpelunkers(ServerPlayer player) {
      Random rand = new Random();
      Level level = player.getLevel();
      BlockPos playerBlockPos = new BlockPos(player.position().x, player.position().y, player.position().z);
      if (this.checkIfPlayerIsSpelunker(player) && !player.isCreative() && !player.isSpectator()) {
         switch(rand.nextInt(4)) {
            case 0:
               Minecraft.getInstance()
                  .getSoundManager()
                  .play(
                     new SimpleSoundInstance(ModSounds.CAVENOISE_1.get(), SoundSource.AMBIENT, 2.0F, 1.0F, RandomSource.create(), playerBlockPos)
                  );
               break;
            case 1:
               Minecraft.getInstance()
                  .getSoundManager()
                  .play(
                     new SimpleSoundInstance(ModSounds.CAVENOISE_2.get(), SoundSource.AMBIENT, 2.0F, 1.0F, RandomSource.create(), playerBlockPos)
                  );
               break;
            case 2:
               Minecraft.getInstance()
                  .getSoundManager()
                  .play(
                     new SimpleSoundInstance(ModSounds.CAVENOISE_3.get(), SoundSource.AMBIENT, 2.0F, 1.0F, RandomSource.create(), playerBlockPos)
                  );
               break;
            case 3:
               Minecraft.getInstance()
                  .getSoundManager()
                  .play(
                     new SimpleSoundInstance(ModSounds.CAVENOISE_4.get(), SoundSource.AMBIENT, 2.0F, 1.0F, RandomSource.create(), playerBlockPos)
                  );
         }

         this.resetNoiseTimer();
      }

      return true;
   }

   public boolean checkIfPlayerIsSpelunker(Player player) {
      if (player == null) {
         return false;
      } else {
         Level level = player.getLevel();
         BlockPos playerBlockPos = new BlockPos(player.position().x, player.position().y, player.position().z);
         return player.position().y < 40.0 && !level.canSeeSky(playerBlockPos);
      }
   }

   private void resetCalmTimer() {
      Random rand = new Random();
      this.calmTimer = this.ticksCalmResetMin + rand.nextInt(this.ticksCalmResetMax);
      if (rand.nextDouble() <= this.chanceToCooldown) {
         this.calmTimer = this.ticksCalmResetCooldown + rand.nextInt(this.ticksCalmResetCooldown);
      }
   }

   private void resetNoiseTimer() {
      Random rand = new Random();
      this.noiseTimer = this.ticksNoiseResetMin + rand.nextInt(this.ticksNoiseResetMax);
   }

   @EventBusSubscriber(
      modid = "cavenoise",
      bus = Bus.MOD,
      value = {Dist.CLIENT}
   )
   public static class ClientModEvents {
      @SubscribeEvent
      public static void onClientSetup(FMLClientSetupEvent event) {
         CaveNoise.LOGGER.info("HELLO FROM CLIENT SETUP");
         CaveNoise.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
         EntityRenderers.register(ModEntityTypes.CAVE_DWELLER.get(), CaveDwellerRenderer::new);
      }
   }

   @EventBusSubscriber(
      modid = "cavenoise",
      bus = Bus.FORGE,
      value = {Dist.CLIENT}
   )
   public static class RegisterLayers {
      @SubscribeEvent
      public static void registerLayer(EntityRenderersEvent event) {
      }
   }
}
