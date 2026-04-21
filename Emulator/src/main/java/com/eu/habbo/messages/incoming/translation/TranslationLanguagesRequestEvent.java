package com.eu.habbo.messages.incoming.translation;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.translations.GoogleTranslateManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.translation.TranslationLanguagesComposer;

public class TranslationLanguagesRequestEvent extends MessageHandler {
    @Override
    public void handle() {
        final GameClient client = this.client;
        final String displayLanguage = this.packet.readString();

        Emulator.getThreading().run(() -> {
            GoogleTranslateManager.SupportedLanguagesResponse response = Emulator.getGameEnvironment()
                    .getGoogleTranslateManager()
                    .getSupportedLanguages(displayLanguage);

            client.sendResponse(new TranslationLanguagesComposer(response).compose());
        });
    }

    @Override
    public int getRatelimit() {
        return 250;
    }
}
