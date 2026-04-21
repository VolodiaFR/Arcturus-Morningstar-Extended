package com.eu.habbo.messages.incoming.translation;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.translations.GoogleTranslateManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.translation.TranslationResultComposer;

public class TranslationTextRequestEvent extends MessageHandler {
    @Override
    public void handle() {
        final GameClient client = this.client;
        final int requestId = this.packet.readInt();
        final String text = this.packet.readString();
        final String targetLanguage = this.packet.readString();

        Emulator.getThreading().run(() -> {
            GoogleTranslateManager.TranslationResponse response = Emulator.getGameEnvironment()
                    .getGoogleTranslateManager()
                    .translate(text, targetLanguage);

            client.sendResponse(new TranslationResultComposer(requestId, response).compose());
        });
    }
}
