package com.eu.habbo.messages.outgoing.inventory.prefixes;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class CustomPrefixPurchaseFailedComposer extends MessageComposer {
    private final String message;

    public CustomPrefixPurchaseFailedComposer(String message) {
        this.message = message != null ? message : "";
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CustomPrefixPurchaseFailedComposer);
        this.response.appendString(this.message);
        return this.response;
    }
}
