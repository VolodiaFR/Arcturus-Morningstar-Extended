package com.eu.habbo.messages.incoming.inventory.prefixes;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.customization.UserCustomizationPurchaseService;
import com.eu.habbo.habbohotel.users.customization.UserCustomizationPurchaseService.CustomPrefixRequest;
import com.eu.habbo.habbohotel.users.customization.UserCustomizationPurchaseService.PurchaseResult;
import com.eu.habbo.habbohotel.users.customization.UserCustomizationRepository;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.inventory.nickicons.UserNickIconsComposer;
import com.eu.habbo.messages.outgoing.inventory.prefixes.ActivePrefixUpdatedComposer;
import com.eu.habbo.messages.outgoing.inventory.prefixes.CustomPrefixPurchaseFailedComposer;
import com.eu.habbo.messages.outgoing.inventory.prefixes.PrefixReceivedComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDataComposer;
import com.eu.habbo.messages.outgoing.users.UserCreditsComposer;
import com.eu.habbo.messages.outgoing.users.UserCurrencyComposer;

public class PurchasePrefixEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        CustomPrefixRequest request = new CustomPrefixRequest(
                this.packet.readString(),
                this.packet.readString(),
                this.packet.readString(),
                this.packet.readString(),
                this.packet.readString());
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) {
            return;
        }

        UserCustomizationPurchaseService service = new UserCustomizationPurchaseService(
                new UserCustomizationRepository(Emulator.getDatabase().getDataSource()),
                Emulator.getGameEnvironment().getWordFilter().getWords());
        PurchaseResult result = service.purchaseCustomPrefix(habbo, request);
        if (result.status() != UserCustomizationPurchaseService.Status.SUCCESS) {
            this.fail(result.message());
            return;
        }

        if (result.creditsChanged()) {
            this.client.sendResponse(new UserCreditsComposer(habbo));
        }
        if (result.currencyChanged()) {
            this.client.sendResponse(new UserCurrencyComposer(habbo));
        }
        result.prefix().run();
        habbo.getInventory().getPrefixesComponent().addPrefix(result.prefix());
        habbo.getInventory().getPrefixesComponent().setActive(result.prefix().getId());
        this.client.sendResponse(new PrefixReceivedComposer(result.prefix()));
        this.client.sendResponse(new ActivePrefixUpdatedComposer(result.prefix()));
        this.client.sendResponse(new UserNickIconsComposer(habbo));
        if (habbo.getHabboInfo().getCurrentRoom() != null) {
            habbo.getHabboInfo().getCurrentRoom().sendComposer(new RoomUserDataComposer(habbo).compose());
        }
    }

    private void fail(String message) {
        this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, message));
        this.client.sendResponse(new CustomPrefixPurchaseFailedComposer(message));
    }

    private boolean containsControlChars(String text) {
        return UserCustomizationPurchaseService.containsControlChars(text);
    }

    private boolean isAllowedFont(String font) {
        return UserCustomizationPurchaseService.isAllowedFont(font);
    }

    private boolean isAllowedEffect(String effect) {
        return UserCustomizationPurchaseService.isAllowedEffect(effect);
    }

    private boolean isValidIcon(String icon) {
        return UserCustomizationPurchaseService.isValidIcon(icon);
    }
}
