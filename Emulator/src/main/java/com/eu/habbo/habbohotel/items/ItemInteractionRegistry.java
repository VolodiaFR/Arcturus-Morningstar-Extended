package com.eu.habbo.habbohotel.items;

import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ItemInteractionRegistry {
    private final Set<ItemInteraction> interactions = new HashSet<>();

    boolean add(ItemInteraction interaction) {
        return interactions.add(interaction);
    }

    void addChecked(ItemInteraction itemInteraction) {
        for (ItemInteraction interaction : interactions) {
            if (interaction.getType() == itemInteraction.getType()
                    || interaction.getName().equalsIgnoreCase(itemInteraction.getName())) {
                throw new RuntimeException("Interaction Types must be unique. An class with type: "
                        + interaction.getClass().getName() + " was already added OR the key: "
                        + interaction.getName() + " is already in use.");
            }
        }

        interactions.add(itemInteraction);
    }

    ItemInteraction find(Class<? extends HabboItem> type) {
        for (ItemInteraction interaction : interactions) {
            if (interaction.getType() == type) {
                return interaction;
            }
        }
        return null;
    }

    ItemInteraction find(String type) {
        for (ItemInteraction interaction : interactions) {
            if (interaction.getName().equalsIgnoreCase(type)) {
                return interaction;
            }
        }
        return null;
    }

    List<String> sortedNames() {
        List<String> names = new ArrayList<>();
        for (ItemInteraction interaction : interactions) {
            names.add(interaction.getName());
        }
        Collections.sort(names);
        return names;
    }
}
