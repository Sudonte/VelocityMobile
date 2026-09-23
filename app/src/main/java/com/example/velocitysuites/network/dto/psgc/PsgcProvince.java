package com.example.velocitysuites.network.dto.psgc;

/** A Philippine province, as returned by GET /regions/{regionCode}/provinces/ on the public PSGC API. */
public class PsgcProvince {
    public String code;
    public String name;
    public String regionCode;
    public String islandGroupCode;
    public String psgc10DigitCode;
}
