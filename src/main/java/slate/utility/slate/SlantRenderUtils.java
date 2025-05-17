package slate.utility.slate;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

public class SlantRenderUtils {
    public static int scaleZeroOneRangeTo255(float f) {
        return (int)(f * 255);
    }

    private static void setupRendering() {
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableCull();
    }

    private static void resetRendering() {
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
    }

    public static <T extends Entity> void draw3dEntityESP(T en, float partialTicks, float red, float green, float blue, float opacity) {
        Vec3 dv = Interpolate.interpolatedDifferenceFromMeAndEntity(en, partialTicks);
        drawBbox3d(dv, red, green, blue, opacity, .25f, false);
    }

    /**
     * Pushes a matrix in GlStateManager with the drawn hitbox, renders it, then pops the matrix. Calls {@see Renderer.setupRendering} and {@see Renderer.resetRendering} internally.
     * @param v The position vector of the hitbox
     * @param red Red color component (0-1)
     * @param green Green color component (0-1)
     * @param blue Blue color component (0-1)
     * @param opacity Overall opacity of the hitbox (0-1)
     * @param filledBboxOpacityMultiplier Opacity multiplier for the filled part of the hitbox
     * @param respectDepth If true, the hitbox will be drawn behind objects closer to the camera
     */
    public static void drawBbox3d(Vec3 v, float red, float green, float blue, float opacity, float filledBboxOpacityMultiplier, boolean respectDepth) {
        setupRendering();

        if (respectDepth) {
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
        } else {
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(v.xCoord, v.yCoord, v.zCoord);

        double halfWidth = .4;
        double height = 1.9;

        // Draw filled box
        GlStateManager.color(red, green, blue, opacity * filledBboxOpacityMultiplier);
        draw3dFilledBox(-halfWidth, 0, -halfWidth, halfWidth, height, halfWidth);

        // Draw outline
        if (respectDepth) {
            // For the outline, we want it to always be visible, so we disable depth writing
            GlStateManager.depthMask(false);
        }
        GlStateManager.color(red, green, blue, opacity);
        draw3dOutlineBox(-halfWidth, 0, -halfWidth, halfWidth, height, halfWidth);

        GlStateManager.popMatrix();

        // Reset depth settings
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);

        resetRendering();
    }

    static void draw3dFilledBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        GL11.glBegin(GL11.GL_QUADS);
        // front
        GL11.glVertex3d(minX, minY, minZ);
        GL11.glVertex3d(maxX, minY, minZ);
        GL11.glVertex3d(maxX, maxY, minZ);
        GL11.glVertex3d(minX, maxY, minZ);

        // back
        GL11.glVertex3d(minX, minY, maxZ);
        GL11.glVertex3d(minX, maxY, maxZ);
        GL11.glVertex3d(maxX, maxY, maxZ);
        GL11.glVertex3d(maxX, minY, maxZ);

        // bottom
        GL11.glVertex3d(minX, minY, minZ);
        GL11.glVertex3d(minX, minY, maxZ);
        GL11.glVertex3d(maxX, minY, maxZ);
        GL11.glVertex3d(maxX, minY, minZ);

        // top
        GL11.glVertex3d(minX, maxY, minZ);
        GL11.glVertex3d(maxX, maxY, minZ);
        GL11.glVertex3d(maxX, maxY, maxZ);
        GL11.glVertex3d(minX, maxY, maxZ);

        // left
        GL11.glVertex3d(minX, minY, minZ);
        GL11.glVertex3d(minX, maxY, minZ);
        GL11.glVertex3d(minX, maxY, maxZ);
        GL11.glVertex3d(minX, minY, maxZ);

        // right
        GL11.glVertex3d(maxX, minY, minZ);
        GL11.glVertex3d(maxX, minY, maxZ);
        GL11.glVertex3d(maxX, maxY, maxZ);
        GL11.glVertex3d(maxX, maxY, minZ);
        GL11.glEnd();
    }

    static void draw3dOutlineBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        GL11.glBegin(GL11.GL_LINE_STRIP);
        GL11.glVertex3d(minX, minY, minZ);
        GL11.glVertex3d(maxX, minY, minZ);
        GL11.glVertex3d(maxX, minY, maxZ);
        GL11.glVertex3d(minX, minY, maxZ);
        GL11.glVertex3d(minX, minY, minZ);
        GL11.glVertex3d(minX, maxY, minZ);
        GL11.glVertex3d(maxX, maxY, minZ);
        GL11.glVertex3d(maxX, maxY, maxZ);
        GL11.glVertex3d(minX, maxY, maxZ);
        GL11.glVertex3d(minX, maxY, minZ);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(maxX, minY, minZ);
        GL11.glVertex3d(maxX, maxY, minZ);
        GL11.glVertex3d(maxX, minY, maxZ);
        GL11.glVertex3d(maxX, maxY, maxZ);
        GL11.glVertex3d(minX, minY, maxZ);
        GL11.glVertex3d(minX, maxY, maxZ);
        GL11.glEnd();
    }
}