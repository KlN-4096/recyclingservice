package com.klnon.recyclingservice.ui;

import com.klnon.recyclingservice.core.TrashBox;
import com.klnon.recyclingservice.core.TrashManager;
import com.klnon.recyclingservice.util.ui.UiUtils;
import com.klnon.recyclingservice.util.ErrorHandler;
import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.Recyclingservice;

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

import javax.annotation.Nonnull;
import org.jetbrains.annotations.NotNull;

/**
 * 垃圾箱菜单 - 负责UI显示和事件分发
 * 物品处理逻辑委托给TrashBoxHandler
 */
public class TrashBoxMenu extends ChestMenu {
    
    private final TrashBox trashBox;
    private final TrashBoxHandler handler;
    private final int trashSlots;

    public TrashBoxMenu(int containerId, Inventory playerInventory, TrashBox trashBox) {
        super(UiUtils.getMenuTypeForRows(), containerId, playerInventory, trashBox, Config.TRASH_BOX_ROWS.get());
        this.trashBox = trashBox;
        this.handler = new TrashBoxHandler(trashBox);
        this.trashSlots = Config.TRASH_BOX_ROWS.get() * 9;
    }
    
    // === 静态工具方法：打开垃圾箱UI ===
    
    /**
     * 为玩家打开指定维度的垃圾箱UI
     */
    public static boolean openTrashBox(ServerPlayer player, ResourceLocation dimensionId, 
                                      int boxNumber, TrashManager trashManager) {
        return ErrorHandler.handleOperation(player, "openTrashBox", () -> {
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
        if (!handler.validateAndProcessPayment(slotId, button, clickType, player, slots, getCarried())) {
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
        
        // 委托给Handler处理具体逻辑
        if (clickType == ClickType.PICKUP && slotItem.getCount() >= slotItem.getMaxStackSize()) {
            result = handler.handlePickupClick(slot, slotItem, carried, button == 0);
            setCarried(result);
        } else if (clickType == ClickType.SWAP && slotItem.getCount() > slotItem.getMaxStackSize()) {
            result = handler.handleSwapClick(slot, slotItem, player.getInventory().getItem(button), 
                                            button, player);
        } else if (clickType == ClickType.PICKUP_ALL) {
            result = handler.handleDoubleClick(slotItem, carried);
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
        UiUtils.updateTooltip(slotItem);
        UiUtils.updateTooltip(result);
    }
    
    /**
     * 处理丢弃物品的点击
     */
    private void handleThrowClick(Slot slot, int button, Player player) {
        ItemStack result = slot.getItem();
        int throwCount = button == 0 ? 1 : result.getCount();
        UiUtils.cleanItemStack(result);
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
                UiUtils.updateSlotAfterMove(slot, moveCount);
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
            return handler.moveToTrashBox(stack);
        }
        
        // 其他情况使用原版逻辑
        UiUtils.updateTooltip(stack);
        return super.moveItemStackTo(stack, startIndex, endIndex, reverseDirection);
    }
}