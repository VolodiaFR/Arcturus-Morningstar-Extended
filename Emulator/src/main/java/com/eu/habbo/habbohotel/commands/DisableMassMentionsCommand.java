package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.users.Habbo;

public class DisableMassMentionsCommand extends Command {
    public DisableMassMentionsCommand() {
        super("cmd_disablemassmentions", new String[]{"disablemassmentions", "togglemassmentions"});
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (gameClient == null) return true;
        Habbo habbo = gameClient.getHabbo();
        if (habbo == null || habbo.getHabboStats() == null) return true;

        boolean newState = !habbo.getHabboStats().massMentionsEnabled();
        habbo.getHabboStats().setMassMentionsEnabled(newState);

        habbo.whisper(newState
                ? "Broadcast mentions (@all / @friends / @room) are now ENABLED for you."
                : "Broadcast mentions (@all / @friends / @room) are now DISABLED for you. Direct @nick mentions still work.");
        return true;
    }
}
