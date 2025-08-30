package com.klnon.recyclingservice.util.management;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.klnon.recyclingservice.Config;

/**
 * 邮费工具类 - 处理垃圾箱邮费相关功能
 * 功能：
 * - 检查玩家是否有足够的邮费
 * - 扣除玩家的邮费
 * - 发送邮费相关消息
 */
public class PaymentUtils {
    
    /**
     * 检查玩家是否有足够的邮费物品
     * @param player 玩家
     * @param requiredCost 需要的邮费数量
     * @return 是否有足够邮费
     */
    public static boolean hasEnoughPayment(Player player, int requiredCost) {
        return processPayment(player, requiredCost, false) >= 0;
    }
    
    /**
     * 扣除玩家的邮费
     * @param player 玩家
     * @param cost 需要扣除的数量
     * @return 是否成功扣除
     */
    public static boolean deductPayment(Player player, int cost) {
        return processPayment(player, cost, true) >= 0;
    }
    
    /**
     * 优化的支付处理方法 - 单次遍历完成检查和扣除
     * @param player 玩家
     * @param requiredCost 需要的邮费数量
     * @param actuallyDeduct 是否实际扣除（false=仅检查，true=检查并扣除）
     * @return 成功返回>=0，失败返回-1
     */
    private static int processPayment(Player player, int requiredCost, boolean actuallyDeduct) {
        if (requiredCost <= 0) {
            return 0;
        }
        
        ResourceLocation paymentItem = Config.getPaymentItem();
        int totalFound = 0;
        int remaining = requiredCost;
        
        // 单次遍历完成检查和扣除
        for (ItemStack stack : player.getInventory().items) {
            if (isPaymentItem(stack, paymentItem)) {
                int stackCount = stack.getCount();
                totalFound += stackCount;
                
                if (actuallyDeduct && remaining > 0) {
                    int deduct = Math.min(remaining, stackCount);
                    stack.shrink(deduct);
                    remaining -= deduct;
                }
            }
        }
        
        // 检查是否有足够的物品
        if (totalFound < requiredCost) {
            return -1; // 不足
        }
        
        return actuallyDeduct ? (remaining == 0 ? 0 : -1) : 0;
    }
    
    /**
     * 检查物品是否为指定的邮费物品
     * @param stack 物品堆
     * @param paymentItem 邮费物品类型
     * @return 是否匹配
     */
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
        String messageTemplate = Config.getPaymentErrorMessage();
        String formattedMessage = messageTemplate
                .replace("{cost}", String.valueOf(requiredCost))
                .replace("{item}", itemName);
        Component message = Component.literal(formattedMessage);
        player.displayClientMessage(message, true);
    }
    
    /**
     * 发送邮费扣除成功消息
     * @param player 玩家
     * @param deductedCost 扣除的邮费数量
     */
    public static void sendPaymentSuccessMessage(Player player, int deductedCost) {
        String itemName = getPaymentItemDisplayName();
        String messageTemplate = Config.getPaymentSuccessMessage();
        String formattedMessage = messageTemplate
                .replace("{cost}", String.valueOf(deductedCost))
                .replace("{item}", itemName);
        Component message = Component.literal(formattedMessage);
        player.displayClientMessage(message, true);
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
}