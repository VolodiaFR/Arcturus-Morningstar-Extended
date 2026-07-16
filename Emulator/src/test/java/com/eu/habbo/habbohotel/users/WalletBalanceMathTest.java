package com.eu.habbo.habbohotel.users;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WalletBalanceMathTest {
    @Test
    void acceptsRepresentableDepositsAndDebits() {
        assertEquals(150, WalletBalanceMath.checkedBalance(100, 50));
        assertEquals(25, WalletBalanceMath.checkedBalance(100, -75));
    }

    @Test
    void rejectsIntegerOverflowInsteadOfWrappingNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> WalletBalanceMath.checkedBalance(Integer.MAX_VALUE, 1));
    }

    @Test
    void rejectsOverdraftInsteadOfPersistingNegativeBalance() {
        assertThrows(IllegalArgumentException.class,
                () -> WalletBalanceMath.checkedBalance(100, -101));
    }

    @Test
    void rejectsAlreadyCorruptNegativeBalances() {
        assertThrows(IllegalArgumentException.class,
                () -> WalletBalanceMath.checkedBalance(-1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> WalletBalanceMath.requireValidBalance(-1));
    }
}
