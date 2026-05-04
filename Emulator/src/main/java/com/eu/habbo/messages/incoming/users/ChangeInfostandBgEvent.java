package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDataComposer;

public class ChangeInfostandBgEvent extends MessageHandler {
    private static final String COOLDOWN_KEY = "infostand_bg_cooldown";
    private static final long COOLDOWN_MS = 500L;
    private static final int MIN_ID = 0;
    private static final int MAX_ID = 9999;

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        HabboInfo info = habbo.getHabboInfo();
        if (info == null) return;

        HabboStats stats = habbo.getHabboStats();
        if (stats != null) {
            long now = System.currentTimeMillis();
            Object last = stats.cache.get(COOLDOWN_KEY);
            if (last instanceof Long && (now - (Long) last) < COOLDOWN_MS) {
                return;
            }
            stats.cache.put(COOLDOWN_KEY, now);
        }

        int backgroundImage = sanitize(this.packet.readInt());
        int backgroundStand = sanitize(this.packet.readInt());
        int backgroundOverlay = sanitize(this.packet.readInt());
        int backgroundCard = this.packet.bytesAvailable() >= 4 ? sanitize(this.packet.readInt()) : 0;

        if (info.getInfostandBg() == backgroundImage
                && info.getInfostandStand() == backgroundStand
                && info.getInfostandOverlay() == backgroundOverlay
                && info.getInfostandCardBg() == backgroundCard) {
            return;
        }

        info.setInfostandBg(backgroundImage);
        info.setInfostandStand(backgroundStand);
        info.setInfostandOverlay(backgroundOverlay);
        info.setInfostandCardBg(backgroundCard);
        info.run();

        if (info.getCurrentRoom() != null) {
            info.getCurrentRoom().sendComposer(new RoomUserDataComposer(habbo).compose());
        } else {
            this.client.sendResponse(new RoomUserDataComposer(habbo));
        }
    }

    private static int sanitize(int value) {
        if (value < MIN_ID || value > MAX_ID) return 0;
        return value;
    }
}
