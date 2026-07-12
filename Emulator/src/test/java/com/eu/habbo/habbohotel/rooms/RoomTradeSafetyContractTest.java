package com.eu.habbo.habbohotel.rooms;

import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomTradeSafetyContractTest {
    private static String roomTradeSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomTrade.java"));
    }

    private static String transactionSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomTradeTransaction.java"));
    }

    @Test
    void sqlFailureStopsBeforeInventoryTransfer() throws Exception {
        String source = roomTradeSource();
        int transaction = source.indexOf("RoomTradeTransaction.execute(");
        int catchIndex = source.indexOf("catch (SQLException e)", transaction);
        int inventoryTransferIndex = source.indexOf("Set<HabboItem> itemsUserOne");

        assertTrue(transaction > -1, "RoomTrade must delegate persistence to its transaction boundary");
        assertTrue(catchIndex > -1, "RoomTrade must handle SQL failures explicitly");
        assertTrue(inventoryTransferIndex < transaction, "Inventory snapshots may be prepared before persistence");
        int firstInventoryMutation = source.indexOf("userOne.clearItems()", transaction);
        assertTrue(firstInventoryMutation > catchIndex, "Inventory mutation should happen after SQL ownership updates");
        assertTrue(source.substring(catchIndex, firstInventoryMutation).contains("return false"),
                "SQL failures must abort the trade before in-memory inventory/credit transfer");
    }

    @Test
    void itemOwnersChangeOnlyAfterDatabaseBatchSucceeds() throws Exception {
        String source = roomTradeSource();
        String transaction = transactionSource();
        int firstOwnerMutation = source.indexOf("item.setUserId(");
        int transactionExecution = source.indexOf("RoomTradeTransaction.execute(");
        int commit = transaction.indexOf("connection.commit()");

        assertTrue(firstOwnerMutation > -1, "RoomTrade should update in-memory item owners after commit");
        assertTrue(transactionExecution > -1, "RoomTrade should persist changes through RoomTradeTransaction");
        assertTrue(commit > -1, "RoomTradeTransaction must commit its database mutations");
        assertTrue(firstOwnerMutation > transactionExecution,
                "In-memory item owners must not change until the transaction has succeeded");
    }

    @Test
    void ownershipUpdatesRequireExpectedDatabaseOwner() throws Exception {
        String source = transactionSource();

        assertTrue(source.contains("UPDATE items SET user_id = ? WHERE id = ? AND user_id = ? LIMIT 1"),
                "RoomTrade ownership transfer should only update items still owned by the offering user");
        assertTrue(source.contains("statement.setInt(3, sourceUserId)"),
                "Every offered item must require its source user as current database owner");
    }

    @Test
    void zeroBatchUpdatesAbortTheTrade() {
        assertTrue(RoomTrade.allOwnershipUpdatesSucceeded(new int[]{1, Statement.SUCCESS_NO_INFO}, 2));
        assertTrue(!RoomTrade.allOwnershipUpdatesSucceeded(new int[]{1, 0}, 2));
        assertTrue(!RoomTrade.allOwnershipUpdatesSucceeded(new int[]{1}, 2));
        assertTrue(!RoomTrade.allOwnershipUpdatesSucceeded(new int[]{Statement.EXECUTE_FAILED}, 1));
    }
}
