package slate.mixins.impl.entity;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.*;
import net.minecraft.entity.boss.EntityDragonPart;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.potion.Potion;
import net.minecraft.stats.AchievementList;
import net.minecraft.stats.StatList;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slate.module.ModuleManager;
import slate.module.impl.player.AutoWeapon;
import slate.module.impl.render.Particles;
import slate.utility.slate.ActionCoordinator;

import static slate.Main.mc;

@Mixin(EntityPlayer.class)
public abstract class MixinEntityPlayer extends EntityLivingBase {
    
    public MixinEntityPlayer(World w) {
        super(w);
    }


    @Inject(method = "attackTargetEntityWithCurrentItem", at = @At("HEAD"))
    private void swapToWeaponIfTargeting(Entity targetEntity, CallbackInfo ci) {
        AutoWeapon aw = ModuleManager.autoWeapon;
        if (!aw.isEnabled() || !ActionCoordinator.isHotbarSelectedSlotChangeAllowed() || !(targetEntity instanceof EntityLivingBase)) return;
        aw.swapToWeapon();
    }

    /**
     * @author ub
     * @reason Particle multiplier needs us to reimplement this. We've replaced occurrences of:
     * <ul>
     *          <li><code>this.</code> with <code>mc.thePlayer.</code> (obtained via {@link slate.Main#mc}), and</li>
     *              <li><code>this</code> with <code>(EntityPlayer) (Object) this</code>.</li>
     * </ul>
     */
    @SuppressWarnings({"UnresolvedMixinReference", "ExtractMethodRecommender"})
    @Inject(method = "attackTargetEntityWithCurrentItem", at = @At("HEAD"), cancellable = true)
    public void attackTargetEntityWithCurrentItem(Entity p_attackTargetEntityWithCurrentItem_1_, CallbackInfo ci) {
        if (ForgeHooks.onPlayerAttackTarget((EntityPlayer) (Object) this /* og: `this */, p_attackTargetEntityWithCurrentItem_1_)) {
            if (p_attackTargetEntityWithCurrentItem_1_.canAttackWithItem() && !p_attackTargetEntityWithCurrentItem_1_.hitByEntity(this)) {
                float f = (float)mc.thePlayer.getEntityAttribute(SharedMonsterAttributes.attackDamage).getAttributeValue();
                int i = 0;
                float f1 = 0.0F;
                if (p_attackTargetEntityWithCurrentItem_1_ instanceof EntityLivingBase) {
                    f1 = EnchantmentHelper.getModifierForCreature(mc.thePlayer.getHeldItem(), ((EntityLivingBase)p_attackTargetEntityWithCurrentItem_1_).getCreatureAttribute());
                } else {
                    f1 = EnchantmentHelper.getModifierForCreature(mc.thePlayer.getHeldItem(), EnumCreatureAttribute.UNDEFINED);
                }

                i += EnchantmentHelper.getKnockbackModifier(this);
                if (mc.thePlayer.isSprinting()) {
                    ++i;
                }

                if (f > 0.0F || f1 > 0.0F) {
                    boolean flag = mc.thePlayer.fallDistance > 0.0F && !mc.thePlayer.onGround && !mc.thePlayer.isOnLadder() && !mc.thePlayer.isInWater() && !mc.thePlayer.isPotionActive(Potion.blindness) && mc.thePlayer.ridingEntity == null && p_attackTargetEntityWithCurrentItem_1_ instanceof EntityLivingBase;
                    if (flag && f > 0.0F) {
                        f *= 1.5F;
                    }

                    f += f1;
                    boolean flag1 = false;
                    int j = EnchantmentHelper.getFireAspectModifier(this);
                    if (p_attackTargetEntityWithCurrentItem_1_ instanceof EntityLivingBase && j > 0 && !p_attackTargetEntityWithCurrentItem_1_.isBurning()) {
                        flag1 = true;
                        p_attackTargetEntityWithCurrentItem_1_.setFire(1);
                    }

                    double d0 = p_attackTargetEntityWithCurrentItem_1_.motionX;
                    double d1 = p_attackTargetEntityWithCurrentItem_1_.motionY;
                    double d2 = p_attackTargetEntityWithCurrentItem_1_.motionZ;
                    boolean flag2 = p_attackTargetEntityWithCurrentItem_1_.attackEntityFrom(DamageSource.causePlayerDamage((EntityPlayer) (Object) this), f);
                    if (flag2) {
                        if (i > 0) {
                            p_attackTargetEntityWithCurrentItem_1_.addVelocity((double)(-MathHelper.sin(mc.thePlayer.rotationYaw * (float)Math.PI / 180.0F) * (float)i * 0.5F), 0.1, (double)(MathHelper.cos(mc.thePlayer.rotationYaw * (float)Math.PI / 180.0F) * (float)i * 0.5F));
                            mc.thePlayer.motionX *= 0.6;
                            mc.thePlayer.motionZ *= 0.6;
                            mc.thePlayer.setSprinting(false);
                        }

                        if (p_attackTargetEntityWithCurrentItem_1_ instanceof EntityPlayerMP && p_attackTargetEntityWithCurrentItem_1_.velocityChanged) {
                            ((EntityPlayerMP)p_attackTargetEntityWithCurrentItem_1_).playerNetServerHandler.sendPacket(new S12PacketEntityVelocity(p_attackTargetEntityWithCurrentItem_1_));
                            p_attackTargetEntityWithCurrentItem_1_.velocityChanged = false;
                            p_attackTargetEntityWithCurrentItem_1_.motionX = d0;
                            p_attackTargetEntityWithCurrentItem_1_.motionY = d1;
                            p_attackTargetEntityWithCurrentItem_1_.motionZ = d2;
                        }

                        // region changed for particles module
                        for (int criticals = 0; criticals < Particles.criticalsParticleMultiplier(flag); criticals++) {
                            mc.thePlayer.onCriticalHit(p_attackTargetEntityWithCurrentItem_1_);
                        }

                        for (int sharpness = 0; sharpness < Particles.sharpnessParticleMultiplier(f1 > 0.0F); sharpness++) {
                            mc.thePlayer.onEnchantmentCritical(p_attackTargetEntityWithCurrentItem_1_);
                        }
                        // endregion

                        if (f >= 18.0F) {
                            mc.thePlayer.triggerAchievement(AchievementList.overkill);
                        }

                        mc.thePlayer.setLastAttacker(p_attackTargetEntityWithCurrentItem_1_);
                        if (p_attackTargetEntityWithCurrentItem_1_ instanceof EntityLivingBase) {
                            EnchantmentHelper.applyThornEnchantments((EntityLivingBase)p_attackTargetEntityWithCurrentItem_1_, this);
                        }

                        EnchantmentHelper.applyArthropodEnchantments(this, p_attackTargetEntityWithCurrentItem_1_);
                        ItemStack itemstack = mc.thePlayer.getCurrentEquippedItem();
                        Entity entity = p_attackTargetEntityWithCurrentItem_1_;
                        if (p_attackTargetEntityWithCurrentItem_1_ instanceof EntityDragonPart) {
                            IEntityMultiPart ientitymultipart = ((EntityDragonPart)p_attackTargetEntityWithCurrentItem_1_).entityDragonObj;
                            if (ientitymultipart instanceof EntityLivingBase) {
                                entity = (EntityLivingBase)ientitymultipart;
                            }
                        }

                        if (itemstack != null && entity instanceof EntityLivingBase) {
                            itemstack.hitEntity((EntityLivingBase)entity, (EntityPlayer) (Object) this);
                            if (itemstack.stackSize <= 0) {
                                mc.thePlayer.destroyCurrentEquippedItem();
                            }
                        }

                        if (p_attackTargetEntityWithCurrentItem_1_ instanceof EntityLivingBase) {
                            mc.thePlayer.addStat(StatList.damageDealtStat, Math.round(f * 10.0F));
                            if (j > 0) {
                                p_attackTargetEntityWithCurrentItem_1_.setFire(j * 4);
                            }
                        }

                        mc.thePlayer.addExhaustion(0.3F);
                    } else if (flag1) {
                        p_attackTargetEntityWithCurrentItem_1_.extinguish();
                    }
                }
            }
        }
        // we insert our impl ahead of the vanilla impl and just early return here,
        // preventing the vanilla impl from running
        ci.cancel();
    }
}
