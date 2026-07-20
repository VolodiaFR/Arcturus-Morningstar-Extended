package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CatalogSpecialAssetTransactionContractTest {
    private static String source(String relativePath) throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/" + relativePath))
                .replaceAll("\\s+", "");
    }

    @Test
    void specialAssetsAcceptTheOwningTransactionConnection() throws Exception {
        assertTrue(source("bots/BotManager.java")
                .contains("createBot(Connectionconnection,Map<String,String>data,Stringtype,intownerId)"));
        assertTrue(source("users/HabboBadge.java").contains("insert(Connectionconnection)"));
        assertTrue(source("pets/Pet.java").contains("save(Connectionconnection)"));
        assertTrue(source("pets/PetManager.java")
                .contains(
                        "createPet(Connectionconnection,Itemitem,Stringname,Stringrace,Stringcolor,GameClientclient)"));
        assertTrue(source("users/inventory/EffectsComponent.java")
                .contains("persistEffect(Connectionconnection,intuserId,inteffectId,intduration)"));
        assertTrue(source("guilds/GuildManager.java")
                .contains("persistGuild(Connectionconnection,intfurniId,intguildId)"));
    }

    @Test
    void connectionAwareMethodsPropagateSqlFailures() throws Exception {
        assertTrue(
                source("bots/BotManager.java")
                        .contains(
                                "createBot(Connectionconnection,Map<String,String>data,Stringtype,intownerId)throwsSQLException"));
        assertTrue(source("users/HabboBadge.java").contains("insert(Connectionconnection)throwsSQLException"));
        assertTrue(source("pets/Pet.java").contains("save(Connectionconnection)throwsSQLException"));
        assertTrue(source("users/inventory/EffectsComponent.java")
                .contains("persistEffect(Connectionconnection,intuserId,inteffectId,intduration)throwsSQLException"));
        assertTrue(source("guilds/GuildManager.java")
                .contains("persistGuild(Connectionconnection,intfurniId,intguildId)throwsSQLException"));
    }
}
