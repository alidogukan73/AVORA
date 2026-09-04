package com.alidogukan.avora.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.alidogukan.avora.R;
import com.alidogukan.avora.viewmodels.GardenSettingsViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

/** Saves the garden point and the preferred weather source. */
@SuppressWarnings("SpellCheckingInspection")
public class GardenLocationActivity extends AppCompatActivity {
    private static final String SOURCE_AUTO = "auto";
    private static final String SOURCE_OPEN_WEATHER = "openweather";
    private static final String SOURCE_OPEN_METEO = "open_meteo";

    private GardenSettingsViewModel viewModel;
    private TextInputEditText city, district;
    private MaterialAutoCompleteTextView source;
    private TextView status;
    private Double savedLatitude, savedLongitude;

    private final ActivityResultLauncher<String> permission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) saveGps();
                else status.setText(R.string.runtime_location_permission_denied);
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_garden_location);
        viewModel = new ViewModelProvider(this).get(GardenSettingsViewModel.class);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        city = findViewById(R.id.inputLocationCity);
        district = findViewById(R.id.inputLocationDistrict);
        source = findViewById(R.id.inputWeatherSource);
        status = findViewById(R.id.txtLocationStatus);
        MaterialButton gps = findViewById(R.id.btnUseGpsLocation);
        source.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                new String[]{
                        getString(R.string.runtime_weather_source_auto),
                        getString(R.string.runtime_weather_source_open_meteo),
                        getString(R.string.runtime_weather_source_open_weather)
                }
        ));
        source.setText(getString(R.string.runtime_weather_source_auto), false);

        findViewById(R.id.btnLocationBack).setOnClickListener(view -> finish());
        viewModel.getWeatherLocation().observe(this, location -> {
            if (location == null) return;
            city.setText(location.getCity());
            district.setText(location.getDistrict());
            source.setText(sourceText(location.getForecastSource()), false);
            savedLatitude = location.getLatitude();
            savedLongitude = location.getLongitude();
            if (location.getLatitude() != null && location.getLongitude() != null) {
                status.setText(getString(R.string.runtime_location_precise_coordinates,
                        location.getLatitude(), location.getLongitude()));
            }
        });
        findViewById(R.id.btnSaveWeatherSource).setOnClickListener(view -> saveSourcePreference());

        gps.setOnClickListener(view -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                saveGps();
            } else {
                permission.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        });
        findViewById(R.id.btnSaveManualLocation).setOnClickListener(view -> saveManual());
    }

    private void saveGps() {
        GardenSettingsViewModel.LocationPoint location = viewModel.lastKnownLocation();
        if (location == null) {
            status.setText(R.string.runtime_location_unavailable);
            return;
        }
        viewModel.saveWeatherLocation(
                text(city), text(district), location.latitude, location.longitude, sourceValue()
        ).addOnSuccessListener(unused ->
                status.setText(R.string.runtime_location_precise_saved)
        ).addOnFailureListener(error ->
                status.setText(R.string.runtime_location_save_failed)
        );
    }

    private void saveManual() {
        String selectedCity = text(city);
        String selectedDistrict = text(district);
        if (selectedCity.isEmpty() || selectedDistrict.isEmpty()) {
            status.setText(R.string.runtime_location_city_required);
            return;
        }
        viewModel.saveWeatherLocation(
                selectedCity, selectedDistrict, null, null, sourceValue()
        ).addOnSuccessListener(unused ->
                status.setText(R.string.runtime_location_manual_saved)
        ).addOnFailureListener(error -> status.setText(R.string.runtime_location_save_failed_short));
    }

    private void saveSourcePreference() {
        String selectedCity = text(city);
        String selectedDistrict = text(district);
        if (selectedCity.isEmpty() || selectedDistrict.isEmpty()) {
            status.setText(R.string.runtime_location_capture_first);
            return;
        }
        viewModel.saveWeatherLocation(
                selectedCity, selectedDistrict, savedLatitude, savedLongitude, sourceValue()
        ).addOnSuccessListener(unused ->
                status.setText(R.string.runtime_weather_source_updated)
        ).addOnFailureListener(error -> status.setText(R.string.runtime_weather_source_save_failed));
    }

    private String sourceValue() {
        String value = source.getText() == null ? "" : source.getText().toString();
        if (value.equals(getString(R.string.runtime_weather_source_open_weather))) return SOURCE_OPEN_WEATHER;
        if (value.equals(getString(R.string.runtime_weather_source_open_meteo))) return SOURCE_OPEN_METEO;
        return SOURCE_AUTO;
    }

    private String sourceText(String value) {
        if (value == null) return getString(R.string.runtime_weather_source_auto);
        switch (value) {
            case SOURCE_OPEN_WEATHER:
                return getString(R.string.runtime_weather_source_open_weather);
            case SOURCE_OPEN_METEO:
                return getString(R.string.runtime_weather_source_open_meteo);
            default:
                return getString(R.string.runtime_weather_source_auto);
        }
    }

    private String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }
}
