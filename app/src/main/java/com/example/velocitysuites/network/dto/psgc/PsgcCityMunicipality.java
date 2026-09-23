package com.example.velocitysuites.network.dto.psgc;

/**
 * A Philippine city or municipality, as returned by
 * GET /provinces/{provinceCode}/cities-municipalities/ (or the provinceless
 * GET /regions/{regionCode}/cities-municipalities/ variant used for NCR) on
 * the public PSGC API.
 */
public class PsgcCityMunicipality {
    public String code;
    public String name;
    public String oldName;
    public boolean isCapital;
    public boolean isCity;
    public boolean isMunicipality;
    public String provinceCode;
    public String districtCode;
    public String regionCode;
    public String islandGroupCode;
    public String psgc10DigitCode;
}
