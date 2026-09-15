package com.alidogukan.avora.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import com.alidogukan.avora.R;
import com.alidogukan.avora.models.WeatherDay;
import com.alidogukan.avora.models.WeatherForecast;
import com.alidogukan.avora.views.WeatherTemperatureChartView;
import com.alidogukan.avora.viewmodels.MainViewModel;
import com.alidogukan.avora.viewmodels.DisplayUnitsViewModel;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class WeatherForecastActivity extends EdgeToEdgeActivity {
    private TextView location, deviceLocation, temp, condition, details, humidity, wind, rain, pressure, advice;
    private ImageView icon;
    private LinearLayout days, insights;
    private WeatherTemperatureChartView chart;
    private DisplayUnitsViewModel displayUnits;
    private WeatherForecast latestForecast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weather_forecast);

        findViewById(R.id.btnWeatherBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnWeatherLocationSettings).setOnClickListener(v ->
                startActivity(new Intent(this, GardenLocationActivity.class)));

        location = findViewById(R.id.txtWeatherLocation);
        deviceLocation = findViewById(R.id.txtWeatherDeviceLocation);
        temp = findViewById(R.id.txtWeatherCurrentTemp);
        condition = findViewById(R.id.txtWeatherCurrentCondition);
        details = findViewById(R.id.txtWeatherCurrentDetails);
        humidity = findViewById(R.id.txtWeatherMetricHumidity);
        wind = findViewById(R.id.txtWeatherMetricWind);
        rain = findViewById(R.id.txtWeatherMetricRain);
        pressure = findViewById(R.id.txtWeatherMetricPressure);
        advice = findViewById(R.id.txtWeatherAdvice);
        icon = findViewById(R.id.imgWeatherCurrent);
        days = findViewById(R.id.layoutWeatherDays);
        insights = findViewById(R.id.layoutWeatherInsights);
        chart = findViewById(R.id.chartWeatherTemperature);
        displayUnits = new ViewModelProvider(this).get(DisplayUnitsViewModel.class);

        new ViewModelProvider(this).get(MainViewModel.class).getWeatherForecast()
                .observe(this, this::render);
    }

    private void render(WeatherForecast w) {
        if (w == null) return;
        latestForecast = w;
        String place = w.getDistrict().isBlank() ? w.getCity() : w.getDistrict() + " / " + w.getCity();
        location.setText(getString(R.string.runtime_weather_weekly_place, place));
        deviceLocation.setText(place);
        temp.setText(w.getCurrentTemperature() == null
                ? "—" : displayUnits.formatTemperature(w.getCurrentTemperature()));
        condition.setText(condition(w.getCurrentWeatherCode()));
        details.setText(getString(R.string.runtime_weather_source_detail, sourceLabel(w.getSource())));
        humidity.setText(getString(R.string.runtime_weather_humidity, n(w.getCurrentHumidity())));
        wind.setText(getString(R.string.runtime_weather_wind, n(w.getCurrentWind())));
        rain.setText(getString(R.string.runtime_weather_rain, n(w.getTodayRainProbability())));
        pressure.setText(getString(R.string.runtime_weather_pressure, n(w.getCurrentPressure())));
        icon.setImageResource(icon(w.getCurrentWeatherCode()));
        chart.setDays(w.getDays());
        renderDays(w.getDays());
        renderInsights(w.getDays());
        advice.setText(advice(w));
    }

    private void renderDays(List<WeatherDay> list) {
        if (list == null) return;
        days.removeAllViews();
        for (WeatherDay d : list) {
            TextView v = new TextView(this);
            v.setText(getString(R.string.runtime_weather_day_card, label(d.getDate()),
                    iconText(d.getRainProbability()), temperature(d.getTemperatureMax()),
                    temperature(d.getTemperatureMin()), n(d.getRainProbability()), n(d.getWindMax())));
            v.setTextSize(12);
            v.setTextColor(ContextCompat.getColor(this, R.color.textPrimary));
            v.setGravity(Gravity.CENTER);
            v.setPadding(dp(12), dp(12), dp(12), dp(12));
            v.setBackgroundResource(R.drawable.bg_weather_day);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(86), LinearLayout.LayoutParams.WRAP_CONTENT);
            p.setMarginEnd(dp(8));
            v.setLayoutParams(p);
            days.addView(v);
        }
    }

    private void renderInsights(List<WeatherDay> l) {
        if (l == null) return;
        insights.removeAllViews();
        WeatherDay hot = null, wet = null;
        for (WeatherDay d : l) {
            if (hot == null || v(d.getTemperatureMax()) > v(hot.getTemperatureMax())) hot = d;
            if (wet == null || v(d.getRainProbability()) > v(wet.getRainProbability())) wet = d;
        }
        addInsight(getString(R.string.runtime_rain_chance), wet == null ? "—" : getString(R.string.runtime_percentage_value, n(wet.getRainProbability())), getString(R.string.runtime_wettest_day));
        addInsight(getString(R.string.runtime_hottest_day), hot == null
                ? "—" : temperature(hot.getTemperatureMax()), hot == null ? "" : label(hot.getDate()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (latestForecast != null) render(latestForecast);
    }

    private String temperature(Double value) {
        return value == null ? "—" : displayUnits.formatTemperature(value);
    }

    private void addInsight(String title, String value, String sub) {
        TextView v = new TextView(this);
        v.setText(getString(R.string.runtime_three_lines, title, value, sub));
        v.setTextSize(12);
        v.setTextColor(ContextCompat.getColor(this, R.color.textPrimary));
        v.setPadding(dp(14), dp(14), dp(10), dp(14));
        v.setBackgroundResource(R.drawable.bg_weather_day);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        p.setMarginEnd(dp(8));
        v.setLayoutParams(p);
        insights.addView(v);
    }

    private String advice(WeatherForecast w) {
        if (w == null) return "";
        return getString(w.getTomorrowRainProbability() != null && w.getTomorrowRainProbability() >= 50
                ? R.string.runtime_tomorrow_rain_advice
                : R.string.runtime_tomorrow_clear_advice);
    }

    private String sourceLabel(String source) {
        return "openweather".equalsIgnoreCase(source) ? "OpenWeather" : "Open-Meteo";
    }

    private int icon(Long c) {
        if (c != null && c >= 51) return R.drawable.ic_water_drop_24;
        if (c != null && c >= 2) return R.drawable.ic_weather_cloud_24;
        return R.drawable.ic_weather_sunny_24;
    }

    private String iconText(Double r) {
        return r != null && r >= 50 ? getString(R.string.symbol_rain) : getString(R.string.symbol_sun);
    }

    private String condition(Long c) {
        return getString(c != null && c >= 51 ? R.string.runtime_condition_rainy : c != null && c >= 2 ? R.string.runtime_condition_cloudy : R.string.runtime_condition_sunny);
    }

    private String n(Double x) {
        return x == null ? "—" : String.valueOf(Math.round(x));
    }

    private double v(Double x) {
        return x == null ? -1 : x;
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private String label(String d) {
        try {
            return LocalDate.parse(d).format(DateTimeFormatter.ofPattern("EEE\nd MMM", Locale.getDefault()));
        } catch (Exception e) {
            return d;
        }
    }
}
