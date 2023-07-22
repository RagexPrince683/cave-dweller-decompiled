package com.gargin.cavenoise.entity.custom;

import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class DwellerFleeGoal extends Goal {
   private final CaveDwellerEntity cavedweller;
   private final float ticksTillLeave;
   private final float ticksTillFlee;
   private float currentTicksTillLeave;
   private float currentTicksTillFlee;
   private boolean shouldLeave;
   private double fleeX;
   private double fleeY;
   private double fleeZ;
   private int ticksUntilNextPathRecalculation;
   private double speedModifier;

   public DwellerFleeGoal(CaveDwellerEntity pCaveDweller, float pTicksTillLeave, double pSpeedModifier) {
      this.cavedweller = pCaveDweller;
      this.ticksTillLeave = pTicksTillLeave;
      this.currentTicksTillLeave = pTicksTillLeave;
      this.ticksTillFlee = 10.0F;
      this.currentTicksTillFlee = this.ticksTillFlee;
      this.speedModifier = pSpeedModifier;
   }

   @Override
   public boolean canUse() {
      if (this.cavedweller.isInvisible()) {
         return false;
      } else if (this.cavedweller.rRollResult != 2) {
         return false;
      } else {
         return this.cavedweller.getTarget() != null;
      }
   }

   @Override
   public boolean canContinueToUse() {
      if (this.cavedweller.rRollResult != 2) {
         return false;
      } else {
         return this.cavedweller.getTarget() != null;
      }
   }

   @Override
   public void start() {
      this.getSpotToWalk();
      this.cavedweller.spottedByPlayer = false;
      this.shouldLeave = false;
   }

   @Override
   public void stop() {}

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

   public boolean inPlayerLineOfSight() {
      return this.cavedweller.getTarget() != null ? this.cavedweller.getTarget().hasLineOfSight(this.cavedweller) : false;
   }

   public double loopAngle(double angle) {
      if (angle > 360.0) {
         return angle - 360.0;
      } else {
         return angle < 0.0 ? angle + 360.0 : angle;
      }
   }

   private boolean getSpotToWalk() {
      Random rand = new Random();
      double randX = rand.nextDouble() - 0.5;
      double randY = rand.nextInt(64) - 32;
      double randZ = rand.nextDouble() - 0.5;
      if (randX > 0.0) {
         this.fleeX = (this.cavedweller.getX() + 1.0) * 64.0;
      } else {
         this.fleeX = (this.cavedweller.getX() - 1.0) * 64.0;
      }

      this.fleeY = this.cavedweller.getY() + randY;
      if (randZ > 0.0) {
         this.fleeZ = (this.cavedweller.getZ() + 1.0) * 64.0;
      } else {
         this.fleeZ = (this.cavedweller.getZ() - 1.0) * 64.0;
      }

      MutableBlockPos blockpos$mutableblockpos = new MutableBlockPos(this.fleeX, this.fleeY, this.fleeZ);

      while(
         blockpos$mutableblockpos.getY() > this.cavedweller.level.getMinBuildHeight()
            && !this.cavedweller.level.getBlockState(blockpos$mutableblockpos).getMaterial().blocksMotion()
      ) {
         blockpos$mutableblockpos.move(Direction.DOWN);
      }

      BlockState blockstate = this.cavedweller.level.getBlockState(blockpos$mutableblockpos);
      boolean flag = blockstate.getMaterial().blocksMotion();
      boolean flag1 = blockstate.getFluidState().is(FluidTags.WATER);
      return flag && !flag1;
   }

   public void tickStareClock() {
      --this.currentTicksTillLeave;
      if (this.currentTicksTillLeave < 0.0F) {
         this.shouldLeave = true;
      }
   }

   void tickFleeClock() {
      --this.currentTicksTillFlee;
   }

   public void fleeTick() {
      this.cavedweller.playFleeSound();
      this.ticksUntilNextPathRecalculation = Math.max(this.ticksUntilNextPathRecalculation - 1, 0);
      if (this.ticksUntilNextPathRecalculation <= 0) {
         this.ticksUntilNextPathRecalculation = 2;
         if (!this.cavedweller.getNavigation().moveTo(this.fleeX, this.fleeY, this.fleeZ, this.speedModifier)) {
            this.ticksUntilNextPathRecalculation += 2;
         }

         this.ticksUntilNextPathRecalculation = this.adjustedTickDelay(this.ticksUntilNextPathRecalculation);
      }
   }

   public void tick() {
      if (this.shouldLeave && (!this.isPlayerLookingTowards() || !this.inPlayerLineOfSight())) {
         this.cavedweller.discard();
      }

      this.tickFleeClock();
      this.tickStareClock();
      if (this.currentTicksTillFlee <= 0.0F) {
         this.fleeTick();
         this.cavedweller.isFleeing = true;
         this.cavedweller.getEntityData().set(CaveDwellerEntity.FLEEING_ACCESSOR, true);
      } else {
         this.cavedweller.getLookControl().setLookAt(this.cavedweller.getTarget(), 180.0F, 1.0F);
      }
   }
}
