package com.eu.habbo.plugin;

import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.core.config.ConfigurationBinder;
import com.eu.habbo.habbohotel.messenger.Messenger;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.messages.PacketManager;
import com.eu.habbo.messages.incoming.users.ChangeNameCheckUsernameEvent;

final class NetworkConfigurationBinder extends ConfigurationBinder {

    NetworkConfigurationBinder(ConfigurationManager configuration) {
        super(configuration);
    }

    void bind() {
        this.apply(
                "save.private.chats",
                () -> Messenger.SAVE_PRIVATE_CHATS = this.configuration.getBoolean("save.private.chats", false));
        this.apply(
                "hotel.users.max.friends",
                () -> Messenger.MAXIMUM_FRIENDS = this.configuration.getInt("hotel.users.max.friends", 300));
        this.apply(
                "hotel.users.max.friends.hc",
                () -> Messenger.MAXIMUM_FRIENDS_HC = this.configuration.getInt("hotel.users.max.friends.hc", 1100));
        this.apply(
                "debug.show.packets",
                () -> PacketManager.DEBUG_SHOW_PACKETS = this.configuration.getBoolean("debug.show.packets"));
        this.apply(
                "io.client.multithreaded.handler",
                () -> PacketManager.MULTI_THREADED_PACKET_HANDLING =
                        this.configuration.getBoolean("io.client.multithreaded.handler"));
        this.apply(
                "hotel.welcome.alert.message",
                () -> HabboManager.WELCOME_MESSAGE = this.configuration
                        .getValue("hotel.welcome.alert.message")
                        .replace("<br>", "<br/>")
                        .replace("<br />", "<br/>")
                        .replace("\\r", "\r")
                        .replace("\\n", "\n")
                        .replace("\\t", "\t"));
        this.apply(
                "allowed.username.characters",
                () -> ChangeNameCheckUsernameEvent.VALID_CHARACTERS = this.configuration.getValue(
                        "allowed.username.characters",
                        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890_-=!?@:,."));
    }
}
