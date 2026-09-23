package com.example.velocitysuites.network;

import com.example.velocitysuites.network.dto.psgc.PsgcBarangay;
import com.example.velocitysuites.network.dto.psgc.PsgcCityMunicipality;
import com.example.velocitysuites.network.dto.psgc.PsgcProvince;
import com.example.velocitysuites.network.dto.psgc.PsgcRegion;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

/**
 * Public Philippine Standard Geographic Code API (https://psgc.gitlab.io/api/),
 * used to power the cascading Region/Province/City/Barangay address pickers.
 */
public interface PsgcApiService {

    @GET("regions/")
    Call<List<PsgcRegion>> getRegions();

    @GET("regions/{regionCode}/provinces/")
    Call<List<PsgcProvince>> getProvinces(@Path("regionCode") String regionCode);

    /** NCR (and any other provinceless region) lists its cities/municipalities directly. */
    @GET("regions/{regionCode}/cities-municipalities/")
    Call<List<PsgcCityMunicipality>> getRegionCitiesMunicipalities(@Path("regionCode") String regionCode);

    @GET("provinces/{provinceCode}/cities-municipalities/")
    Call<List<PsgcCityMunicipality>> getProvinceCitiesMunicipalities(@Path("provinceCode") String provinceCode);

    @GET("cities-municipalities/{cityCode}/barangays/")
    Call<List<PsgcBarangay>> getBarangays(@Path("cityCode") String cityCode);
}
