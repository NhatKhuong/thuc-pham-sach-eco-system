package com.nss.ddd.controller.mapper;

import com.nss.ddd.application.mapper.OrderMapper;
import com.nss.ddd.application.model.command.CreateOrderCommand;
import com.nss.ddd.application.model.command.ShippingInfoCommand;
import com.nss.ddd.controller.dto.CreateOrderRequest;
import com.nss.ddd.controller.dto.ShippingInfoRequest;
import com.nss.ddd.domain.model.OrderFilter;

/**
 * Converter ở ranh giới HTTP: {@code CreateOrderRequest} của controller sang
 * {@code CreateOrderCommand} của application (coding-conventions §7).
 * <p>
 * Class stateless, method {@code public static}, không phải Spring bean, luôn null-guard.
 * <p>
 * <b>{@code userId} là tham số riêng, KHÔNG đọc từ {@code request}</b> — nó đến từ claim {@code sub}
 * của JWT, đúng khuôn {@code AuthControllerMapper.toLogoutCommand}. Chữ ký của
 * {@link #toCommand(CreateOrderRequest, Long)} là chỗ điều đó nhìn thấy được: hai nguồn dữ liệu
 * khác nhau vào cùng một lệnh, và cái thứ hai không bao giờ đi qua body.
 * <p>
 * <b>Không {@code BeanUtils.copyProperties}</b>, và ở đây lý do cấm nó sắc hơn thường lệ — cùng lý
 * do đã viết ở {@code CartControllerMapper}: một bản chép ngầm theo tên trường sẽ tự động mang theo
 * bất kỳ trường nào ai đó thêm vào DTO về sau, kể cả một {@code userId} lỡ tay khai. Danh sách viết
 * tay bên dưới là chỗ duy nhất phải sửa để một trường mới đi được qua ranh giới này.
 * <p>
 * <b>Phần {@code items} uỷ thác cho {@code CartControllerMapper}</b> thay vì chép lại: hai endpoint
 * nhận cùng một kiểu {@code CartItem[]} và phải lọc trường theo cùng một luật (§C.1), nên chúng
 * dùng chung đúng một phép chuyển đổi.
 */
public final class OrderControllerMapper {

    /**
     * Class tiện ích, không có thể hiện.
     */
    private OrderControllerMapper() {
    }

    /**
     * Gom năm tham số truy vấn của {@code GET /api/admin/orders} thành điều kiện lọc của domain
     * (§B.12.2).
     * <p>
     * <b>Đây là chỗ chuỗi {@code status} trên dây được dịch thành con số của DB</b>, bằng chính
     * bảng dịch của {@code OrderMapper} — không có bảng thứ hai ở tầng này.
     * <p>
     * <b>Một {@code status} lạ cho ra TẬP RỖNG, không phải "bỏ lọc"</b> — xem javadoc của
     * {@link OrderFilter#STATUS_NONE} về việc vì sao ở đây ngược với cách
     * {@code ProductControllerMapper} xử lý một {@code stockStatus} lạ.
     *
     * @param q từ khoá tìm kiếm; rỗng là không tìm
     * @param status trạng thái dạng chuỗi trên dây; rỗng là không lọc, lạ là tập rỗng
     * @param userId chủ đơn; {@code null} là không lọc
     * @param page trang, đánh số từ 1
     * @param limit số phần tử mỗi trang
     * @return điều kiện lọc của domain, không bao giờ {@code null}
     */
    public static OrderFilter toFilter(String q, String status, Long userId, int page, int limit) {
        return OrderFilter.of(
                toNullIfBlank(q),
                toStatusFilter(status),
                userId,
                page,
                limit);
    }

    /**
     * Chuỗi {@code status} trên dây thành con số dùng cho <b>bộ lọc</b>.
     * <p>
     * Ba kết quả cho ba tình huống khác nhau, và việc phân biệt chúng là toàn bộ ý nghĩa của method
     * này:
     * <ul>
     *   <li>tham số vắng mặt hoặc rỗng {@literal ->} {@code null}, tức <b>không lọc</b>;</li>
     *   <li>một trong năm chuỗi hợp lệ {@literal ->} con số tương ứng;</li>
     *   <li>bất kỳ chuỗi nào khác {@literal ->} {@link OrderFilter#STATUS_NONE}, tức <b>tập
     *       rỗng</b>.</li>
     * </ul>
     * Gộp hai ca đầu và ca cuối lại — tức trả {@code null} cho cả "vắng mặt" lẫn "lạ" — sẽ trả về
     * <i>mọi</i> đơn cho câu hỏi "cho tôi các đơn ở trạng thái {@code xong_roi}".
     *
     * @param status chuỗi trên dây
     * @return con số để lọc, hoặc {@code null} khi không lọc
     */
    private static Integer toStatusFilter(String status) {
        String normalized = toNullIfBlank(status);
        if (normalized == null) {
            return null;
        }
        Integer code = OrderMapper.toStatusCode(normalized);
        return code == null ? OrderFilter.STATUS_NONE : code;
    }

    /**
     * Gộp {@code null} và chuỗi toàn khoảng trắng thành một tín hiệu duy nhất.
     * <p>
     * Cùng khuôn — và cùng lý do — với {@code ProductControllerMapper.toNullIfBlank}: tham số vắng
     * mặt cho {@code null}, còn {@code ?q=} trên URL cho chuỗi rỗng, nhưng chúng cùng nghĩa "không
     * lọc". Không gộp thì chuỗi rỗng đi tiếp xuống dưới và trở thành một điều kiện lọc thật.
     *
     * @param value chuỗi thô
     * @return chuỗi đã {@code trim}, hoặc {@code null} khi rỗng
     */
    private static String toNullIfBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * @param request body đã qua validate
     * @param userId chủ đơn lấy từ claim {@code sub}; {@code null} là đơn khách vãng lai
     * @return lệnh tạo đơn, hoặc {@code null} khi {@code request} rỗng
     */
    public static CreateOrderCommand toCommand(CreateOrderRequest request, Long userId) {
        if (request == null) {
            return null;
        }
        return new CreateOrderCommand()
                .setUserId(userId)
                .setItems(CartControllerMapper.toCommands(request.getItems()))
                .setShipping(toShippingCommand(request.getShipping()))
                .setPaymentMethod(request.getPaymentMethod())
                .setCouponCode(request.getCouponCode());
    }

    /**
     * @param request khối {@code shipping} trong body
     * @return lệnh tương ứng, hoặc {@code null} khi {@code request} rỗng
     */
    public static ShippingInfoCommand toShippingCommand(ShippingInfoRequest request) {
        if (request == null) {
            return null;
        }
        return new ShippingInfoCommand()
                .setFullName(request.getFullName())
                .setPhone(request.getPhone())
                .setEmail(request.getEmail())
                .setProvince(request.getProvince())
                .setDistrict(request.getDistrict())
                .setWard(request.getWard())
                .setStreet(request.getStreet())
                .setNote(request.getNote());
    }
}
