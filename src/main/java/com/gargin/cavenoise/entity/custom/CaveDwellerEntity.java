package com.gargin.cavenoise.entity.custom;

import com.gargin.cavenoise.sound.ModSounds;
import java.util.Random;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.core.animation.Animation.LoopType;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.network.GeckoLibNetwork;
import software.bernie.geckolib.network.packet.EntityAnimTriggerPacket;

public class CaveDwellerEntity extends Monster implements GeoEntity {
   private AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
   public int rRollResult = 3;
   public boolean isAggro;
   private boolean isAggroState;
   private boolean returnShort = false;
   private boolean inTwoBlockSpace = false;
   public boolean spottedByPlayer = false;
   public boolean spottedOld = false;
   private boolean shouldClearAnim = true;
   public boolean squeezeCrawling = false;
   public boolean isFleeing;
   public boolean startedMovingChase = false;
   private float waitToStartAnimatorController = 20.0F;
   private Vec3 oldPos;
   private int ticksTillRemove;
   private RawAnimation OLD_RUN = RawAnimation.begin().then("animation.cave_dweller.run", LoopType.LOOP);
   private RawAnimation IDLE = RawAnimation.begin().then("animation.cave_dweller.idle", LoopType.LOOP);
   private RawAnimation CHASE = RawAnimation.begin().then("animation.cave_dweller.new_run", LoopType.LOOP);
   private RawAnimation CHASE_IDLE = RawAnimation.begin().then("animation.cave_dweller.run_idle", LoopType.LOOP);
   private RawAnimation CROUCH_RUN = RawAnimation.begin().then("animation.cave_dweller.crouch_run_new", LoopType.LOOP);
   private RawAnimation CROUCH_IDLE = RawAnimation.begin().then("animation.cave_dweller.crouch_idle", LoopType.LOOP);
   private RawAnimation CALM_RUN = RawAnimation.begin().then("animation.cave_dweller.calm_move", LoopType.LOOP);
   private RawAnimation CALM_STILL = RawAnimation.begin().then("animation.cave_dweller.calm_idle", LoopType.LOOP);
   private RawAnimation IS_SPOTTED = RawAnimation.begin().then("animation.cave_dweller.spotted", LoopType.HOLD_ON_LAST_FRAME);
   private RawAnimation CRAWL = RawAnimation.begin().then("animation.cave_dweller.crawl", LoopType.HOLD_ON_LAST_FRAME);
   private RawAnimation FLEE = RawAnimation.begin().then("animation.cave_dweller.flee", LoopType.LOOP);
   private RawAnimation currentAnim;
   public static final EntityDataAccessor<Boolean> FLEEING_ACCESSOR = SynchedEntityData.defineId(CaveDwellerEntity.class, EntityDataSerializers.BOOLEAN);
   public static final EntityDataAccessor<Boolean> CROUCHING_ACCESSOR = SynchedEntityData.defineId(CaveDwellerEntity.class, EntityDataSerializers.BOOLEAN);
   public static final EntityDataAccessor<Boolean> AGGRO_ACCESSOR = SynchedEntityData.defineId(CaveDwellerEntity.class, EntityDataSerializers.BOOLEAN);
   public static final EntityDataAccessor<Boolean> SQUEEZING_ACCESSOR = SynchedEntityData.defineId(CaveDwellerEntity.class, EntityDataSerializers.BOOLEAN);
   public static final EntityDataAccessor<Boolean> SPOTTED_ACCESSOR = SynchedEntityData.defineId(CaveDwellerEntity.class, EntityDataSerializers.BOOLEAN);
   Logger logger = LogManager.getLogManager().getLogger("cavenoise");
   private float twoBlockSpaceCooldown;
   private float twoBlockSpaceTimer = 0.0F;
   private float movingCooldown = 3.0F;
   private float movingClock = 3.0F;
   private int chaseSoundClockReset = 80;
   private int chaseSoundClock = 0;
   private boolean alreadyPlayedFleeSound = false;
   private boolean alreadyPlayedSpottedSound = false;
   private boolean startedPlayingChaseSound = false;
   private boolean alreadyPlayedDeathSound = false;

   public CaveDwellerEntity(EntityType<? extends CaveDwellerEntity> pEntityType, Level pLevel) {
      super(pEntityType, pLevel);
      this.maxUpStep = 1.0F;
      this.refreshDimensions();
      this.twoBlockSpaceCooldown = 5.0F;
      this.oldPos = this.position();
      this.ticksTillRemove = 6000;
   }

   public static AttributeSupplier setAttributes() {
      return Monster.createMobAttributes()
         .add(Attributes.MAX_HEALTH, 60.0)
         .add(Attributes.ATTACK_DAMAGE, 6.0)
         .add(Attributes.ATTACK_SPEED, 0.35)
         .add(Attributes.MOVEMENT_SPEED, 0.5)
         .add(Attributes.FOLLOW_RANGE, 100.0)
         .build();
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(FLEEING_ACCESSOR, false);
      this.entityData.define(CROUCHING_ACCESSOR, false);
      this.entityData.define(AGGRO_ACCESSOR, false);
      this.entityData.define(SQUEEZING_ACCESSOR, false);
      this.entityData.define(SPOTTED_ACCESSOR, false);
   }

   @Override
   protected void registerGoals() {
      this.goalSelector.addGoal(1, new DwellerStareGoal(this, 100.0F));
      this.goalSelector.addGoal(1, new DwellerChaseGoal(this, this, 0.85F, true, 20.0F));
      this.goalSelector.addGoal(1, new DwellerFleeGoal(this, 20.0F, 1.0));
      this.goalSelector.addGoal(1, new DwellerStrollGoal(this, 0.7));
      this.goalSelector.addGoal(1, new DwellerBreakInvisGoal(this));
      this.targetSelector.addGoal(1, new DwellerTargetTooCloseGoal(this, 12.0F));
      this.targetSelector.addGoal(2, new DwellerTargetSeesMeGoal(this));
   }

   public Vec3 generatePos(Entity player) {
      Vec3 playerPos = player.position();
      Random rand = new Random();
      double randX = rand.nextInt(70) - 35;
      double randZ = rand.nextInt(70) - 35;
      double posX = playerPos.x() + randX;
      double posY = playerPos.y + 10.0;
      double posZ = playerPos.z() + randZ;

      for(int runFor = 100; runFor >= 0; --posY) {
         BlockPos blockPosition = new BlockPos(posX, posY, posZ);
         BlockPos blockPosition2 = new BlockPos(posX, posY + 1.0, posZ);
         BlockPos blockPosition3 = new BlockPos(posX, posY + 2.0, posZ);
         BlockPos blockPosition4 = new BlockPos(posX, posY - 1.0, posZ);
         --runFor;
         if (!this.level.getBlockState(blockPosition).getMaterial().blocksMotion()
            && !this.level.getBlockState(blockPosition2).getMaterial().blocksMotion()
            && !this.level.getBlockState(blockPosition3).getMaterial().blocksMotion()
            && this.level.getBlockState(blockPosition4).getMaterial().blocksMotion()) {
            break;
         }
      }

      return new Vec3(posX, posY, posZ);
   }

   @Override
   public void tick() {
      --this.ticksTillRemove;
      if (this.ticksTillRemove <= 0) {
         this.discard();
      }

      MutableBlockPos blockpos$mutableblockpos = new MutableBlockPos(this.position().x, this.position().y + 2.0, this.position().z);
      BlockState blockstate = this.level.getBlockState(blockpos$mutableblockpos);
      boolean flag = blockstate.getMaterial().blocksMotion();
      if (flag) {
         this.twoBlockSpaceTimer = this.twoBlockSpaceCooldown;
         this.inTwoBlockSpace = true;
         if (this.getTarget() != null) {
         }
      } else {
         --this.twoBlockSpaceTimer;
         if (this.twoBlockSpaceTimer <= 0.0F) {
            this.inTwoBlockSpace = false;
         }
      }

      if (this.isAggro || this.isFleeing) {
         this.shouldClearAnim = false;
         this.spottedByPlayer = false;
         this.entityData.set(SPOTTED_ACCESSOR, false);
      }

      super.tick();
      this.entityData.set(CROUCHING_ACCESSOR, this.inTwoBlockSpace);
      if (this.entityData.get(SPOTTED_ACCESSOR)) {
         this.playSpottedSound();
      }
   }

   public boolean isMoving() {
      Vec3 velocity = this.getDeltaMovement();
      float avgVelocity = (float)(Math.abs(velocity.x) + Math.abs(velocity.z)) / 2.0F;
      if (this.getTarget() != null) {
      }

      return avgVelocity > 0.03F;
   }

   private void TriggeredAnimationControllerTick() {
      int testNum = 0;
      --this.waitToStartAnimatorController;
      if (this.waitToStartAnimatorController <= 0.0F) {
         if (this.squeezeCrawling) {
            ++testNum;
            this.triggerAnim("controller", "crawl");
            this.currentAnim = this.CRAWL;
            return;
         }

         if (this.spottedByPlayer) {
            this.triggerAnim("controller", "is_spotted");
            this.currentAnim = this.IS_SPOTTED;
         }
      }

      if (this.getTarget() != null) {
      }
   }

   public void triggerDwellerAnim(@Nullable String controllerName, String animName, RawAnimation animRaw) {
      RawAnimation anim = this.currentAnim;
      if (this.getTarget() != null && anim != null) {
      }

      if (anim != null) {
         if (anim != animRaw) {
            if (this.getTarget() != null) {
               this.getTarget().sendSystemMessage(Component.nullToEmpty("anim does not match name, setting." + anim + " -> " + animName));
            }

            if (this.getLevel().isClientSide()) {
               this.getAnimatableInstanceCache().getManagerForId((long)this.getId()).tryTriggerAnimation(controllerName, animName);
            } else {
               GeckoLibNetwork.send(
                  new EntityAnimTriggerPacket(this.getId(), controllerName, animName), PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> this)
               );
            }
         }
      } else {
         if (this.getTarget() != null) {
            this.getTarget().sendSystemMessage(Component.nullToEmpty("anim null and setting"));
         }

         if (this.getLevel().isClientSide()) {
            this.getAnimatableInstanceCache().getManagerForId((long)this.getId()).tryTriggerAnimation(controllerName, animName);
         } else {
            GeckoLibNetwork.send(
               new EntityAnimTriggerPacket(this.getId(), controllerName, animName), PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> this)
            );
         }
      }
   }

   public void rRoll() {
      Random rand = new Random();
      this.rRollResult = rand.nextInt(3);
   }

   public Path createShortPath(LivingEntity pathTarget) {
      this.returnShort = true;
      this.refreshDimensions();
      Path shortPath = this.getNavigation().createPath(pathTarget, 0);
      this.returnShort = false;
      this.refreshDimensions();
      return shortPath;
   }

   private PlayState predicate(AnimationState tAnimationState) {
      if (this.entityData.get(AGGRO_ACCESSOR)) {
         if (this.entityData.get(SQUEEZING_ACCESSOR)) {
            return tAnimationState.setAndContinue(this.CRAWL);
         } else if (this.entityData.get(CROUCHING_ACCESSOR)) {
            return tAnimationState.isMoving() ? tAnimationState.setAndContinue(this.CROUCH_RUN) : tAnimationState.setAndContinue(this.CROUCH_IDLE);
         } else {
            return tAnimationState.isMoving() ? tAnimationState.setAndContinue(this.CHASE) : tAnimationState.setAndContinue(this.CHASE_IDLE);
         }
      } else if (this.entityData.get(FLEEING_ACCESSOR)) {
         return tAnimationState.isMoving() ? tAnimationState.setAndContinue(this.FLEE) : tAnimationState.setAndContinue(this.CHASE_IDLE);
      } else if (this.entityData.get(SPOTTED_ACCESSOR)) {
         return tAnimationState.setAndContinue(this.IS_SPOTTED);
      } else {
         return tAnimationState.isMoving() ? tAnimationState.setAndContinue(this.CALM_RUN) : tAnimationState.setAndContinue(this.CALM_STILL);
      }
   }

   @Override
   public void registerControllers(ControllerRegistrar controllerRegistrar) {
      controllerRegistrar.add(
              new AnimationController(this, "controller", 3, this::predicate)
                 .triggerableAnim("calm_run", this.CALM_RUN)
                 .triggerableAnim("calm_still", this.CALM_STILL)
                 .triggerableAnim("chase", this.CHASE)
                 .triggerableAnim("chase_idle", this.CHASE_IDLE)
                 .triggerableAnim("crouch_run", this.CROUCH_RUN)
                 .triggerableAnim("crouch_idle", this.CROUCH_IDLE)
                 .triggerableAnim("is_spotted", this.IS_SPOTTED)
                 .triggerableAnim("crawl", this.CRAWL)
      );
   }

   @Override
   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }

   @Override
   protected void playStepSound(BlockPos pPos, BlockState pState) {
      super.playStepSound(pPos, pState);
      this.playEntitySound(this.chooseStep());
   }

   private void playEntitySound(SoundEvent soundEvent) {
      this.playEntitySound(soundEvent, 1.0F, 1.0F);
   }

   private void playEntitySound(SoundEvent soundEvent, float volume, float pitch) {
      this.level.playSound(null, this, soundEvent, SoundSource.HOSTILE, volume, pitch);
   }

   private void playBlockPosSound(SoundEvent soundEvent, float volume, float pitch) {
      BlockPos blockPos = new BlockPos(this.position());
      Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(soundEvent, SoundSource.HOSTILE, volume, pitch, RandomSource.create(), blockPos));
   }

   public void playChaseSound() {
      if (this.startedPlayingChaseSound || this.isMoving()) {
         if (this.chaseSoundClock <= 0) {
            Random rand = new Random();
            switch(rand.nextInt(4)) {
               case 0:
                  this.playEntitySound((SoundEvent)ModSounds.CHASE_1.get(), 3.0F, 1.0F);
                  break;
               case 1:
                  this.playEntitySound((SoundEvent)ModSounds.CHASE_2.get(), 3.0F, 1.0F);
                  break;
               case 2:
                  this.playEntitySound((SoundEvent)ModSounds.CHASE_3.get(), 3.0F, 1.0F);
                  break;
               case 3:
                  this.playEntitySound((SoundEvent)ModSounds.CHASE_4.get(), 3.0F, 1.0F);
            }

            this.startedPlayingChaseSound = true;
            this.resetChaseSoundClock();
         }

         --this.chaseSoundClock;
      }
   }

   public void playFleeSound() {
      if (!this.alreadyPlayedFleeSound) {
         Random rand = new Random();
         switch(rand.nextInt(2)) {
            case 0:
               this.playEntitySound((SoundEvent)ModSounds.FLEE_1.get(), 3.0F, 1.0F);
               break;
            case 1:
               this.playEntitySound((SoundEvent)ModSounds.FLEE_2.get(), 3.0F, 1.0F);
         }

         this.alreadyPlayedFleeSound = true;
      }
   }

   public void playSpottedSound() {
      if (!this.alreadyPlayedSpottedSound) {
         this.playEntitySound((SoundEvent)ModSounds.SPOTTED.get(), 3.0F, 1.0F);
         this.alreadyPlayedSpottedSound = true;
      }
   }

   public void playDisappearSound() {
      this.playBlockPosSound((SoundEvent)ModSounds.DISAPPEAR.get(), 3.0F, 1.0F);
   }

   private void resetChaseSoundClock() {
      this.chaseSoundClock = this.chaseSoundClockReset;
   }

   private SoundEvent chooseStep() {
      Random rand = new Random();
      switch(rand.nextInt(4)) {
         case 0:
            return (SoundEvent)ModSounds.CHASE_STEP_1.get();
         case 1:
            return (SoundEvent)ModSounds.CHASE_STEP_2.get();
         case 2:
            return (SoundEvent)ModSounds.CHASE_STEP_3.get();
         case 3:
            return (SoundEvent)ModSounds.CHASE_STEP_4.get();
         default:
            return (SoundEvent)ModSounds.CHASE_STEP_1.get();
      }
   }

   @Override
   public EntityDimensions getDimensions(Pose pPose) {
      if (this.isAggro) {
         return this.returnShort ? new EntityDimensions(0.5F, 0.9F, true) : new EntityDimensions(0.5F, 1.9F, true);
      } else {
         return new EntityDimensions(0.5F, 1.9F, true);
      }
   }

   private SoundEvent chooseHurtSound() {
      Random rand = new Random();
      switch(rand.nextInt(4)) {
         case 0:
            return ModSounds.DWELLER_HURT_1.get();
         case 1:
            return ModSounds.DWELLER_HURT_2.get();
         case 2:
            return ModSounds.DWELLER_HURT_3.get();
         case 3:
            return ModSounds.DWELLER_HURT_4.get();
         default:
            return ModSounds.DWELLER_HURT_1.get();
      }
   }

   @Override
   protected void playHurtSound(DamageSource pSource) {
      SoundEvent soundevent = this.chooseHurtSound();
      if (soundevent != null) {
         this.playEntitySound(soundevent, 2.0F, 1.0F);
      }
   }

   @Override
   protected void tickDeath() {
      super.tickDeath();
      if (!this.alreadyPlayedDeathSound) {
         this.playBlockPosSound((SoundEvent)ModSounds.DWELLER_DEATH.get(), 2.0F, 1.0F);
         this.alreadyPlayedDeathSound = true;
      }
   }

   @Override
   protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
      return this.chooseHurtSound();
   }

   @Override
   protected SoundEvent getDeathSound() {
      return (SoundEvent)ModSounds.DWELLER_DEATH.get();
   }

   @Override
   protected float getSoundVolume() {
      return 0.4F;
   }
}
