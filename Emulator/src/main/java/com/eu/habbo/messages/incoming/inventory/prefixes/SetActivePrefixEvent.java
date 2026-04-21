package com.eu.habbo.messages.incoming.inventory.prefixes;

import com.eu.habbo.habbohotel.users.UserPrefix;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.inventory.prefixes.ActivePrefixUpdatedComposer;
import com.eu.habbo.messages.outgoing.inventory.nickicons.UserNickIconsComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDataComposer;

public class SetActivePrefixEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int prefixId = this.packet.readInt();

        if (prefixId == 0) {
            this.client.getHabbo().getInventory().getPrefixesComponent().deactivateAll();
            this.client.sendResponse(new ActivePrefixUpdatedComposer(null));
            this.client.sendResponse(new UserNickIconsComposer(this.client.getHabbo()));

            if (this.client.getHabbo().getHabboInfo().getCurrentRoom() != null) {
                this.client.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new RoomUserDataComposer(this.client.getHabbo()).compose());
            }
            return;
        }

        UserPrefix prefix = this.client.getHabbo().getInventory().getPrefixesComponent().getPrefix(prefixId);

        if (prefix == null) return;

        this.client.getHabbo().getInventory().getPrefixesComponent().setActive(prefixId);
        this.client.sendResponse(new ActivePrefixUpdatedComposer(prefix));
        this.client.sendResponse(new UserNickIconsComposer(this.client.getHabbo()));

        if (this.client.getHabbo().getHabboInfo().getCurrentRoom() != null) {
            this.client.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new RoomUserDataComposer(this.client.getHabbo()).compose());
        }
    }
}
