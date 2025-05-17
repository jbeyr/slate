package slate.module.impl.world.targeting;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.monster.EntityGolem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import slate.module.Module;
import slate.module.setting.impl.ButtonSetting;

public class TargetManager extends Module {

    private final ButtonSetting onlyPlayers = new ButtonSetting("Only players", true);
    private final ButtonSetting respectTeams = new ButtonSetting("Respect teams", true);

    public TargetManager() {
        super("Target Manager", category.world);
        this.registerSetting(onlyPlayers, respectTeams);
    }

    public boolean isRecommendedTarget(Entity en) {
        if(!isEnabled()) return true;
        if(!(en instanceof EntityLivingBase)) return false;
        return isRecommendedTarget((EntityLivingBase) en);
    }

    public boolean isRecommendedTarget(EntityLivingBase elb) {
        if(!isEnabled()) return true;
        EntityPlayer me = Minecraft.getMinecraft().thePlayer;
        boolean isPlayer = elb instanceof EntityPlayer;

        if (elb == null || me == elb) return false;
        if (!isPlayer && onlyPlayers.isToggled()) return false;
        if (elb instanceof EntityArmorStand || ((!(elb instanceof EntityMob) && elb instanceof EntityCreature && !(elb instanceof EntityGolem)))) return false;
        if (respectTeams.isToggled() && isPlayer && hasSameHelmetColor(me, elb)) return false;
        return elb.isEntityAlive() && !AntiBot.isBot(elb);
    }

    private boolean hasSameHelmetColor(EntityLivingBase player, EntityLivingBase otherPlayer) {
        ItemStack playerHelmet = player.getEquipmentInSlot(4);
        ItemStack otherPlayerHelmet = otherPlayer.getEquipmentInSlot(4);

        return helmetColorsMatch(playerHelmet, otherPlayerHelmet);
    }

    private boolean helmetColorsMatch(ItemStack helmet1, ItemStack helmet2) {
        if (helmet1 == null || helmet2 == null) return false;
        if (!(helmet1.getItem() instanceof ItemArmor) || !(helmet2.getItem() instanceof ItemArmor)) {
            return false;
        }

        ItemArmor itemArmor1 = (ItemArmor) helmet1.getItem();
        ItemArmor itemArmor2 = (ItemArmor) helmet2.getItem();

        if (itemArmor1.getArmorMaterial() != ItemArmor.ArmorMaterial.LEATHER ||
                itemArmor2.getArmorMaterial() != ItemArmor.ArmorMaterial.LEATHER) {
            return false;
        }

        if (!itemArmor1.hasColor(helmet1) || !itemArmor2.hasColor(helmet2)) {
            return false;
        }

        return itemArmor1.getColor(helmet1) == itemArmor2.getColor(helmet2);
    }
}