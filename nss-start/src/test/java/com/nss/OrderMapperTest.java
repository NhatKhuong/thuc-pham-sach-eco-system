package com.nss;

import com.nss.ddd.application.mapper.OrderMapper;
import com.nss.ddd.application.model.command.ShippingInfoCommand;
import com.nss.ddd.application.model.response.OrderItemResponse;
import com.nss.ddd.application.model.response.OrderResponse;
import com.nss.ddd.domain.model.entity.Order;
import com.nss.ddd.domain.model.entity.OrderItem;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.domain.model.entity.ShippingInfo;
import com.nss.ddd.domain.model.entity.User;
import com.nss.ddd.domain.service.OrderDomainService;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm {@code OrderMapper} — "đúng một chỗ" của hai bảng dịch enum và của luật chống gian lận giá.
 * <p>
 * <b>Ba khẳng định ở đây đều thuộc contract, không phải chi tiết cài đặt:</b>
 * <ul>
 *   <li><b>{@code toItem} không có đường nào nhận số tiền của client.</b> Chữ ký của nó chỉ nhận
 *       {@code Product}, {@code quantity} và {@code image}; ca kiểm dưới đây dựng một sản phẩm giá
 *       449.000 và chứng minh dòng hàng ra đời với đúng con số ấy — bất kể client khai gì (§C.1).</li>
 *   <li><b>Hai bảng dịch {@code status} 0..4 và {@code paymentMethod} 0..3</b> (§Contract 4), kể cả
 *       chiều ngược cho {@code paymentMethod}.</li>
 *   <li><b>Dòng hàng của đơn KHÔNG mang trường {@code stock}</b> (§Contract 7) — và khẳng định đó
 *       đi kèm positive control trên JSON thật.</li>
 * </ul>
 */
class OrderMapperTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ========== §C.1: GIA LAY TU DB, KHONG TU CLIENT ==========

    /**
     * Dòng hàng lấy <b>mọi</b> trường trừ số lượng từ {@code Product} của database.
     * <p>
     * <b>Đây là ca chống gian lận giá ở mức đơn vị.</b> {@code price} phải bằng cột sinh
     * {@code effective_price} (giá bán thật), còn {@code originalPrice} bằng cột {@code price} (giá
     * gốc để gạch ngang) — hai cột khác nhau, và đảo chỗ chúng sẽ khiến khách bị tính giá gốc trên
     * một món đang khuyến mãi mà không có gì báo lỗi.
     */
    @Test
    @DisplayName("toItem lay gia tu effective_price cua DB, khong tu con so nao cua client")
    void itemTakesPriceFromDatabase() {
        Product product = new Product()
                .setId(11L)
                .setSlug("ca-rot-huu-co")
                .setName("Cà rốt hữu cơ")
                .setUnit("kg")
                .setPrice(449_000L)
                .setSalePrice(399_000L)
                .setEffectivePrice(399_000L);

        OrderItem item = OrderMapper.toItem(product, 2, "/images/rau-cu/ca-rot-1.jpg");

        assertEquals(11L, item.getProductId());
        assertEquals("ca-rot-huu-co", item.getSlug());
        assertEquals("Cà rốt hữu cơ", item.getName());
        assertEquals("kg", item.getUnit());
        assertEquals(399_000L, item.getPrice(), "price phai la effective_price");
        assertEquals(449_000L, item.getOriginalPrice(), "originalPrice phai la cot price goc");
        assertEquals(2, item.getQuantity());
        assertEquals("/images/rau-cu/ca-rot-1.jpg", item.getImage());
    }

    @Test
    @DisplayName("toItem giu image null khi san pham chua co anh nao")
    void itemAllowsMissingImage() {
        Product product = new Product().setId(1L).setSlug("s").setName("n").setUnit("kg")
                .setPrice(1_000L).setEffectivePrice(1_000L);

        assertNull(OrderMapper.toItem(product, 1, null).getImage());
    }

    @Test
    @DisplayName("toItem null-guard: san pham rong tra null")
    void itemIsNullGuarded() {
        assertNull(OrderMapper.toItem(null, 1, null));
    }

    // ========== §Contract 4: HAI BANG DICH ENUM ==========

    /**
     * @param status con số trong cột {@code status}
     * @param expected chuỗi tương ứng trên dây
     */
    @ParameterizedTest(name = "status={0} -> \"{1}\"")
    @CsvSource({
            "0, pending",
            "1, confirmed",
            "2, shipping",
            "3, delivered",
            "4, cancelled"
    })
    @DisplayName("Bang dich status 0..4 sang chuoi thuong")
    void statusTableCoversAllFiveValues(int status, String expected) {
        Order order = genOrder().setStatus(status);

        assertEquals(expected, OrderMapper.toResponse(order, List.of()).getStatus());
    }

    /**
     * @param paymentMethod con số trong cột {@code payment_method}
     * @param expected chuỗi tương ứng trên dây
     */
    @ParameterizedTest(name = "paymentMethod={0} -> \"{1}\"")
    @CsvSource({
            "0, cod",
            "1, bank_transfer",
            "2, momo",
            "3, vnpay"
    })
    @DisplayName("Bang dich paymentMethod 0..3 sang chuoi thuong")
    void paymentMethodTableCoversAllFourValues(int paymentMethod, String expected) {
        Order order = genOrder().setPaymentMethod(paymentMethod);

        assertEquals(expected, OrderMapper.toResponse(order, List.of()).getPaymentMethod());
    }

    /**
     * @param wireValue chuỗi client gửi
     * @param expected con số lưu xuống DB
     */
    @ParameterizedTest(name = "\"{0}\" -> paymentMethod={1}")
    @CsvSource({
            "cod,           0",
            "bank_transfer, 1",
            "momo,          2",
            "vnpay,         3"
    })
    @DisplayName("Chieu nguoc: chuoi cua day sang con so cua DB")
    void paymentMethodTableIsBidirectional(String wireValue, int expected) {
        assertEquals(expected, OrderMapper.toPaymentMethodCode(wireValue));
    }

    /**
     * Chuỗi lạ trả {@code null} chứ <b>không đoán</b>.
     * <p>
     * Rơi về {@code cod} khi không nhận ra sẽ ghi xuống chứng từ một phương thức thanh toán mà
     * khách không hề chọn, và không có gì ném lỗi. {@code null} thì tầng use case biến nó thành 422
     * với thông điệp đọc được.
     *
     * @param wireValue chuỗi không nằm trong bảng dịch
     */
    @ParameterizedTest(name = "\"{0}\" khong nam trong bang dich -> null")
    @ValueSource(strings = {"COD", "paypal", "tien-mat", "", "  "})
    @DisplayName("paymentMethod la tra null, KHONG doan ve cod")
    void unknownPaymentMethodReturnsNull(String wireValue) {
        assertNull(OrderMapper.toPaymentMethodCode(wireValue));
    }

    @Test
    @DisplayName("paymentMethod hop le van nhan duoc khi thua khoang trang hai dau")
    void paymentMethodIsTrimmed() {
        assertEquals(0, OrderMapper.toPaymentMethodCode("  cod  "));
    }

    // ========== §Contract 7: DONG HANG KHONG CO `stock` ==========

    /**
     * Response của đơn hàng <b>không chứa khoá {@code stock}</b> ở bất kỳ đâu — kiểm trên JSON thật,
     * không kiểm trên object.
     * <p>
     * <b>Positive control nằm ngay trong cùng ca:</b> trước hết khẳng định JSON <i>có</i> chứa
     * {@code slug} và {@code unit} — hai trường có thật của cùng dòng hàng — để chứng minh phép đo
     * nhìn thấy được tên trường khi nó có mặt. Không có bước đó, một chuỗi JSON rỗng hay một lỗi
     * tuần tự hoá cũng cho ra "không chứa stock" và ca vẫn xanh.
     *
     * @throws Exception khi tuần tự hoá lỗi
     */
    @Test
    @DisplayName("JSON cua don hang KHONG co khoa `stock` — kem control duong tren `slug` va `unit`")
    void orderJsonCarriesNoStockField() throws Exception {
        OrderItem item = new OrderItem()
                .setProductId(11L)
                .setSlug("ca-rot-huu-co")
                .setName("Cà rốt hữu cơ")
                .setImage("/images/rau-cu/ca-rot-1.jpg")
                .setUnit("kg")
                .setPrice(399_000L)
                .setOriginalPrice(449_000L)
                .setQuantity(2);

        String json = OBJECT_MAPPER.writeValueAsString(
                OrderMapper.toResponse(genOrder(), List.of(item)));

        // 1. CONTROL DUONG: phep do nhin thay duoc ten truong khi no CO mat
        assertTrue(json.contains("\"slug\""), "phep do phai bat duoc mot truong co that");
        assertTrue(json.contains("\"unit\""), "phep do phai bat duoc mot truong co that");
        // 2. Khang dinh chinh — chi co nghia sau buoc 1
        assertFalse(json.contains("\"stock\""), "dong hang cua don KHONG duoc mang stock (§Contract 7)");
    }

    /**
     * {@code OrderItemResponse} khai đúng tám trường — không thừa, không thiếu.
     * <p>
     * Đếm bằng reflection chứ không liệt kê tay: một trường thứ chín thêm vào sau này sẽ làm ca này
     * đỏ ngay, kể cả khi nó không tên là {@code stock}.
     */
    @Test
    @DisplayName("OrderItemResponse co dung tam truong")
    void orderItemResponseHasExactlyEightFields() {
        assertEquals(8, OrderItemResponse.class.getDeclaredFields().length);
    }

    // ========== HINH DANG CHUNG ==========

    /**
     * Đơn của khách vãng lai trả {@code userId} là {@code null} <b>tường minh</b>, không phải một
     * khoá vắng mặt (§A.5, §D #2).
     *
     * @throws Exception khi tuần tự hoá lỗi
     */
    @Test
    @DisplayName("Don khach vang lai: userId la null TUONG MINH, khoa van co mat trong JSON")
    void guestOrderKeepsExplicitNullUserId() throws Exception {
        String json = OBJECT_MAPPER.writeValueAsString(
                OrderMapper.toResponse(genOrder(), List.of()));

        assertTrue(json.contains("\"userId\":null"), "khoa userId phai co mat voi gia tri null");
        assertTrue(json.contains("\"couponCode\":null"), "khoa couponCode phai co mat voi gia tri null");
    }

    @Test
    @DisplayName("Don co chu: userId lay tu quan he user cua entity")
    void ownedOrderCarriesUserId() {
        Order order = genOrder().setUser(new User().setId(7L));

        assertEquals(7L, OrderMapper.toResponse(order, List.of()).getUserId());
    }

    /**
     * {@code createdAt} là chuỗi ISO 8601 <b>kèm hậu tố {@code Z}</b> (§A.5).
     * <p>
     * Thiếu {@code Z} thì {@code new Date(...)} phía trình duyệt đọc chuỗi như giờ địa phương và
     * lệch đúng 7 tiếng ở VN — không có gì báo lỗi, chỉ có ngày đặt hàng hiển thị sai.
     */
    @Test
    @DisplayName("createdAt la chuoi ISO 8601 co hau to Z")
    void createdAtIsIsoUtc() {
        Order order = genOrder().setCreatedAt(LocalDateTime.of(2026, 8, 17, 10, 30));

        assertEquals("2026-08-17T10:30:00Z", OrderMapper.toResponse(order, List.of()).getCreatedAt());
    }

    /**
     * {@code note} vắng mặt khỏi JSON khi khách không ghi gì, nhưng bảy trường còn lại thì luôn có.
     *
     * @throws Exception khi tuần tự hoá lỗi
     */
    @Test
    @DisplayName("shipping.note vang mat khoi JSON khi rong; bay truong con lai van co mat")
    void shippingNoteIsOmittedWhenAbsent() throws Exception {
        Order order = genOrder();
        order.getShipping().setNote(null);

        String json = OBJECT_MAPPER.writeValueAsString(OrderMapper.toResponse(order, List.of()));

        // CONTROL DUONG: phep do bat duoc mot truong bat buoc cua chinh khoi shipping
        assertTrue(json.contains("\"ward\""), "phep do phai bat duoc mot truong co that");
        assertFalse(json.contains("\"note\""), "note rong thi vang mat khoi JSON");
    }

    @Test
    @DisplayName("shipping.note co mat khi khach that su ghi ghi chu")
    void shippingNoteIsKeptWhenPresent() throws Exception {
        Order order = genOrder();
        order.getShipping().setNote("Giao giờ hành chính");

        String json = OBJECT_MAPPER.writeValueAsString(OrderMapper.toResponse(order, List.of()));

        assertTrue(json.contains("Giao giờ hành chính"));
    }

    @Test
    @DisplayName("toShippingInfo chep du tam truong sang gia tri nhung cua entity")
    void shippingCommandMapsAllEightFields() {
        ShippingInfo shipping = OrderMapper.toShippingInfo(new ShippingInfoCommand()
                .setFullName("Nguyễn Văn An")
                .setPhone("0900000000")
                .setEmail("an@nongsansach.vn")
                .setProvince("Hà Nội")
                .setDistrict("Ba Đình")
                .setWard("Phúc Xá")
                .setStreet("12 Nguyễn Trãi")
                .setNote("Gọi trước"));

        assertEquals("Nguyễn Văn An", shipping.getFullName());
        assertEquals("0900000000", shipping.getPhone());
        assertEquals("an@nongsansach.vn", shipping.getEmail());
        assertEquals("Hà Nội", shipping.getProvince());
        assertEquals("Ba Đình", shipping.getDistrict());
        assertEquals("Phúc Xá", shipping.getWard());
        assertEquals("12 Nguyễn Trãi", shipping.getStreet());
        assertEquals("Gọi trước", shipping.getNote());
    }

    @Test
    @DisplayName("Cac phep chuyen doi deu null-guard")
    void convertersAreNullGuarded() {
        assertNull(OrderMapper.toResponse(null, List.of()));
        assertNull(OrderMapper.toItemResponse(null));
        assertNull(OrderMapper.toShippingInfo(null));
        assertNull(OrderMapper.toShippingResponse(null));
        assertNull(OrderMapper.toPaymentMethodCode(null));
        assertTrue(OrderMapper.toItemResponses(null).isEmpty());
    }

    /**
     * Trạng thái ngoài bảng dịch trả {@code null} chứ không đoán về một trong năm chuỗi cũ.
     */
    @Test
    @DisplayName("status ngoai bang dich tra null, KHONG doan")
    void unknownStatusReturnsNull() {
        OrderResponse response = OrderMapper.toResponse(genOrder().setStatus(9), List.of());

        assertNull(response.getStatus());
        assertNotNull(response.getCode(), "phan con lai cua payload van dung");
    }

    // ========== HELPERS ==========

    /**
     * @return một đơn tối thiểu ở trạng thái {@code pending}, không chủ đơn và không mã giảm giá
     */
    private Order genOrder() {
        return new Order()
                .setId(1L)
                .setCode("NSS-20260817-0001")
                .setShipping(new ShippingInfo()
                        .setFullName("Nguyễn Văn An")
                        .setPhone("0900000000")
                        .setEmail("an@nongsansach.vn")
                        .setProvince("Hà Nội")
                        .setDistrict("Ba Đình")
                        .setWard("Phúc Xá")
                        .setStreet("12 Nguyễn Trãi"))
                .setPaymentMethod(0)
                .setStatus(OrderDomainService.STATUS_PENDING)
                .setSubtotal(798_000L)
                .setDiscount(0L)
                .setShippingFee(0L)
                .setTotal(798_000L)
                .setCreatedAt(LocalDateTime.of(2026, 8, 17, 10, 30));
    }
}
