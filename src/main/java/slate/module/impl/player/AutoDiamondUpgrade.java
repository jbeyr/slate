package slate.module.impl.player;

import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slate.module.Module;
import slate.module.setting.impl.ButtonSetting;
import slate.module.setting.impl.SliderSetting;
import slate.utility.Utils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class AutoDiamondUpgrade extends Module {

    private static final Pattern DIAMOND_RX = Pattern.compile("(\\d+)\\s+Diamond", Pattern.CASE_INSENSITIVE);

    private final SliderSetting minStart = new SliderSetting("Min start delay", 100, 0, 500, 10, "ms");
    private final SliderSetting maxStart = new SliderSetting("Max start delay", 200, 0, 500, 10, "ms");
    private final SliderSetting minBuy   = new SliderSetting("Min buy delay" , 80 , 0, 500, 10, "ms");
    private final SliderSetting maxBuy   = new SliderSetting("Max buy delay" ,120 , 0, 500, 10, "ms");
    private final ButtonSetting autoClose= new ButtonSetting("Auto close", true);

    private final ButtonSetting useAllowlist = new ButtonSetting("Use allowlist", true);

    private final ButtonSetting allowlistSharp = new ButtonSetting("Sharpened Swords", true, useAllowlist::isToggled);
    private final SliderSetting sharpPriority = new SliderSetting("Priority: Sharp", 1, 1, 5, 1, allowlistSharp::isToggled);

    private final ButtonSetting allowlistProt = new ButtonSetting("Reinforced Armor", true, useAllowlist::isToggled);
    private final SliderSetting protPriority = new SliderSetting("Priority: Prot", 1, 1, 5, 1, allowlistProt::isToggled);

    private final ButtonSetting allowlistHealPool = new ButtonSetting("Heal Pool", false, useAllowlist::isToggled);
    private final SliderSetting healPoolPriority = new SliderSetting("Priority: Heal Pool", 1, 1, 5, 1, allowlistHealPool::isToggled);

    private long nextBuy;
    private final Set<Integer> clicked = new HashSet<>();
    private State state = State.NONE;

    public AutoDiamondUpgrade() {
        super("Auto Diamond Upgrade", category.player);
        registerSetting(minStart, maxStart, minBuy, maxBuy, autoClose, useAllowlist, allowlistSharp, allowlistProt, allowlistHealPool,
                sharpPriority, protPriority, healPoolPriority);
    }

    private int getDiamonds() {
        int d = 0;
        for (ItemStack s : mc.thePlayer.inventory.mainInventory) {
            if (s != null && s.getItem() == Items.diamond)
                d += s.stackSize;
        }
        return d;
    }

    /** Returns true if the upgrade is already unlocked (i.e. Tier line is green). */
    private boolean isUnlocked(@NotNull ItemStack stack) {
        for (String raw : getLore(stack)) {
            if (!raw.startsWith("§")) continue;          // no colour = ignore
            char colour = raw.charAt(1);

            // §aTier X   or   §aUNLOCKED!
            if (colour == 'a' && (raw.contains("Tier") || raw.toUpperCase().contains("UNLOCKED"))) return true;
        }
        return false;
    }

    /** Returns diamond cost encoded in the lore, or -1 if no cost present. */
    private int extractCost(@NotNull ItemStack stack) {
        for (String raw : getLore(stack)) {
            String line = Utils.stripString(raw);        // strip § codes
            Matcher m = DIAMOND_RX.matcher(line);
            if (m.find()) {
                try { return Integer.parseInt(m.group(1)); }
                catch (NumberFormatException ignored) {}
            }
        }
        return -1;
    }




    @Override
    public void onUpdate() {
        if(!isEnabled()) return;
        if (isBedwarsUpgradeShop()) {
            if (state == State.NONE) {
                state = State.BEFORE;
                long delay = Utils.randomizeInt(minStart.getInput(), maxStart.getInput());
                nextBuy = System.currentTimeMillis() + delay;
            }
        } else {
            state = State.NONE;
            clicked.clear();
        }
    }

    /**
     * Called via {@link slate.mixins.impl.entity.MixinEntityPlayerSP#onUpdateWalkingPlayer(CallbackInfo)}
     */
    public void onPreMotion() {
        if (!isEnabled()) return;
        if (!isBedwarsUpgradeShop()) return;

        ContainerChest chest = (ContainerChest) mc.thePlayer.openContainer;

        switch (state) {
            case BEFORE:
                if (System.currentTimeMillis() >= nextBuy)
                    state = State.BUY;
                break;

            case BUY:
                if (System.currentTimeMillis() < nextBuy) break;

                List<Integer> slots = getBuyableSlots(chest);
                System.out.println("purchasable upgrades: " + slots);
                if (slots.isEmpty()) { // nothing left
                    if (autoClose.isToggled()) mc.thePlayer.closeScreen();
                    state = State.NONE;
                    break;
                }
                int slot = slots.get(0);
                clicked.add(slot);
                buy(chest, slot);
                nextBuy = System.currentTimeMillis() +
                        Utils.randomizeInt(minBuy.getInput(), maxBuy.getInput());
                break;
        }
    }

    private enum Upgrade {
        SHARP("Sharpened Swords"),
        PROT("Reinforced Armor"),
        HEAL_POOL("Heal Pool");

        private final String plainName;
        Upgrade(String plain) { this.plainName = plain; }

        static Upgrade fromStack(ItemStack s) {
            if (s == null || !s.hasDisplayName()) return null;
            String name = Utils.stripString(s.getDisplayName());   // strip '§'
            for (Upgrade u : values()) {
                if (name.contains(u.plainName)) return u;
            }
            return null;                                           // not recognised
        }
    }

    // 2. ──────────────────────────────────────────────────────────────
    // two helpers:  enabled(u)   &   priority(u)

    private boolean enabled(Upgrade u) {                        // ★
        if (u == null) return false;
        if (!useAllowlist.isToggled()) return true;             // allow everything

        switch (u) {
            case SHARP:      return allowlistSharp.isToggled();
            case PROT:       return allowlistProt.isToggled();
            case HEAL_POOL:  return allowlistHealPool.isToggled();
            default:         return true;
        }
    }

    private int priority(Upgrade u) {                           // ★ lower = better
        if (u == null) return Integer.MAX_VALUE;
        switch (u) {
            case SHARP:      return (int) sharpPriority.getInput();
            case PROT:       return (int) protPriority.getInput();
            case HEAL_POOL:  return (int) healPoolPriority.getInput();
            default:         return Integer.MAX_VALUE;
        }
    }

    // 3. ──────────────────────────────────────────────────────────────
    // Replace your getBuyableSlots(...) with the version below

    private List<Integer> getBuyableSlots(ContainerChest chest) {
        int diamonds = getDiamonds();
        List<Integer> candidates = new ArrayList<>();

        for (int i = 0; i < chest.getLowerChestInventory().getSizeInventory(); i++) {
            if (clicked.contains(i)) continue;                         // already tried
            ItemStack stack = chest.getLowerChestInventory().getStackInSlot(i);

            if (!canBuy(stack, diamonds)) continue;                    // not affordable
            Upgrade upg = Upgrade.fromStack(stack);
            if (!enabled(upg)) continue;                               // disabled by user

            candidates.add(i);
        }

        // ---- sort by priority (1..5) and keep original chest order inside equal prio
        candidates.sort(Comparator.comparingInt(
                i -> priority(Upgrade.fromStack(chest.getLowerChestInventory().getStackInSlot(i)))
        ));

        return candidates;
    }

    /* … helpers getDiamonds(), canBuy(), buy() from previous sections … */
    private void buy(ContainerChest chest, int slot) {
        mc.playerController.windowClick(
                chest.windowId,
                slot,
                0, // button 0 = left-click
                0, // mode 0 = PICKUP
                mc.thePlayer);
    }

    /**
     * Reads the lore (tooltip lines) directly from the ItemStack’s NBT.
     * Colour codes (§) are kept – strip them later if needed.
     */
    private static @NotNull List<String> getLore(@NotNull ItemStack stack) {
        List<String> out = new ArrayList<>();

        if (stack.hasTagCompound()) {
            NBTTagCompound tag = stack.getTagCompound();

            if (tag.hasKey("display", 10)) {                  // 10 = COMPOUND
                NBTTagCompound display = tag.getCompoundTag("display");

                if (display.hasKey("Lore", 9)) {              // 9 = LIST
                    NBTTagList loreList = display.getTagList("Lore", 8); // 8 = STRING

                    for (int i = 0; i < loreList.tagCount(); i++) {
                        out.add(loreList.getStringTagAt(i));
                    }
                }
            }
        }
        return out;
    }

    /**
     * Determines whether the player can buy the next tier of this upgrade.
     * Works for multi-tier upgrades (Tier 1…n) and single-tier ones.
     */
    private boolean canBuy(ItemStack stack, int diamonds) {
        if (stack == null) return false;

        // 0) ignore placeholder panes
        if (stack.getItem() == Item.getItemFromBlock(Blocks.stained_glass_pane))
            return false;

        List<String> lore = getLore(stack);

    /*-------------------------------------------------------------
      Multi-tier: find FIRST red (§c) "Tier X: … n Diamonds" line
     ------------------------------------------------------------*/
        for (String raw : lore) {
            if (raw.length() < 2 || raw.charAt(0) != '§') continue;

            char colour = raw.charAt(1);
            if (colour == 'c' && raw.contains("Tier")) {          // red tier line
                Matcher m = DIAMOND_RX.matcher(Utils.stripString(raw));
                if (m.find()) {
                    int cost = Integer.parseInt(m.group(1));
                    return diamonds >= cost;
                }
            }
            // If we see a green tier AFTER the red one we know it is already bought,
            // but the "red first" rule guarantees we return beforehand.
        }

    /*-------------------------------------------------------------
      Single-tier (Heal Pool / Maniac Miner / Traps …):
      find any line with a diamond cost as long as the item
      is NOT already marked green ("UNLOCKED").
     ------------------------------------------------------------*/
        boolean unlocked = false;
        int cost = -1;

        for (String raw : lore) {
            // green line = already bought
            if (raw.startsWith("§a") && (raw.contains("UNLOCKED") || raw.contains("Tier"))) {
                unlocked = true;
                break;
            }
            Matcher m = DIAMOND_RX.matcher(Utils.stripString(raw));
            if (m.find()) cost = Integer.parseInt(m.group(1));
        }

        if (unlocked || cost <= 0) return false;
        return diamonds >= cost;
    }



    public static boolean isBedwarsUpgradeShop() {
        if (!(Utils.mc.currentScreen instanceof GuiChest)) return false;

        IInventory inv = ((ContainerChest) mc.thePlayer.openContainer).getLowerChestInventory();
        String name = Utils.stripString(inv.getDisplayName().getUnformattedText());

        return name.contains("Upgrades & Traps") || name.contains("Team Upgrades");
    }

    private enum State { NONE, BEFORE, BUY }
}