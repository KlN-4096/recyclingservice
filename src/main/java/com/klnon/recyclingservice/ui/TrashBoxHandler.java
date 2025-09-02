package com.klnon.recyclingservice.ui;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.core.TrashBox;
import com.klnon.recyclingservice.util.cleanup.ItemMerge;
import com.klnon.recyclingservice.util.ui.UiUtils;
import com.klnon.recyclingservice.util.management.PaymentService;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 垃圾箱物品处理器 - 负责所有物品交互逻辑
 * 从TrashBoxMenu分离出来，专注于物品处理
 */
public class TrashBoxHandler {
    
    private final TrashBox trashBox;
    private final int trashSlots;
    
    public TrashBoxHandler(TrashBox trashBox) {
        this.trashBox = trashBox;
        this.trashSlots = Config.TRASH_BOX_ROWS.get() * 9;
    }
    
    // === 支付验证 ===
    
    /**
     * 验证并处理支付
     */
    public boolean validateAndProcessPayment(int slotId, int button, ClickType clickType, 
                                            Player player, List<Slot> slots, ItemStack carried) {
        String operation = getOperationType(slotId, button, clickType, player, slots, carried);
        if (operation == null) return true; // 不涉及邮费的操作
        
        ResourceLocation playerDim = player.level().dimension().location();
        ResourceLocation trashDim = trashBox.getDimensionId();
        
        int cost = calculatePaymentCost(playerDim, trashDim, operation);
        if (cost <= 0) return true;
        
        return PaymentService.checkAndDeductPayment(player, cost);
    }
    
    /**
     * 判断操作类型
     */
    private String getOperationType(int slotId, int button, ClickType clickType, 
                                   Player player, List<Slot> slots, ItemStack carriedItem) {
        if (slotId >= 0 && slotId < trashSlots) {
            ItemStack slotItem = slots.get(slotId).getItem();

            if (!carriedItem.isEmpty() && clickType == ClickType.PICKUP) {
                return "insert";
            }
            if (!player.getInventory().getItem(button).isEmpty() && clickType == ClickType.SWAP) {
                return "insert";
            }
            if (carriedItem.isEmpty() && !slotItem.isEmpty() && clickType == ClickType.PICKUP) {
                return "extract";
            }
            if (clickType == ClickType.SWAP && !slotItem.isEmpty()) {
                return "extract";
            }
            if (clickType == ClickType.QUICK_MOVE && !slotItem.isEmpty()) {
                return "extract";
            }
            if (clickType == ClickType.PICKUP_ALL) {
                return "extract";
            }
            if (clickType == ClickType.THROW && carriedItem.isEmpty() && !slotItem.isEmpty()) {
                return "extract";
            }
        } else if (slotId >= trashSlots && !slots.get(slotId).getItem().isEmpty() && 
                   clickType == ClickType.QUICK_MOVE) {
            return "insert";
        }
        return null;
    }
    
    /**
     * 计算邮费
     */
    private int calculatePaymentCost(ResourceLocation playerDim, ResourceLocation trashDim, String operation) {
        boolean isSameDimension = playerDim.equals(trashDim);
        String paymentMode = "insert".equals(operation) ? 
            Config.INSERT_PAYMENT_MODE.get() : Config.EXTRACT_PAYMENT_MODE.get();
        
        return switch (paymentMode) {
            case "current_dimension_free" -> 
                isSameDimension ? 0 : calculateCrossDimensionCost(trashDim);
            case "all_dimensions_pay" -> 
                isSameDimension ? Config.CROSS_DIMENSION_ACCESS_COST.get() : 
                                  calculateCrossDimensionCost(trashDim);
            default -> 0;
        };
    }
    
    private int calculateCrossDimensionCost(ResourceLocation trashDim) {
        int baseCost = Config.CROSS_DIMENSION_ACCESS_COST.get();
        double multiplier = Config.getDimensionMultiplier(trashDim.toString());
        return (int) Math.ceil(baseCost * multiplier);
    }
    
    // === 物品处理方法 ===
    
    /**
     * 处理拾取点击（左键/右键点击）
     */
    public ItemStack handlePickupClick(Slot slot, ItemStack slotItem, ItemStack carried, 
                                      boolean isLeftClick) {
        if (carried.isEmpty() && !slotItem.isEmpty()) {
            // 从垃圾箱取物品
            int maxMove = Math.min(slotItem.getMaxStackSize(), slotItem.getCount());
            int moveCount = isLeftClick ? maxMove : (slotItem.getCount() == 1 ? 1 : 
                (slotItem.getCount() >= slotItem.getMaxStackSize() ? 
                 slotItem.getMaxStackSize()/2 : (slotItem.getCount() + 1) / 2));
            
            ItemStack result = slotItem.copyWithCount(moveCount);
            UiUtils.updateSlotAfterMove(slot, moveCount);
            trashBox.setChanged();
            return result;
            
        } else if (!carried.isEmpty()) {
            // 放物品到垃圾箱
            if (slotItem.isEmpty()) {
                if (isLeftClick) {
                    slot.set(carried.copy());
                    return ItemStack.EMPTY;
                } else {
                    ItemStack singleItem = carried.copyWithCount(1);
                    slot.set(singleItem);
                    ItemStack remainingCarried = carried.copy();
                    remainingCarried.shrink(1);
                    return remainingCarried.isEmpty() ? ItemStack.EMPTY : remainingCarried;
                }
            } else if (ItemMerge.isSameItem(carried, slotItem)) {
                // 相同物品合并
                int configLimit = Config.getItemStackMultiplier(slotItem);
                if (slotItem.getCount() < configLimit) {
                    ItemStack remainingCarried = carried.copy();
                    if (isLeftClick) {
                        int canAdd = Math.min(configLimit - slotItem.getCount(), carried.getCount());
                        slotItem.grow(canAdd);
                        remainingCarried.shrink(canAdd);
                    } else {
                        slotItem.grow(1);
                        remainingCarried.shrink(1);
                    }
                    return remainingCarried.isEmpty() ? ItemStack.EMPTY : remainingCarried;
                }
            } else {
                // 不同物品交换
                if (slotItem.getCount() <= slotItem.getMaxStackSize()) {
                    ItemStack result = slotItem.copy();
                    slot.set(carried.copy());
                    return result;
                }
            }
            trashBox.setChanged();
            return slot.getItem();
        }
        
        return carried;
    }

    /**
     * 处理数字键交换点击
     */
    public ItemStack handleSwapClick(Slot slot, ItemStack slotItem, ItemStack swapItem, 
                                    int button, Player player) {
        ItemStack result = swapItem;
        
        if (slotItem.isEmpty()) {
            slot.set(swapItem.copy());
            player.getInventory().setItem(button, ItemStack.EMPTY);
            result = ItemStack.EMPTY;
        } else if (slotItem.getCount() <= slotItem.getMaxStackSize() || swapItem.isEmpty()) {
            int moveCount = Math.min(slotItem.getMaxStackSize(), slotItem.getCount());
            result = slotItem.copyWithCount(moveCount);
            player.getInventory().setItem(button, result);
            UiUtils.updateSlotAfterMove(slot, moveCount);
        }
        
        trashBox.setChanged();
        return result;
    }

    /**
     * 处理双击收集
     */
    public ItemStack handleDoubleClick(ItemStack clickedItem, ItemStack carried) {
        ItemStack result = carried;
        
        if (carried.isEmpty()) {
            result = clickedItem.copyWithCount(0);
        }
        
        if (!ItemMerge.isSameItem(result, clickedItem) && !clickedItem.isEmpty()) {
            return result;
        }
        
        // 收集垃圾箱内所有相同物品
        int maxStackSize = result.getMaxStackSize();
        for (int i = 0; i < trashSlots && result.getCount() < maxStackSize; i++) {
            ItemStack slotItem = trashBox.getItem(i);
            if (ItemMerge.isSameItem(result, slotItem)) {
                int maxTake = Math.min(slotItem.getMaxStackSize(), slotItem.getCount());
                int canAdd = maxStackSize - result.getCount();
                int takeAmount = Math.min(maxTake, canAdd);
                
                if (takeAmount > 0) {
                    result.grow(takeAmount);
                    Slot tempSlot = new Slot(trashBox, i, 0, 0) {};
                    UiUtils.updateSlotAfterMove(tempSlot, takeAmount);
                }
            }
        }
        
        trashBox.setChanged();
        return result;
    }
    
    /**
     * 移动物品到垃圾箱
     */
    public boolean moveToTrashBox(ItemStack stack) {
        if (stack.isEmpty()) return false;
        
        boolean moved = false;
        int configLimit = Config.getItemStackMultiplier(stack);
        
        // 按顺序搜索：空位和相同物品谁先找到就用谁
        for (int i = 0; i < trashSlots && !stack.isEmpty(); i++) {
            ItemStack slotItem = trashBox.getItem(i);
            
            if (slotItem.isEmpty()) {
                // 找到空槽位，直接放入
                trashBox.setItem(i, stack.copy());
                stack.setCount(0);
                moved = true;
                break;
            } else if (ItemMerge.isSameItem(stack, slotItem)) {
                // 找到相同物品，尝试合并
                int canAdd = configLimit - slotItem.getCount();
                if (canAdd > 0) {
                    int addAmount = Math.min(canAdd, stack.getCount());
                    slotItem.grow(addAmount);
                    UiUtils.updateTooltip(slotItem);
                    stack.shrink(addAmount);
                    moved = true;
                    // 注意：这里不break，因为可能还有剩余物品需要继续寻找下一个槽位
                }
            }
        }
        
        if (moved) {
            trashBox.setChanged();
        }
        
        return moved;
    }
}