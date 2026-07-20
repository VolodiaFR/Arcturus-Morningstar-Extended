package com.eu.habbo.plugin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.catalog.CatalogManager;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessage;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.habbohotel.wired.core.WiredEngine;
import com.eu.habbo.messages.PacketManager;
import com.eu.habbo.messages.outgoing.catalog.DiscountComposer;
import com.eu.habbo.messages.outgoing.catalog.GiftConfigurationComposer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigurationPublicationContractTest {

    @Test
    void crossThreadConfigurationTargetsAreVolatile() throws Exception {
        List<Field> fields = List.of(
                Room.class.getField("HABBO_CHAT_DELAY"),
                RoomChatMessage.class.getField("BANNED_BUBBLES"),
                RoomLayout.class.getField("MAXIMUM_STEP_HEIGHT"),
                RoomManager.class.getField("SHOW_PUBLIC_IN_POPULAR_TAB"),
                WiredEngine.class.getField("MAX_RECURSION_DEPTH"),
                PacketManager.class.getField("DEBUG_SHOW_PACKETS"),
                CatalogManager.class.getField("PURCHASE_COOLDOWN"),
                HabboManager.class.getField("WELCOME_MESSAGE"),
                DiscountComposer.class.getField("ADDITIONAL_DISCOUNT_THRESHOLDS"),
                GiftConfigurationComposer.class.getField("BOX_TYPES"),
                Bot.class.getField("PLACEMENT_MESSAGES"));

        for (Field field : fields) {
            assertTrue(
                    Modifier.isVolatile(field.getModifiers()),
                    () -> field.getDeclaringClass().getSimpleName()
                            + "."
                            + field.getName()
                            + " must publish reloads through a volatile field");
        }
    }
}
