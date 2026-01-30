package com.klnon.recyclingservice.foundation.utility;

import com.klnon.recyclingservice.Config;
import com.klnon.recyclingservice.content.cleanup.CleanupManager;
import com.klnon.recyclingservice.content.trashbox.TrashBoxManager;
import com.klnon.recyclingservice.content.trashbox.data.TrashBox;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Map;

/**
 * 消息工具类 - 负责消息格式化和发送
 */
public class MessageHelper {

    // ==================== 常量 ====================

    /**
     * 警告消息颜色
     */
    public static final int COLOR_WARNING = 0xFFAA00;

    // ==================== 模板格式化 ====================

    /**
     * 字符串模板处理
     *
     * @param template 模板字符串，如 "Hello {name}!"
     * @param params   参数映射，如 {"name": "World"}
     * @return 格式化后的字符串
     */
    public static String formatTemplate(String template, Map<String, String> params) {
        String result = template;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    // ==================== 消息构建 ====================

    /**
     * 获取清理倒计时警告消息
     */
    public static String getWarningMessage(int remainingSeconds) {
        return formatTemplate(Config.MESSAGE.warningMessage.get(),
                Map.of("time", String.valueOf(remainingSeconds)));
    }

    /**
     * 构建详细清理完成消息（包含各维度统计和快捷按钮）
     */
    public static Component buildCleanupResultMessage(MinecraftServer server) {
        MutableComponent result = Component.literal(Config.MESSAGE.cleanupResultHeader.get());

        for (ServerLevel level : server.getAllLevels()) {
            ResourceLocation dimensionId = level.dimension().location();
            int itemCount = CleanupManager.getEntityCount(CleanupManager.ITEM, dimensionId);
            int projectileCount = CleanupManager.getEntityCount(CleanupManager.PROJECTILE, dimensionId);

            if (itemCount > 0 || projectileCount > 0) {
                Component entry = buildDimensionEntry(dimensionId, itemCount, projectileCount);
                result.append(Component.literal("\n")).append(entry);
            }
        }

        return result;
    }

    /**
     * 构建单个维度的清理条目（带可点击按钮）
     *
     * @param dimensionId     维度ID
     * @param itemCount       清理的物品数量
     * @param projectileCount 清理的弹射物数量
     */
    private static Component buildDimensionEntry(ResourceLocation dimensionId, int itemCount, int projectileCount) {
        String dimensionName = dimensionId.getPath();

        // 基础文本
        String baseText = formatTemplate(Config.MESSAGE.dimensionEntryFormat.get(), Map.of(
                "name", dimensionName,
                "items", String.valueOf(itemCount),
                "entities", String.valueOf(projectileCount)
        ));

        MutableComponent result = Component.literal(baseText);

        // 为每个非空垃圾箱生成按钮
        List<TrashBox> boxes = TrashBoxManager.getDimensionTrashBoxes(dimensionId);
        for (TrashBox box : boxes) {
            if (!box.isEmpty()) {
                result.append(Component.literal(" "));
                result.append(buildTrashBoxButton(dimensionId, dimensionName, TrashBoxManager.getBoxNumber(box)));
            }
        }

        // 如果没有非空垃圾箱，显示默认的#1按钮
        if (boxes.stream().allMatch(TrashBox::isEmpty)) {
            result.append(Component.literal(" "));
            result.append(buildTrashBoxButton(dimensionId, dimensionName, 1));
        }

        return result;
    }

    /**
     * 构建垃圾箱快捷按钮
     */
    private static Component buildTrashBoxButton(ResourceLocation dimensionId, String dimensionName, int boxNumber) {
        String buttonText = formatTemplate(Config.MESSAGE.trashBoxButtonText.get(),
                Map.of("name", dimensionName, "box", String.valueOf(boxNumber)));
        String hoverText = formatTemplate(Config.MESSAGE.trashBoxButtonHover.get(),
                Map.of("name", dimensionName, "box", String.valueOf(boxNumber)));

        return Component.literal(buttonText)
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.GREEN)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/bin open " + dimensionId + " " + boxNumber))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal(hoverText).withStyle(ChatFormatting.YELLOW))));
    }

    // ==================== 消息发送 ====================

    /**
     * 发送聊天消息给所有玩家
     */
    public static void sendToAll(MinecraftServer server, Component message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
        }
    }

    /**
     * 发送 ActionBar 消息给所有玩家
     */
    public static void sendActionBarToAll(MinecraftServer server, String message, int color) {
        Component component = Component.literal(message)
                .withStyle(style -> style.withColor(color));
        ClientboundSetActionBarTextPacket packet = new ClientboundSetActionBarTextPacket(component);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(packet);
        }
    }

    /**
     * 发送支付相关消息给玩家
     *
     * @param player   玩家
     * @param cost     费用数量
     * @param template 消息模板（需包含 {cost} 和 {item} 占位符）
     * @param color    消息颜色
     */
    public static void sendPaymentMessage(Player player, int cost, String template, ChatFormatting color) {
        String itemName = Config.getPaymentItem().getPath();
        String formatted = formatTemplate(template, Map.of(
                "cost", String.valueOf(cost),
                "item", itemName
        ));

        String prefix = Config.MESSAGE.messagePrefix.get();
        Component message = Component.literal(prefix + formatted)
                .withStyle(style -> style.withColor(color).withBold(true));

        player.displayClientMessage(message, false);
    }

    /**
     * 发送支付成功消息
     */
    public static void sendPaymentSuccess(Player player, int cost) {
        sendPaymentMessage(player, cost, Config.MESSAGE.paymentSuccessMessage.get(), ChatFormatting.GREEN);
    }

    /**
     * 发送支付失败消息
     */
    public static void sendPaymentError(Player player, int cost) {
        sendPaymentMessage(player, cost, Config.MESSAGE.paymentErrorMessage.get(), ChatFormatting.RED);
    }
}