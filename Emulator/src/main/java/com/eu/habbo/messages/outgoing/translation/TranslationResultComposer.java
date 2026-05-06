package com.eu.habbo.messages.outgoing.translation;

import com.eu.habbo.habbohotel.translations.GoogleTranslateManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class TranslationResultComposer extends MessageComposer {
    private final int requestId;
    private final GoogleTranslateManager.TranslationResponse responseData;

    public TranslationResultComposer(int requestId, GoogleTranslateManager.TranslationResponse responseData) {
        this.requestId = requestId;
        this.responseData = responseData;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.TranslationResultComposer);
        this.response.appendInt(this.requestId);
        this.response.appendBoolean(this.responseData != null && this.responseData.isSuccess());
        this.response.appendString(this.responseData != null ? this.responseData.getErrorMessage() : "Unknown error");
        this.response.appendString(this.responseData != null ? this.responseData.getOriginalText() : "");
        this.response.appendString(this.responseData != null ? this.responseData.getTranslatedText() : "");
        this.response.appendString(this.responseData != null ? this.responseData.getDetectedLanguage() : "");
        this.response.appendString(this.responseData != null ? this.responseData.getTargetLanguage() : "");
        return this.response;
    }
}
