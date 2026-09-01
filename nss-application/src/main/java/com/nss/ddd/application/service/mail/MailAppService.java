package com.nss.ddd.application.service.mail;

import com.nss.ddd.domain.model.entity.Order;
import com.nss.ddd.domain.model.entity.OrderItem;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import java.util.List;

/**
 * Đường gửi mail của hệ thống (ADR 0004).
 * <p>
 * <b>Vì sao interface này tồn tại tách khỏi {@code AuthAppService}:</b> gửi mail là một tác dụng
 * phụ <i>ra ngoài hệ thống</i> — nó chậm, nó hỏng theo cách mà database không hỏng (sai credential,
 * hộp thư đầy, bị chặn spam), và nó <b>không được phép</b> chạy trong cùng luồng với request. Tách
 * ra một bean riêng là điều kiện kỹ thuật để {@code @Async} hoạt động: một self-call trong cùng
 * class không đi qua Spring AOP proxy, nên gọi một method {@code @Async} của chính mình sẽ chạy
 * đồng bộ và <i>không có gì báo lỗi</i>.
 *
 * <h2>Vì sao gửi bất đồng bộ là một yêu cầu về BẢO MẬT, không phải về hiệu năng</h2>
 * {@code POST /auth/forgot-password} phải trả 204 <b>không phân biệt được</b> giữa email có thật và
 * email không tồn tại (API_CONTRACT §B.4 điều 5). Mã trạng thái thì dễ làm cho giống nhau; <b>thời
 * gian phản hồi thì không</b>. Một lần gửi SMTP đồng bộ tốn hàng chục tới hàng trăm mili-giây, còn
 * nhánh "email không tồn tại" thì gần như tức thì — chênh lệch đó là một công cụ dò tài khoản hoàn
 * chỉnh, chỉ cần một đồng hồ bấm giờ. Đẩy việc gửi sang luồng khác khiến cả hai nhánh trả về ở cùng
 * một điểm trong luồng xử lý.
 *
 * <h2>Vì sao method này PHẢI log kết quả gửi</h2>
 * Endpoint gọi nó luôn trả 204 — nghĩa là <b>nó không tự nói được rằng nó hỏng</b>. SMTP sai
 * credential, hộp thư đầy, mail rơi vào spam: người dùng thấy y hệt lúc thành công, và một bộ test
 * chỉ nhìn mã HTTP cũng thấy y hệt. Dòng log ở đây là <i>tín hiệu duy nhất</i> phân biệt "đã gửi"
 * với "đã im lặng nuốt mất" (ADR 0004 §Consequences).
 */
public interface MailAppService {

    /**
     * Gửi email chứa link đặt lại mật khẩu.
     * <p>
     * <b>Chạy bất đồng bộ: trả về ngay, kết quả thật xuất hiện ở log.</b> Người gọi không nhận được
     * và không được phép chờ kết quả gửi — xem javadoc cấp interface.
     * <p>
     * <b>Ngoại lệ không bao giờ được ném ngược ra người gọi.</b> Một lỗi SMTP không được biến thành
     * 500 trên một endpoint mà contract khai là luôn 204; nó được bắt và ghi log ở tầng này.
     *
     * @param toEmail địa chỉ nhận — đã xác nhận là của một tài khoản có thật
     * @param rawToken chuỗi token thô đặt vào link; <b>không bao giờ được ghi vào log</b>
     */
    void sendPasswordResetMail(String toEmail, String rawToken);

    /**
     * Gửi email chứa link xác nhận tài khoản (backlog 0037).
     * <p>
     * <b>Cùng ba tính chất với {@link #sendPasswordResetMail}</b>: chạy bất đồng bộ và trả về ngay,
     * tự nuốt mọi ngoại lệ (method chạy trên luồng khác nên không ai bắt được nếu ném ra), và bọc
     * {@code CircuitBreaker} quanh đúng lời gọi {@code send()}. Khác đúng một điểm: link trỏ tới
     * endpoint do <b>chính backend phục vụ</b> ({@code GET /api/auth/confirm-email?token=...}), không
     * phải một trang frontend.
     *
     * @param toEmail địa chỉ nhận — đã xác nhận là của một tài khoản có thật
     * @param rawToken chuỗi token thô đặt vào link; <b>không bao giờ được ghi vào log</b>
     */
    void sendEmailConfirmationMail(String toEmail, String rawToken);

    /**
     * Gửi email HTML thông báo trạng thái đơn hàng (backlog 0032, Quyết định Owner 2) — kích hoạt
     * bởi {@code OrderStatusChangedConsumer}, sau khi Kafka đã publish event {@code OrderStatusChanged}.
     * <p>
     * <b>Chạy bất đồng bộ, tự nuốt mọi exception, và bọc breaker quanh {@code send()}</b> — tái dùng
     * đúng ba tính chất của {@link #sendPasswordResetMail}: đường gửi vẫn là cùng một SMTP, cùng một
     * lớp bảo vệ {@code CircuitBreaker} tên {@code "mail"}. Khác nhau đúng một điểm: thân thư ở đây
     * là HTML có định dạng (bảng sản phẩm + badge trạng thái theo màu), dựng qua Thymeleaf — không
     * phải văn bản thuần.
     * <p>
     * <b>{@code toStatus} là tham số riêng, KHÔNG đọc từ {@code order.getStatus()}.</b> Đơn có thể
     * đã chuyển tiếp trạng thái khác trước khi consumer xử lý xong một event cũ hơn (Kafka không
     * đảm bảo thứ tự tuyệt đối giữa các partition); dùng snapshot trạng thái tại đúng thời điểm
     * event này được sinh ra tránh email của một transition cũ hiển thị nhầm trạng thái mới nhất.
     *
     * @param toEmail địa chỉ nhận — {@code order.shipping.email}, không phải {@code User.email}
     *                (đơn khách vãng lai không có tài khoản)
     * @param order đơn hàng đã ghi — dùng để lấy mã đơn, tên người nhận, các khoản tiền
     * @param items dòng hàng của đơn, để dựng bảng sản phẩm trong email
     * @param toStatus trạng thái mới của đơn tại thời điểm event được sinh ra, con số cột {@code status}
     */
    void sendOrderStatusEmail(String toEmail, Order order, List<OrderItem> items, Integer toStatus);

    /**
     * Cho {@code nss-start} lấy về để bind vào Micrometer (backlog 0038,
     * {@code com.nss.config.ResilienceMetricsConfig}).
     * <p>
     * <b>Khai trên interface dù đây là chi tiết quan sát (observability), không phải nghiệp vụ gửi
     * mail — lý do là kỹ thuật, không phải kiến trúc.</b> {@code sendPasswordResetMail}/
     * {@code sendEmailConfirmationMail}/{@code sendOrderStatusEmail} đều {@code @Async}, nên Spring
     * bọc bean thật bằng một JDK dynamic proxy chỉ hiện diện qua interface này — autowire theo type
     * cụ thể {@code MailAppServiceImpl} ném {@code BeanNotOfRequiredTypeException} ngay lúc khởi
     * động context (đã đo được: proxy thật sự là {@code jdk.proxy2.$ProxyNNN}, không phải
     * {@code MailAppServiceImpl}). Không có cách nào lấy registry này mà không đi qua interface.
     *
     * @return registry đang giữ breaker của đường gửi SMTP
     */
    CircuitBreakerRegistry getCircuitBreakerRegistry();
}
