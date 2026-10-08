package com.example.velocitysuites.network.dto;

/**
 * One step of the server-built Transaction Timeline (Api: `timeline` on a reservation/booking -
 * see App\\Support\\TransactionTimeline). status is Verified | Pending | Rejected | Recorded;
 * `at` is an ISO-8601 UTC instant, null only for a step that has not happened yet.
 */
public class TimelineStepDto {
    public String key;
    public String label;
    public String status;
    public String at;
}
