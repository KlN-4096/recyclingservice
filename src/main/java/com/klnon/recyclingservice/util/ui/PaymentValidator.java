package com.klnon.recyclingservice.util.ui;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.core.TrashBox;
import com.klnon.recyclingservice.util.management.PaymentService;

/**
 * 支付验证器 - 验证UI操作是否需要支付并处理支付逻辑
 * 职责：
 * - 分析UI操作类型（insert/extract）
 * - 计算所需支付费用
 * - 调用PaymentService执行支付
 */
public class PaymentValidator {

    /**
     * 处理邮费检查和扣除
     * @param slotId 点击的槽位ID
     * @param button 按钮
     * @param clickType 点击类型
     * @param player 玩家
     * @param trashBox 垃圾箱
     * @param slots 槽位列表
     * @param carriedItem 手持物品
     * @param trashSlots 垃圾箱槽位数量
     * @return true=继续操作，false=阻止操作
     */
    public static boolean validateAndProcessPayment(int slotId, int button, ClickType clickType, Player player, 
                                      TrashBox trashBox, java.util.List<Slot> slots, ItemStack carriedItem, int trashSlots) {
        String operation = getOperationType(slotId, button, clickType, player, slots, carriedItem, trashSlots);
        if (operation == null) return true; // 不涉及邮费的操作
        
        ResourceLocation playerDim = player.level().dimension().location();
        ResourceLocation trashDim = trashBox.getDimensionId();
        
        if ("insert".equals(operation)) {
            return handleInsertPayment(playerDim, trashDim, player);
        } else if ("extract".equals(operation)) {
            return handleExtractPayment(playerDim, trashDim, player);
        }
        
        return true;
    }
    
    /**
     * 处理放入操作的邮费
     */
    private static boolean handleInsertPayment(ResourceLocation playerDim, ResourceLocation trashDim, Player player) {
        int cost = calculatePaymentCost(playerDim, trashDim, "insert");
        if (cost <= 0) return true;
        
        return PaymentService.checkAndDeductPayment(player, cost);
    }
    
    /**
     * 处理取出操作的邮费
     */
    private static boolean handleExtractPayment(ResourceLocation playerDim, ResourceLocation trashDim, Player player) {
        int cost = calculatePaymentCost(playerDim, trashDim, "extract");
        if (cost <= 0) return true;
        
        return PaymentService.checkAndDeductPayment(player, cost);
    }
    
    /**
     * 根据点击行为判断操作类型
     * @param slotId 槽位ID
     * @param button 按钮
     * @param clickType 点击类型
     * @param player 玩家
     * @param slots 槽位列表
     * @param carriedItem 手持物品
     * @param trashSlots 垃圾箱槽位数量
     * @return "insert"、"extract" 或 null（不收费）
     */
    private static String getOperationType(int slotId, int button, ClickType clickType, Player player,
                                         java.util.List<Slot> slots, ItemStack carriedItem, int trashSlots) {
        if (slotId >= 0 && slotId < trashSlots) {
            // 点击垃圾箱槽位
            ItemStack slotItem = slots.get(slotId).getItem();

            if (!carriedItem.isEmpty() && clickType == ClickType.PICKUP) {
                return "insert"; // 放入操作
            }
            if (!player.getInventory().getItem(button).isEmpty() && clickType == ClickType.SWAP) {
                return "insert"; // 放入操作
            }
            
            // 取出操作识别
            if (carriedItem.isEmpty() && !slotItem.isEmpty() && clickType == ClickType.PICKUP) {
                return "extract"; // 取出操作
            }
            if (clickType == ClickType.SWAP && !slotItem.isEmpty()) {
                return "extract"; // 数字键交换取出
            }
            if (clickType == ClickType.QUICK_MOVE && !slotItem.isEmpty()) {
                return "extract"; // Shift点击取出
            }
            if (clickType == ClickType.PICKUP_ALL) {
                return "extract"; // 双击收集
            }
            if (clickType == ClickType.THROW && carriedItem.isEmpty() && !slotItem.isEmpty()) {
                return "extract"; // Q键丢出
            }
        } else if (slotId >= trashSlots && !slots.get(slotId).getItem().isEmpty() && clickType == ClickType.QUICK_MOVE) {
            return "insert"; // Shift点击放入
        }
        
        return null; // 其他操作不收费
    }
    
    /**
     * 计算邮费数量（支持insert和extract操作）
     * @param playerDim 玩家所在维度
     * @param trashDim 垃圾箱所在维度
     * @param operation 操作类型："insert" 或 "extract"
     * @return 需要支付的邮费数量，0表示免费
     */
    public static int calculatePaymentCost(ResourceLocation playerDim, ResourceLocation trashDim, String operation) {
        boolean isSameDimension = playerDim.equals(trashDim);
        String paymentMode = "insert".equals(operation) ? Config.INSERT_PAYMENT_MODE.get() : Config.EXTRACT_PAYMENT_MODE.get();
        
        return switch (paymentMode) {
            case "current_dimension_free" -> isSameDimension ? 0 : calculateCrossDimensionCost(trashDim);
            case "all_dimensions_pay" -> isSameDimension ? Config.CROSS_DIMENSION_ACCESS_COST.get() : calculateCrossDimensionCost(trashDim);
            default -> 0;
        };
    }
    
    /**
     * 计算跨维度邮费
     * @param trashDim 垃圾箱所在维度
     * @return 计算后的邮费数量
     */
    private static int calculateCrossDimensionCost(ResourceLocation trashDim) {
        int baseCost = Config.CROSS_DIMENSION_ACCESS_COST.get();
        double multiplier = Config.getDimensionMultiplier(trashDim.toString());
        return (int) Math.ceil(baseCost * multiplier);
    }
}