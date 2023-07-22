package com.gargin.cavenoise.entity.custom;

import javax.annotation.Nullable;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

public class DwellerTargetTooCloseGoal extends NearestAttackableTargetGoal<Player> {
   private Player pendingTarget;
   private CaveDwellerEntity cavedweller;
   private float distanceThreshold;

   public DwellerTargetTooCloseGoal(CaveDwellerEntity pCaveDweller, float pDistanceThreshold) {
      super(pCaveDweller, Player.class, false);
      this.cavedweller = pCaveDweller;
      this.distanceThreshold = pDistanceThreshold;
   }

   public void setPendingTarget(@Nullable Player pendingTarget) {
      this.pendingTarget = pendingTarget;
   }

   public boolean inPlayerLineOfSight() {
      return this.pendingTarget != null ? this.pendingTarget.hasLineOfSight(this.cavedweller) : false;
   }

   @Override
   public boolean canUse() {
      if (this.cavedweller.isInvisible()) {
         return false;
      } else {
         this.setPendingTarget(this.cavedweller.level.getNearestPlayer(this.cavedweller, this.distanceThreshold));
         if (this.pendingTarget == null) {
            return false;
         } else {
            return this.pendingTarget.isCreative() ? false : this.inPlayerLineOfSight();
         }
      }
   }

   @Override
   public void start() {
      this.cavedweller.getEntityData().set(CaveDwellerEntity.AGGRO_ACCESSOR, true);
      this.cavedweller.isAggro = true;
      this.cavedweller.rRollResult = 0;
      super.target = this.pendingTarget;
      this.cavedweller.setTarget(this.pendingTarget);
      super.start();
   }

   @Override
   public void stop() {
      this.pendingTarget = null;
      super.stop();
   }

   @Override
   public boolean canContinueToUse() {
      if (this.pendingTarget.isCreative()) {
         return false;
      } else {
         return this.pendingTarget != null;
      }
   }

   @Override
   public void tick() {
      super.tick();
   }
}
