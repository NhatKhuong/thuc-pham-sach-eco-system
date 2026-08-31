package com.nss;

import com.nss.ddd.controller.exception.InvalidFilterValueException;
import com.nss.ddd.controller.mapper.ProductControllerMapper;
import com.nss.ddd.domain.model.ProductSort;
import com.nss.ddd.domain.model.PublicProductFilter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm phép dịch mười hai tham số của {@code GET /products} công khai — ranh giới giữa chuỗi trên
 * dây và {@link PublicProductFilter} của domain (backlog 0024).
 * <p>
 * <b>Trọng tâm là {@code sort}, và nó khoan dung NGƯỢC với {@code ProductFilterMapperTest}</b> —
 * đó là điều ADR 0007 chốt: {@code sort} là tham số tập đóng của endpoint công khai, giá trị lạ (có
 * mặt nhưng không nhận ra) phải ném {@link InvalidFilterValueException}, không được âm thầm rơi về
 * {@code NEWEST} như bên quản trị.
 */
class PublicProductFilterMapperTest {

    // ========== sort — tap DONG (ADR 0007 ve 1) ==========

    @ParameterizedTest(name = "sort={0} -> {1}")
    @CsvSource({
            "newest,        NEWEST",
            "price_asc,     PRICE_ASC",
            "price_desc,    PRICE_DESC",
            "best_selling,  BEST_SELLING",
            "rating,        RATING",
            "PRICE_ASC,     PRICE_ASC"
    })
    @DisplayName("Nam gia tri sort dich dung sang enum, khong phan biet hoa thuong")
    void mapsWireSortValues(String wire, ProductSort expected) {
        assertEquals(expected, ProductControllerMapper.toPublicProductSort(wire));
    }

    /**
     * Vắng mặt/rỗng là "chưa chọn", không phải giá trị rác — control dương cho ca dưới: nó chứng
     * minh phép đo phân biệt được "rỗng" với "lạ", chứ không phải mọi giá trị đều ném lỗi.
     *
     * @param wire {@code null}, rỗng, hoặc toàn khoảng trắng
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("sort rong hoac vang mat -> NEWEST, KHONG nem loi (control duong)")
    void blankSortFallsBackToDefaultWithoutThrowing(String wire) {
        assertEquals(ProductSort.NEWEST, ProductControllerMapper.toPublicProductSort(wire));
    }

    /**
     * Giá trị có mặt nhưng không nhận ra ném {@link InvalidFilterValueException} kèm khoá
     * {@code "sort"} — khác hẳn {@code ProductControllerMapper#toProductSort} của khu quản trị.
     *
     * @param wire giá trị không nhận ra
     */
    @ParameterizedTest(name = "sort={0} -> 422 (InvalidFilterValueException)")
    @ValueSource(strings = {"khong-ton-tai", "price", "asc", "createdAt"})
    @DisplayName("sort la (khac rong) -> nem InvalidFilterValueException, khoa 'sort'")
    void unknownSortThrowsWithFieldKey(String wire) {
        InvalidFilterValueException exception = assertThrows(InvalidFilterValueException.class,
                () -> ProductControllerMapper.toPublicProductSort(wire));
        assertEquals("sort", exception.getParameterName());
        assertTrue(exception.getMessage().contains("newest"), "Thong diep phai liet ke gia tri hop le");
        assertTrue(exception.getMessage().contains("price_asc"), "Thong diep phai liet ke gia tri hop le");
    }

    // ========== toIdList — tap MO (ADR 0007 ve 2) ==========

    @Test
    @DisplayName("toIdList parse dung danh sach so nguyen phan tach boi dau phay")
    void parsesCommaSeparatedIds() {
        assertEquals(List.of(1L, 2L, 3L), ProductControllerMapper.toIdList("1,2,3"));
    }

    @Test
    @DisplayName("toIdList cat khoang trang quanh moi token")
    void trimsWhitespaceAroundTokens() {
        assertEquals(List.of(1L, 2L, 3L), ProductControllerMapper.toIdList(" 1 , 2 ,3 "));
    }

    /**
     * <b>Token không phải số bị bỏ qua trong im lặng, không ném lỗi</b> — {@code ids} là tham số tập
     * mở (ADR 0007 vế 2): một id không parse được đồng nghĩa "không có sản phẩm nào như vậy", không
     * phải một request sai.
     */
    @Test
    @DisplayName("Token khong phai so bi bo qua, cac id hop le con lai van giu")
    void skipsNonNumericTokensWithoutThrowing() {
        assertEquals(List.of(1L, 3L), ProductControllerMapper.toIdList("1,abc,3"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "abc", ",,,"})
    @DisplayName("Rong, vang mat, hoac toan token khong phai so -> danh sach rong, khong loi")
    void blankOrAllInvalidMeansEmptyList(String raw) {
        assertEquals(List.of(), ProductControllerMapper.toIdList(raw));
    }

    // ========== toPublicFilter ==========

    @Test
    @DisplayName("toPublicFilter gom du muoi hai tham so vao dung sau truong")
    void buildsFilterFromAllTwelveParameters() {
        PublicProductFilter filter = ProductControllerMapper.toPublicFilter(
                "ca rot", "rau-cu", 10000L, 500000L, new BigDecimal("4.0"),
                true, true, false, null, "price_asc", 2, 24);

        assertEquals("ca rot", filter.getKeyword());
        assertEquals("rau-cu", filter.getCategorySlug());
        assertEquals(Long.valueOf(10000L), filter.getMinPrice());
        assertEquals(Long.valueOf(500000L), filter.getMaxPrice());
        assertEquals(new BigDecimal("4.0"), filter.getMinRating());
        assertEquals(Boolean.TRUE, filter.getInStockOnly());
        assertEquals(Boolean.TRUE, filter.getOnSaleOnly());
        assertEquals(Boolean.FALSE, filter.getIsFeatured());
        assertEquals(null, filter.getIsBestSeller());
        assertEquals(ProductSort.PRICE_ASC, filter.getSort());
        assertEquals(2, filter.getPage());
        assertEquals(24, filter.getLimit());
    }

    /**
     * Cùng luật với bên quản trị: chuỗi rỗng và tham số vắng mặt phải cùng nghĩa "không lọc" —
     * xem {@code ProductFilterMapperTest#blankStringsAndMissingParametersMeanTheSameThing}.
     */
    @Test
    @DisplayName("Chuoi rong va tham so vang mat cung nghia 'khong loc'")
    void blankStringsAndMissingParametersMeanTheSameThing() {
        PublicProductFilter fromNulls = ProductControllerMapper.toPublicFilter(
                null, null, null, null, null, null, null, null, null, null, 1, 12);
        PublicProductFilter fromBlanks = ProductControllerMapper.toPublicFilter(
                "", "  ", null, null, null, null, null, null, null, "", 1, 12);

        assertEquals(null, fromNulls.getKeyword());
        assertEquals(null, fromNulls.getCategorySlug());
        assertEquals(null, fromBlanks.getKeyword(), "?q= rong phai la 'khong tim'");
        assertEquals(null, fromBlanks.getCategorySlug(), "?category= rong phai la 'khong loc'");
        assertEquals(ProductSort.NEWEST, fromNulls.getSort());
        assertEquals(ProductSort.NEWEST, fromBlanks.getSort());
    }
}
