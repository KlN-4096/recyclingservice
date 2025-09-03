package com.klnon.recyclingservice.content.trashbox.ui;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.Recyclingservice;

import com.klnon.recyclingservice.content.cleanup.entity.EntityMerger;
import com.klnon.recyclingservice.content.trashbox.core.TrashBox;
import com.klnon.recyclingservice.content.trashbox.core.TrashManager;
import com.klnon.recyclingservice.content.trashbox.payment.PaymentService;
import com.klnon.recyclingservice.foundation.utility.ErrorHelper;
import com.klnon.recyclingservice.foundation.utility.UiHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

import javax.annotation.Nonnull;
import org.jetbrains.annotations.NotNull;

/**
 * 垃圾箱菜单 - 负责UI显示和物品处理逻辑
 * 整合了原 TrashBoxHandler 的功能，减少抽象层
 */
public class TrashBoxMenu extends ChestMenu {
    
    private final TrashBox trashBox;
    private final int trashSlots;

    public TrashBoxMenu(int containerId, Inventory playerInventory, TrashBox trashBox) {
        super(UiHelper.getMenuTypeForRows(), containerId, playerInventory, trashBox, Config.GAMEPLAY.trashBoxRows.get());
        this.trashBox = trashBox;
        this.trashSlots = Config.GAMEPLAY.trashBoxRows.get() * 9;
    }
    
    // === 静态工具方法：打开垃圾箱UI ===
    
    /**
     * 为玩家打开指定维度的垃圾箱UI
     */
    public static boolean openTrashBox(ServerPlayer player, ResourceLocation dimensionId, 
                                      int boxNumber, TrashManager trashManager) {
        return ErrorHelper.handleOperation(player, "openTrashBox", () -> {
            // 获取指定的垃圾箱
            TrashBox trashBox = trashManager.getOrCreateTrashBox(dimensionId, boxNumber);
            if (trashBox == null) return false;

            // 创建简洁的标题：例如 "overworld-1"
            String dimensionName = dimensionId.getPath();
            Component title = Component.literal(dimensionName + "-" + boxNumber);
            
            // 创建MenuProvider并打开
            MenuProvider provider = new TrashBoxMenuProvider(trashBox, title);
            player.openMenu(provider);

            // 记录日志（调试用）
            Recyclingservice.LOGGER.debug("Player {} opened trash box {}-{}",
                player.getName().getString(), dimensionId, boxNumber);

            return true;
        }, false);
    }
    
    // === 内部MenuProvider实现 ===
    
    /**
     * 垃圾箱菜单提供者
     */
    private record TrashBoxMenuProvider(TrashBox trashBox, Component title) implements MenuProvider {
        @Override
        public @Nonnull Component getDisplayName() {
            return title;
        }

        @Override
        public AbstractContainerMenu createMenu(int containerId, @Nonnull Inventory playerInventory, 
                                               @Nonnull Player player) {
            return new TrashBoxMenu(containerId, playerInventory, trashBox);
        }
    }
    
    // === 点击处理（委托给Handler） ===

    @Override
    public void clicked(int slotId, int button, @Nonnull ClickType clickType, @Nonnull Player player) {
        // 支付检查和扣除
        if (!validateAndProcessPayment(slotId, button, clickType, player, slots, getCarried())) {
            return; // 邮费不足，阻止操作
        }
        
        // 检查维度是否允许放入
        if (slotId >= 0 && !trashBox.isAllowedToPutIn()) {
            if (clickType == ClickType.QUICK_MOVE && slotId >= trashSlots)
                return;
            if ((!getCarried().isEmpty() || (clickType == ClickType.SWAP && slotId > trashSlots)) 
                && slotId < trashSlots)
                return;
        }
        
        // 处理垃圾箱槽位的点击
        if (slotId >= 0 && slotId < trashSlots) {
            handleTrashBoxSlotClick(slotId, button, clickType, player);
            return;
        }

        super.clicked(slotId, button, clickType, player);
    }
    
    /**
     * 处理垃圾箱槽位的点击事件
     */
    private void handleTrashBoxSlotClick(int slotId, int button, ClickType clickType, Player player) {
        Slot slot = slots.get(slotId);
        ItemStack carried = getCarried();
        ItemStack slotItem = slot.getItem();
        ItemStack result;
        
        // 直接处理各种点击类型的逻辑
        if (clickType == ClickType.PICKUP && slotItem.getCount() >= slotItem.getMaxStackSize()) {
            result = handlePickupClick(slot, slotItem, carried, button == 0);
            setCarried(result);
        } else if (clickType == ClickType.SWAP && slotItem.getCount() > slotItem.getMaxStackSize()) {
            result = handleSwapClick(slot, slotItem, player.getInventory().getItem(button), 
                                            button, player);
        } else if (clickType == ClickType.PICKUP_ALL) {
            result = handleDoubleClick(slotItem, carried);
            setCarried(result);
        } else if (clickType == ClickType.QUICK_MOVE) {
            result = quickMoveStack(player, slotId);
        } else if (clickType == ClickType.THROW && getCarried().isEmpty()) {
            handleThrowClick(slot, button, player);
            return;
        } else {
            super.clicked(slotId, button, clickType, player);
            return;
        }
        
        // 统一更新受影响的物品
        UiHelper.updateTooltip(slotItem);
        UiHelper.updateTooltip(result);
    }
    
    /**
     * 处理丢弃物品的点击
     */
    private void handleThrowClick(Slot slot, int button, Player player) {
        ItemStack result = slot.getItem();
        int throwCount = button == 0 ? 1 : result.getCount();
        UiHelper.cleanItemStack(result);
        result = slot.safeTake(throwCount, Integer.MAX_VALUE, player);
        player.drop(result, true);
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@Nonnull Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.mayPickup(player)) return ItemStack.EMPTY;

        ItemStack slotItem = slot.getItem();
        if (index < trashSlots) {
            // 从垃圾箱到玩家背包：最多64个
            int moveCount = Math.min(slotItem.getCount(), slotItem.getMaxStackSize());
            ItemStack moveItem = slotItem.copyWithCount(moveCount);

            if (moveItemStackTo(moveItem, trashSlots, slots.size(), true)) {
                UiHelper.updateSlotAfterMove(slot, moveCount);
                return ItemStack.EMPTY;
            }
        } else {
            // 从玩家背包到垃圾箱
            if (!slotItem.isEmpty()) {
                ItemStack originalStack = slotItem.copy();

                // 尝试移动到垃圾箱槽位
                if (moveItemStackTo(slotItem, 0, trashSlots, false)) {
                    // 移动成功后更新槽位
                    if (slotItem.isEmpty()) {
                        slot.setByPlayer(ItemStack.EMPTY);
                    } else {
                        slot.setChanged();
                    }
                    return originalStack;
                }
            }
        }

        return ItemStack.EMPTY;
    }
    
    @Override
    protected boolean moveItemStackTo(@Nonnull ItemStack stack, int startIndex, int endIndex, 
                                     boolean reverseDirection) {
        // 移动到垃圾箱的特殊处理
        if (startIndex == 0 && endIndex <= trashSlots) {
            return moveToTrashBox(stack);
        }
        
        // 其他情况使用原版逻辑
        UiHelper.updateTooltip(stack);
        return super.moveItemStackTo(stack, startIndex, endIndex, reverseDirection);
    }
    
    // === 支付验证和处理方法（从 TrashBoxHandler 迁移） ===
    
    /**
     * 验证并处理支付
     */
    private boolean validateAndProcessPayment(int slotId, int button, ClickType clickType, 
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
            Config.GAMEPLAY.insertPaymentMode.get() : Config.GAMEPLAY.extractPaymentMode.get();
        
        return switch (paymentMode) {
            case "current_dimension_free" -> 
                isSameDimension ? 0 : calculateCrossDimensionCost(trashDim);
            case "all_dimensions_pay" -> 
                isSameDimension ? Config.GAMEPLAY.crossDimensionAccessCost.get() : 
                                  calculateCrossDimensionCost(trashDim);
            default -> 0;
        };
    }
    
    private int calculateCrossDimensionCost(ResourceLocation trashDim) {
        int baseCost = Config.GAMEPLAY.crossDimensionAccessCost.get();
        double multiplier = Config.getDimensionMultiplier(trashDim.toString());
        return (int) Math.ceil(baseCost * multiplier);
    }
    
    // === 物品处理方法（从 TrashBoxHandler 迁移） ===
    
    /**
     * 处理拾取点击（左键/右键点击）
     */
    private ItemStack handlePickupClick(Slot slot, ItemStack slotItem, ItemStack carried, 
                                      boolean isLeftClick) {
        if (carried.isEmpty() && !slotItem.isEmpty()) {
            // 从垃圾箱取物品
            int maxMove = Math.min(slotItem.getMaxStackSize(), slotItem.getCount());
            int moveCount = isLeftClick ? maxMove : (slotItem.getCount() == 1 ? 1 : 
                (slotItem.getCount() >= slotItem.getMaxStackSize() ? 
                 slotItem.getMaxStackSize()/2 : (slotItem.getCount() + 1) / 2));
            
            ItemStack result = slotItem.copyWithCount(moveCount);
            UiHelper.updateSlotAfterMove(slot, moveCount);
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
            } else if (EntityMerger.isSameItem(carried, slotItem)) {
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
    private ItemStack handleSwapClick(Slot slot, ItemStack slotItem, ItemStack swapItem, 
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
            UiHelper.updateSlotAfterMove(slot, moveCount);
        }
        
        trashBox.setChanged();
        return result;
    }

    /**
     * 处理双击收集
     */
    private ItemStack handleDoubleClick(ItemStack clickedItem, ItemStack carried) {
        ItemStack result = carried;
        
        if (carried.isEmpty()) {
            result = clickedItem.copyWithCount(0);
        }
        
        if (!EntityMerger.isSameItem(result, clickedItem) && !clickedItem.isEmpty()) {
            return result;
        }
        
        // 收集垃圾箱内所有相同物品
        int maxStackSize = result.getMaxStackSize();
        for (int i = 0; i < trashSlots && result.getCount() < maxStackSize; i++) {
            ItemStack slotItem = trashBox.getItem(i);
            if (EntityMerger.isSameItem(result, slotItem)) {
                int maxTake = Math.min(slotItem.getMaxStackSize(), slotItem.getCount());
                int canAdd = maxStackSize - result.getCount();
                int takeAmount = Math.min(maxTake, canAdd);
                
                if (takeAmount > 0) {
                    result.grow(takeAmount);
                    Slot tempSlot = new Slot(trashBox, i, 0, 0) {};
                    UiHelper.updateSlotAfterMove(tempSlot, takeAmount);
                }
            }
        }
        
        trashBox.setChanged();
        return result;
    }
    
    /**
     * 移动物品到垃圾箱
     */
    private boolean moveToTrashBox(ItemStack stack) {
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
            } else if (EntityMerger.isSameItem(stack, slotItem)) {
                // 找到相同物品，尝试合并
                int canAdd = configLimit - slotItem.getCount();
                if (canAdd > 0) {
                    int addAmount = Math.min(canAdd, stack.getCount());
                    slotItem.grow(addAmount);
                    UiHelper.updateTooltip(slotItem);
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