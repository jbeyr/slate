package slate.module.impl.player;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import slate.module.Module;
import slate.module.setting.impl.ButtonSetting;
import slate.module.setting.impl.SliderSetting;
import slate.utility.Utils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class AutoDiamondUpgrade extends Module {

    private static final Pattern DIAMOND_RX = Pattern.compile(".*?(\\d+)\\s+Diamonds?", Pattern.CASE_INSENSITIVE);

    // --- Settings ---
    private final SliderSetting minStartDelay = new SliderSetting("Min start delay", 100, 0, 500, 10, "ms");
    private final SliderSetting maxStartDelay = new SliderSetting("Max start delay", 200, 0, 500, 10, "ms");
    private final SliderSetting minBuyDelay = new SliderSetting("Min buy delay", 80, 0, 500, 10, "ms");
    private final SliderSetting maxBuyDelay = new SliderSetting("Max buy delay", 120, 0, 500, 10, "ms");
    private final ButtonSetting autoClose = new ButtonSetting("Auto close", true);

    private final ButtonSetting useAllowlist = new ButtonSetting("Use allowlist", true);

    private final ButtonSetting allowlistSharp = new ButtonSetting("Sharpened Swords", true, useAllowlist::isToggled);
    private final SliderSetting sharpPriority = new SliderSetting("Priority: Sharp", 1, 1, 5, 1, allowlistSharp::isToggled);

    private final ButtonSetting allowlistProt = new ButtonSetting("Reinforced Armor", true, useAllowlist::isToggled);
    private final SliderSetting protPriority = new SliderSetting("Priority: Prot", 2, 1, 5, 1, allowlistProt::isToggled);

    private final ButtonSetting allowlistHealPool = new ButtonSetting("Heal Pool", false, useAllowlist::isToggled);
    private final SliderSetting healPoolPriority = new SliderSetting("Priority: Heal Pool", 3, 1, 5, 1, allowlistHealPool::isToggled);

    // --- State ---
    private long nextActionTime;
    private final Set<Integer> clickedSlots = new HashSet<>();
    private State state = State.IDLE;

    public AutoDiamondUpgrade() {
        super("Auto Diamond Upgrade", category.player);
        registerSetting(minStartDelay, maxStartDelay, minBuyDelay, maxBuyDelay, autoClose, useAllowlist,
                allowlistSharp, sharpPriority,
                allowlistProt, protPriority,
                allowlistHealPool, healPoolPriority);
    }

    @Override
    public void onUpdate() {
        if (!isEnabled()) return;

        if (isBedwarsUpgradeShop()) {
            if (state == State.IDLE) {
                state = State.PENDING_PURCHASE;
                long delay = Utils.randomizeInt(minStartDelay.getInput(), maxStartDelay.getInput());
                nextActionTime = System.currentTimeMillis() + delay;
            }
        } else {
            state = State.IDLE;
            clickedSlots.clear();
        }
    }

    public void onPreMotion() {
        if (!isEnabled()) return;
        if (state == State.PENDING_PURCHASE && System.currentTimeMillis() >= nextActionTime) state = State.PURCHASING;
        if (state != State.PURCHASING || System.currentTimeMillis() < nextActionTime) return;
        if (!(mc.currentScreen instanceof GuiChest)) {
            state = State.IDLE;
            return;
        }

        ContainerChest chest = (ContainerChest) mc.thePlayer.openContainer;

        Optional<Integer> bestPurchaseOpt = findHighestPriorityPurchase(chest);

        bestPurchaseOpt.ifPresent(slotToClick -> {
            clickedSlots.add(slotToClick);
            buy(chest, slotToClick);
            long delay = Utils.randomizeInt(minBuyDelay.getInput(), maxBuyDelay.getInput());
            nextActionTime = System.currentTimeMillis() + delay;
        });

        if (autoClose.isToggled() && !bestPurchaseOpt.isPresent()) {
            mc.thePlayer.closeScreen();
            state = State.IDLE;
        }
    }

    private Optional<Integer> findHighestPriorityPurchase(ContainerChest chest) {
        int currentDiamonds = countPlayerDiamonds();

        List<PurchaseCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < chest.getLowerChestInventory().getSizeInventory(); i++) {
            if (clickedSlots.contains(i)) continue;

            ItemStack stack = chest.getLowerChestInventory().getStackInSlot(i);
            if (stack == null || stack.getItem() == Item.getItemFromBlock(Blocks.stained_glass_pane)) continue;

            final int slotIndex = i;
            final ItemStack itemStack = stack;

            Optional<Upgrade> upgradeOpt = Upgrade.fromStack(itemStack, this);
            if (useAllowlist.isToggled() && !isUpgradeAllowed(upgradeOpt.orElse(null))) continue;

            upgradeOpt.ifPresent(upgrade -> {
                int priority = getUpgradePriority(upgrade);
                candidates.add(new PurchaseCandidate(slotIndex, priority, itemStack));
            });
        }

        candidates.sort(Comparator.comparingInt(PurchaseCandidate::getPriority));

        for (PurchaseCandidate candidate : candidates) {
            if (canAfford(candidate.getStack(), currentDiamonds)) {
                return Optional.of(candidate.getSlot());
            }
        }
        return Optional.empty();
    }

    private boolean isBedwarsUpgradeShop() {
        if (!(mc.currentScreen instanceof GuiChest)) return false;
        ContainerChest chest = (ContainerChest) mc.thePlayer.openContainer;
        // use the local, correct stripper method
        String inventoryName = stripColorCodes(chest.getLowerChestInventory().getDisplayName().getUnformattedText());
        return inventoryName.equals("Team Upgrades") || inventoryName.equals("Upgrades & Traps");
    }

    private boolean canAfford(@NotNull ItemStack stack, int diamondCount) {
        List<String> lore = getLore(stack);
        if (lore.stream().anyMatch(line -> line.startsWith("§a") && line.toUpperCase().contains("UNLOCKED"))) return false;

        for (String line : lore) {
            Optional<Integer> costOpt = parseCost(line);
            if (costOpt.isPresent()) return diamondCount >= costOpt.get();
        }
        return false;
    }

    private Optional<Integer> parseCost(String loreLine) {
        // use the local, correct stripper method
        Matcher matcher = DIAMOND_RX.matcher(stripColorCodes(loreLine));
        if (matcher.find()) {
            try { return Optional.of(Integer.parseInt(matcher.group(1))); }
            catch (NumberFormatException ignored) {}
        }
        return Optional.empty();
    }

    /**
     * A local, self-contained method to correctly remove Minecraft color codes from a string.
     * This avoids relying on the broken global Utils.stripString method.
     * @param text The string to strip color codes from.
     * @return The cleaned string.
     */
    private String stripColorCodes(String text) {
        return StringUtils.stripControlCodes(text);
    }

    private void buy(ContainerChest chest, int slot) {
        mc.playerController.windowClick(chest.windowId, slot, 0, 0, mc.thePlayer);
    }

    private int countPlayerDiamonds() {
        return Arrays.stream(mc.thePlayer.inventory.mainInventory)
                .filter(s -> s != null && s.getItem() == Items.diamond)
                .mapToInt(s -> s.stackSize).sum();
    }

    private List<String> getLore(@NotNull ItemStack stack) {
        return Optional.of(stack).map(ItemStack::getTagCompound)
                .map(tag -> tag.getCompoundTag("display"))
                .map(display -> display.getTagList("Lore", 8))
                .map(loreList -> IntStream.range(0, loreList.tagCount()).mapToObj(loreList::getStringTagAt).collect(Collectors.toList()))
                .orElse(Collections.emptyList());
    }

    private boolean isUpgradeAllowed(Upgrade upgrade) {
        if (upgrade == null) return false;
        switch (upgrade) {
            case SHARPENED_SWORDS: return allowlistSharp.isToggled();
            case REINFORCED_ARMOR: return allowlistProt.isToggled();
            case HEAL_POOL: return allowlistHealPool.isToggled();
            default: return false;
        }
    }

    private int getUpgradePriority(Upgrade upgrade) {
        if (upgrade == null) return Integer.MAX_VALUE;
        switch (upgrade) {
            case SHARPENED_SWORDS: return (int) sharpPriority.getInput();
            case REINFORCED_ARMOR: return (int) protPriority.getInput();
            case HEAL_POOL: return (int) healPoolPriority.getInput();
            default: return Integer.MAX_VALUE;
        }
    }

    private enum State { IDLE, PENDING_PURCHASE, PURCHASING }

    @Getter @AllArgsConstructor
    private enum Upgrade {
        SHARPENED_SWORDS("Sharpened Swords"),
        REINFORCED_ARMOR("Reinforced Armor"),
        HEAL_POOL("Heal Pool");
        private final String displayName;

        // Pass the parent module instance to access its local stripColorCodes method
        public static Optional<Upgrade> fromStack(ItemStack stack, AutoDiamondUpgrade module) {
            return Optional.ofNullable(stack).filter(ItemStack::hasDisplayName)
                    .map(s -> module.stripColorCodes(s.getDisplayName()))
                    .flatMap(name -> Arrays.stream(values()).filter(u -> name.contains(u.getDisplayName())).findFirst());
        }
    }

    @Getter @AllArgsConstructor
    private static class PurchaseCandidate {
        private final int slot;
        private final int priority;
        private final ItemStack stack;
    }
}