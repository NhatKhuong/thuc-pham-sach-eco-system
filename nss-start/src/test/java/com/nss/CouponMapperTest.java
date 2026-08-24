package com.nss;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nss.ddd.application.mapper.CouponMapper;
import com.nss.ddd.application.model.response.CouponResponse;
import com.nss.ddd.domain.model.entity.Coupon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm {@code CouponMapper} — logic thuần, không cần Spring context và không cần database.
 * <p>
 * Hai phép kiểm đáng giá nhất ở đây:
 * <ul>
 *   <li>{@link #responseCarriesExactlyFiveWireFields()} tuần tự hoá <b>thật</b> bằng Jackson thay vì
 *       đọc danh sách field bằng mắt, nên nó bắt được cả trường hợp ai đó thêm {@code usedCount}
 *       vào {@code CouponResponse} về sau. Phép kiểm khẳng định cả hai chiều — đúng năm khoá, và
 *       bảy cột nội bộ vắng mặt — vì "không có X" chỉ có nghĩa khi phép đo <i>bắt được</i> X.</li>
 *   <li>{@link #wireTypeTranslatesBothDirections()} khoá bảng dịch {@code 0 -> percent},
 *       {@code 1 -> fixed}. Đảo hai giá trị này không làm gãy gì cả — chỉ khiến mọi mã giảm phần
 *       trăm bị tính như số tiền cố định.</li>
 * </ul>
 */
class CouponMapperTest {

    /** Đúng năm khoá của type {@code Coupon} phía client (API_CONTRACT §B.7). */
    private static final Set<String> WIRE_FIELDS =
            Set.of("code", "type", "value", "minOrderValue", "description");

    /** Bảy cột nội bộ của bảng {@code coupon} — không cột nào được lên dây. */
    private static final List<String> INTERNAL_COLUMNS = List.of(
            "isActive", "startsAt", "endsAt", "usageLimit", "usedCount", "createdAt", "updatedAt");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("toResponse map du nam truong cua type Coupon phia client")
    void toResponseMapsEveryClientField() {
        CouponResponse response = CouponMapper.toResponse(genFixedCoupon());

        assertEquals("HUUCO50", response.getCode());
        assertEquals("fixed", response.getType());
        assertEquals(50000L, response.getValue());
        assertEquals(500000L, response.getMinOrderValue());
        assertEquals("Giảm ngay 50.000 ₫ cho đơn từ 500.000 ₫.", response.getDescription());
    }

    /**
     * Bảng dịch {@code type} theo cả hai chiều, trong <b>một</b> ca.
     * <p>
     * Gộp lại là có chủ ý: hai khẳng định này chỉ có nghĩa khi cùng đúng. Một mapper trả
     * {@code "fixed"} cho mọi giá trị vẫn qua được nửa phép kiểm nếu tách rời.
     */
    @Test
    @DisplayName("type 0 -> percent va 1 -> fixed, khong phai nguoc lai")
    void wireTypeTranslatesBothDirections() {
        assertEquals("percent", CouponMapper.toResponse(genPercentCoupon()).getType());
        assertEquals("fixed", CouponMapper.toResponse(genFixedCoupon()).getType());
    }

    /**
     * {@code value} của mã phần trăm là <b>số phần trăm</b>, không phải phân số.
     * <p>
     * {@code 10} chứ không phải {@code 0.1}: frontend nhân thẳng vào {@code subtotal} rồi chia 100.
     * Một giá trị đã chia sẵn làm mọi đơn giảm 0,1% thay vì 10% — con số vẫn trông hợp lý nên không
     * ai nhìn ra ngay.
     */
    @Test
    @DisplayName("value cua ma percent giu nguyen so phan tram, khong chia 100")
    void percentValueStaysAsWholePercent() {
        assertEquals(10L, CouponMapper.toResponse(genPercentCoupon()).getValue());
    }

    /**
     * Phép kiểm chống rò trường, có <b>positive control</b>.
     * <p>
     * Thứ tự khẳng định quan trọng: trước hết chứng minh phép đo <i>nhìn thấy</i> trường có thật
     * ({@code description}, {@code minOrderValue}), rồi mới khẳng định bảy cột nội bộ vắng mặt. Một
     * JSON rỗng cũng "không chứa {@code usedCount}" — nếu không có control, ca này sẽ xanh cả khi
     * mapper hỏng hoàn toàn.
     *
     * @throws Exception khi Jackson lỗi
     */
    @Test
    @DisplayName("JSON tra ra co DUNG nam khoa; bay cot noi bo khong lot ra day")
    void responseCarriesExactlyFiveWireFields() throws Exception {
        JsonNode json = objectMapper.valueToTree(CouponMapper.toResponse(genFixedCoupon()));

        // 1. POSITIVE CONTROL: phep do nhin thay duoc truong CO THAT
        assertTrue(json.has("description"), "control: description phai co mat");
        assertTrue(json.has("minOrderValue"), "control: minOrderValue phai co mat");

        // 2. Dung nam khoa, khong hon khong kem
        List<String> actualFields = new ArrayList<>();
        json.fieldNames().forEachRemaining(actualFields::add);
        assertEquals(WIRE_FIELDS.size(), actualFields.size(),
                "so khoa tren day phai dung 5, dang co: " + actualFields);
        assertEquals(WIRE_FIELDS, Set.copyOf(actualFields));

        // 3. Bay cot noi bo vang mat — khang dinh nay chi co nghia sau buoc 1
        for (String column : INTERNAL_COLUMNS) {
            assertNull(json.get(column), "cot noi bo " + column + " khong duoc len day");
        }
    }

    @Test
    @DisplayName("toResponse va toResponses null-guard theo coding-conventions muc 7")
    void mapperNullGuards() {
        assertNull(CouponMapper.toResponse(null));
        assertTrue(CouponMapper.toResponses(null).isEmpty());
        assertTrue(CouponMapper.toResponses(List.of()).isEmpty());
    }

    @Test
    @DisplayName("toResponses giu nguyen thu tu repository tra ve")
    void toResponsesKeepsOrder() {
        List<CouponResponse> responses =
                CouponMapper.toResponses(List.of(genPercentCoupon(), genFixedCoupon()));

        assertEquals(2, responses.size());
        assertEquals("CHAOBAN10", responses.get(0).getCode());
        assertEquals("HUUCO50", responses.get(1).getCode());
    }

    /**
     * Giá trị {@code type} ngoài bảng dịch trả {@code null} chứ không đoán bừa.
     * <p>
     * Rơi về một trong hai chuỗi cũ sẽ khiến frontend tính tiền theo loại sai mà <i>vẫn ra một con
     * số</i>; {@code null} thì hỏng ngay và hỏng ở chỗ đọc được.
     */
    @Test
    @DisplayName("type ngoai bang dich tra null, khong doan bua ve percent hay fixed")
    void unknownTypeMapsToNull() {
        assertNull(CouponMapper.toResponse(genFixedCoupon().setType(2)).getType());
        assertNull(CouponMapper.toResponse(genFixedCoupon().setType(null)).getType());
    }

    // ========== FIXTURES ==========

    /**
     * @return mã {@code HUUCO50} như seed của ticket 0006, đủ cả bảy cột nội bộ
     */
    private Coupon genFixedCoupon() {
        return new Coupon()
                .setCode("HUUCO50")
                .setType(1)
                .setValue(50000L)
                .setMinOrderValue(500000L)
                .setDescription("Giảm ngay 50.000 ₫ cho đơn từ 500.000 ₫.")
                .setIsActive(true)
                .setStartsAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .setEndsAt(LocalDateTime.of(2027, 1, 1, 0, 0))
                .setUsageLimit(100)
                .setUsedCount(7)
                .setCreatedAt(LocalDateTime.of(2026, 8, 22, 0, 0))
                .setUpdatedAt(LocalDateTime.of(2026, 8, 22, 0, 0));
    }

    /**
     * @return mã {@code CHAOBAN10} như seed của ticket 0006 — {@code type = 0}, giảm 10%
     */
    private Coupon genPercentCoupon() {
        return new Coupon()
                .setCode("CHAOBAN10")
                .setType(0)
                .setValue(10L)
                .setMinOrderValue(200000L)
                .setDescription("Giảm 10% cho đơn từ 200.000 ₫ — dành cho khách hàng mới.")
                .setIsActive(true)
                .setUsedCount(0);
    }
}
