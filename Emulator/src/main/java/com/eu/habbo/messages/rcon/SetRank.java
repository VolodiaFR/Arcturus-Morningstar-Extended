package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.google.gson.Gson;
import jakarta.validation.constraints.Positive;

public class SetRank extends RCONMessage<SetRank.JSONSetRank> {

    public SetRank() {
        super(JSONSetRank.class);
    }

    @Override
    public void handle(Gson gson, JSONSetRank object) {
        try {
            Emulator.getGameEnvironment().getHabboManager().setRank(object.user_id, object.rank);
        } catch (Exception e) {
            this.status = RCONMessage.SYSTEM_ERROR;
            this.message = "invalid rank";
            return;
        }

        this.message = "updated offline user";

        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(object.user_id);

        if (habbo != null) {
            this.message = "updated online user";
        }
    }

    static class JSONSetRank {

        @Positive(message = "invalid user")
        public int user_id;


        @Positive(message = "invalid rank")
        public int rank;
    }
}
