package com.nss;

import com.nss.ddd.application.model.response.PaginatedResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Kiểm phép tính {@code totalPages} và việc giữ nguyên {@code page} đánh số từ 1 (§A.4).
 * <p>
 * Logic thuần, không cần Spring context và không cần database.
 */
class PaginatedResponseTest {

    @Test
    @DisplayName("42 ban ghi chia 12 mot trang ra 4 trang")
    void totalPagesRoundsUp() {
        PaginatedResponse<String> response = PaginatedResponse.of(List.of("a"), 42L, 1, 12);

        assertEquals(4, response.getTotalPages());
        assertEquals(42L, response.getTotal());
    }

    @Test
    @DisplayName("chia het thi khong sinh them trang thua")
    void totalPagesHasNoOffByOneWhenDivisible() {
        assertEquals(1, PaginatedResponse.of(List.of(), 12L, 1, 12).getTotalPages());
        assertEquals(2, PaginatedResponse.of(List.of(), 24L, 1, 12).getTotalPages());
        assertEquals(4, PaginatedResponse.of(List.of(), 41L, 1, 12).getTotalPages());
        assertEquals(4, PaginatedResponse.of(List.of(), 43L, 1, 12).getTotalPages());
    }

    @Test
    @DisplayName("khong co ban ghi nao thi totalPages bang 0")
    void totalPagesIsZeroWhenEmpty() {
        assertEquals(0, PaginatedResponse.of(List.of(), 0L, 1, 12).getTotalPages());
    }

    @Test
    @DisplayName("page tra ra giu nguyen cach danh so tu 1")
    void pageStaysOneBased() {
        assertEquals(1, PaginatedResponse.of(List.of(), 42L, 1, 12).getPage());
        assertEquals(4, PaginatedResponse.of(List.of(), 42L, 4, 12).getPage());
    }
}
