package com.example.velocitysuites.network.dto;

/** The money summary an Edit Reservation response carries: the old and new totals, what was already paid, and what is now due (or overpaid). */
public class EditSummaryDto {
    public double old_total;
    public double new_total;
    public double amount_paid;
    public double balance_due;
    public double excess;
}
