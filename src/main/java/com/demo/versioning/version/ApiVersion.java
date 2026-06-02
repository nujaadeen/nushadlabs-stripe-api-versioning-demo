package com.demo.versioning.version;

import java.time.LocalDate;
import java.util.Arrays;

public enum ApiVersion {

    V_2020_01_01(LocalDate.of(2020, 1, 1),  "Initial stable release"),
    V_2022_06_15(LocalDate.of(2022, 6, 15), "Nested billing preview, deprecate legacy_amount_field"),
    V_2024_03_10(LocalDate.of(2024, 3, 10), "Nested billing GA, multi-currency support");

    private final LocalDate date;
    private final String description;

    ApiVersion(LocalDate date, String description) {
        this.date = date;
        this.description = description;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getDescription() {
        return description;
    }

    public boolean isOlderThan(ApiVersion other) {
        return this.date.isBefore(other.date);
    }

    public static ApiVersion fromString(String dateStr) {
        LocalDate target = LocalDate.parse(dateStr);
        return Arrays.stream(values())
                .filter(v -> v.date.equals(target))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No API version found for date: " + dateStr));
    }
}
