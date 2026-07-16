package com.eu.habbo.messages.incoming;

/**
 * Validates packet-supplied counts before allocation or repeated reads.
 */
public final class PacketListGuard {
    private static final int INTEGER_BYTES = Integer.BYTES;

    private PacketListGuard() {
    }

    public static boolean isValidIntList(int count, int bytesAvailable, int maxCount) {
        if (count < 0 || count > maxCount || bytesAvailable < 0) {
            return false;
        }

        return (long) count * INTEGER_BYTES <= bytesAvailable;
    }
}
