package com.nss.ddd.application.service.order;

import com.nss.ddd.application.model.command.CreateOrderCommand;
import com.nss.ddd.application.model.response.OrderMutationResponse;
import com.nss.ddd.application.model.response.OrderResponse;

import java.util.List;

/**
 * Use case đơn hàng — API_CONTRACT §B.6, ba endpoint của backlog 0014 phase 3.
 * <p>
 * Không có quy tắc nghiệp vụ nào ở đây: quy tắc sống trong {@code OrderDomainService} và
 * {@code CouponDomainService}, tầng này chỉ điều phối trình tự và lắp kết quả thành kiểu của bề mặt
 * dây. Thứ duy nhất thuộc về tầng này là <b>chuỗi tiếng Việt</b> mà người dùng cuối đọc, vì đó là
 * chuyện trình bày chứ không phải chuyện nghiệp vụ.
 * <p>
 * <b>Hai đường đọc dưới đây không nhận {@code userId} qua bất kỳ đường nào ngoài tham số do
 * controller lấy từ claim {@code sub}</b> (§C.4.1). Không có biến thể nào nhận bộ lọc người dùng —
 * việc liệt kê chéo người dùng thuộc namespace {@code /admin} và phải là một endpoint song sinh
 * riêng (§C.4.3b). Nới lỏng ở đây là <b>rò rỉ dữ liệu</b>, không phải lỗi hiển thị.
 */
public interface OrderAppService {

    /**
     * Tạo đơn hàng — {@code POST /orders}.
     * <p>
     * Toàn bộ năm bước của §Contract 9 nằm trong <b>một</b> transaction: hỏng ở bước nào thì không
     * còn dấu vết nào — không có đơn mồ côi, không có kho bị trừ oan, không có lượt mã bị đốt.
     *
     * @param command lệnh đã dựng từ body, {@code userId} lấy từ JWT chứ không từ body (§C.2)
     * @return đơn đã tạo khi thành công; ngược lại là mã lỗi nghiệp vụ kèm thông điệp tiếng Việt
     */
    OrderMutationResponse createOrder(CreateOrderCommand command);

    /**
     * Đơn của chính người đang đăng nhập — {@code GET /orders/me}.
     * <p>
     * <b>Không phân trang</b>: hợp đồng §B.6 chốt {@code Order[]} trần.
     *
     * @param userId chủ đơn, <b>chỉ</b> lấy từ claim {@code sub} của JWT (§C.4.1)
     * @return các đơn của người này, mới nhất trước; danh sách rỗng khi chưa có đơn nào — không bao
     *         giờ {@code null}
     */
    List<OrderResponse> findMyOrders(Long userId);

    /**
     * Tra một đơn theo mã — {@code GET /orders/{code}}.
     * <p>
     * <b>Endpoint công khai và cố ý không kiểm quyền sở hữu</b> (§B.6): đây là lối duy nhất để
     * khách vãng lai xem lại đơn của mình, vì {@code /orders/me} lọc nghiêm ngặt theo {@code userId}.
     * Rủi ro đoán mã đã được ghi nhận và <b>cố ý hoãn</b> ở §Contract 6 — không xử lý trong ticket này.
     *
     * @param code mã đơn dạng {@code NSS-20260817-0001}
     * @return đơn hàng, hoặc {@code null} khi không có mã nào như vậy
     */
    OrderResponse findOrderByCode(String code);
}
