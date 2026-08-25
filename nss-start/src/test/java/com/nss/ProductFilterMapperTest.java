package com.nss;

import com.nss.ddd.controller.mapper.ProductControllerMapper;
import com.nss.ddd.domain.model.ProductFilter;
import com.nss.ddd.domain.model.ProductSort;
import com.nss.ddd.domain.model.StockStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Kiểm phép dịch tham số truy vấn thành {@link ProductFilter} ở
 * {@code ProductControllerMapper} — ranh giới giữa chuỗi trên dây và enum của domain.
 * <p>
 * <b>Điều được kiểm ở đây là tính KHOAN DUNG, và đó là một quyết định chứ không phải sự cẩu thả.</b>
 * Frontend coi giá trị lạ là chuyện bình thường: {@code applySort} có nhánh {@code default},
 * {@code parseSort} ép mọi giá trị lạ trên URL về {@code 'newest'}, và {@code applyFilters} có
 * {@code default: break} cho {@code stockStatus}. Nếu backend trả 400 cho những ca đó thì một người
 * sửa tay tham số trên URL sẽ thấy màn quản trị vỡ, trong khi frontend coi đường đi ấy là hợp lệ.
 */
class ProductFilterMapperTest {

    // ========== stockStatus ==========

    /**
     * @param wire giá trị trên dây
     * @param expected trạng thái mong đợi
     */
    @ParameterizedTest(name = "stockStatus={0} -> {1}")
    @CsvSource({
            "in_stock,      IN_STOCK",
            "low_stock,     LOW_STOCK",
            "out_of_stock,  OUT_OF_STOCK",
            "LOW_STOCK,     LOW_STOCK",
            "Low_Stock,     LOW_STOCK"
    })
    @DisplayName("Ba gia tri tren day dich dung sang enum, khong phan biet hoa thuong")
    void mapsWireStockStatusValues(String wire, StockStatus expected) {
        assertEquals(expected, ProductControllerMapper.toStockStatus(wire));
    }

    /**
     * Giá trị lạ cho ra {@code null} — tức <b>không lọc</b>, khớp nhánh {@code default: break} của
     * frontend. Trả lỗi ở đây là làm hỏng một màn hình mà frontend coi là chạy đúng.
     *
     * @param wire giá trị không nhận ra
     */
    @ParameterizedTest(name = "stockStatus={0} -> null (khong loc)")
    @ValueSource(strings = {"khong-ton-tai", "stock", "in stock", "0", "true"})
    @DisplayName("stockStatus la khong gay loi — coi nhu khong loc")
    void unknownStockStatusMeansNoFilter(String wire) {
        assertNull(ProductControllerMapper.toStockStatus(wire),
                "Gia tri la phai la 'khong loc', khong duoc thanh 400");
    }

    /**
     * @param wire {@code null} hoặc chuỗi rỗng
     */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("stockStatus rong hoac toan khoang trang -> khong loc")
    void blankStockStatusMeansNoFilter(String wire) {
        assertNull(ProductControllerMapper.toStockStatus(wire));
    }

    // ========== sort ==========

    /**
     * @param wire giá trị trên dây
     * @param expected thứ tự mong đợi
     */
    @ParameterizedTest(name = "sort={0} -> {1}")
    @CsvSource({
            "newest,        NEWEST",
            "price_asc,     PRICE_ASC",
            "price_desc,    PRICE_DESC",
            "best_selling,  BEST_SELLING",
            "rating,        RATING",
            "PRICE_ASC,     PRICE_ASC"
    })
    @DisplayName("Nam gia tri sort dich dung sang enum")
    void mapsWireSortValues(String wire, ProductSort expected) {
        assertEquals(expected, ProductControllerMapper.toProductSort(wire));
    }

    /**
     * <b>Giá trị lạ ở {@code sort} rơi về {@code NEWEST}, KHÔNG về {@code null}.</b>
     * <p>
     * Khác {@code stockStatus} một cách có chủ ý: "không sắp xếp" không phải một trạng thái tồn tại
     * được — kết quả vẫn phải ra theo một thứ tự nào đó, và thứ tự ấy phải ổn định giữa các trang.
     * Frontend chọn {@code newest} ở cả hai chỗ ({@code applySort} và {@code parseSort}).
     *
     * @param wire giá trị không nhận ra
     */
    @ParameterizedTest(name = "sort={0} -> NEWEST")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "khong-ton-tai", "price", "asc", "createdAt"})
    @DisplayName("sort rong hoac la deu roi ve NEWEST, KHONG phai null")
    void unknownSortFallsBackToNewest(String wire) {
        assertEquals(ProductSort.NEWEST, ProductControllerMapper.toProductSort(wire));
        assertEquals(ProductSort.DEFAULT, ProductControllerMapper.toProductSort(wire));
    }

    // ========== toFilter ==========

    @Test
    @DisplayName("toFilter gom du sau tham so vao dung sau truong")
    void buildsFilterFromAllSixParameters() {
        ProductFilter filter = ProductControllerMapper.toFilter(
                "ca rot", "rau-cu", "low_stock", "price_asc", 2, 24);

        assertEquals("ca rot", filter.getKeyword());
        assertEquals("rau-cu", filter.getCategorySlug());
        assertEquals(StockStatus.LOW_STOCK, filter.getStockStatus());
        assertEquals(ProductSort.PRICE_ASC, filter.getSort());
        assertEquals(2, filter.getPage());
        assertEquals(24, filter.getLimit());
    }

    /**
     * <b>Chuỗi rỗng và tham số vắng mặt phải cùng nghĩa "không lọc".</b>
     * <p>
     * Hai thứ đó tới controller bằng hai đường khác nhau — tham số vắng mặt cho {@code null}, còn
     * {@code ?q=&category=} trên URL cho chuỗi rỗng. Không gộp chúng lại thì chuỗi rỗng đi tiếp
     * xuống dưới và trở thành một điều kiện lọc thật: {@code LIKE '%%'} khớp mọi dòng nhưng bỏ
     * index, và một {@code category=} rỗng sẽ khớp <b>không</b> danh mục nào, tức trang quản trị
     * trống trơn ngay khi người dùng xoá ô lọc.
     */
    @Test
    @DisplayName("Chuoi rong va tham so vang mat cung nghia 'khong loc'")
    void blankStringsAndMissingParametersMeanTheSameThing() {
        ProductFilter fromNulls = ProductControllerMapper.toFilter(null, null, null, null, 1, 12);
        ProductFilter fromBlanks = ProductControllerMapper.toFilter("", "  ", "", "", 1, 12);

        assertNull(fromNulls.getKeyword());
        assertNull(fromNulls.getCategorySlug());
        assertNull(fromBlanks.getKeyword(), "?q= rong phai la 'khong tim', khong phai tim chuoi rong");
        assertNull(fromBlanks.getCategorySlug(), "?category= rong phai la 'khong loc', khong phai tap rong");
        assertEquals(fromNulls.getSort(), fromBlanks.getSort());
        assertEquals(ProductSort.NEWEST, fromBlanks.getSort());
    }

    @Test
    @DisplayName("Khoang trang hai dau cua q va category bi cat")
    void trimsKeywordAndCategory() {
        ProductFilter filter = ProductControllerMapper.toFilter(
                "  ca rot  ", "  rau-cu  ", null, null, 1, 12);

        assertEquals("ca rot", filter.getKeyword());
        assertEquals("rau-cu", filter.getCategorySlug());
    }
}
