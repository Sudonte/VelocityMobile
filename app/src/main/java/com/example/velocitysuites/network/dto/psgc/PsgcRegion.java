package com.example.velocitysuites.network.dto.psgc;

/**
 * A Philippine region, as returned by GET /regions/ on the public PSGC API
 * (https://psgc.gitlab.io/api/). Only {@code code} and {@code name} are read
 * by AddressHierarchyController today; the rest are kept because they're
 * real fields on the same response and cheap to carry along for future use.
 */
public class PsgcRegion {
    public String code;
    public String name;
    public String regionName;
    public String islandGroupCode;
    public String psgc10DigitCode;
}
