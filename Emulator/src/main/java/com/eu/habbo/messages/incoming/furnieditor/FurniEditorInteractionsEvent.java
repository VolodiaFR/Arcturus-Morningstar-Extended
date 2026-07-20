package com.eu.habbo.messages.incoming.furnieditor;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.editor.FurniEditorRepository;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.furnieditor.FurniEditorInteractionsComposer;
import com.eu.habbo.messages.outgoing.furnieditor.FurniEditorResultComposer;
import java.util.List;

public class FurniEditorInteractionsEvent extends MessageHandler {

    private static List<String> cachedInteractions = null;

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_CATALOGFURNI)) {
            this.client.sendResponse(new FurniEditorResultComposer(false, "No permission"));
            return;
        }

        if (cachedInteractions == null) {
            synchronized (FurniEditorInteractionsEvent.class) {
                if (cachedInteractions == null) {
                    cachedInteractions =
                            new FurniEditorRepository(Emulator.getDatabase().getDataSource()).findInteractionTypes();
                }
            }
        }

        this.client.sendResponse(new FurniEditorInteractionsComposer(cachedInteractions));
    }
}
