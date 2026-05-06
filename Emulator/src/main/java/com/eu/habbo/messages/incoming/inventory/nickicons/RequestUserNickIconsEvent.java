package com.eu.habbo.messages.incoming.inventory.nickicons;

import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.inventory.nickicons.UserNickIconsComposer;

public class RequestUserNickIconsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new UserNickIconsComposer(this.client.getHabbo()));
    }
}
