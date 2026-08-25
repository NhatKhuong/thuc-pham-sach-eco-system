package com.nss.ddd.application.service.order;

import com.nss.ddd.application.model.command.CreateOrderCommand;
import com.nss.ddd.application.model.response.PaginatedResponse;
import com.nss.ddd.application.model.response.OrderMutationResponse;
import com.nss.ddd.application.model.response.OrderResponse;
import com.nss.ddd.domain.model.OrderFilter;

import java.util.List;

/**
 * Use case đơn hàng — API_CONTRACT §B.6, ba endpoint của backlog 0014 phase 3.
 * <p>
 * Không có quy tắc nghiệp vụ nào ở đây: quy tắc sống trong {@code OrderDomainService} và
 * {@code CouponDomainService}, tầng này chỉ điều phối trình tự và lắp kết quả thành kiểu của bề mặt
 * dây. Thứ duy nhất thuộc về tầng này là <b>chuỗi tiếng Việt</b> mà người dùng cuối đọc, vì đó là
 * chuyện trình bày chứ không phải chuyện nghiệp vụ.
 * <p>
 * <b>{@link #findMyOrders(Long)} không nhận {@code userId} qua bất kỳ đường nào ngoài tham số do
 * controller lấy từ claim {@code sub}</b> (§C.4.1), và <b>nó vẫn không có biến thể nào nhận bộ lọc
 * người dùng</b> — kể cả sau khi backlog 0019 thêm ba use case quản trị vào chính interface này.
 * Việc liệt kê chéo người dùng đi qua {@link #findAdminOrders(OrderFilter)}, một method
 * <i>riêng</i> nằm sau hàng rào {@code /api/admin/**} (§C.4.3b). Nhập hai đường đó lại làm một —
 * chẳng hạn thêm {@code userId} vào {@code findMyOrders} rồi để controller quyết định — là <b>rò
 * rỉ dữ liệu</b>, không phải một sự gọn gàng: hàng rào khi ấy sống trong một câu {@code if} thay vì
 * trong filter chain.
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

    // ========== KHU QUAN TRI (§B.12.2) ==========

    /**
     * Đơn hàng của <b>mọi</b> người dùng — {@code GET /admin/orders}.
     * <p>
     * <b>Song sinh của {@link #findMyOrders(Long)}, và việc hai method cùng tồn tại CHÍNH LÀ cách
     * giữ §C.4.1 không bị nới lỏng</b> (§B.12.2). Ở đây {@code userId} là một <i>bộ lọc</i> hợp lệ;
     * ở kia nó là <i>danh tính</i> lấy từ token và không bao giờ đến từ query.
     * <p>
     * <b>Có phân trang, khác {@code /orders/me}</b> — cái kia trả {@code Order[]} trần theo §B.6,
     * còn bảng quản trị đọc chéo toàn hệ nên không có trần nào để dựa vào.
     *
     * @param filter điều kiện lọc; {@code keyword} là chuỗi thô client gửi
     * @return trang đơn hàng theo §A.4
     */
    PaginatedResponse<OrderResponse> findAdminOrders(OrderFilter filter);

    /**
     * Đổi trạng thái một đơn — {@code PATCH /admin/orders/{code}/status}.
     * <p>
     * <b>Backend cưỡng chế luồng trạng thái; ô chọn ở giao diện chỉ là tiện tay</b> (§B.12.2).
     * Luật sống ở {@code OrderDomainService.canTransition} và chỉ ở đó; method này chỉ điều phối và
     * dịch một {@code false} thành mã lỗi nghiệp vụ.
     * <p>
     * <b>Cả hai write nằm trong MỘT transaction:</b> cột {@code status} của đơn và một dòng
     * {@code order_status_history} mới. Ghi được cái này mà hỏng cái kia thì bảng nhật ký — thứ tồn
     * tại để trả lời "đơn đi qua đâu, lúc nào, do ai" — trở thành một câu trả lời không còn kiểm
     * chứng được.
     *
     * @param code mã đơn dạng {@code NSS-20260817-0001}
     * @param wireStatus trạng thái muốn chuyển sang, <b>chuỗi trên dây</b> ({@code confirmed}…)
     * @param changedBy định danh admin thực hiện, lấy từ claim {@code sub}
     * @return đơn sau khi chuyển khi thành công; ngược lại là mã lỗi nghiệp vụ kèm thông điệp
     *         tiếng Việt
     */
    OrderMutationResponse changeOrderStatus(String code, String wireStatus, String changedBy);
}
