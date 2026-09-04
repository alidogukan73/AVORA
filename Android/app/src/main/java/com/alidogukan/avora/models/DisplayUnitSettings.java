package com.alidogukan.avora.models;

public class DisplayUnitSettings {
    public static final String CELSIUS = "celsius";
    public static final String FAHRENHEIT = "fahrenheit";
    public static final String SQUARE_METER = "square_meter";
    public static final String DECARE = "decare";
    public static final String CENTIMETER = "centimeter";
    public static final String METER = "meter";
    public static final String LITER = "liter";
    public static final String CUBIC_METER = "cubic_meter";
    public static final String GRAM = "gram";
    public static final String KILOGRAM = "kilogram";

    private String temperature;
    private String area;
    private String length;
    private String volume;
    private String weight;
    private long updated_at_epoch;

    public DisplayUnitSettings() {
        // Firebase requires an empty constructor.
    }

    public DisplayUnitSettings(String temperature, String area, String length,
                               String volume, String weight) {
        setTemperature(temperature);
        setArea(area);
        setLength(length);
        setVolume(volume);
        setWeight(weight);
    }

    public String getTemperature() { return temperature; }
    public String getArea() { return area; }
    public String getLength() { return length; }
    public String getVolume() { return volume; }
    public String getWeight() { return weight; }
    public long getUpdated_at_epoch() { return updated_at_epoch; }

    public void setTemperature(String value) {
        temperature = normalize(value, CELSIUS, FAHRENHEIT);
    }
    public void setArea(String value) {
        area = normalize(value, SQUARE_METER, DECARE);
    }
    public void setLength(String value) {
        length = normalize(value, CENTIMETER, METER);
    }
    public void setVolume(String value) {
        volume = normalize(value, LITER, CUBIC_METER);
    }
    public void setWeight(String value) {
        weight = normalize(value, GRAM, KILOGRAM);
    }
    public void setUpdated_at_epoch(long updatedAtEpoch) { this.updated_at_epoch = updatedAtEpoch; }

    public boolean isComplete() {
        return temperature != null && area != null && length != null
                && volume != null && weight != null;
    }

    private static String normalize(String value, String defaultValue, String alternative) {
        return alternative.equals(value) ? alternative : defaultValue;
    }
}
