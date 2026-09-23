package com.example.velocitysuites;

import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.velocitysuites.network.PsgcApiClient;
import com.example.velocitysuites.network.dto.psgc.PsgcBarangay;
import com.example.velocitysuites.network.dto.psgc.PsgcCityMunicipality;
import com.example.velocitysuites.network.dto.psgc.PsgcProvince;
import com.example.velocitysuites.network.dto.psgc.PsgcRegion;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Drives the cascading Country -> Region -> Province -> City/Municipality -> Barangay -> Street
 * -> ZIP address block shared by Registration (Step 3) and Profile Management's address editor.
 *
 * Philippines drives live PSGC lookups for Region/Province/City/Barangay. Any other country hides
 * the entire PH-specific block (Region/Province/City/Barangay/Street/ZIP are not collected outside
 * the Philippines) and, for the handful of countries that span multiple timezones, shows a Timezone
 * picker instead - other countries resolve a single timezone server-side from Country alone.
 * Expects a fixed set of view IDs (addrCountry*, addrRegion*, addrProvince*, addrCity*,
 * addrBarangay*, addrStreet*, addrZip*, addrTimezone*, addrIntlNoteText) to exist under the given
 * root, shared between registration.xml and dialog_edit_address.xml.
 */
public class AddressHierarchyController {

    private static final String PHILIPPINES = "Philippines";
    private static final String[] COUNTRIES = {
            "Philippines", "United States", "Canada", "United Kingdom", "Australia",
            "Singapore", "Malaysia", "Japan", "South Korea", "United Arab Emirates",
            "Saudi Arabia", "Qatar", "Hong Kong", "New Zealand", "Other"
    };

    /** Countries whose selection reveals a Timezone picker because a single IANA zone can't be
     *  safely assumed from the country alone. */
    private static final Map<String, TimezoneOption[]> MULTI_TZ_COUNTRIES = new HashMap<>();
    /** Every other supported country resolves straight to one IANA zone, no picker needed. */
    private static final Map<String, String> SINGLE_TZ_COUNTRIES = new HashMap<>();

    static {
        MULTI_TZ_COUNTRIES.put("United States", new TimezoneOption[]{
                new TimezoneOption("America/New_York", "New York — Eastern Time"),
                new TimezoneOption("America/Chicago", "Chicago — Central Time"),
                new TimezoneOption("America/Denver", "Denver — Mountain Time"),
                new TimezoneOption("America/Los_Angeles", "Los Angeles — Pacific Time"),
                new TimezoneOption("America/Anchorage", "Anchorage — Alaska Time"),
                new TimezoneOption("Pacific/Honolulu", "Honolulu — Hawaii Time"),
        });
        MULTI_TZ_COUNTRIES.put("Canada", new TimezoneOption[]{
                new TimezoneOption("America/St_Johns", "St. John's — Newfoundland Time"),
                new TimezoneOption("America/Halifax", "Halifax — Atlantic Time"),
                new TimezoneOption("America/Toronto", "Toronto — Eastern Time"),
                new TimezoneOption("America/Winnipeg", "Winnipeg — Central Time"),
                new TimezoneOption("America/Edmonton", "Edmonton — Mountain Time"),
                new TimezoneOption("America/Vancouver", "Vancouver — Pacific Time"),
        });
        MULTI_TZ_COUNTRIES.put("Australia", new TimezoneOption[]{
                new TimezoneOption("Australia/Sydney", "Sydney — Eastern Time"),
                new TimezoneOption("Australia/Brisbane", "Brisbane — Eastern Time (no DST)"),
                new TimezoneOption("Australia/Adelaide", "Adelaide — Central Time"),
                new TimezoneOption("Australia/Darwin", "Darwin — Central Time (no DST)"),
                new TimezoneOption("Australia/Perth", "Perth — Western Time"),
        });

        SINGLE_TZ_COUNTRIES.put("Philippines", "Asia/Manila");
        SINGLE_TZ_COUNTRIES.put("United Kingdom", "Europe/London");
        SINGLE_TZ_COUNTRIES.put("Singapore", "Asia/Singapore");
        SINGLE_TZ_COUNTRIES.put("Malaysia", "Asia/Kuala_Lumpur");
        SINGLE_TZ_COUNTRIES.put("Japan", "Asia/Tokyo");
        SINGLE_TZ_COUNTRIES.put("South Korea", "Asia/Seoul");
        SINGLE_TZ_COUNTRIES.put("United Arab Emirates", "Asia/Dubai");
        SINGLE_TZ_COUNTRIES.put("Saudi Arabia", "Asia/Riyadh");
        SINGLE_TZ_COUNTRIES.put("Qatar", "Asia/Qatar");
        SINGLE_TZ_COUNTRIES.put("Hong Kong", "Asia/Hong_Kong");
        SINGLE_TZ_COUNTRIES.put("New Zealand", "Pacific/Auckland");
    }

    private static final class TimezoneOption {
        final String id;
        final String label;

        TimezoneOption(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    /** Shared across both screens for the lifetime of the process - the region list never changes. */
    private static List<PsgcRegion> regionCache;

    private final Context context;
    private final AutoCompleteTextView countryField, regionField, provinceField, cityField, barangayField, timezoneField;
    private final TextInputLayout regionLayout, provinceLayout, cityLayout, barangayLayout, streetLayout, zipLayout;
    private final TextInputEditText streetField, zipField;
    private final View regionGroup, provinceGroup, cityGroup, barangayGroup, streetGroup, zipGroup, timezoneGroup;
    private final View intlNoteText;

    private List<PsgcRegion> regions = new ArrayList<>();
    private List<PsgcProvince> provinces = new ArrayList<>();
    private List<PsgcCityMunicipality> cities = new ArrayList<>();
    private List<PsgcBarangay> barangays = new ArrayList<>();

    private PsgcRegion selectedRegion;
    private PsgcProvince selectedProvince;
    private PsgcCityMunicipality selectedCity;
    private PsgcBarangay selectedBarangay;
    private boolean isPhilippines;
    private boolean provinceHasNoProvinces;
    private OnCountryChangeListener countryChangeListener;

    private TimezoneOption[] currentTimezoneOptions = new TimezoneOption[0];
    private String selectedTimezoneId;

    /**
     * Bumped on every country/region/province/city selection change and captured by each async
     * load below - a response is only applied if this hasn't moved on since the request was
     * fired. Without this, rapidly changing a parent field (e.g. Region twice in a row) can let
     * an older, now-stale PSGC response land after a newer selection and silently repopulate a
     * dropdown with the wrong data (see class scenario: "rapid dropdown changes").
     */
    private int selectionGeneration;

    /** Notified whenever the guest picks a different country - e.g. so a
     *  screen can gate PH-only features (mobile-number format, SMS OTP). */
    public interface OnCountryChangeListener {
        void onCountryChanged(boolean isPhilippines);
    }

    public static AddressHierarchyController attach(Context context, View root) {
        return new AddressHierarchyController(context, root);
    }

    public void setOnCountryChangeListener(OnCountryChangeListener listener) {
        this.countryChangeListener = listener;
    }

    /** True when the guest's selected country is the Philippines (the default). */
    public boolean isPhilippines() {
        return isPhilippines;
    }

    /** The currently selected Country display name (e.g. "Japan"), or empty if unset. */
    public String getSelectedCountry() {
        return textOf(countryField);
    }

    /** The resolved IANA timezone id for the current selection - either the guest's explicit pick
     *  (ambiguous countries) or the deterministic single-zone mapping. Null for "Other"/unknown. */
    public String getResolvedTimezone() {
        return selectedTimezoneId;
    }

    private AddressHierarchyController(Context context, View root) {
        this.context = context;
        countryField = root.findViewById(R.id.addrCountryField);
        regionField = root.findViewById(R.id.addrRegionField);
        regionLayout = root.findViewById(R.id.addrRegionLayout);
        regionGroup = root.findViewById(R.id.addrRegionGroup);
        provinceField = root.findViewById(R.id.addrProvinceField);
        provinceLayout = root.findViewById(R.id.addrProvinceLayout);
        provinceGroup = root.findViewById(R.id.addrProvinceGroup);
        cityField = root.findViewById(R.id.addrCityField);
        cityLayout = root.findViewById(R.id.addrCityLayout);
        cityGroup = root.findViewById(R.id.addrCityGroup);
        barangayField = root.findViewById(R.id.addrBarangayField);
        barangayLayout = root.findViewById(R.id.addrBarangayLayout);
        barangayGroup = root.findViewById(R.id.addrBarangayGroup);
        streetField = root.findViewById(R.id.addrStreetField);
        streetLayout = root.findViewById(R.id.addrStreetLayout);
        streetGroup = root.findViewById(R.id.addrStreetGroup);
        zipField = root.findViewById(R.id.addrZipField);
        zipLayout = root.findViewById(R.id.addrZipLayout);
        zipGroup = root.findViewById(R.id.addrZipGroup);
        timezoneField = root.findViewById(R.id.addrTimezoneField);
        timezoneGroup = root.findViewById(R.id.addrTimezoneGroup);
        intlNoteText = root.findViewById(R.id.addrIntlNoteText);
        setup();
    }

    private void setup() {
        ArrayAdapter<String> countryAdapter = new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line, COUNTRIES);
        countryField.setAdapter(countryAdapter);
        countryField.setInputType(InputType.TYPE_NULL);
        countryField.setOnClickListener(v -> countryField.showDropDown());
        countryField.setOnItemClickListener((parent, view, position, id) -> onCountrySelected(COUNTRIES[position], null));

        regionField.setOnClickListener(v -> { if (isPhilippines) regionField.showDropDown(); });
        provinceField.setOnClickListener(v -> { if (isPhilippines) provinceField.showDropDown(); });
        cityField.setOnClickListener(v -> { if (isPhilippines) cityField.showDropDown(); });
        barangayField.setOnClickListener(v -> barangayField.showDropDown());
        timezoneField.setOnClickListener(v -> timezoneField.showDropDown());

        regionField.setOnItemClickListener((parent, view, position, id) -> selectRegion(position, null));
        provinceField.setOnItemClickListener((parent, view, position, id) -> selectProvince(position, null));
        cityField.setOnItemClickListener((parent, view, position, id) -> selectCity(position, null));
        barangayField.setOnItemClickListener((parent, view, position, id) -> selectBarangay(position));
        timezoneField.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < currentTimezoneOptions.length) {
                selectedTimezoneId = currentTimezoneOptions[position].id;
            }
        });

        setFieldEnabled(regionField, regionLayout, false);
        setFieldEnabled(provinceField, provinceLayout, false);
        setFieldEnabled(cityField, cityLayout, false);
        setFieldEnabled(barangayField, barangayLayout, false);
        setFieldEnabled(streetField, streetLayout, false);
        setFieldEnabled(zipField, zipLayout, false);

        // Default to Philippines since this is a PH-based app - saves the common case a tap.
        countryField.setText(PHILIPPINES, false);
        onCountrySelected(PHILIPPINES, null);
    }

    private void onCountrySelected(String country, Runnable afterReady) {
        selectionGeneration++;
        String trimmedCountry = country == null ? "" : country.trim();
        isPhilippines = PHILIPPINES.equalsIgnoreCase(trimmedCountry);
        if (countryChangeListener != null) countryChangeListener.onCountryChanged(isPhilippines);

        selectedRegion = null;
        selectedProvince = null;
        selectedCity = null;
        selectedBarangay = null;
        provinceHasNoProvinces = false;
        regions = new ArrayList<>();
        provinces = new ArrayList<>();
        cities = new ArrayList<>();
        barangays = new ArrayList<>();

        regionField.setText("", false);
        regionField.setAdapter(null);
        provinceField.setText("", false);
        provinceField.setAdapter(null);
        cityField.setText("", false);
        cityField.setAdapter(null);
        barangayField.setText("", false);
        barangayField.setAdapter(null);
        streetField.setText("");
        zipField.setText("");

        setupTimezoneForCountry(trimmedCountry);
        setGroupVisible(intlNoteText, !isPhilippines);

        if (isPhilippines) {
            setGroupVisible(regionGroup, true);
            setGroupVisible(provinceGroup, true);
            setGroupVisible(cityGroup, true);
            setGroupVisible(barangayGroup, true);
            setGroupVisible(streetGroup, true);
            setGroupVisible(zipGroup, true);

            regionField.setInputType(InputType.TYPE_NULL);
            provinceField.setInputType(InputType.TYPE_NULL);
            cityField.setInputType(InputType.TYPE_NULL);
            barangayField.setInputType(InputType.TYPE_NULL);
            zipField.setInputType(InputType.TYPE_CLASS_NUMBER);
            zipField.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});

            setFieldEnabled(regionField, regionLayout, true);
            setFieldEnabled(provinceField, provinceLayout, false);
            setFieldEnabled(cityField, cityLayout, false);
            setFieldEnabled(barangayField, barangayLayout, false);
            setFieldEnabled(streetField, streetLayout, false);
            setFieldEnabled(zipField, zipLayout, false);

            loadRegions(afterReady);
        } else {
            // Region/Province/City/Barangay/Street/ZIP are only ever collected for Philippine
            // addresses - hide the whole block (not just disable it) for every other country.
            setGroupVisible(regionGroup, false);
            setGroupVisible(provinceGroup, false);
            setGroupVisible(cityGroup, false);
            setGroupVisible(barangayGroup, false);
            setGroupVisible(streetGroup, false);
            setGroupVisible(zipGroup, false);

            if (afterReady != null) afterReady.run();
        }
    }

    /** Shows/populates the Timezone picker for ambiguous countries; resolves a fixed IANA zone
     *  silently (no UI) for every other known country; leaves it null for "Other"/unrecognized. */
    private void setupTimezoneForCountry(String country) {
        timezoneField.setText("", false);
        timezoneField.setAdapter(null);

        TimezoneOption[] options = MULTI_TZ_COUNTRIES.get(country);
        if (options != null) {
            currentTimezoneOptions = options;
            List<String> labels = new ArrayList<>();
            for (TimezoneOption option : options) labels.add(option.label);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line, labels);
            timezoneField.setAdapter(adapter);
            selectedTimezoneId = null;
            setGroupVisible(timezoneGroup, true);
        } else {
            currentTimezoneOptions = new TimezoneOption[0];
            selectedTimezoneId = SINGLE_TZ_COUNTRIES.get(country);
            setGroupVisible(timezoneGroup, false);
        }
    }

    private void selectRegion(int index, Runnable after) {
        if (index < 0 || index >= regions.size()) return;
        selectionGeneration++;
        selectedRegion = regions.get(index);
        resetProvinceAndBelow();
        loadProvinces(selectedRegion.code, after);
    }

    private void selectProvince(int index, Runnable after) {
        if (index < 0 || index >= provinces.size()) return;
        selectionGeneration++;
        selectedProvince = provinces.get(index);
        resetCityAndBelow();
        loadCitiesForProvince(selectedProvince.code, after);
    }

    private void selectCity(int index, Runnable after) {
        if (index < 0 || index >= cities.size()) return;
        selectionGeneration++;
        selectedCity = cities.get(index);
        resetBarangayAndBelow();
        setFieldEnabled(streetField, streetLayout, true);
        setFieldEnabled(zipField, zipLayout, true);
        loadBarangays(selectedCity.code, after);
    }

    private void selectBarangay(int index) {
        if (index < 0 || index >= barangays.size()) return;
        selectedBarangay = barangays.get(index);
    }

    private void resetProvinceAndBelow() {
        selectedProvince = null;
        provinceHasNoProvinces = false;
        provinces = new ArrayList<>();
        provinceField.setText("", false);
        provinceField.setAdapter(null);
        setGroupVisible(provinceGroup, true);
        setFieldEnabled(provinceField, provinceLayout, false);
        resetCityAndBelow();
    }

    private void resetCityAndBelow() {
        selectedCity = null;
        cities = new ArrayList<>();
        cityField.setText("", false);
        cityField.setAdapter(null);
        setFieldEnabled(cityField, cityLayout, false);
        resetBarangayAndBelow();
    }

    private void resetBarangayAndBelow() {
        selectedBarangay = null;
        barangays = new ArrayList<>();
        barangayField.setText("", false);
        barangayField.setAdapter(null);
        setFieldEnabled(barangayField, barangayLayout, false);
        setFieldEnabled(streetField, streetLayout, false);
        setFieldEnabled(zipField, zipLayout, false);
    }

    private void loadRegions(Runnable after) {
        if (regionCache != null) {
            applyRegionAdapter(regionCache);
            if (after != null) after.run();
            return;
        }
        final int generation = selectionGeneration;
        PsgcApiClient.getService().getRegions().enqueue(new Callback<List<PsgcRegion>>() {
            @Override
            public void onResponse(Call<List<PsgcRegion>> call, Response<List<PsgcRegion>> response) {
                if (generation != selectionGeneration) return; // country changed again while this was in flight
                if (response.isSuccessful() && response.body() != null) {
                    regionCache = response.body();
                    applyRegionAdapter(regionCache);
                    if (after != null) after.run();
                } else {
                    reportLookupFailure();
                }
            }

            @Override
            public void onFailure(Call<List<PsgcRegion>> call, Throwable t) {
                if (generation != selectionGeneration) return;
                reportLookupFailure();
            }
        });
    }

    private void loadProvinces(String regionCode, Runnable after) {
        final int generation = selectionGeneration;
        PsgcApiClient.getService().getProvinces(regionCode).enqueue(new Callback<List<PsgcProvince>>() {
            @Override
            public void onResponse(Call<List<PsgcProvince>> call, Response<List<PsgcProvince>> response) {
                if (generation != selectionGeneration) return; // region changed again while this was in flight
                List<PsgcProvince> list = response.isSuccessful() && response.body() != null ? response.body() : new ArrayList<>();
                if (list.isEmpty()) {
                    // No provinces under this region (e.g. NCR) - skip straight to cities.
                    provinceHasNoProvinces = true;
                    setGroupVisible(provinceGroup, false);
                    loadCitiesForRegion(regionCode, after);
                } else {
                    provinceHasNoProvinces = false;
                    applyProvinceAdapter(list);
                    if (after != null) after.run();
                }
            }

            @Override
            public void onFailure(Call<List<PsgcProvince>> call, Throwable t) {
                if (generation != selectionGeneration) return;
                reportLookupFailure();
            }
        });
    }

    private void loadCitiesForRegion(String regionCode, Runnable after) {
        final int generation = selectionGeneration;
        PsgcApiClient.getService().getRegionCitiesMunicipalities(regionCode).enqueue(new Callback<List<PsgcCityMunicipality>>() {
            @Override
            public void onResponse(Call<List<PsgcCityMunicipality>> call, Response<List<PsgcCityMunicipality>> response) {
                if (generation != selectionGeneration) return;
                List<PsgcCityMunicipality> list = response.isSuccessful() && response.body() != null ? response.body() : new ArrayList<>();
                applyCityAdapter(list);
                if (after != null) after.run();
            }

            @Override
            public void onFailure(Call<List<PsgcCityMunicipality>> call, Throwable t) {
                if (generation != selectionGeneration) return;
                reportLookupFailure();
            }
        });
    }

    private void loadCitiesForProvince(String provinceCode, Runnable after) {
        final int generation = selectionGeneration;
        PsgcApiClient.getService().getProvinceCitiesMunicipalities(provinceCode).enqueue(new Callback<List<PsgcCityMunicipality>>() {
            @Override
            public void onResponse(Call<List<PsgcCityMunicipality>> call, Response<List<PsgcCityMunicipality>> response) {
                if (generation != selectionGeneration) return; // province changed again while this was in flight
                List<PsgcCityMunicipality> list = response.isSuccessful() && response.body() != null ? response.body() : new ArrayList<>();
                applyCityAdapter(list);
                if (after != null) after.run();
            }

            @Override
            public void onFailure(Call<List<PsgcCityMunicipality>> call, Throwable t) {
                if (generation != selectionGeneration) return;
                reportLookupFailure();
            }
        });
    }

    private void loadBarangays(String cityCode, Runnable after) {
        final int generation = selectionGeneration;
        PsgcApiClient.getService().getBarangays(cityCode).enqueue(new Callback<List<PsgcBarangay>>() {
            @Override
            public void onResponse(Call<List<PsgcBarangay>> call, Response<List<PsgcBarangay>> response) {
                if (generation != selectionGeneration) return; // city changed again while this was in flight
                List<PsgcBarangay> list = response.isSuccessful() && response.body() != null ? response.body() : new ArrayList<>();
                applyBarangayAdapter(list);
                if (after != null) after.run();
            }

            @Override
            public void onFailure(Call<List<PsgcBarangay>> call, Throwable t) {
                if (generation != selectionGeneration) return;
                reportLookupFailure();
            }
        });
    }

    private void applyRegionAdapter(List<PsgcRegion> list) {
        regions = list;
        setAdapterFor(regionField, namesOfRegions(list));
    }

    private void applyProvinceAdapter(List<PsgcProvince> list) {
        provinces = list;
        setAdapterFor(provinceField, namesOfProvinces(list));
        setFieldEnabled(provinceField, provinceLayout, true);
    }

    private void applyCityAdapter(List<PsgcCityMunicipality> list) {
        cities = list;
        setAdapterFor(cityField, namesOfCities(list));
        setFieldEnabled(cityField, cityLayout, true);
    }

    private void applyBarangayAdapter(List<PsgcBarangay> list) {
        barangays = list;
        setAdapterFor(barangayField, namesOfBarangays(list));
        setFieldEnabled(barangayField, barangayLayout, true);
    }

    private void setAdapterFor(AutoCompleteTextView field, List<String> names) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line, names);
        field.setAdapter(adapter);
    }

    private List<String> namesOfRegions(List<PsgcRegion> list) {
        List<String> names = new ArrayList<>();
        for (PsgcRegion r : list) names.add(r.name);
        return names;
    }

    private List<String> namesOfProvinces(List<PsgcProvince> list) {
        List<String> names = new ArrayList<>();
        for (PsgcProvince p : list) names.add(p.name);
        return names;
    }

    private List<String> namesOfCities(List<PsgcCityMunicipality> list) {
        List<String> names = new ArrayList<>();
        for (PsgcCityMunicipality c : list) names.add(c.name);
        return names;
    }

    private List<String> namesOfBarangays(List<PsgcBarangay> list) {
        List<String> names = new ArrayList<>();
        for (PsgcBarangay b : list) names.add(b.name);
        return names;
    }

    private int indexOfRegionName(String name) {
        for (int i = 0; i < regions.size(); i++) if (regions.get(i).name.equalsIgnoreCase(name.trim())) return i;
        return -1;
    }

    private int indexOfProvinceName(String name) {
        for (int i = 0; i < provinces.size(); i++) if (provinces.get(i).name.equalsIgnoreCase(name.trim())) return i;
        return -1;
    }

    private int indexOfCityName(String name) {
        for (int i = 0; i < cities.size(); i++) if (cities.get(i).name.equalsIgnoreCase(name.trim())) return i;
        return -1;
    }

    private int indexOfBarangayName(String name) {
        for (int i = 0; i < barangays.size(); i++) if (barangays.get(i).name.equalsIgnoreCase(name.trim())) return i;
        return -1;
    }

    private void setFieldEnabled(View field, TextInputLayout layout, boolean enabled) {
        field.setEnabled(enabled);
        View target = layout != null ? layout : field;
        target.setAlpha(enabled ? 1f : 0.55f);
    }

    private void setGroupVisible(View group, boolean visible) {
        if (group != null) group.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void reportLookupFailure() {
        Toast.makeText(context, R.string.address_lookup_failed, Toast.LENGTH_SHORT).show();
    }

    private String textOf(TextView tv) {
        return tv.getText() != null ? tv.getText().toString().trim() : "";
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private boolean err(TextView field, String message) {
        field.setError(message);
        field.requestFocus();
        return false;
    }

    /** Enforces the whole chain is filled for Philippine addresses (skipping Province for
     *  provinceless regions); non-PH only requires Country (and a Timezone pick, when shown). */
    public boolean validate() {
        if (TextUtils.isEmpty(textOf(countryField))) return err(countryField, context.getString(R.string.country_required));

        if (!isPhilippines) {
            if (currentTimezoneOptions.length > 0 && TextUtils.isEmpty(selectedTimezoneId)) {
                return err(timezoneField, context.getString(R.string.timezone_required));
            }
            return true;
        }

        if (selectedRegion == null) return err(regionField, context.getString(R.string.region_required));
        if (!provinceHasNoProvinces && selectedProvince == null) return err(provinceField, context.getString(R.string.province_required));
        if (selectedCity == null) return err(cityField, context.getString(R.string.city_required));
        if (selectedBarangay == null) return err(barangayField, context.getString(R.string.barangay_required));

        if (TextUtils.isEmpty(textOf(streetField))) return err(streetField, context.getString(R.string.street_required));

        String zip = textOf(zipField);
        if (TextUtils.isEmpty(zip)) return err(zipField, context.getString(R.string.zip_required));
        if (!zip.matches("\\d{4}")) return err(zipField, context.getString(R.string.zip_invalid));

        return true;
    }

    /** Single-line string for the backend's existing flat `address` field. Non-PH addresses are
     *  just the country - no other address detail is collected outside the Philippines. */
    public String getComposedAddress() {
        if (!isPhilippines) {
            return textOf(countryField);
        }

        List<String> parts = new ArrayList<>();
        String street = textOf(streetField);
        if (!TextUtils.isEmpty(street)) parts.add(street);

        if (selectedBarangay != null) parts.add("Brgy. " + selectedBarangay.name);
        if (selectedCity != null) parts.add(selectedCity.name);
        if (!provinceHasNoProvinces && selectedProvince != null) parts.add(selectedProvince.name);
        if (selectedRegion != null) parts.add(selectedRegion.name);

        String zip = textOf(zipField);
        if (!TextUtils.isEmpty(zip)) parts.add(zip);
        String country = textOf(countryField);
        if (!TextUtils.isEmpty(country)) parts.add(country);

        return TextUtils.join(", ", parts);
    }

    public AddressSelection getStructuredValues() {
        AddressSelection a = new AddressSelection();
        a.country = textOf(countryField);
        if (isPhilippines) {
            a.region = selectedRegion != null ? selectedRegion.name : "";
            a.province = provinceHasNoProvinces ? "" : (selectedProvince != null ? selectedProvince.name : "");
            a.city = selectedCity != null ? selectedCity.name : "";
            a.barangay = selectedBarangay != null ? selectedBarangay.name : "";
            a.street = textOf(streetField);
            a.zip = textOf(zipField);
        }
        a.timezone = nullToEmpty(getResolvedTimezone());
        return a;
    }

    /** Preloads a previously-saved address, re-walking the live hierarchy to resolve codes from cached names. */
    public void populateFrom(AddressSelection saved) {
        if (saved == null || TextUtils.isEmpty(saved.country)) return;
        String country = saved.country;
        countryField.setText(country, false);
        onCountrySelected(country, () -> continuePopulateRegion(saved));
    }

    private void continuePopulateRegion(AddressSelection saved) {
        if (!isPhilippines) {
            if (!TextUtils.isEmpty(saved.timezone)) {
                for (TimezoneOption option : currentTimezoneOptions) {
                    if (option.id.equalsIgnoreCase(saved.timezone)) {
                        timezoneField.setText(option.label, false);
                        selectedTimezoneId = option.id;
                        break;
                    }
                }
            }
            return;
        }
        if (TextUtils.isEmpty(saved.region)) return;
        int ri = indexOfRegionName(saved.region);
        if (ri < 0) return;
        regionField.setText(regions.get(ri).name, false);
        selectRegion(ri, () -> continuePopulateProvince(saved));
    }

    private void continuePopulateProvince(AddressSelection saved) {
        if (!provinceHasNoProvinces) {
            if (TextUtils.isEmpty(saved.province)) return;
            int pi = indexOfProvinceName(saved.province);
            if (pi < 0) return;
            provinceField.setText(provinces.get(pi).name, false);
            selectProvince(pi, () -> continuePopulateCity(saved));
        } else {
            continuePopulateCity(saved);
        }
    }

    private void continuePopulateCity(AddressSelection saved) {
        if (TextUtils.isEmpty(saved.city)) return;
        int ci = indexOfCityName(saved.city);
        if (ci < 0) return;
        cityField.setText(cities.get(ci).name, false);
        selectCity(ci, () -> {
            if (!TextUtils.isEmpty(saved.barangay)) {
                int bi = indexOfBarangayName(saved.barangay);
                if (bi >= 0) {
                    barangayField.setText(barangays.get(bi).name, false);
                    selectBarangay(bi);
                }
            }
            streetField.setText(nullToEmpty(saved.street));
            zipField.setText(nullToEmpty(saved.zip));
        });
    }
}
