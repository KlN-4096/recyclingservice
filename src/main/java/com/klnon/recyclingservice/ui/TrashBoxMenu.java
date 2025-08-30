package com.klnon.recyclingservice.ui;

import com.klnon.recyclingservice.core.TrashBox;
import com.klnon.recyclingservice.util.cleanup.ItemMerge;
import com.klnon.recyclingservice.util.management.PaymentUtils;
import com.klnon.recyclingservice.util.ui.UiUtils;
import com.klnon.recyclingservice.Config;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;

public class TrashBoxMenu extends ChestMenu {
    
    private final TrashBox trashBox;
    private final int trashSlots;

    public TrashBoxMenu(int containerId, Inventory playerInventory, TrashBox trashBox) {
        super(Config.getMenuTypeForRows(), containerId, playerInventory, trashBox, Config.getTrashBoxRows());
        this.trashBox = trashBox;
        this.trashSlots = Config.getTrashBoxRows() * 9;
    }

    @Override
    public void clicked(int slotId, int button, @Nonnull ClickType clickType, @Nonnull Player player) {
        // 邮费检查和扣除
        if (!handlePayment(slotId, button, clickType, player)) {
            return; // 邮费不足，阻止操作
        }
        
        // 全面检查：如果是垃圾箱槽位且维度不允许放入，拦截所有可能的放入操作
        if (slotId >= 0 && !trashBox.isAllowedToPutIn()) {
            if (clickType==ClickType.QUICK_MOVE && slotId>=trashSlots)
                return;
            if ((!getCarried().isEmpty() || (clickType==ClickType.SWAP && slotId>trashSlots)) && slotId < trashSlots)
                return;
        }
        if (slotId >= 0 && slotId < trashSlots) {
            Slot slot = slots.get(slotId);
            ItemStack carried = getCarried();
            ItemStack result = ItemStack.EMPTY;
            ItemStack slotItem = slot.getItem();
            if (clickType == ClickType.PICKUP && slotItem.getCount()>=slotItem.getMaxStackSize()) {
                result = handlePickupClick(slot, slotItem, carried, button == 0);
            } else if (clickType == ClickType.SWAP && slotItem.getCount()>slotItem.getMaxStackSize()) {
                result = handleSwapClick(slot, slotItem, player.getInventory().getItem(button), button, player);
            } else if (clickType == ClickType.PICKUP_ALL) {
                result = handleDoubleClick(slotItem);
            } else if (clickType == ClickType.QUICK_MOVE) {
                result = quickMoveStack(player, slotId);
            }else if  (clickType == ClickType.THROW && this.getCarried().isEmpty()) {
                Slot slot3 = this.slots.get(slotId);
                result =slot3.getItem();
                int j1 = button == 0 ? 1 : result.getCount();
                UiUtils.cleanItemStack(result);
                result = slot3.safeTake(j1, Integer.MAX_VALUE, player);
                player.drop(result, true);
                return;
            }else{
                super.clicked(slotId, button, clickType, player);
            }
            
            // 统一更新受影响的物品
            UiUtils.updateTooltip(slotItem);
            UiUtils.updateTooltip(result);
            return;
        }

        super.clicked(slotId, button, clickType, player);
    }
    
    private ItemStack handlePickupClick(Slot slot, ItemStack slotItem, ItemStack carried, boolean isLeftClick) {
        if (carried.isEmpty() && !slotItem.isEmpty()) {
            // 从垃圾箱取物品 -> 返回手上物品
            int maxMove = Math.min(slotItem.getMaxStackSize(), slotItem.getCount());
            int moveCount = isLeftClick ? maxMove : (slotItem.getCount() == 1 ? 1 : 
                (slotItem.getCount() >= slotItem.getMaxStackSize() ? slotItem.getMaxStackSize()/2 : (slotItem.getCount() + 1) / 2));
            
            setCarried(slotItem.copyWithCount(moveCount));
            //这里垃圾箱内的物品数量信息会更新,所以只需要return手上物品的数量信息
            UiUtils.updateSlotAfterMove(slot, moveCount);
            trashBox.setChanged();
            return getCarried();
            
        } else if (!carried.isEmpty()) {
            // 放物品到垃圾箱 -> 返回槽位物品
            if (slotItem.isEmpty()) {
                // 空槽位放入
                if (isLeftClick) {
                    // 左键：直接放入全部
                    slot.set(carried.copy());
                    setCarried(ItemStack.EMPTY);
                } else {
                    // 右键：放入1个
                    ItemStack singleItem = carried.copyWithCount(1);
                    slot.set(singleItem);
                    carried.shrink(1);
                    if (carried.isEmpty()) setCarried(ItemStack.EMPTY);
                }
            } else if (ItemMerge.isSameItem(carried, slotItem)) {
                // 相同物品合并
                int configLimit = Config.getItemStackMultiplier(slotItem);
                if (slotItem.getCount() < configLimit) {
                    if (isLeftClick) {
                        // 左键：尽可能合并至上限
                        int canAdd = Math.min(configLimit - slotItem.getCount(), carried.getCount());
                        slotItem.grow(canAdd);
                        carried.shrink(canAdd);
                    } else {
                        // 右键：放入1个
                        slotItem.grow(1);
                        carried.shrink(1);
                    }
                    if (carried.isEmpty()) setCarried(ItemStack.EMPTY);
                }
            } else {
                // 不同物品交换（仅当槽位物品不超过配置上限）
                if (slotItem.getCount() <= slotItem.getMaxStackSize()) {
                    setCarried(slotItem.copy());
                    slot.set(carried.copy());
                }
            }
            trashBox.setChanged();
            //这里由于是从物品栏/快捷栏到垃圾箱,物品栏/快捷栏不用更新数量,原版自动更新,所以只需要返回垃圾箱格子中的物品
            return slot.getItem();
        }
        
        // 无操作情况
        return getCarried();
    }

    private ItemStack handleSwapClick(Slot slot, ItemStack slotItem, ItemStack swapItem, int button, Player player) {
        // 数字键交换 -> 往垃圾箱放物品，返回玩家物品栏中的物品
        if (slotItem.isEmpty()) {
            // 空槽位直接移动
            slot.set(swapItem.copy());
            player.getInventory().setItem(button, ItemStack.EMPTY);
        } else if (slotItem.getCount() <= slotItem.getMaxStackSize() || swapItem.isEmpty()) {
            // 交换（仅当槽位物品不超过配置上限）
            int moveCount = Math.min(slotItem.getMaxStackSize(), slotItem.getCount());
            swapItem = slotItem.copyWithCount(moveCount);
            player.getInventory().setItem(button, swapItem);
            UiUtils.updateSlotAfterMove(slot, moveCount);
        }
        trashBox.setChanged();
        return swapItem;
    }

    private ItemStack handleDoubleClick(ItemStack clickedItem) {
        //这个因为是双击,所以始终返回手上物品
        ItemStack carried = getCarried();
        if (carried.isEmpty()) {
            // 空手双击，创建新的物品堆
            carried = clickedItem.copyWithCount(0);
            setCarried(carried);
        }
        
        if (!ItemMerge.isSameItem(carried, clickedItem) && !clickedItem.isEmpty()) return getCarried();
        
        // 收集垃圾箱内所有相同物品到手持物品堆
        int maxStackSize = carried.getMaxStackSize();
        for (int i = 0; i < trashSlots && carried.getCount() < maxStackSize; i++) {
            ItemStack slotItem = trashBox.getItem(i);
            if (ItemMerge.isSameItem(carried, slotItem)) {
                // 计算能取出多少（按正常规则：最多64个）
                int maxTake = Math.min(slotItem.getMaxStackSize(), slotItem.getCount());
                int canAdd = maxStackSize - carried.getCount();
                int takeAmount = Math.min(maxTake, canAdd);
                
                if (takeAmount > 0) {
                    carried.grow(takeAmount);
                    UiUtils.updateSlotAfterMove(slots.get(i), takeAmount);
                }
            }
        }
        
        trashBox.setChanged();
        return getCarried();
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
            // 从玩家背包到垃圾箱：手动实现
            if (!slotItem.isEmpty()) {
                ItemStack originalStack = slotItem.copy();

                // 尝试移动到垃圾箱槽位 (0 到 trashSlots-1)
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
    protected boolean moveItemStackTo(@Nonnull ItemStack stack, int startIndex, int endIndex, boolean reverseDirection) {
        // 移动到垃圾箱的特殊处理
        if (startIndex == 0 && endIndex <= trashSlots) {
            return moveToTrashBox(stack);
        }
        
        // 其他情况使用原版逻辑
        UiUtils.updateTooltip(stack);
        return super.moveItemStackTo(stack, startIndex, endIndex, reverseDirection);
    }

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
            } else if (ItemMerge.isSameItem(stack, slotItem)) {
                // 找到相同物品，尝试合并
                int canAdd = configLimit - slotItem.getCount();
                if (canAdd > 0) {
                    int addAmount = Math.min(canAdd, stack.getCount());
                    slotItem.grow(addAmount);
                    UiUtils.updateTooltip(slots.get(i).getItem());
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
    
    /**
     * 处理邮费检查和扣除
     * @param slotId 点击的槽位ID
     * @param clickType 点击类型
     * @param player 玩家
     * @return true=继续操作，false=阻止操作
     */
    private boolean handlePayment(int slotId, int button, ClickType clickType, Player player) {
        String operation = getOperationType(slotId, button, clickType, player);
        if (operation == null) return true; // 不涉及邮费的操作
        
        ResourceLocation playerDim = player.level().dimension().location();
        ResourceLocation trashDim = trashBox.getDimensionId();
        
        if ("insert".equals(operation)) {
            return handleInsertPayment(playerDim, trashDim, player);
        }
        
        return true;
    }
    
    
    /**
     * 处理放入操作的邮费
     */
    private boolean handleInsertPayment(ResourceLocation playerDim, ResourceLocation trashDim, Player player) {
        int cost = Config.calculatePaymentCost(playerDim, trashDim);
        if (cost <= 0) return true;
        
        // 检查和扣除邮费
        return PaymentUtils.checkAndDeductPayment(player, cost);
    }
    
    
    
    /**
     * 根据点击行为判断操作类型
     * @param slotId 槽位ID
     * @param clickType 点击类型
     * @return "insert" 或 null（不收费）
     */
    private String getOperationType(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < trashSlots) {
            // 点击垃圾箱槽位
            ItemStack carried = getCarried();

            if (!carried.isEmpty() && clickType == ClickType.PICKUP) {
                return "insert"; // 放入操作
            }
            if(!player.getInventory().getItem(button).isEmpty() && clickType == ClickType.SWAP){
                return "insert"; // 放入操作
            }
        } else if (slotId >= trashSlots && !slots.get(slotId).getItem().isEmpty() && clickType == ClickType.QUICK_MOVE) {
            return "insert"; // Shift点击放入
        }
        
        return null; // 其他操作不收费
    }
}