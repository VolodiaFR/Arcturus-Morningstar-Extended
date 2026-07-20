package com.eu.habbo.messages.incoming.inventory.prefixes;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.customization.UserCustomizationPurchaseService;
import com.eu.habbo.habbohotel.users.customization.UserCustomizationPurchaseService.PurchaseResult;
import com.eu.habbo.habbohotel.users.customization.UserCustomizationRepository;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.inventory.nickicons.UserNickIconsComposer;
import com.eu.habbo.messages.outgoing.users.UserCurrencyComposer;
import java.sql.SQLException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PurchaseCatalogPrefixEvent extends MessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PurchaseCatalogPrefixEvent.class);

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int catalogPrefixId = this.packet.readInt();
        Habbo habbo = this.client.getHabbo();
        if (habbo == null || catalogPrefixId <= 0) {
            return;
        }

        UserCustomizationPurchaseService service = new UserCustomizationPurchaseService(
                new UserCustomizationRepository(Emulator.getDatabase().getDataSource()), List.of());
        try {
            PurchaseResult result = service.purchaseCatalogPrefix(habbo, catalogPrefixId);
            switch (result.status()) {
                case UNAVAILABLE -> {
                    return;
                }
                case FAILURE -> {
                    this.client.sendResponse(
                            new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, result.message()));
                    return;
                }
                case SUCCESS -> {
                    if (result.currencyChanged()) {
                        this.client.sendResponse(new UserCurrencyComposer(habbo));
                    }
                    result.prefix().run();
                    habbo.getInventory().getPrefixesComponent().addPrefix(result.prefix());
                }
                case REFRESH -> {
                    // The established response is the same inventory refresh
                    // used after a successful purchase.
                }
            }
            this.client.sendResponse(new UserNickIconsComposer(habbo));
        } catch (SQLException exception) {
            LOGGER.error("Caught SQL exception while purchasing catalog prefix", exception);
        }
    }
}
