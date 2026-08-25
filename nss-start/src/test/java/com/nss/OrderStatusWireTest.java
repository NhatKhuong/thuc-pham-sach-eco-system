package com.nss;

import com.nss.ddd.application.mapper.OrderMapper;
import com.nss.ddd.controller.mapper.OrderControllerMapper;
import com.nss.ddd.domain.model.OrderFilter;
import com.nss.ddd.domain.service.OrderDomainService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Kiểm <b>bảng dịch trạng thái hai chiều</b> ({@code OrderMapper}) và bộ lọc {@code ?status=} của
 * {@code GET /admin/orders} ({@code OrderControllerMapper}).
 * <p>
 * <b>Chiều {@code wire} {@literal ->} {@code int} ra đời ở backlog 0019</b>: trước đó chỉ có chiều
 * ngược, vì chưa endpoint nào <i>nhận</i> một trạng thái từ client (đơn luôn ra đời ở
 * {@code pending}). Phép kiểm quan trọng nhất ở đây là <b>vòng tròn</b>: dịch xuôi rồi dịch ngược
 * phải về đúng chỗ cũ. Hai bảng lệch nhau thì một đơn {@code cancelled} sẽ hiển thị thành
 * {@code delivered} — không exception nào, chỉ một chứng từ nói sai.
 * <p>
 * <b>Ba kết quả khác nhau cho ba tình huống của bộ lọc, và việc phân biệt chúng là toàn bộ ý nghĩa
 * của {@code toStatusFilter}:</b> vắng mặt {@literal ->} không lọc; hợp lệ {@literal ->} con số;
 * <b>lạ {@literal ->} tập rỗng</b>. Gộp ca đầu với ca cuối sẽ trả về <i>mọi</i> đơn cho câu hỏi
 * "cho tôi các đơn ở trạng thái {@code xong_roi}".
 */
class OrderStatusWireTest {

    // ========== BANG DICH HAI CHIEU ==========

    /**
     * @param wire chuỗi trên dây
     * @param code con số trong cột {@code status}
     */
    @ParameterizedTest(name = "{0} <-> {1}")
    @CsvSource({
            "pending,    0",
            "confirmed,  1",
            "shipping,   2",
            "delivered,  3",
            "cancelled,  4"
    })
    @DisplayName("Nam trang thai dich duoc CA HAI CHIEU va ve dung cho cu")
    void translatesBothDirections(String wire, int code) {
        assertEquals(code, OrderMapper.toStatusCode(wire), "wire -> int");
        assertEquals(wire, OrderMapper.toWireStatus(code), "int -> wire");
        // Vong tron: hai bang lech nhau thi mot trong hai khang dinh tren van xanh, ca nay thi khong
        assertEquals(wire, OrderMapper.toWireStatus(OrderMapper.toStatusCode(wire)));
        assertEquals(code, OrderMapper.toStatusCode(OrderMapper.toWireStatus(code)));
    }

    /**
     * Chuỗi lạ trả {@code null} — <b>không đoán</b>. Rơi về một trạng thái mặc định nghĩa là một
     * lệnh {@code PATCH} gõ sai sẽ <i>đổi thật</i> trạng thái của một chứng từ.
     *
     * @param wire chuỗi không nằm trong bảng dịch
     */
    @ParameterizedTest(name = "toStatusCode({0}) -> null")
    @ValueSource(strings = {"xong_roi", "PENDING", "Pending", "done", "0", "shipped", "cancel"})
    @DisplayName("Chuoi la cho null, khong roi ve mot trang thai mac dinh nao")
    void unknownWireStatusMapsToNull(String wire) {
        assertNull(OrderMapper.toStatusCode(wire),
                "Doan mot trang thai la doi that mot chung tu sang gia tri admin khong he chon");
    }

    /**
     * {@code null} vào cho {@code null} ra ở cả hai chiều — null-guard bắt buộc của mọi
     * {@code *Mapper} (coding-conventions §7).
     */
    @Test
    @DisplayName("null vao cho null ra o ca hai chieu")
    void nullIsGuardedBothWays() {
        assertNull(OrderMapper.toStatusCode(null));
        assertNull(OrderMapper.toWireStatus(null));
    }

    /**
     * Con số ngoài dải {@code 0..4} trả {@code null} chứ không rơi về một chuỗi cũ — một trạng thái
     * thứ sáu ra đời mà quên khai ở đây thì hỏng ngay và hỏng ở chỗ đọc được.
     *
     * @param code con số ngoài dải
     */
    @ParameterizedTest(name = "toWireStatus({0}) -> null")
    @ValueSource(ints = {-1, 5, 99})
    @DisplayName("Con so ngoai dai cho null, khong roi ve mot chuoi cu")
    void outOfRangeCodeMapsToNull(int code) {
        assertNull(OrderMapper.toWireStatus(code));
    }

    /**
     * Nhãn tiếng Việt có mặt cho cả năm trạng thái, và <b>giá trị lạ KHÔNG cho ra chuỗi
     * {@code "null"}</b>.
     * <p>
     * Nhãn chỉ đi vào {@code detail} của {@code ProblemDetail} (§A.3), nên quy ước ở đây ngược với
     * hai bảng dịch trên: một {@code null} rơi vào giữa câu sẽ biến một thông điệp đọc được thành
     * chuỗi {@code "null"} trước mắt người dùng.
     */
    @Test
    @DisplayName("toStatusLabel co nhan cho ca nam trang thai va khong bao gio tra null")
    void labelsExistForEveryStatusAndNeverReturnNull() {
        assertEquals(OrderMapper.LABEL_STATUS_PENDING,
                OrderMapper.toStatusLabel(OrderDomainService.STATUS_PENDING));
        assertEquals(OrderMapper.LABEL_STATUS_CONFIRMED,
                OrderMapper.toStatusLabel(OrderDomainService.STATUS_CONFIRMED));
        assertEquals(OrderMapper.LABEL_STATUS_SHIPPING,
                OrderMapper.toStatusLabel(OrderDomainService.STATUS_SHIPPING));
        assertEquals(OrderMapper.LABEL_STATUS_DELIVERED,
                OrderMapper.toStatusLabel(OrderDomainService.STATUS_DELIVERED));
        assertEquals(OrderMapper.LABEL_STATUS_CANCELLED,
                OrderMapper.toStatusLabel(OrderDomainService.STATUS_CANCELLED));
        assertNotNull(OrderMapper.toStatusLabel(99));
        assertNotNull(OrderMapper.toStatusLabel(null));
    }

    // ========== BO LOC ?status= CUA GET /admin/orders ==========

    /**
     * Tham số vắng mặt hoặc rỗng nghĩa là <b>không lọc</b>.
     * <p>
     * Hai thứ đó tới đây bằng hai đường khác nhau — tham số vắng cho {@code null}, còn
     * {@code ?status=} trên URL cho chuỗi rỗng — nhưng chúng cùng nghĩa.
     *
     * @param wire giá trị rỗng
     */
    @ParameterizedTest(name = "status=[{0}] -> khong loc")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("status vang mat hoac rong nghia la KHONG loc")
    void blankStatusMeansNoFilter(String wire) {
        assertNull(OrderControllerMapper.toFilter(null, wire, null, 1, 12).getStatus());
    }

    /**
     * Một {@code status} lạ cho ra <b>tập rỗng</b>, không phải "bỏ lọc".
     * <p>
     * <b>Đây là ca duy nhất phân biệt được hai cách cài đặt</b>, và khác biệt giữa chúng là câu trả
     * lời cho "cho tôi các đơn ở trạng thái {@code xong_roi}": {@link OrderFilter#STATUS_NONE} trả
     * về danh sách rỗng, còn {@code null} trả về <i>mọi</i> đơn. Frontend so bằng
     * ({@code adminOrders.api.ts:59-61}) nên tập rỗng mới là câu đúng.
     *
     * @param wire chuỗi không nằm trong bảng dịch
     */
    @ParameterizedTest(name = "status={0} -> tap rong")
    @ValueSource(strings = {"xong_roi", "PENDING", "done", "0"})
    @DisplayName("status la cho TAP RONG, khong phai bo loc")
    void unknownStatusMeansEmptyResult(String wire) {
        assertEquals(OrderFilter.STATUS_NONE,
                OrderControllerMapper.toFilter(null, wire, null, 1, 12).getStatus(),
                "Bo loc voi mot trang thai la phai tra ve khong dong nao, khong phai moi dong");
    }

    /**
     * {@code q} và {@code userId} đi thẳng qua; {@code q} rỗng thành {@code null}.
     * <p>
     * <b>{@code keyword} ở đây vẫn còn nguyên dấu</b> — phép bỏ dấu là quy tắc nghiệp vụ và nó nằm
     * ở domain service, không ở mapper biên (xem javadoc {@code OrderFilter}).
     */
    @Test
    @DisplayName("q va userId di thang qua; q rong thanh null; keyword CON NGUYEN DAU")
    void passesKeywordAndUserIdThrough() {
        OrderFilter filter = OrderControllerMapper.toFilter("  Đỗ Thị Hoa  ", null, 7L, 2, 5);
        assertEquals("Đỗ Thị Hoa", filter.getKeyword(), "Mapper bien chi trim, KHONG bo dau");
        assertEquals(7L, filter.getUserId());
        assertEquals(2, filter.getPage());
        assertEquals(5, filter.getLimit());
        assertNull(filter.getStatus());

        assertNull(OrderControllerMapper.toFilter("   ", null, null, 1, 12).getKeyword());
        assertNull(OrderControllerMapper.toFilter(null, null, null, 1, 12).getUserId());
    }
}
