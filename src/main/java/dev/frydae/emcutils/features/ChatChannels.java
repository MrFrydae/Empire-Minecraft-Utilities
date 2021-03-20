package dev.frydae.emcutils.features;

import dev.frydae.emcutils.utils.Config;
import dev.frydae.emcutils.utils.Util;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

public class ChatChannels {
    @Setter private static ChatChannel currentChannel = null;
    @Setter private static boolean inPrivateConversation = false;
    @Setter private static String targetUsername = null;
    @Setter private static int targetGroupId = 0;
    private static long lastClickedButtonTime = 0L;

    public static void handleChatScreenRender(Screen screen, MatrixStack matrices, CallbackInfo info) {
        if (Util.IS_ON_EMC) {
            for (ChatChannel channel : ChatChannel.values()) {
                if (channel == ChatChannel.SUPPORTER && Util.getPlayerGroupId() < 2) break;
                if (channel == ChatChannel.MODERATOR && Util.getPlayerGroupId() < 5) break;
                drawButton(screen, matrices, channel);
            }

            if (inPrivateConversation) {
                drawPrivateConversation(screen, matrices);
            }
        }
    }

    public static void handleChatScreenMouseClicked(Screen screen, double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (Util.IS_ON_EMC) {
            for (ChatChannel channel : ChatChannel.values()) {
                if (channel == ChatChannel.SUPPORTER && Util.getPlayerGroupId() < 2) break;
                if (channel == ChatChannel.MODERATOR && Util.getPlayerGroupId() < 5) break;

                if (isInBounds(screen, channel.name, channel.getOffset(), mouseX, mouseY) && (System.currentTimeMillis() - lastClickedButtonTime) >= 1000L && currentChannel != channel) {
                    lastClickedButtonTime = System.currentTimeMillis();
                    currentChannel = channel;
                    channel.executeCommand();

                    MinecraftClient.getInstance().player.playSound(Config.getInstance().getChatAlertSound().getSoundEvent(), 5, Config.getInstance().getChatAlertPitch());

                    // Cancel private conversation if in one
                    inPrivateConversation = false;

                    return;
                }
            }
        }
    }

    private static boolean isInBounds(Screen screen, String text, int offset, double mouseX, double mouseY) {
        int width = MinecraftClient.getInstance().textRenderer.getWidth(text);
        int height = MinecraftClient.getInstance().textRenderer.fontHeight;

        // Check X coordinate
        if (mouseX < offset + 1 || mouseX >= offset + width) {
            return false;
        }

        // Check Y coordinate
        if (mouseY < screen.height - 32 || mouseY >= screen.height - (32 - height - 4)) {
            return false;
        }

        return true;
    }

    private static void drawButton(Screen screen, MatrixStack matrices, ChatChannel channel) {
        int width = MinecraftClient.getInstance().textRenderer.getWidth(channel.name);
        int height = MinecraftClient.getInstance().textRenderer.fontHeight;

        if (currentChannel == channel && !inPrivateConversation) {
            DrawableHelper.fill(matrices, channel.getOffset(), screen.height - 33, channel.getOffset() + width + 5, screen.height - (32 - height - 4), (0xff << 24) | channel.format.getColorValue());
        }

        DrawableHelper.fill(matrices, channel.getOffset() + 1, screen.height - 32, channel.getOffset() + width + 4, screen.height - (32 - height - 3), (0xc0 << 24));
        MinecraftClient.getInstance().textRenderer.draw(matrices, new LiteralText(channel.name), channel.getOffset() + 3, screen.height - 30, channel.format.getColorValue());
    }

    private static void drawPrivateConversation(Screen screen, MatrixStack matrices) {
        int fullWidth = MinecraftClient.getInstance().textRenderer.getWidth("PM with: " + targetUsername);
        int nameWidth = MinecraftClient.getInstance().textRenderer.getWidth(targetUsername);
        int height = MinecraftClient.getInstance().textRenderer.fontHeight;

        DrawableHelper.fill(matrices, screen.width - 3, screen.height - 33, screen.width - fullWidth - 8, screen.height - (32 - height - 4), (0xff << 24) | Formatting.LIGHT_PURPLE.getColorValue());
        DrawableHelper.fill(matrices, screen.width - 4, screen.height - 32, screen.width - fullWidth - 7, screen.height - (32 - height - 3), (0xc0 << 24));
        MinecraftClient.getInstance().textRenderer.draw(matrices, new LiteralText("PM with: "), screen.width - fullWidth - 5, screen.height - 30, Formatting.WHITE.getColorValue());
        MinecraftClient.getInstance().textRenderer.draw(matrices, new LiteralText(targetUsername), screen.width - nameWidth - 5, screen.height - 30, Util.groupIdToFormatting(targetGroupId).getColorValue());
    }

    public static void processGameJoin(GameJoinS2CPacket packet, CallbackInfo info) {
        inPrivateConversation = false;
    }

    @AllArgsConstructor
    public enum ChatChannel {
        COMMUNITY("Community", "/cc", Formatting.DARK_GREEN, null),
        MARKET("Market", "/cm", Formatting.GOLD, COMMUNITY),
        SERVER("Server", "/cs", Formatting.RED, MARKET),
        LOCAL("Local", "/cl", Formatting.YELLOW, SERVER),
        RESIDENCE("Residence", "/cr", Formatting.BLUE, LOCAL),
        GROUP("Group", "/cg", Formatting.DARK_AQUA, RESIDENCE),
        SUPPORTER("Supporter", "/csup", Formatting.AQUA, GROUP),
        MODERATOR("Moderator", "/cx", Formatting.LIGHT_PURPLE, SUPPORTER)
        ;

        @Getter private final String name;
        @Getter private final String command;
        @Getter private final Formatting format;
        private final ChatChannel adjustAgainst;

        public static ChatChannel getChannelByName(String name) {
            return Arrays.stream(values()).filter(value -> value.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
        }

        public int getOffset() {
            if (adjustAgainst == null) {
                return 2;
            }

            return adjustAgainst.getOffset() + MinecraftClient.getInstance().textRenderer.getWidth(adjustAgainst.name) + 6;
        }

        public void executeCommand() {
            MinecraftClient.getInstance().player.sendChatMessage(command);
        }
    }
}
