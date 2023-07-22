package com.gargin.cavenoise.entity.custom;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class DwellerBreakInvisGoal extends Goal {
   private CaveDwellerEntity cavedweller;
   private Player pendingTarget;

   public DwellerBreakInvisGoal(CaveDwellerEntity pCavedweller) {
      this.cavedweller = pCavedweller;
   }

   @Override
   public boolean canUse() {
      this.pendingTarget = this.cavedweller.level.getNearestPlayer(this.cavedweller, 200.0);
      return !this.inPlayerLineOfSight() || !this.isPlayerLookingTowards();
   }

   @Override
   public void start() {
      super.start();
      this.cavedweller.setInvisible(false);
   }

   public boolean inPlayerLineOfSight() {
      return this.pendingTarget != null ? this.pendingTarget.hasLineOfSight(this.cavedweller) : false;
   }

   public boolean isPlayerLookingTowards() {
      Minecraft minecraft = Minecraft.getInstance();
      boolean yawPlayerLookingTowards = false;
      float fov = (float) minecraft.options.fov().get().intValue();
      float yFovMod = 0.65F;
      float fovMod = (35.0F / fov - 1.0F) * 0.4F + 1.0F;
      fov *= fovMod;
      Vec3 a = this.pendingTarget.position();
      Vec3 b = this.cavedweller.position();
      Vec2 dist = new Vec2((float)b.x - (float)a.x, (float)b.z - (float)a.z);
      dist = dist.normalized();
      double newAngle = Math.toDegrees(Math.atan2(dist.x, dist.y));
      float lookX = (float)this.pendingTarget.getViewVector(1.0F).x;
      float lookZ = (float)this.pendingTarget.getViewVector(1.0F).z;
      double newLookAngle = Math.toDegrees(Math.atan2(lookX, (double)lookZ));
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
      float lookY = (float)this.pendingTarget.getViewVector(1.0F).y;
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
}
