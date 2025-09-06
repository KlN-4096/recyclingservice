package com.klnon.recyclingservice.content.trashbox;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.Recyclingservice;

import com.klnon.recyclingservice.content.trashbox.core.TrashBox;
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


    public TrashBoxMenu(int containerId, Inventory playerInventory, TrashBox trashBox) {
        super(UiHelper.getMenuTypeForRows(), containerId, playerInventory, trashBox, Config.GAMEPLAY.trashBoxRows.get());
        this.trashBox = trashBox;
        this.trashSlots = Config.GAMEPLAY.trashBoxRows.get() * 9;
    }

    // === 静态工具方法：打开垃圾箱UI ===
    
    /**
     * 为玩家打开指定维度的垃圾箱UI
     */
    public static boolean openTrashBox(ServerPlayer player, ResourceLocation dimensionId, int boxNumber) {
        return ErrorHelper.handleOperation(player, "openTrashBox", () -> {
            // 获取指定的垃圾箱
            TrashBox trashBox = TrashBoxManager.getOrCreateTrashBox(dimensionId, boxNumber);
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
            // 放物品到垃圾箱 - 使用TrashBox的优化方法
            if (slotItem.isEmpty()) {
                // 空槽位：左键放全部，右键放一个
                ItemStack toAdd = isLeftClick ? carried.copy() : carried.copyWithCount(1);
                if (trashBox.tryAddToEmptySlot(toAdd, slot.index)) {
                    carried.shrink(toAdd.getCount());
                    return carried.isEmpty() ? ItemStack.EMPTY : carried;
                }
            } else if (TrashBoxManager.isSameItem(carried, slotItem)) {
                // 相同物品：尝试合并
                ItemStack mergeItem = isLeftClick ? carried.copy() : carried.copyWithCount(1);
                if (trashBox.tryMergeToExisting(mergeItem)) {
                    carried.shrink(isLeftClick ? carried.getCount() - mergeItem.getCount() : 1);
                    return carried.isEmpty() ? ItemStack.EMPTY : carried;
                }
            } else {
                // 不同物品：交换
                if (slotItem.getCount() <= slotItem.getMaxStackSize()) {
                    ItemStack result = slotItem.copy();
                    slot.set(carried.copy());
                    return result;
                }
            }
            return carried;
        }
        
        return carried;
    }

    /**
     * 处理数字键交换点击
     */
    private ItemStack handleSwapClick(Slot slot, ItemStack slotItem, ItemStack swapItem, 
                                    int button, Player player) {
        if (slotItem.isEmpty() && !swapItem.isEmpty()) {
            if (trashBox.tryAddToEmptySlot(swapItem.copy(), slot.index)) {
                player.getInventory().setItem(button, ItemStack.EMPTY);
                return ItemStack.EMPTY;
            }
        } else if (!slotItem.isEmpty()) {
            // 有物品：交换
            int moveCount = Math.min(slotItem.getMaxStackSize(), slotItem.getCount());
            ItemStack result = slotItem.copyWithCount(moveCount);
            player.getInventory().setItem(button, result);
            UiHelper.updateSlotAfterMove(slot, moveCount);
            return result;
        }
        
        return swapItem;
    }

    /**
     * 处理双击收集
     */
    private ItemStack handleDoubleClick(ItemStack clickedItem, ItemStack carried) {
        ItemStack result = carried;
        
        if (carried.isEmpty()) {
            result = clickedItem.copyWithCount(0);
        }
        
        if (!TrashBoxManager.isSameItem(result, clickedItem) && !clickedItem.isEmpty()) {
            return result;
        }
        
        // 收集垃圾箱内所有相同物品
        int maxStackSize = result.getMaxStackSize();
        List<Integer> sameItemSlots = trashBox.getSameItemSlots(result);
        
        for (Integer slotIndex : sameItemSlots) {
            if (result.getCount() >= maxStackSize) break;
            
            ItemStack slotItem = trashBox.getItem(slotIndex);
            int maxTake = Math.min(slotItem.getMaxStackSize(), slotItem.getCount());
            int canAdd = maxStackSize - result.getCount();
            int takeAmount = Math.min(maxTake, canAdd);
            
            if (takeAmount > 0) {
                result.grow(takeAmount);
                Slot tempSlot = new Slot(trashBox, slotIndex, 0, 0) {};
                UiHelper.updateSlotAfterMove(tempSlot, takeAmount);
            }
        }
        
        trashBox.setChanged();
        return result;
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
    protected boolean moveItemStackTo(@Nonnull ItemStack stack, int startIndex, int endIndex, 
                                     boolean reverseDirection) {
        // 移动到垃圾箱的特殊处理
        if (startIndex == 0 && endIndex <= trashSlots) {
            if (stack.isEmpty()) return false;
            
            ItemStack remaining = stack.copy();
            trashBox.addItem(remaining);
            
            // 检查是否完全添加
            if (remaining.isEmpty()) {
                stack.setCount(0);
                return true;
            } else {
                // 部分添加，更新原始栈的数量
                stack.setCount(remaining.getCount());
                return stack.getCount() < stack.getMaxStackSize(); // 返回是否有部分添加成功
            }
        }
        
        // 其他情况使用原版逻辑
        UiHelper.updateTooltip(stack);
        return super.moveItemStackTo(stack, startIndex, endIndex, reverseDirection);
    }

    // === 支付验证和处理方法 ===
    
    /**
     * 验证并处理支付
     */
    private boolean validateAndProcessPayment(int slotId, int button, ClickType clickType, 
                                            Player player, List<Slot> slots, ItemStack carried) {
        String operation = getOperationType(slotId, button, clickType, player, slots, carried);
        if (operation == null) return true; // 不涉及邮费的操作
        
        ResourceLocation playerDim = player.level().dimension().location();
        ResourceLocation trashDim = trashBox.getDimensionId();
        
        int cost = TrashPaymentHandler.calculateOperationCost(playerDim, trashDim, operation);
        if (cost <= 0) return true;
        
        return TrashPaymentHandler.checkAndDeductPayment(player, cost);
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
    
}