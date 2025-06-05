package slate.module.impl.player;


import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import lombok.Getter;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import slate.module.Module;
import slate.utility.slate.ActionCoordinator;

import java.util.*;

import static slate.module.impl.player.AutoGhead.NbtComparer.overlappingNbt;

public class AutoGhead extends Module {

    private static final Map<NbtComparer.HealingItem, Long> individualCooldowns = new HashMap<>();
    private static final long IN_PROGRESS_DURATION = 50;
    private static long inProgressUntil = 0;
    private int swappedFrom = 0;
    private boolean needToSwapBack = false;

    public AutoGhead() {
        super("Auto Ghead", category.player);
    }

    public static boolean isInProgress() {
        return System.currentTimeMillis() < inProgressUntil;
    }

    public static void setInProgress(NbtComparer.HealingItem healingItem) {
        inProgressUntil = System.currentTimeMillis() + IN_PROGRESS_DURATION;
        individualCooldowns.put(healingItem, System.currentTimeMillis() + healingItem.cooldownMs);
    }

    /**
     * @param player the player whose hotbar should be scanned for a suitable item
     * @return the healing item if (1) the player health threshold is below it, (2) if the item isn't on cooldown, and (3) if the nbt matches
     */
    public static Optional<NbtComparer.HealingItemResult> getFirstHealingItemInHotbarNotOnCooldown(EntityPlayer player) {

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            for (NbtComparer.HealingItem hItem : NbtComparer.getItemCooldowns()) {
                if (!overlappingNbt(stack, hItem.sourceNbt)) continue;
                if (player.getAbsorptionAmount() > 1f || player.getHealth() / player.getMaxHealth() > hItem.usageThreshold) continue;

                Long healingItemCooldown = individualCooldowns.getOrDefault(hItem, 0L);
                if(System.currentTimeMillis() > healingItemCooldown) {
                    return Optional.of(new NbtComparer.HealingItemResult(hItem, i));
                }
            }
        }
        return Optional.empty();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (!isEnabled()) return;
        if (!ActionCoordinator.isGheadAllowed()) return;
        if (event.phase != TickEvent.Phase.START) return;

        EntityPlayer me = mc.thePlayer;

        if (needToSwapBack) {
            me.inventory.currentItem = swappedFrom;
            needToSwapBack = false;
            return;
        }

        Optional<NbtComparer.HealingItemResult> res = getFirstHealingItemInHotbarNotOnCooldown(me);
        if (!res.isPresent()) return;

        swappedFrom = me.inventory.currentItem;

        setInProgress(res.get().hItem);
        me.inventory.currentItem = res.get().slot;

        // swap to item and use it
        int useItemKey = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(useItemKey, true);
        KeyBinding.onTick(useItemKey);
        KeyBinding.setKeyBindState(useItemKey, false);

        // schedule swapping back on next tick
        needToSwapBack = true;
    }


    static class NbtComparer {

        @Getter private static final Set<HealingItem> itemCooldowns = new HashSet<>();
        private static final String FIRST_AID_EGG_JSON_PARTIAL = "§cFirst-Aid Egg";
        private static final String FRACTURED_SOUL_JSON_PARTIAL = "Fractured Soul";
        private static final String RAGE_POTATO_JSON_PARTIAL = "Rage Potato";
        private static final String GOLDEN_HEAD_JSON_PARTIAL = "eyJ0aW1lc3RhbXAiOjE0ODUwMjM0NDEyNzAsInByb2ZpbGVJZCI6ImRhNDk4YWM0ZTkzNzRlNWNiNjEyN2IzODA4NTU3OTgzIiwicHJvZmlsZU5hbWUiOiJOaXRyb2hvbGljXzIiLCJzaWduYXR1cmVSZXF1aXJlZCI6dHJ1ZSwidGV4dHVyZXMiOnsiU0tJTiI6eyJ1cmwiOiJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2Y5MzdlMWM0NWJiOGRhMjliMmM1NjRkZDlhN2RhNzgwZGQyZmU1NDQ2OGE1ZGZiNDExM2I0ZmY2NThmMDQzZTEifX19";

        static {
            itemCooldowns.add(new HealingItem(FIRST_AID_EGG_JSON_PARTIAL, 0.75f, 200));
            itemCooldowns.add(new HealingItem(FRACTURED_SOUL_JSON_PARTIAL, 0.6f, 200));
            itemCooldowns.add(new HealingItem(RAGE_POTATO_JSON_PARTIAL, 0.8f, 200));
            itemCooldowns.add(new HealingItem(GOLDEN_HEAD_JSON_PARTIAL, 0.75f, 1000));
        }

        public static boolean exactlyNbtString(ItemStack item, String nbtStr) {
            if (item != null && item.hasTagCompound()) {
                JsonParser parser = new JsonParser();
                String nbtJson = item.writeToNBT(new NBTTagCompound()).toString();
                JsonElement expectedJson = parser.parse(nbtStr);
                JsonElement actualJson = parser.parse(nbtJson);
                return expectedJson.equals(actualJson);
            }
            return false;
        }

        /**
         * @return true if <code>nbtStr</code> is contained in the item, or vice versa.
         */
        public static boolean overlappingNbt(ItemStack item, String nbtStr) {
            if (item != null && item.hasTagCompound()) {
                JsonParser parser = new JsonParser();
                String itemNbtJson = item.writeToNBT(new NBTTagCompound()).toString();
                String s1, s2;

                try {
                    s1 = parser.parse(nbtStr).toString();
                } catch (JsonSyntaxException e) { // assume it's base64 encoded
                    s1 = nbtStr;
                }

                try {
                    s2 = parser.parse(itemNbtJson).toString();
                } catch (JsonSyntaxException e) { // try raw string
                    s2 = itemNbtJson;
                }

                return s1.contains(s2) || s2.contains(s1);
            }
            return false;
        }

        public static class HealingItem {
            public final int cooldownMs;
            public final String sourceNbt;
            public final float usageThreshold;

            public HealingItem(String sourceNbt, float usageThreshold, int cooldownMs) {
                this.cooldownMs = cooldownMs;
                this.usageThreshold = usageThreshold;
                this.sourceNbt = sourceNbt;
            }
        }

        public static class HealingItemResult {
            public final HealingItem hItem;
            public final int slot;

            public HealingItemResult(HealingItem hItem, int slot) {
                this.hItem = hItem;
                this.slot = slot;
            }
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

}