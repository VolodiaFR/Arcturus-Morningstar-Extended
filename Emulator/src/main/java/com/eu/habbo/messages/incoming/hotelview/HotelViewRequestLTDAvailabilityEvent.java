package com.eu.habbo.messages.incoming.hotelview;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.hotelview.HotelViewNextLTDAvailableComposer;

public class HotelViewRequestLTDAvailabilityEvent extends MessageHandler {
    public static volatile boolean ENABLED = false;
    public static volatile int TIMESTAMP;
    public static volatile int ITEM_ID;
    public static volatile int PAGE_ID;
    public static volatile String ITEM_NAME;

    @Override
    public void handle() throws Exception {
        if (ENABLED) {
            int timeremaining = Math.max(TIMESTAMP - Emulator.getIntUnixTimestamp(), -1);
            this.client.sendResponse(new HotelViewNextLTDAvailableComposer(
                    timeremaining,
                    timeremaining > 0 ? -1 : ITEM_ID,
                    timeremaining > 0 ? -1 : PAGE_ID,
                    timeremaining > 0 ? "" : ITEM_NAME));
        }
    }
}
