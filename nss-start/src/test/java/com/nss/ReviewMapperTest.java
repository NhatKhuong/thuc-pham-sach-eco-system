package com.nss;

import com.nss.ddd.application.mapper.ReviewMapper;
import com.nss.ddd.application.model.response.ReviewResponse;
import com.nss.ddd.application.model.response.ReviewSummaryResponse;
import com.nss.ddd.domain.model.RatingCount;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.domain.model.entity.Review;
import com.nss.ddd.domain.model.entity.User;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm {@code ReviewMapper} — hai luật, cả hai đều hỏng trong im lặng.
 * <ul>
 *   <li><b>{@code user_id} không được rò ra response.</b> {@code types/product.ts#Review} có đúng
 *       sáu trường; bảng có bảy cột từ ADR 0008. Rò ra thì response vẫn parse được, giao diện vẫn
 *       chạy, và không có gì báo lỗi — chỉ là danh tính người viết đánh giá thành công khai.</li>
 *   <li><b>{@code distribution} phải đủ năm mức sao.</b> {@code GROUP BY} bỏ hẳn mức không ai chọn;
 *       khoá vắng mặt cho ra {@code undefined} ở frontend và thanh biểu đồ đó không vẽ ra gì mà
 *       cũng không nổ lỗi nào.</li>
 * </ul>
 */
class ReviewMapperTest {

    /** Đúng sáu trường của {@code types/product.ts#Review} — con số này là contract. */
    private static final Set<String> CONTRACT_FIELDS =
            Set.of("id", "productId", "authorName", "rating", "content", "createdAt");

    /**
     * @return một đánh giá đã nạp sẵn {@code product} và <b>có</b> {@code user}
     */
    private Review genReviewWithUser() {
        return new Review()
                .setId(5L)
                .setProduct(new Product().setId(11L).setSlug("cam-sanh-huu-co"))
                .setUser(new User().setId(777L).setEmail("demo@nongsansach.vn"))
                .setAuthorName("Đặng Thu Trang")
                .setRating(5)
                .setContent("Cam mọng nước, vắt một quả được gần nửa ly.")
                .setCreatedAt(LocalDateTime.of(2026, 8, 14, 0, 0, 0));
    }

    // ========== BAY #8: user_id la cot NOI BO ==========

    /**
     * <b>Khẳng định trên DANH SÁCH trường của chính class, không phải trên một trường cụ thể.</b>
     * Kiểm "không có getUserId()" chỉ chặn được đúng cái tên đó; đọc hết field thì một trường thứ
     * bảy mang <i>bất kỳ</i> tên nào cũng làm ca này đỏ.
     */
    @Test
    @DisplayName("ReviewResponse co DUNG sau truong — khong hon mot truong nao")
    void responseHasExactlySixContractFields() {
        Set<String> actual = Arrays.stream(ReviewResponse.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertEquals(CONTRACT_FIELDS, actual,
                "ReviewResponse phai co dung 6 truong cua types/product.ts#Review");
    }

    /**
     * Control dương chạy <b>trước</b> phép kiểm âm: nếu bản thân payload rỗng thì mọi khẳng định
     * "không chứa userId" đều đúng một cách vô nghĩa.
     */
    @Test
    @DisplayName("Leak check: authorName CO mat (control duong) roi userId VANG mat")
    void userIdNeverReachesTheResponse() {
        ReviewResponse response = ReviewMapper.toResponse(genReviewWithUser());
        assertNotNull(response);

        // 1. CONTROL DUONG chay TRUOC — payload phai that su co du lieu
        assertEquals("Đặng Thu Trang", response.getAuthorName(),
                "Control duong: authorName phai co mat, neu khong thi phep kiem duoi la control cam");
        assertEquals(11L, response.getProductId());
        assertEquals(5, response.getRating());

        // 2. PHEP KIEM AM — khong truong nao mang danh tinh
        Set<String> fieldNames = Arrays.stream(ReviewResponse.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertFalse(fieldNames.contains("userId"), "userId khong duoc co mat trong ReviewResponse");
        assertFalse(fieldNames.contains("user"), "user khong duoc co mat trong ReviewResponse");
    }

    @Test
    @DisplayName("createdAt dong dau hau to Z — cot luu gio UTC (§A.5)")
    void createdAtIsIsoUtc() {
        ReviewResponse response = ReviewMapper.toResponse(genReviewWithUser());

        assertEquals("2026-08-14T00:00:00Z", response.getCreatedAt(),
                "Thieu hau to Z thi trinh duyet doc nhu gio dia phuong va lech 7 tieng o VN");
    }

    @Test
    @DisplayName("Null-guard: entity rong cho ra null, danh sach rong cho ra danh sach rong")
    void nullGuards() {
        assertNull(ReviewMapper.toResponse(null));
        assertTrue(ReviewMapper.toResponses(null).isEmpty());
        assertTrue(ReviewMapper.toResponses(List.of()).isEmpty());
    }

    // ========== BAY #2: distribution phai zero-fill du NAM muc ==========

    /**
     * <b>Sản phẩm 11 "Cam sành hữu cơ" trong seed là ca thật của bẫy này:</b> bốn đánh giá ở các
     * mức {@code 5, 4, 5, 3} — <b>không có mức 1 và mức 2</b>. {@code GROUP BY} trả về đúng ba
     * dòng, và biểu đồ của frontend cần năm.
     */
    @Test
    @DisplayName("distribution du nam khoa ke ca muc sao khong ai chon — ca that cua san pham 11")
    void distributionIsZeroFilled() {
        List<RatingCount> counts = List.of(
                RatingCount.of(3, 1L),
                RatingCount.of(4, 1L),
                RatingCount.of(5, 2L));

        ReviewSummaryResponse summary =
                ReviewMapper.toSummaryResponse(counts, 4L, new BigDecimal("4.3"));

        assertEquals(Set.of("1", "2", "3", "4", "5"), summary.getDistribution().keySet(),
                "Phai co du nam khoa chuoi '1'..'5'");
        assertEquals(0L, summary.getDistribution().get("1"), "Muc 1 sao khong ai chon van phai la 0");
        assertEquals(0L, summary.getDistribution().get("2"), "Muc 2 sao khong ai chon van phai la 0");
        assertEquals(1L, summary.getDistribution().get("3"));
        assertEquals(1L, summary.getDistribution().get("4"));
        assertEquals(2L, summary.getDistribution().get("5"));
        assertEquals(4L, summary.getTotal());
        assertEquals(new BigDecimal("4.3"), summary.getAverage());
    }

    @Test
    @DisplayName("San pham chua co danh gia nao: du nam khoa deu 0, total 0")
    void distributionOfProductWithoutReviews() {
        ReviewSummaryResponse summary =
                ReviewMapper.toSummaryResponse(List.of(), 0L, new BigDecimal("0.0"));

        assertEquals(Set.of("1", "2", "3", "4", "5"), summary.getDistribution().keySet());
        assertTrue(summary.getDistribution().values().stream().allMatch(value -> value == 0L),
                "Ca nam muc deu phai la 0");
        assertEquals(0L, summary.getTotal());
    }

    /**
     * Thứ tự khoá là thứ tự đọc ra của JSON. Một biểu đồ có các cột đảo lung tung giữa hai lần gọi
     * là thứ không ai debug được vì nó không sai ở đâu cả — nên map phải giữ thứ tự chèn.
     */
    @Test
    @DisplayName("Khoa cua distribution giu dung thu tu '1'..'5'")
    void distributionKeepsKeyOrder() {
        ReviewSummaryResponse summary = ReviewMapper.toSummaryResponse(
                List.of(RatingCount.of(5, 3L), RatingCount.of(1, 1L)), 4L, new BigDecimal("4.0"));

        assertEquals(List.of("1", "2", "3", "4", "5"),
                List.copyOf(summary.getDistribution().keySet()),
                "distribution phai la LinkedHashMap giu thu tu chen, khong phai HashMap");
    }

    @Test
    @DisplayName("counts rong hoac null deu cho ra khung du nam muc")
    void distributionHandlesNullCounts() {
        ReviewSummaryResponse summary =
                ReviewMapper.toSummaryResponse(null, 0L, new BigDecimal("0.0"));

        assertEquals(5, summary.getDistribution().size());
    }
}
