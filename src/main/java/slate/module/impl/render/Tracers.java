package slate.module.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;
import slate.module.Module;
import slate.module.ModuleManager;
import slate.module.setting.impl.ButtonSetting;
import slate.module.setting.impl.SliderSetting;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Tracers extends Module {
    private float maxDistanceSqr;

    private final ButtonSetting onlyPlayers = new ButtonSetting("Only players", true);
    private final SliderSetting cameraDistance = new SliderSetting("Distance", 80d, -10d, 30d, 1d); // [-10f, 30f]
    private final SliderSetting maxDistance = new SliderSetting("Distance", 20d, 5d, 50d, 1);
    private final ButtonSetting respectLineOfSight = new ButtonSetting("Line of sight", false);
    private final SliderSetting maxLines = new SliderSetting("Lines", 5, 1, 20, 1);
    private final SliderSetting maxVerticalDistance = new SliderSetting("Vertical distance", 20d, 5d, 50d, 1);

    public Tracers() {
        super("Tracers", category.render);
        this.registerSetting(cameraDistance, maxDistance, respectLineOfSight, maxLines, maxVerticalDistance);
    }

    @Override
    public void guiUpdate() throws Throwable {
        super.guiUpdate();

        maxDistanceSqr = (float) Math.pow(maxDistance.getInput(), 2);
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!isEnabled()) return;

        EntityPlayer me = mc.thePlayer;
        if (me == null) return;

        float partialTicks = event.partialTicks;

        // interpolate player position
        double meX = me.lastTickPosX + (me.posX - me.lastTickPosX) * partialTicks;
        double meY = me.lastTickPosY + (me.posY - me.lastTickPosY) * partialTicks;
        double meZ = me.lastTickPosZ + (me.posZ - me.lastTickPosZ) * partialTicks;

        Vec3 start;
        if (mc.gameSettings.thirdPersonView == 0) {
            double pitch = ((me.rotationPitch + 90) * Math.PI) / 180;
            double yaw = ((me.rotationYaw + 90) * Math.PI) / 180;

            Vec3 modif = new Vec3(
                    Math.sin(pitch) * Math.cos(yaw) * cameraDistance.getInput(),
                    Math.cos(pitch) * cameraDistance.getInput() - 0.35,
                    Math.sin(pitch) * Math.sin(yaw) * cameraDistance.getInput()
            );

            start = new Vec3(meX, meY + me.getEyeHeight(), meZ).add(modif);
        } else {
            start = new Vec3(meX, meY + me.getEyeHeight(), meZ);
        }

        List<EntityDatum> entities = new ArrayList<>();

        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity == me) continue;
            if (!(entity instanceof EntityLivingBase)) continue;
            if (onlyPlayers.isToggled() && !(entity instanceof EntityPlayer)) continue;
            if (!ModuleManager.targetManager.isRecommendedTarget((EntityLivingBase) entity)) continue;

            // interpolate entity position
            double entityX = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
            double entityY = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
            double entityZ = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;

            double dx = entityX - meX;
            double dy = entityY - meY;
            double dz = entityZ - meZ;
            double distanceSqr = dx * dx + dy * dy + dz * dz;

            if (distanceSqr > maxDistanceSqr) continue;

            // vertical distance check
            if (Math.abs((float) (meY + 1.0 - entityY)) > maxVerticalDistance.getInput()) continue;

            // do raycasting to check actual visibility
            if (respectLineOfSight.isToggled()) {
                Vec3 eyePos = new Vec3(meX, meY + me.getEyeHeight(), meZ);
                Vec3 targetPos = new Vec3(entityX, entityY + entity.getEyeHeight(), entityZ);
                MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eyePos, targetPos);
                if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                    continue;
                }
            }

            entities.add(new EntityDatum((EntityLivingBase) entity, (float)distanceSqr, entityX, entityY, entityZ));
        }

        entities.sort(Comparator.comparingDouble(EntityDatum::distSqr));

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glLineWidth(1f);

        final float CLOSE_DISTANCE_SQR = (float) Math.pow(10, 2);

        int drawn = 0;
        for (EntityDatum datum : entities) {
            Color color;
            if (datum.distSqr() <= CLOSE_DISTANCE_SQR) {
                color = new Color(255, 0, 0, 255);
            } else {
                color = new Color(255, 255, 255, 255);
            }

            GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, color.getAlpha() / 255f);

            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex3d(start.xCoord - mc.getRenderManager().viewerPosX, start.yCoord - mc.getRenderManager().viewerPosY, start.zCoord - mc.getRenderManager().viewerPosZ);
            GL11.glVertex3d(datum.x - mc.getRenderManager().viewerPosX, datum.y + datum.entity().getEyeHeight() - mc.getRenderManager().viewerPosY, datum.z - mc.getRenderManager().viewerPosZ);
            GL11.glEnd();

            drawn++;
            if (drawn >= maxLines.getInput()) break;
        }

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();
    }

    public static class EntityDatum {
        public final EntityLivingBase entity;
        public final float distSqr;
        public final double x, y, z;

        public EntityDatum(EntityLivingBase entity, float distSqr, double x, double y, double z) {
            this.entity = entity;
            this.distSqr = distSqr;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public float distSqr() { return distSqr; }
        public EntityLivingBase entity() { return entity; }
    }
}
