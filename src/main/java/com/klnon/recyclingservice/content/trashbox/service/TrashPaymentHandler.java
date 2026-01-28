package com.klnon.recyclingservice.content.trashbox.service;

import com.klnon.recyclingservice.Recyclingservice;
import com.klnon.recyclingservice.content.trashbox.TrashBoxMenu;
import com.klnon.recyclingservice.content.trashbox.data.TrashBox;
import com.klnon.recyclingservice.foundation.utility.ExpressionEvaluator;
import com.klnon.recyclingservice.foundation.utility.MessageHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.klnon.recyclingservice.Config;

/**
 * 垃圾箱支付系统 - 处理垃圾箱相关的支付功能
 * 职责：
 * - 检查和扣除玩家邮费
 * - 发送支付相关消息  
 * - 支付物品验证
 */
public class TrashPaymentHandler {

    private static final Map<UUID, ArrayDeque<Long>> EXTRACT_HISTORY = new ConcurrentHashMap<>();
    private static volatile String lastFormulaError;

    private record ExtractInfo(int count, int baseFlag) {
    }

    public static void resetExtractHistory() {
        EXTRACT_HISTORY.clear();
    }


    /**
     * 扣除玩家的邮费
     * @param player 玩家
     * @param cost 需要扣除的数量
     * @return 是否成功扣除
     */
    public static boolean deductPayment(Player player, int cost) {
        return processPayment(player, cost) >= 0;
    }
    
    /**
     * Process postage payment with a count-first pass.
     * @param player Player
     * @param requiredCost Required postage count
     * @return 0 on success, -1 on failure
     */
    private static int processPayment(Player player, int requiredCost) {
        if (requiredCost <= 0) {
            return 0;
        }

        ResourceLocation paymentItem = Config.getPaymentItem();
        int totalFound = 0;

        // First pass: count without mutating.
        for (ItemStack stack : player.getInventory().items) {
            if (isPaymentItem(stack, paymentItem)) {
                totalFound += stack.getCount();
            }
        }

        if (totalFound < requiredCost) {
            return -1; // Insufficient payment items.
        }

        int remaining = requiredCost;

        // Second pass: deduct after validation.
        for (ItemStack stack : player.getInventory().items) {
            if (remaining <= 0) {
                break;
            }
            if (isPaymentItem(stack, paymentItem)) {
                int deduct = Math.min(remaining, stack.getCount());
                stack.shrink(deduct);
                remaining -= deduct;
            }
        }

        return (remaining == 0 ? 0 : -1);
    }

    private static boolean isPaymentItem(ItemStack stack, ResourceLocation paymentItem) {
        return !stack.isEmpty() && 
               BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(paymentItem);
    }
    
    /**
     * 发送邮费不足错误消息
     * @param player 玩家
     * @param requiredCost 需要的邮费数量
     */
    public static void sendPaymentErrorMessage(Player player, int requiredCost) {
        String itemName = getPaymentItemDisplayName();
        String formattedMessage = MessageHelper.formatTemplate(Config.MESSAGE.paymentErrorMessage.get(), Map.of(
            "cost", String.valueOf(requiredCost),
            "item", itemName
        ));
        String prefix = Config.MESSAGE.messagePrefix.get();
        Component message = Component.literal(prefix + formattedMessage)
                .withStyle(style -> style.withColor(net.minecraft.ChatFormatting.RED).withBold(true));
        player.displayClientMessage(message, false);
    }
    
    /**
     * 发送邮费扣除成功消息
     * @param player 玩家
     * @param deductedCost 扣除的邮费数量
     */
    public static void sendPaymentSuccessMessage(Player player, int deductedCost) {
        String itemName = getPaymentItemDisplayName();
        String formattedMessage = MessageHelper.formatTemplate(Config.MESSAGE.paymentSuccessMessage.get(), Map.of(
            "cost", String.valueOf(deductedCost),
            "item", itemName
        ));
        String prefix = Config.MESSAGE.messagePrefix.get();
        Component message = Component.literal(prefix + formattedMessage)
                .withStyle(style -> style.withColor(net.minecraft.ChatFormatting.GREEN).withBold(true));
        player.displayClientMessage(message, false);
    }
    
    /**
     * 获取邮费物品的显示名称
     * @return 邮费物品显示名称
     */
    private static String getPaymentItemDisplayName() {
        ResourceLocation paymentItem = Config.getPaymentItem();
        // 简化处理：直接使用路径作为显示名
        return paymentItem.getPath();
    }
    
    /**
     * 检查并扣除邮费的便捷方法 - 优化为单次遍历
     * @param player 玩家
     * @param cost 邮费数量
     * @return 是否成功（true=允许操作，false=阻止操作）
     */
    public static boolean checkAndDeductPayment(Player player, int cost) {
        if (cost <= 0) {
            return true;
        }
        
        // 直接尝试扣除，如果失败说明不足
        if (deductPayment(player, cost)) {
            sendPaymentSuccessMessage(player, cost);
            return true;
        } else {
            sendPaymentErrorMessage(player, cost);
            return false;
        }
    }
    
    /**
     * Calculate extract postage cost preview.
     * @param player Player
     * @param trashDim Trash box dimension
     * @param itemCount Item count for this extraction
     * @param baseFlag 1 for player-origin items, 0 for auto-cleaned items
     * @return Cost required
     */
    public static int previewExtractCost(Player player, ResourceLocation trashDim, int itemCount, int baseFlag) {
        ResourceLocation playerDim = player.level().dimension().location();
        boolean sameDimension = playerDim.equals(trashDim);
        int baseCost = calculateBaseExtractCost(playerDim, trashDim);
        if (baseFlag <= 0) {
            baseCost = 0;
        }
        int recent = getEffectiveRecentCount(player, sameDimension);
        int costCap = Config.getDimensionCostCap(trashDim.toString());
        return evaluateExtractCost(baseCost, itemCount, recent, sameDimension,
                Config.getDimensionMultiplier(trashDim.toString()), costCap);
    }

    private static int calculateBaseExtractCost(ResourceLocation playerDim, ResourceLocation trashDim) {
        boolean isSameDimension = playerDim.equals(trashDim);
        String paymentMode = Config.GAMEPLAY.extractPaymentMode.get();

        return switch (paymentMode) {
            case "current_dimension_free" ->
                isSameDimension ? 0 : calculateCrossDimensionCost(trashDim);
            case "all_dimensions_pay" ->
                isSameDimension ? Config.GAMEPLAY.crossDimensionAccessCost.get() :
                                  calculateCrossDimensionCost(trashDim);
            default -> 0;
        };
    }


    /**
     * Calculate cross-dimension base cost (dimension multiplier applied after formula).
     */
    public static int calculateCrossDimensionCost(ResourceLocation trashDim) {
        return Config.GAMEPLAY.crossDimensionAccessCost.get();
    }

    /**
     * 验证并处理支付
     */
    public static boolean validateAndProcessPayment(TrashBoxMenu menu, int slotId, int button,
                                                    ClickType clickType, Player player) {
        if (clickType == ClickType.QUICK_CRAFT) {
            return true;
        }

        ExtractInfo extractInfo = getExtractInfo(menu.getTrashSlots(), menu.getTrashBox(), slotId, button,
                clickType, player, menu.slots, menu.getCarried());
        if (extractInfo == null || extractInfo.count() <= 0) return true;
        ResourceLocation playerDim = player.level().dimension().location();
        ResourceLocation trashDim = menu.getTrashBox().getData().getDimensionId();

        boolean sameDimension = playerDim.equals(trashDim);
        int baseCost = calculateBaseExtractCost(playerDim, trashDim);
        if (extractInfo.baseFlag() <= 0) {
            baseCost = 0;
        }
        int recent = getEffectiveRecentCount(player, sameDimension);
        int costCap = Config.getDimensionCostCap(trashDim.toString());
        int cost = evaluateExtractCost(baseCost, extractInfo.count(), recent, sameDimension,
                Config.getDimensionMultiplier(trashDim.toString()), costCap);
        if (cost > 0 && !TrashPaymentHandler.checkAndDeductPayment(player, cost)) {
            return false;
        }

        recordExtract(player, sameDimension);
        return true;
    }

    /**
     * Determine operation type for payment checks.
     */
    public static String getOperationType(int trashSlots, int slotId, int button, ClickType clickType,
                                   Player player, List<Slot> slots, ItemStack carriedItem) {
        ExtractInfo extractInfo = getExtractInfo(trashSlots, null, slotId, button, clickType,
                player, slots, carriedItem);
        return extractInfo != null ? "extract" : null;
    }

    private static ExtractInfo getExtractInfo(int trashSlots, TrashBox trashBox, int slotId, int button,
                                              ClickType clickType, Player player, List<Slot> slots,
                                              ItemStack carriedItem) {
        if (slotId >= 0 && slotId < trashSlots) {
            ItemStack slotItem = slots.get(slotId).getItem();
            if (slotItem.isEmpty()) {
                return null;
            }
            ItemStack swapItem = player.getInventory().getItem(button);
            int maxExtract = getMaxExtractCount(slotItem);
            int baseFlag = trashBox != null ? trashBox.getBaseFlag(slotItem) : 0;

            if (clickType == ClickType.SWAP) {
                boolean slotHasItem = !slotItem.isEmpty();
                boolean swapHasItem = !swapItem.isEmpty();
                if (!slotHasItem) {
                    return null;
                }
                if (slotItem.getCount() > slotItem.getMaxStackSize() && swapHasItem) {
                    return null;
                }
                return new ExtractInfo(maxExtract, baseFlag);
            }

            if (clickType == ClickType.PICKUP) {
                if (!slotItem.isEmpty() && carriedItem.isEmpty()) {
                    int extractCount = button == 0 ? maxExtract : getRightClickExtractCount(slotItem);
                    return new ExtractInfo(extractCount, baseFlag);
                }
            }

            if (clickType == ClickType.QUICK_MOVE && !slotItem.isEmpty()) {
                return new ExtractInfo(maxExtract, baseFlag);
            }
            if (clickType == ClickType.PICKUP_ALL && !slotItem.isEmpty()) {
                int extractCount = Math.min(slotItem.getMaxStackSize(), slotItem.getCount());
                return new ExtractInfo(extractCount, baseFlag);
            }
            if (clickType == ClickType.THROW && carriedItem.isEmpty() && !slotItem.isEmpty()) {
                int extractCount = button == 0 ? 1 : Math.min(slotItem.getCount(), slotItem.getMaxStackSize());
                return new ExtractInfo(extractCount, baseFlag);
            }
        }
        return null;
    }

    private static int getEffectiveRecentCount(Player player, boolean sameDimension) {
        if (sameDimension && Config.GAMEPLAY.extractPenaltyCrossDimensionOnly.get()) {
            return 0;
        }
        long now = System.currentTimeMillis();
        long windowMillis = Config.GAMEPLAY.extractPenaltyWindowSeconds.get() * 1000L;
        return getRecentExtractCount(player.getUUID(), now, windowMillis);
    }

    private static void recordExtract(Player player, boolean sameDimension) {
        if (sameDimension && Config.GAMEPLAY.extractPenaltyCrossDimensionOnly.get()) {
            return;
        }
        long now = System.currentTimeMillis();
        long windowMillis = Config.GAMEPLAY.extractPenaltyWindowSeconds.get() * 1000L;
        addExtract(player.getUUID(), now, windowMillis);
    }

    private static int getRecentExtractCount(UUID playerId, long now, long windowMillis) {
        ArrayDeque<Long> history = EXTRACT_HISTORY.get(playerId);
        if (history == null) {
            return 0;
        }
        int size;
        synchronized (history) {
            pruneHistory(history, now, windowMillis);
            size = history.size();
        }
        if (size == 0) {
            EXTRACT_HISTORY.remove(playerId, history);
        }
        return size;
    }

    private static void addExtract(UUID playerId, long now, long windowMillis) {
        ArrayDeque<Long> history = EXTRACT_HISTORY.computeIfAbsent(playerId, id -> new ArrayDeque<>());
        synchronized (history) {
            pruneHistory(history, now, windowMillis);
            history.addLast(now);
        }
    }

    private static void pruneHistory(ArrayDeque<Long> history, long now, long windowMillis) {
        long cutoff = now - windowMillis;
        while (!history.isEmpty() && history.peekFirst() < cutoff) {
            history.removeFirst();
        }
    }

    private static int evaluateExtractCost(int baseCost, int itemCount, int recent, boolean sameDimension,
                                           double multiplier, int costCap) {
        String formula = Config.GAMEPLAY.extractCostFormula.get();
        Map<String, Double> variables = Map.of(
                "base", (double) baseCost,
                "count", (double) itemCount,
                "recent", (double) recent,
                "same_dim", sameDimension ? 1D : 0D,
                "cross_dim", sameDimension ? 0D : 1D
        );
        double result;
        try {
            result = ExpressionEvaluator.evaluate(formula, variables);
        } catch (IllegalArgumentException ex) {
            logFormulaError(formula, ex.getMessage());
            result = baseCost;
        }
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            logFormulaError(formula, "Formula returned invalid number");
            result = baseCost;
        }
        double adjusted = result * multiplier;
        if (Double.isNaN(adjusted) || Double.isInfinite(adjusted)) {
            logFormulaError(formula, "Multiplier returned invalid number");
            adjusted = result;
        }
        int cost = (int) Math.ceil(adjusted);
        cost = Math.max(0, cost);
        if (costCap > 0 && cost > costCap) {
            cost = costCap;
        }
        return cost;
    }

    private static void logFormulaError(String formula, String reason) {
        if (!formula.equals(lastFormulaError)) {
            lastFormulaError = formula;
            Recyclingservice.LOGGER.warn("Invalid extract_cost_formula '{}': {}", formula, reason);
        }
    }

    private static int getMaxExtractCount(ItemStack slotItem) {
        return Math.min(slotItem.getCount(), slotItem.getMaxStackSize());
    }

    private static int getRightClickExtractCount(ItemStack slotItem) {
        int count = slotItem.getCount();
        int max = slotItem.getMaxStackSize();
        if (count <= 1) {
            return 1;
        }
        if (count >= max) {
            return Math.max(1, max / 2);
        }
        return (count + 1) / 2;
    }

}
