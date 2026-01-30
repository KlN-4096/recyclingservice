package com.klnon.recyclingservice.content.trashbox.service;

import com.klnon.recyclingservice.Recyclingservice;
import com.klnon.recyclingservice.content.trashbox.data.TrashBox;
import com.klnon.recyclingservice.foundation.utility.ExpressionEvaluator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.klnon.recyclingservice.Config;

import static com.klnon.recyclingservice.foundation.utility.MessageHelper.sendPaymentError;
import static com.klnon.recyclingservice.foundation.utility.MessageHelper.sendPaymentSuccess;


/**
 * 垃圾箱支付系统 - 处理邮费检查、扣除、费用计算
 */
public class TrashPaymentHandler {

    private static final String MODE_CURRENT_DIM_FREE = "current_dimension_free";
    private static final String MODE_ALL_DIMS_PAY = "all_dimensions_pay";

    private static final Map<UUID, ArrayDeque<Long>> EXTRACT_HISTORY = new ConcurrentHashMap<>();
    private static volatile String lastFormulaError;

    public record ExtractInfo(int count, int baseFlag) {}

    public static void resetExtractHistory() {
        EXTRACT_HISTORY.clear();
    }

    // ==================== 支付相关 ====================
    /**
     * 是否有足够邮费
     * @param player 玩家
     * @param requiredCost 需要扣除的数量
     * @return 能否成功扣除
     */
    public static boolean hasEnoughPaymentItems(Player player, int requiredCost) {
        if (requiredCost <= 0) return true;

        ResourceLocation paymentItem = Config.getPaymentItem();
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (isPaymentItem(stack, paymentItem)) {
                total += stack.getCount();
                if (total >= requiredCost) return true;  // 提前退出
            }
        }
        return false;
    }


    /**
     * 扣除玩家的邮费
     * @param player 玩家
     * @param cost 需要扣除的数量
     * @return 是否成功扣除
     */
    public static boolean deductPayment(Player player, int cost) {
        if (cost <= 0) return true;

        ResourceLocation paymentItem = Config.getPaymentItem();
        List<ItemStack> paymentStacks = new ArrayList<>();
        int total = 0;

        for (ItemStack stack : player.getInventory().items) {
            if (isPaymentItem(stack, paymentItem)) {
                paymentStacks.add(stack);
                total += stack.getCount();
            }
        }

        if (total < cost) return false;

        int remaining = cost;
        for (ItemStack stack : paymentStacks) {
            if (remaining <= 0) break;
            int deduct = Math.min(remaining, stack.getCount());
            stack.shrink(deduct);
            remaining -= deduct;
        }
        return true;
    }

    private static boolean isPaymentItem(ItemStack stack, ResourceLocation paymentItem) {
        return !stack.isEmpty() &&
                BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(paymentItem);
    }

    // ==================== 费用计算 ====================

    /**
     * 根据支付模式计算基础提取费用
     * 支付模式：
     * - current_dimension_free: 同维度免费，跨维度收费
     * - all_dimensions_pay: 所有维度都收费
     *
     * @param sameDim 是否与垃圾箱同维度
     * @return 基础费用
     */
    private static int calculateBaseExtractCost(boolean sameDim) {
        String mode = Config.GAMEPLAY.extractPaymentMode.get();
        int crossCost = Config.GAMEPLAY.crossDimensionAccessCost.get();

        return switch (mode) {
            case MODE_CURRENT_DIM_FREE -> sameDim ? 0 : crossCost;
            case MODE_ALL_DIMS_PAY -> crossCost;
            default -> 0;
        };
    }

    /**
     * 预览计算费用（不实际扣除）
     * 根据维度、物品数量、最近提取次数等因素计算邮费
     * @param player 玩家
     * @param trashDim 垃圾箱维度
     * @param itemCount 取出物品数量
     * @param baseFlag 物品来源标记（1=玩家放入，0=自动清理）
     * @return 预计邮费价格
     */
    public static int previewExtractCost(Player player, ResourceLocation trashDim, int itemCount, int baseFlag) {
        ResourceLocation playerDim = player.level().dimension().location();
        boolean sameDim = playerDim.equals(trashDim);

        int baseCost = calculateBaseExtractCost(sameDim);
        if (baseFlag <= 0 && Config.GAMEPLAY.autoCleanItemsFree.get()) {
            baseCost = 0;
        }

        int recent = getEffectiveRecentCount(player, sameDim);
        double multiplier = Config.getDimensionMultiplier(trashDim.toString());
        int costCap = Config.getDimensionCostCap(trashDim.toString());

        return evaluateExtractCost(baseCost, itemCount, recent, sameDim, multiplier, costCap);
    }

    /**
     * 使用配置的公式计算最终提取费用
     * 公式可用变量：
     * - base: 基础费用
     * - count: 物品数量
     * - recent: 最近提取次数
     * - same_dim: 是否同维度（1或0）
     * - cross_dim: 是否跨维度（1或0）
     * - multiplier: 维度费用倍率
     * 计算后应用费用上限
     *
     * @param baseCost   基础费用
     * @param itemCount  物品数量
     * @param recent     最近提取次数
     * @param sameDim    是否同维度
     * @param multiplier 维度费用倍率
     * @param costCap    费用上限（0表示无上限）
     * @return 最终费用（最小为0）
     */
    private static int evaluateExtractCost(int baseCost, int itemCount, int recent,
                                           boolean sameDim, double multiplier, int costCap) {
        String formula = Config.GAMEPLAY.extractCostFormula.get();
        Map<String, Double> vars = Map.of(
                "base", (double) baseCost,
                "count", (double) itemCount,
                "recent", (double) recent,
                "same_dim", sameDim ? 1D : 0D,
                "cross_dim", sameDim ? 0D : 1D,
                "multiplier", multiplier
        );

        double result;
        try {
            result = ExpressionEvaluator.evaluate(formula, vars);
            if (Double.isNaN(result) || Double.isInfinite(result)) {
                logFormulaError(formula, "Invalid result");
                result = baseCost;
            }
        } catch (IllegalArgumentException ex) {
            logFormulaError(formula, ex.getMessage());
            result = baseCost;
        }

        int cost = Math.max(0, (int) Math.floor(result));
        return (costCap > 0 && cost > costCap) ? costCap : cost;
    }

    private static void logFormulaError(String formula, String reason) {
        if (!formula.equals(lastFormulaError)) {
            lastFormulaError = formula;
            Recyclingservice.LOGGER.warn("Invalid extract_cost_formula '{}': {}", formula, reason);
        }
    }

    // ==================== 提取支付流程 ====================
    /**
     * 完成提取支付流程（使用预计算的费用）
     *
     * @param player         玩家
     * @param trashDim       垃圾箱所在维度
     * @param extractCount   提取物品数量
     * @param previewedCost  预计算的费用（避免重复计算）
     */
    public static void finalizeExtractPayment(Player player, ResourceLocation trashDim,
                                              int extractCount, int previewedCost) {
        if (extractCount <= 0) return;

        boolean sameDim = player.level().dimension().location().equals(trashDim);

        // 免费情况
        if (previewedCost <= 0) {
            if (!Config.GAMEPLAY.autoCleanItemsFree.get()) {
                recordExtract(player, sameDim);
            }
            return;
        }

        // 尝试扣费
        if (!deductPayment(player, previewedCost)) {
            sendPaymentError(player, previewedCost);
            return;
        }

        sendPaymentSuccess(player, previewedCost);
        recordExtract(player, sameDim);
    }

    // ==================== 提取历史记录 ====================

    public static void recordExtract(Player player, boolean sameDim) {
        if (sameDim && Config.GAMEPLAY.extractPenaltyCrossDimensionOnly.get()) return;

        long now = System.currentTimeMillis();
        long window = Config.GAMEPLAY.autoCleanTime.get() * 1000L;

        ArrayDeque<Long> history = EXTRACT_HISTORY.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        synchronized (history) {
            pruneHistory(history, now, window);
            history.addLast(now);
        }
    }

    private static int getEffectiveRecentCount(Player player, boolean sameDim) {
        if (sameDim && Config.GAMEPLAY.extractPenaltyCrossDimensionOnly.get()) return 0;

        long now = System.currentTimeMillis();
        long window = Config.GAMEPLAY.autoCleanTime.get() * 1000L;

        ArrayDeque<Long> history = EXTRACT_HISTORY.get(player.getUUID());
        if (history == null) return 0;

        int size;
        synchronized (history) {
            pruneHistory(history, now, window);
            size = history.size();
        }

        // 清理空队列，防止内存泄漏
        if (size == 0) {
            EXTRACT_HISTORY.remove(player.getUUID(), history);
        }

        return size;
    }

    private static void pruneHistory(ArrayDeque<Long> history, long now, long window) {
        long cutoff = now - window;
        while (!history.isEmpty() && history.peekFirst() < cutoff) {
            history.removeFirst();
        }
    }

    // ==================== 提取信息获取 ====================

    public static ExtractInfo getExtractInfo(int trashSlots, TrashBox trashBox, int slotId, int button,
                                             ClickType clickType, Player player, List<Slot> slots,
                                             ItemStack carriedItem) {
        if (slotId < 0 || slotId >= trashSlots) return null;

        ItemStack slotItem = slots.get(slotId).getItem();
        if (slotItem.isEmpty()) return null;

        int baseFlag = (trashBox != null) ? trashBox.getBaseFlag(slotItem) : 0;
        int maxExtract = getMaxExtractCount(slotItem);

        switch (clickType) {
            case SWAP -> {
                ItemStack swapItem = player.getInventory().getItem(button);
                if (slotItem.getCount() > slotItem.getMaxStackSize() && !swapItem.isEmpty()) {
                    return null;
                }
                return new ExtractInfo(maxExtract, baseFlag);
            }
            case PICKUP -> {
                if (!carriedItem.isEmpty()) return null;
                int count = (button == 0) ? maxExtract : getRightClickExtractCount(slotItem);
                return new ExtractInfo(count, baseFlag);
            }
            case QUICK_MOVE -> {
                return new ExtractInfo(maxExtract, baseFlag);
            }
            case PICKUP_ALL -> {
                return new ExtractInfo(Math.min(slotItem.getMaxStackSize(), slotItem.getCount()), baseFlag);
            }
            case THROW -> {
                if (!carriedItem.isEmpty()) return null;
                int count = (button == 0) ? 1 : Math.min(slotItem.getCount(), slotItem.getMaxStackSize());
                return new ExtractInfo(count, baseFlag);
            }
            default -> {
                return null;
            }
        }
    }

    private static int getMaxExtractCount(ItemStack item) {
        return Math.min(item.getCount(), item.getMaxStackSize());
    }

    private static int getRightClickExtractCount(ItemStack item) {
        int count = item.getCount();
        int max = item.getMaxStackSize();

        if (count <= 1) return 1;
        if (count >= max) return max / 2;
        return (count + 1) / 2;
    }
}