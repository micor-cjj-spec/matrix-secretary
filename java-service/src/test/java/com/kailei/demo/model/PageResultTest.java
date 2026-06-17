package com.kailei.demo.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResultTest {

    @Test
    void normalizeMissingOrInvalidPageParams() {
        assertThat(PageResult.normalizePage(null)).isEqualTo(1);
        assertThat(PageResult.normalizePage(0L)).isEqualTo(1);
        assertThat(PageResult.normalizePage(-1L)).isEqualTo(1);
        assertThat(PageResult.normalizePage(3L)).isEqualTo(3);
    }

    @Test
    void normalizeMissingInvalidOrTooLargeSizeParams() {
        assertThat(PageResult.normalizeSize(null)).isEqualTo(20);
        assertThat(PageResult.normalizeSize(0L)).isEqualTo(20);
        assertThat(PageResult.normalizeSize(-1L)).isEqualTo(20);
        assertThat(PageResult.normalizeSize(50L)).isEqualTo(50);
        assertThat(PageResult.normalizeSize(500L)).isEqualTo(PageResult.maxSize());
    }

    @Test
    void resultDefensivelyCopiesRecords() {
        PageResult<String> result = new PageResult<>(List.of("a", "b"), 2, 1, 20, 1);

        assertThat(result.records()).containsExactly("a", "b");
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.pages()).isEqualTo(1);
    }
}
