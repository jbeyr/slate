package slate.module.impl.render;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;
import slate.module.Module;
import slate.module.setting.impl.SliderSetting;
import slate.utility.slate.SlantRenderUtils;

public class BedESP extends Module {
    private final SliderSetting activationRadius = new SliderSetting("Range", 10, 1, 25, 1);

    public BedESP() {
        super("Bed ESP", category.render);
        this.registerSetting(activationRadius);
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if(!isEnabled()) return;

        EntityPlayer player = mc.thePlayer;
        double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.partialTicks;
        double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.partialTicks;
        double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.partialTicks;

        GlStateManager.pushMatrix();
        GlStateManager.translate(-x, -y, -z);

        BlockPos playerPos = player.getPosition();

        int range = (int)activationRadius.getInput();
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    BlockPos pos = playerPos.add(dx, dy, dz);
                    Block block = mc.theWorld.getBlockState(pos).getBlock();

                    if (block instanceof BlockBed) {
                        drawOutlinedBoundingBox(pos);
                    }
                }
            }
        }

        GlStateManager.popMatrix();
    }

    private void drawOutlinedBoundingBox(BlockPos pos) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        AxisAlignedBB box = block.getSelectedBoundingBox(mc.theWorld, pos);

        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GlStateManager.color(0.8F, 0.8F, 0.8F, 0.2F);

        RenderGlobal.drawOutlinedBoundingBox(box, SlantRenderUtils.scaleZeroOneRangeTo255(0.8f), SlantRenderUtils.scaleZeroOneRangeTo255(0.8f), SlantRenderUtils.scaleZeroOneRangeTo255(0.8f), SlantRenderUtils.scaleZeroOneRangeTo255(0.2f));

        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
    }
}