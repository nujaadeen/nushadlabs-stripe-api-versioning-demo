package com.demo.versioning;

import com.demo.versioning.version.ApiVersion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.demo.versioning.version.ApiVersion.*;
import static org.assertj.core.api.Assertions.assertThat;

class ApiVersionOrderingTest {

    @Test
    void v2020IsBefore2022() {
        assertThat(V_2020_01_01.isBefore(V_2022_06_15)).isTrue();
    }

    @Test
    void v2024IsAfter2022() {
        assertThat(V_2024_03_10.isAfter(V_2022_06_15)).isTrue();
    }

    @Test
    void orderedReturnsAscendingByDate() {
        List<ApiVersion> ordered = ApiVersion.ordered();
        assertThat(ordered).containsExactly(V_2020_01_01, V_2022_06_15, V_2024_03_10);
    }
}
