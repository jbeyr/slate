package slate.module.impl.render;

import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;
import slate.module.Module;
import slate.module.setting.impl.ButtonSetting;
import slate.module.setting.impl.SliderSetting;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders small HUD-triangles that point toward nearby projectiles.
 */
public class Pointers extends Module {

    /* ────────────────── Settings ────────────────── */

    private final ButtonSetting showArrows    = new ButtonSetting("Arrows",    true);
    private final ButtonSetting showFireballs = new ButtonSetting("Fireballs", true);

    private final SliderSetting maxDistance = new SliderSetting("Max dist", 40d, 5d, 100d, 1d);
    private final SliderSetting maxPointers = new SliderSetting("Pointers",  8,   1,  25,  1);

    /* keeps pointers above the vanilla hot-bar */
    private final SliderSetting bottomMargin =
            new SliderSetting("Hot-bar margin", 24d, 0d, 60d, 1d);

    /* Visual constants (PX) */
    private static final float RADIUS = 28f;
    private static final float SIZE   =  6f;

    public Pointers() {
        super("Pointers", category.render);
        this.registerSetting(
                showArrows, showFireballs,
                maxDistance, maxPointers,
                bottomMargin
        );
    }

    /* ────────────────── Rendering ────────────────── */

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        if (!isEnabled()) return;
        if (e.type != RenderGameOverlayEvent.ElementType.CROSSHAIRS) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        /* 1. Collect nearby projectiles */
        List<Entity> targets = new ArrayList<>();
        double maxDistSqr = maxDistance.getInput() * maxDistance.getInput();

        for (Entity ent : mc.theWorld.loadedEntityList) {
            if (ent == mc.thePlayer) continue;

            boolean ok =
                    (showArrows.isToggled() &&
                            ent instanceof EntityArrow &&
                            !ent.onGround) ||                     // ← correct “stuck” check
                            (showFireballs.isToggled() && ent instanceof EntityFireball);

            if (!ok) continue;
            if (mc.thePlayer.getDistanceSqToEntity(ent) > maxDistSqr) continue;

            targets.add(ent);
            if (targets.size() >= maxPointers.getInput()) break;
        }

        if (targets.isEmpty()) return;

        /* 2. Screen / player view info */
        ScaledResolution sr = new ScaledResolution(mc);
        float centreX = sr.getScaledWidth()  / 2f;
        float centreY = sr.getScaledHeight() / 2f;
        float yaw   = mc.thePlayer.rotationYaw;
        float pitch = mc.thePlayer.rotationPitch;

        /* 3. OpenGL begin */
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);   // save every flag

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);

        float safeBottom = sr.getScaledHeight() - (float) bottomMargin.getInput();

        for (Entity ent : targets) {

            /* 3a. Direction from camera to entity → 2-D vector */
            double dx = ent.posX - mc.thePlayer.posX;
            double dy = (ent.posY + ent.height * 0.5)
                    - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
            double dz = ent.posZ - mc.thePlayer.posZ;

            double yawTo   = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
            double pitchTo = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx*dx + dz*dz)));

            double relYaw   = MathHelper.wrapAngleTo180_double(yawTo - yaw);
            double relPitch = pitchTo - pitch;

            float dirX = (float) MathHelper.sin((float) Math.toRadians(relYaw));
            float dirY = (float) MathHelper.sin((float) Math.toRadians(relPitch));

            float len = Math.max(0.0001f, (float) Math.sqrt(dirX*dirX + dirY*dirY));
            dirX /= len; dirY /= len;

            float px = centreX + dirX * RADIUS;
            float py = centreY + dirY * RADIUS;
            if (py > safeBottom) py = safeBottom;     // keep above hot-bar

            /* 3b. Triangle */
            GL11.glColor4f(1f, 1f, 1f, 1f);

            GL11.glBegin(GL11.GL_TRIANGLES);
            GL11.glVertex2f(px + dirX * SIZE,           py + dirY * SIZE);           // tip
            GL11.glVertex2f(px - dirY * SIZE * 0.7f,    py + dirX * SIZE * 0.7f);    // base left
            GL11.glVertex2f(px + dirY * SIZE * 0.7f,    py - dirX * SIZE * 0.7f);    // base right
            GL11.glEnd();

            /* 3c. Distance text */
            int distBlocks = (int) Math.round(mc.thePlayer.getDistanceToEntity(ent));
            String txt = distBlocks + "m";

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            mc.fontRendererObj.drawStringWithShadow(
                    txt,
                    px - mc.fontRendererObj.getStringWidth(txt) / 2f,
                    py - 4 - SIZE,
                    0xFFFFFF
            );
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        }

        /* 4. Restore OpenGL (matrix + attrib) */
        GL11.glPopAttrib();   // restores textures, blend, depth, cull, colours, …
        GL11.glPopMatrix();
    }
}