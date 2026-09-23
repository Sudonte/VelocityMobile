package com.example.velocitysuites.network.dto.psgc;

/** A Philippine barangay, as returned by GET /cities-municipalities/{cityCode}/barangays/ on the public PSGC API. */
public class PsgcBarangay {
    public String code;
    public String name;
    public String oldName;
    public String subMunicipalityCode;
    public String cityCode;
    public String municipalityCode;
    public String districtCode;
    public String provinceCode;
    public String regionCode;
    public String islandGroupCode;
    public String psgc10DigitCode;
}
