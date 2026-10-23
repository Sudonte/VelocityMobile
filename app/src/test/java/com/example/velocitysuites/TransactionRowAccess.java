package com.example.velocitysuites;

/** Test-only door to the package-private TransactionRow. */
public final class TransactionRowAccess {
    private TransactionRowAccess() {
    }

    public static String roomText(Booking b) {
        return TransactionRow.roomTextOf(b);
    }
}
