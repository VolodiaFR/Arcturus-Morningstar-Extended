package com.eu.habbo.messages.incoming.inventory.prefixes;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.UserPrefix;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.inventory.nickicons.UserNickIconsComposer;
import com.eu.habbo.messages.outgoing.inventory.prefixes.PrefixReceivedComposer;
import com.eu.habbo.messages.outgoing.users.UserCreditsComposer;
import com.eu.habbo.messages.outgoing.users.UserCurrencyComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class PurchasePrefixEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(PurchasePrefixEvent.class);
    private static final String[] ALLOWED_FONTS = { "", "pixel", "cherry", "vampiro" };

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        String text = this.packet.readString();
        String color = this.packet.readString();
        String icon = this.packet.readString();
        String effect = this.packet.readString();
        String font = this.packet.readString();

        Habbo habbo = this.client.getHabbo();

        if (habbo == null) return;

        // Load settings
        int maxLength = getSettingInt("max_length", 15);
        int minRank = getSettingInt("min_rank_to_buy", 1);
        int priceCredits = getSettingInt("price_credits", 5);
        int pricePoints = getSettingInt("price_points", 0);
        int pointsType = getSettingInt("points_type", 0);
        int fontPriceCredits = getSettingInt("font_price_credits", 10);
        int fontPricePoints = getSettingInt("font_price_points", 0);
        int fontPointsType = getSettingInt("font_points_type", pointsType);

        // Validate text
        text = text.trim();

        if (text.isEmpty() || text.length() > maxLength) {
            this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, "Prefix text is invalid or too long (max " + maxLength + " characters)."));
            return;
        }

        // Validate color (single hex or comma-separated multi hex for per-letter colors)
        String[] colorParts = color.split(",");
        for (String part : colorParts) {
            if (!part.matches("^#[0-9A-Fa-f]{6}$")) {
                this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, "Invalid color format."));
                return;
            }
        }

        // Check rank
        if (habbo.getHabboInfo().getRank().getId() < minRank) {
            this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, "Your rank is too low to purchase prefixes."));
            return;
        }

        // Check blacklist
        if (isBlacklisted(text)) {
            this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, "This prefix contains a blocked word."));
            return;
        }

        if (icon == null) icon = "";
        icon = icon.trim();

        if (effect == null) effect = "";
        effect = effect.trim();

        if (font == null) font = "";
        font = font.trim().toLowerCase();

        if (!isAllowedFont(font)) {
            this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, "Invalid font format."));
            return;
        }

        int totalPriceCredits = priceCredits + (!font.isEmpty() ? fontPriceCredits : 0);

        // Check credits
        if (totalPriceCredits > 0 && habbo.getHabboInfo().getCredits() < totalPriceCredits) {
            this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, "Not enough credits."));
            return;
        }

        int totalPricePointsSameType = pricePoints + ((fontPricePoints > 0 && fontPointsType == pointsType && !font.isEmpty()) ? fontPricePoints : 0);

        // Check points
        if (totalPricePointsSameType > 0 && habbo.getHabboInfo().getCurrencyAmount(pointsType) < totalPricePointsSameType) {
            this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, "Not enough points."));
            return;
        }

        if (!font.isEmpty() && fontPricePoints > 0 && fontPointsType != pointsType && habbo.getHabboInfo().getCurrencyAmount(fontPointsType) < fontPricePoints) {
            this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, "Not enough points."));
            return;
        }

        // Deduct currency
        if (totalPriceCredits > 0) {
            habbo.getHabboInfo().addCredits(-totalPriceCredits);
            this.client.sendResponse(new UserCreditsComposer(habbo));
        }

        if (totalPricePointsSameType > 0) {
            habbo.getHabboInfo().addCurrencyAmount(pointsType, -totalPricePointsSameType);
            this.client.sendResponse(new UserCurrencyComposer(habbo));
        }

        if (!font.isEmpty() && fontPricePoints > 0 && fontPointsType != pointsType) {
            habbo.getHabboInfo().addCurrencyAmount(fontPointsType, -fontPricePoints);
            this.client.sendResponse(new UserCurrencyComposer(habbo));
        }

        // Create prefix
        int storedPoints = totalPricePointsSameType;
        int storedPointsType = (storedPoints > 0) ? pointsType : ((!font.isEmpty() && fontPricePoints > 0) ? fontPointsType : pointsType);

        UserPrefix prefix = new UserPrefix(habbo.getHabboInfo().getId(), text, color, icon, effect, font, 0, text, storedPoints, storedPointsType, true);
        prefix.run(); // Insert into DB synchronously to get the ID
        habbo.getInventory().getPrefixesComponent().addPrefix(prefix);

        this.client.sendResponse(new PrefixReceivedComposer(prefix));
        this.client.sendResponse(new UserNickIconsComposer(habbo));
    }

    private int getSettingInt(String key, int defaultValue) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT `value` FROM custom_prefix_settings WHERE key_name = ?")) {
            statement.setString(1, key);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    return Integer.parseInt(set.getString("value"));
                }
            }
        } catch (SQLException | NumberFormatException e) {
            LOGGER.error("Error reading prefix setting: " + key, e);
        }
        return defaultValue;
    }

    private boolean isBlacklisted(String text) {
        String lowerText = text.toLowerCase();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT word FROM custom_prefix_blacklist")) {
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    if (lowerText.contains(set.getString("word").toLowerCase())) {
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Error checking prefix blacklist", e);
        }
        return false;
    }

    private boolean isAllowedFont(String font) {
        for (String allowedFont : ALLOWED_FONTS) {
            if (allowedFont.equals(font)) {
                return true;
            }
        }

        return false;
    }
}
