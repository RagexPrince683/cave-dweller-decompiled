package com.gargin.cavenoise.entity.custom;

import java.util.EnumSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.Goal.Flag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class DwellerChaseGoal extends Goal {
   protected final PathfinderMob mob;
   private final CaveDwellerEntity cavedweller;
   private final double speedModifier;
   private double crawlModifier = 0.6;
   private final boolean followingTargetEvenIfNotSeen;
   private Path path;
   private double pathedTargetX;
   private double pathedTargetY;
   private double pathedTargetZ;
   private int ticksUntilNextPathRecalculation;
   private int ticksUntilNextAttack;
   private final int attackInterval = 20;
   private long lastCanUseCheck;
   private static final long COOLDOWN_BETWEEN_CAN_USE_CHECKS = 20L;
   private int failedPathFindingPenalty = 0;
   private boolean canPenalize = false;
   private float ticksTillChase;
   private float currentTicksTillChase;
   private boolean shouldUseShortPath = false;
   private boolean squeezing = false;
   private boolean crawling = false;
   private Path shortPath;
   private Vec3 vecNodePos;
   private Vec3 vecMobPos;
   private int ticksToSqueeze;
   private int currentTicksToSqueeze;
   private int ticksTillLeave;
   private int currentTicksTillLeave;
   Vec3 xPathStartVec;
   Vec3 zPathStartVec;
   Vec3 xPathTargetVec;
   Vec3 zPathTargetVec;
   Vec3 vecTargetPos;
   Vec3 nodePositionCooldownPos;
   BlockPos nodePos;

   public DwellerChaseGoal(
      PathfinderMob pMob, CaveDwellerEntity pCaveDweller, double pSpeedModifier, boolean pFollowingTargetEvenIfNotSeen, float pTicksTillChase
   ) {
      this.mob = pMob;
      this.speedModifier = pSpeedModifier;
      this.followingTargetEvenIfNotSeen = pFollowingTargetEvenIfNotSeen;
      this.cavedweller = pCaveDweller;
      this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
      this.ticksTillChase = pTicksTillChase;
      this.currentTicksTillChase = pTicksTillChase;
      this.vecNodePos = null;
      this.ticksToSqueeze = 15;
      this.nodePos = null;
      this.ticksTillLeave = 600;
      this.currentTicksTillLeave = this.ticksTillLeave;
   }

   @Override
   public boolean canUse() {
      if (this.cavedweller.isInvisible()) {
         return false;
      } else if (this.cavedweller.rRollResult != 0) {
         return false;
      } else {
         long i = this.mob.level.getGameTime();
         if (i - this.lastCanUseCheck < 20L) {
            return false;
         } else {
            this.lastCanUseCheck = i;
            LivingEntity livingentity = this.mob.getTarget();
            if (livingentity == null) {
               return false;
            } else if (!livingentity.isAlive()) {
               return false;
            } else if (this.canPenalize) {
               if (--this.ticksUntilNextPathRecalculation <= 0) {
                  this.path = this.mob.getNavigation().createPath(livingentity, 0);
                  this.ticksUntilNextPathRecalculation = 2;
                  return this.path != null;
               } else {
                  return true;
               }
            } else {
               this.path = this.mob.getNavigation().createPath(livingentity, 0);
               if (this.path != null) {
                  return true;
               } else {
                  return this.getAttackReachSqr(livingentity) >= this.mob.distanceToSqr(livingentity.getX(), livingentity.getY(), livingentity.getZ());
               }
            }
         }
      }
   }

   @Override
   public boolean canContinueToUse() {
      LivingEntity livingentity = this.mob.getTarget();
      if (livingentity == null) {
         return false;
      } else if (!livingentity.isAlive()) {
         this.cavedweller.discard();
         return false;
      } else if (!this.followingTargetEvenIfNotSeen) {
         return !this.mob.getNavigation().isDone();
      } else if (!this.mob.isWithinRestriction(livingentity.blockPosition())) {
         return false;
      } else {
         return !(livingentity instanceof Player) || !livingentity.isSpectator() && !((Player)livingentity).isCreative();
      }
   }

   @Override
   public void start() {
      this.ticksUntilNextPathRecalculation = 0;
      this.ticksUntilNextAttack = 0;
   }

   @Override
   public void stop() {
      LivingEntity livingentity = this.mob.getTarget();
      if (!EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(livingentity)) {
         this.mob.setTarget(null);
      }

      this.cavedweller.squeezeCrawling = false;
      this.cavedweller.getEntityData().set(CaveDwellerEntity.AGGRO_ACCESSOR, false);
      this.cavedweller.isAggro = false;
      this.cavedweller.refreshDimensions();
      this.currentTicksTillChase = this.ticksTillChase;
      this.mob.setAggressive(false);
      this.mob.getNavigation().stop();
      this.cavedweller.setNoGravity(false);
      this.cavedweller.noPhysics = false;
   }

   @Override
   public boolean requiresUpdateEveryTick() {
      return true;
   }

   public void tickAggroClock() {
      --this.currentTicksTillChase;
      if (this.currentTicksTillChase <= 0.0F) {
         this.cavedweller.getEntityData().set(CaveDwellerEntity.AGGRO_ACCESSOR, true);
      }

      this.cavedweller.isAggro = true;
      this.cavedweller.refreshDimensions();
   }

   public Path getShortPath(LivingEntity livingentity) {
      return this.shortPath = this.cavedweller.createShortPath(livingentity);
   }

   public static double lerp(double a, double b, double f) {
      return (b - a) * f + a;
   }

   public void squeezingTick() {
      this.cavedweller.setNoGravity(true);
      this.cavedweller.noPhysics = true;
      LivingEntity livingentity = this.mob.getTarget();
      if (this.mob.getNavigation().getPath() != null) {
         this.nodePos = this.mob.getNavigation().getPath().getNextNodePos();
      }

      this.mob.getNavigation().stop();
      if (this.nodePos == null) {
         this.stopSqueezing();
      } else {
         if (this.vecNodePos == null) {
            this.vecNodePos = new Vec3(this.nodePos.getX(), this.nodePos.getY(), this.nodePos.getZ());
         }

         this.nodePositionCooldownPos = this.vecNodePos;
         Vec3 vecOldMobPos = this.cavedweller.getPosition(1.0F);
         if (this.xPathStartVec == null) {
            if (vecOldMobPos.x < this.vecNodePos.x) {
               this.xPathStartVec = new Vec3(this.vecNodePos.x - 1.0, this.vecNodePos.y - 1.0, this.vecNodePos.z + 0.5);
               this.xPathTargetVec = new Vec3(this.vecNodePos.x + 1.0, this.vecNodePos.y - 1.0, this.vecNodePos.z + 0.5);
            } else {
               this.xPathStartVec = new Vec3(this.vecNodePos.x + 1.0, this.vecNodePos.y - 1.0, this.vecNodePos.z + 0.5);
               this.xPathTargetVec = new Vec3(this.vecNodePos.x - 1.0, this.vecNodePos.y - 1.0, this.vecNodePos.z + 0.5);
            }
         }

         if (this.zPathStartVec == null) {
            if (vecOldMobPos.z < this.vecNodePos.z) {
               this.zPathStartVec = new Vec3(this.vecNodePos.x + 0.5, this.vecNodePos.y - 1.0, this.vecNodePos.z - 1.0);
               this.zPathTargetVec = new Vec3(this.vecNodePos.x + 0.5, this.vecNodePos.y - 1.0, this.vecNodePos.z + 1.0);
            } else {
               this.zPathStartVec = new Vec3(this.vecNodePos.x + 0.5, this.vecNodePos.y - 1.0, this.vecNodePos.z + 1.0);
               this.zPathTargetVec = new Vec3(this.vecNodePos.x + 0.5, this.vecNodePos.y - 1.0, this.vecNodePos.z - 1.0);
            }
         }

         MutableBlockPos blockpos$mutableblockpos = new MutableBlockPos(this.xPathTargetVec.x, this.xPathTargetVec.y, this.xPathTargetVec.z);
         BlockState blockstate = this.cavedweller.level.getBlockState(blockpos$mutableblockpos);
         boolean xBlocked = blockstate.getMaterial().blocksMotion();
         blockpos$mutableblockpos = new MutableBlockPos(this.zPathTargetVec.x, this.zPathTargetVec.y, this.zPathTargetVec.z);
         blockstate = this.cavedweller.level.getBlockState(blockpos$mutableblockpos);
         boolean zBlocked = blockstate.getMaterial().blocksMotion();
         if (xBlocked) {
            this.vecMobPos = this.zPathStartVec;
            this.vecTargetPos = this.zPathTargetVec;
         }

         if (zBlocked) {
            this.vecMobPos = this.xPathStartVec;
            this.vecTargetPos = this.xPathTargetVec;
         }

         if (this.vecTargetPos != null && this.vecMobPos != null) {
            ++this.currentTicksToSqueeze;
            float tickF = (float)this.currentTicksToSqueeze / (float)this.ticksToSqueeze;
            Vec3 vecCurrentMobPos = new Vec3(
               lerp(this.vecMobPos.x, this.vecTargetPos.x, tickF), this.vecMobPos.y, lerp(this.vecMobPos.z, this.vecTargetPos.z, tickF)
            );
            Vec3 rotAxis = new Vec3(this.vecTargetPos.x - this.vecMobPos.x, 0.0, this.vecTargetPos.z - this.vecMobPos.z);
            rotAxis = rotAxis.normalize();
            double rotAngle = Math.toDegrees(Math.atan2(-rotAxis.x, rotAxis.z));
            this.cavedweller.setYHeadRot((float)rotAngle);
            this.cavedweller.moveTo(vecCurrentMobPos.x, vecCurrentMobPos.y, vecCurrentMobPos.z, (float)rotAngle, (float)rotAngle);
            if (tickF >= 1.0F) {
               this.cavedweller.setPos(this.vecTargetPos.x, this.vecTargetPos.y, this.vecTargetPos.z);
               this.stopSqueezing();
            }
         } else {
            this.stopSqueezing();
         }
      }
   }

   public void stopSqueezing() {
      this.squeezing = false;
      this.cavedweller.getEntityData().set(CaveDwellerEntity.SQUEEZING_ACCESSOR, false);
      this.cavedweller.setNoGravity(false);
      this.cavedweller.noPhysics = false;
   }

   public void startSqueezing() {
      this.vecNodePos = null;
      this.vecMobPos = null;
      this.xPathStartVec = null;
      this.zPathStartVec = null;
      this.xPathTargetVec = null;
      this.zPathTargetVec = null;
      this.vecTargetPos = null;
      this.currentTicksToSqueeze = 0;
      this.squeezing = true;
      this.cavedweller.getEntityData().set(CaveDwellerEntity.SQUEEZING_ACCESSOR, true);
      this.nodePos = null;
   }

   public boolean checkIfShouldSqueeze(Path pathToCheck) {
      if (pathToCheck == null) {
         return false;
      } else {
         BlockPos blockpos;
         if (!pathToCheck.isDone()) {
            blockpos = pathToCheck.getNextNodePos();
            if (this.nodePositionCooldownPos != null
               && blockpos.getX() == (int)this.nodePositionCooldownPos.x
               && blockpos.getY() == (int)this.nodePositionCooldownPos.y
               && blockpos.getZ() == (int)this.nodePositionCooldownPos.z) {
               return false;
            } else {
               MutableBlockPos blockpos$mutableblockpos = new MutableBlockPos(blockpos.getX(), blockpos.getY() + 1, blockpos.getZ());
               BlockState blockstate = this.cavedweller.level.getBlockState(blockpos$mutableblockpos);
               boolean flag = blockstate.getMaterial().blocksMotion();
               boolean flag2 = this.cavedweller.distanceToSqr(blockpos.getX(), blockpos.getY(), blockpos.getZ()) < 1.5;
               return flag;
            }
         } else {
            return false;
         }
      }
   }

   public void aggroTick() {
      this.cavedweller.playChaseSound();
      this.cavedweller.noPhysics = false;
      this.cavedweller.setNoGravity(false);
      LivingEntity livingentity = this.mob.getTarget();
      if (this.shouldUseShortPath) {
         this.getShortPath(livingentity);
      }

      if (this.mob.getNavigation().getPath() != null && this.checkIfShouldSqueeze(this.mob.getNavigation().getPath()) && this.shouldUseShortPath) {
         this.startSqueezing();
         this.squeezing = true;
         this.cavedweller.getEntityData().set(CaveDwellerEntity.SQUEEZING_ACCESSOR, true);
      } else {
         if (livingentity != null) {
            double d0 = this.mob.getPerceivedTargetDistanceSquareForMeleeAttack(livingentity);
            this.ticksUntilNextPathRecalculation = Math.max(this.ticksUntilNextPathRecalculation - 1, 0);
            if ((this.followingTargetEvenIfNotSeen || this.mob.getSensing().hasLineOfSight(livingentity))
               && this.ticksUntilNextPathRecalculation <= 0
               && (
                  this.pathedTargetX == 0.0 && this.pathedTargetY == 0.0 && this.pathedTargetZ == 0.0
                     || livingentity.distanceToSqr(this.pathedTargetX, this.pathedTargetY, this.pathedTargetZ) >= 1.0
                     || this.mob.getRandom().nextFloat() < 0.05F
               )) {
               this.pathedTargetX = livingentity.getX();
               this.pathedTargetY = livingentity.getY();
               this.pathedTargetZ = livingentity.getZ();
               this.ticksUntilNextPathRecalculation = 2;
               if (this.canPenalize) {
                  this.ticksUntilNextPathRecalculation += this.failedPathFindingPenalty;
                  if (this.mob.getNavigation().getPath() != null) {
                     Node finalPathPoint = this.mob.getNavigation().getPath().getEndNode();
                     if (finalPathPoint != null
                        && livingentity.distanceToSqr(finalPathPoint.x, finalPathPoint.y, finalPathPoint.z) < 1.0) {
                        this.failedPathFindingPenalty = 0;
                     } else {
                        this.failedPathFindingPenalty += 10;
                     }
                  } else {
                     this.failedPathFindingPenalty += 10;
                  }
               }

               if (this.mob.getNavigation().getPath() != null) {
                  Node finalPathPoint = this.mob.getNavigation().getPath().getEndNode();
                  if (finalPathPoint == null
                     || !(livingentity.distanceToSqr(finalPathPoint.x, finalPathPoint.y, finalPathPoint.z) < 1.0)) {
                     this.shouldUseShortPath = true;
                  }
               } else {
                  this.shouldUseShortPath = true;
               }

               if (this.shortPath != null && !this.shortPath.isDone()) {
               }

               if (d0 > 1024.0) {
                  this.ticksUntilNextPathRecalculation += 10;
               } else if (d0 > 256.0) {
                  this.ticksUntilNextPathRecalculation += 5;
               }

               if (!this.shouldUseShortPath) {
                  if (!this.mob.getNavigation().moveTo(livingentity, this.speedModifier)) {
                     this.cavedweller.startedMovingChase = true;
                     this.ticksUntilNextPathRecalculation += 8;
                  }
               } else if (!this.mob.getNavigation().moveTo(this.shortPath, this.speedModifier)) {
                  this.cavedweller.startedMovingChase = true;
                  this.ticksUntilNextPathRecalculation += 8;
               }

               this.ticksUntilNextPathRecalculation = this.adjustedTickDelay(this.ticksUntilNextPathRecalculation);
            }

            this.ticksUntilNextAttack = Math.max(this.ticksUntilNextAttack - 1, 0);
            this.checkAndPerformAttack(livingentity, d0);
         }
      }
   }

   @Override
   public void tick() {
      this.cavedweller.squeezeCrawling = this.squeezing;
      LivingEntity livingentity = null;
      if (this.cavedweller.getTarget() != null) {
         livingentity = this.mob.getTarget();
      }

      this.tickAggroClock();
      if (!this.squeezing) {
         if (this.cavedweller.isAggro) {
            this.mob.getLookControl().setLookAt(livingentity, 90.0F, 90.0F);
         } else {
            this.mob.getLookControl().setLookAt(livingentity, 180.0F, 1.0F);
         }
      }

      if (this.cavedweller.getEntityData().get(CaveDwellerEntity.AGGRO_ACCESSOR)) {
         if (this.squeezing) {
            this.squeezingTick();
         } else {
            this.aggroTick();
         }
      }

      --this.currentTicksTillLeave;
      if (this.currentTicksTillLeave <= 0 && (!this.isPlayerLookingTowards() || !this.inPlayerLineOfSight())) {
         this.cavedweller.discard();
      }
   }

   public boolean isPlayerLookingTowards() {
      LivingEntity pendingTarget = this.cavedweller.getTarget();
      Minecraft minecraft = Minecraft.getInstance();
      boolean yawPlayerLookingTowards = false;
      float fov = (float) minecraft.options.fov().get().intValue();
      float yFovMod = 0.65F;
      float fovMod = (35.0F / fov - 1.0F) * 0.4F + 1.0F;
      fov *= fovMod;
      Vec3 a = pendingTarget.position();
      Vec3 b = this.cavedweller.position();
      Vec2 dist = new Vec2((float)b.x - (float)a.x, (float)b.z - (float)a.z);
      dist = dist.normalized();
      double newAngle = Math.toDegrees(Math.atan2(dist.x, dist.y));
      float lookX = (float)pendingTarget.getViewVector(1.0F).x;
      float lookZ = (float)pendingTarget.getViewVector(1.0F).z;
      double newLookAngle = Math.toDegrees(Math.atan2(lookX, lookZ));
      double newNewAngle = this.loopAngle(newAngle - newLookAngle) + (double)fov;
      newNewAngle = this.loopAngle(newNewAngle);
      if (newNewAngle > 0.0 && newNewAngle < (double)(fov * 2.0F)) {
         yawPlayerLookingTowards = true;
      }

      boolean pitchPlayerLookingTowards = false;
      boolean shouldOnlyUsePitch = false;
      float yFov = fov * yFovMod;
      Vec2 yDist = new Vec2((float)Math.sqrt((b.x - a.x) * (b.x - a.x) + (b.z - a.z) * (b.z - a.z)), (float)(b.y - a.y));
      yDist = yDist.normalized();
      double yAngle = Math.toDegrees(Math.atan2(yDist.x, yDist.y));
      float lookY = (float)pendingTarget.getViewVector(1.0F).y;
      Vec2 lookDist = new Vec2((float)Math.sqrt(lookX * lookX + lookZ * lookZ), lookY);
      lookDist = lookDist.normalized();
      double yLookAngle = Math.toDegrees(Math.atan2(lookDist.x, lookDist.y));
      double newYAngle = this.loopAngle(yAngle - yLookAngle) + (double)yFov;
      newYAngle = this.loopAngle(newYAngle);
      if (newYAngle > 0.0 && newYAngle < (double)(yFov * 2.0F)) {
         pitchPlayerLookingTowards = true;
      }

      if (!(yLookAngle < (double)(180.0F - yFov)) || !(yLookAngle > (double)yFov)) {
         shouldOnlyUsePitch = true;
      }

      return (yawPlayerLookingTowards || shouldOnlyUsePitch) && pitchPlayerLookingTowards;
   }

   public double loopAngle(double angle) {
      if (angle > 360.0) {
         return angle - 360.0;
      } else {
         return angle < 0.0 ? angle + 360.0 : angle;
      }
   }

   public boolean inPlayerLineOfSight() {
      LivingEntity pendingTarget = this.cavedweller.getTarget();
      return pendingTarget != null ? pendingTarget.hasLineOfSight(this.cavedweller) : false;
   }

   protected void checkAndPerformAttack(LivingEntity pEnemy, double pDistToEnemySqr) {
      double d0 = this.getAttackReachSqr(pEnemy);
      if (pDistToEnemySqr <= d0 && this.ticksUntilNextAttack <= 0) {
         this.resetAttackCooldown();
         this.mob.swing(InteractionHand.MAIN_HAND);
         this.mob.doHurtTarget(pEnemy);
      }
   }

   protected void resetAttackCooldown() {
      this.ticksUntilNextAttack = this.adjustedTickDelay(20);
   }

   protected boolean isTimeToAttack() {
      return this.ticksUntilNextAttack <= 0;
   }

   protected int getTicksUntilNextAttack() {
      return this.ticksUntilNextAttack;
   }

   protected int getAttackInterval() {
      return this.adjustedTickDelay(20);
   }

   protected double getAttackReachSqr(LivingEntity pAttackTarget) {
      return this.mob.getBbWidth() * 4.0F * this.mob.getBbWidth() * 4.0F + pAttackTarget.getBbWidth();
   }
}
