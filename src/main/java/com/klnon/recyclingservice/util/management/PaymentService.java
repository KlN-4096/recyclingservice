package com.klnon.recyclingservice.util.management;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.util.MessageUtils;

/**
 * 支付服务 - 处理所有支付相关的核心功能
 * 职责：
 * - 检查和扣除玩家邮费
 * - 发送支付相关消息  
 * - 支付物品验证
 */
public class PaymentService {

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
     * 优化的支付处理方法 - 单次遍历完成检查和扣除
     *
     * @param player       玩家
     * @param requiredCost 需要的邮费数量
     * @return 成功返回>=0，失败返回-1
     */
    private static int processPayment(Player player, int requiredCost) {
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
                
                if (remaining > 0) {
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
        
        return (remaining == 0 ? 0 : -1);
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
        String formattedMessage = MessageUtils.formatTemplate(Config.MESSAGE.paymentErrorMessage.get(), Map.of(
            "cost", String.valueOf(requiredCost),
            "item", itemName
        ));
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
        String formattedMessage = MessageUtils.formatTemplate(Config.MESSAGE.paymentSuccessMessage.get(), Map.of(
            "cost", String.valueOf(deductedCost),
            "item", itemName
        ));
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