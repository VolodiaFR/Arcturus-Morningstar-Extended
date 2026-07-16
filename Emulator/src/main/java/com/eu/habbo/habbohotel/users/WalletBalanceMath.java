package com.eu.habbo.habbohotel.users;

/**
 * Checked arithmetic for balances persisted in signed INT columns.
 */
public final class WalletBalanceMath {
    private WalletBalanceMath() {
    }

    public static int checkedBalance(int currentBalance, int delta) {
        if (currentBalance < 0) {
            throw new IllegalArgumentException("current balance must not be negative");
        }

        long updated = (long) currentBalance + delta;
        if (updated < 0 || updated > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("wallet update exceeds the supported balance range");
        }

        return (int) updated;
    }

    public static int requireValidBalance(int balance) {
        if (balance < 0) {
            throw new IllegalArgumentException("balance must not be negative");
        }
        return balance;
    }
}
