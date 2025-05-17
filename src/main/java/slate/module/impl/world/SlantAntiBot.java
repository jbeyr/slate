package slate.module.impl.world;

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

public class SlantAntiBot extends Module {

    @Getter @Setter
    private static boolean respectTeams;
    @Getter @Setter
    private static boolean onlyPlayers;

    public SlantAntiBot() {
        super("Slant AntiBot", category.world);
    }


    public static boolean isRecommendedTarget(Entity en) {
        if(!(en instanceof EntityLivingBase)) return false;
        return isRecommendedTarget((EntityLivingBase) en);
    }

    public static boolean isRecommendedTarget(EntityLivingBase elb) {
        EntityPlayer me = Minecraft.getMinecraft().thePlayer;
        boolean isPlayer = elb instanceof EntityPlayer;

        if (elb == null || me == elb) return false;
        if (!isPlayer && onlyPlayers) return false;
        if (elb instanceof EntityArmorStand || ((!(elb instanceof EntityMob) && elb instanceof EntityCreature && !(elb instanceof EntityGolem)))) return false;
        if (respectTeams && isPlayer && hasSameHelmetColor(me, elb)) return false;
        return elb.isEntityAlive();
    }

    public static boolean isValidMinecraftName(EntityPlayer p) {
        return isValidMinecraftName(p.getName()) && p.getUniqueID() != null && p.getUniqueID().version() == 4;
    }


    public static boolean isValidMinecraftName(String name) {
        boolean ret = true;
        if (name == null || name.isEmpty()) {
            ret = false;
            return ret;
        }

        if (name.length() < 3 || name.length() > 16) ret = false;
        for (char c : name.toCharArray()) if (!Character.isLetterOrDigit(c) && c != '_') ret = false;
        return ret;
    }

    public boolean isSameTeamIndicatedByHelmetColor(EntityLivingBase other) {
        EntityPlayer me = Minecraft.getMinecraft().thePlayer;
        return hasSameHelmetColor(me, other);
    }

    public static boolean hasSameHelmetColor(EntityLivingBase player, EntityLivingBase otherPlayer) {
        ItemStack playerHelmet = player.getEquipmentInSlot(4);
        ItemStack otherPlayerHelmet = otherPlayer.getEquipmentInSlot(4);

        return helmetColorsMatch(playerHelmet, otherPlayerHelmet);
    }

    private static boolean helmetColorsMatch(ItemStack helmet1, ItemStack helmet2) {
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