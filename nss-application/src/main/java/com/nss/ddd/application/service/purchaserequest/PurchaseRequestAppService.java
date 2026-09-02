package com.nss.ddd.application.service.purchaserequest;

import com.nss.ddd.application.model.command.CreateOrderCommand;
import com.nss.ddd.application.model.response.PurchaseRequestResponse;

/**
 * Use case của luồng mua hàng bất đồng bộ — Luồng B (backlog 0039 §Contract).
 * <p>
 * Producer thuần: method duy nhất ghi dữ liệu ({@link #submitAsync}) chỉ chèn một dòng
 * {@code purchase_request} + một dòng {@code outbox_event}, KHÔNG chạm Redis/MySQL stock — đó là
 * việc của {@code PurchaseRequestedConsumer} chạy sau, không đồng bộ với request HTTP.
 */
public interface PurchaseRequestAppService {

    /**
     * Nộp một yêu cầu mua hàng — {@code POST /orders/async}.
     * <p>
     * <b>Idempotent theo {@code idempotencyKey}</b> (§Contract): key đã tồn tại thì trả lại đúng
     * {@code requestId}/{@code status} hiện tại của bản ghi cũ, KHÔNG tạo outbox event mới — dù bản
     * ghi cũ đã resolve (SUCCESS/FAILED) hay vẫn PENDING.
     *
     * @param command        lệnh đã dựng từ body ({@code userId} lấy từ JWT, giống {@code createOrder})
     * @param idempotencyKey chuỗi client cung cấp qua header {@code Idempotency-Key}
     * @return {@code requestId} + {@code status} — của bản ghi mới nếu key chưa từng thấy, hoặc của
     *         bản ghi cũ nếu key đã tồn tại (idempotent replay)
     */
    PurchaseRequestResponse submitAsync(CreateOrderCommand command, String idempotencyKey);

    /**
     * Tra trạng thái một yêu cầu — {@code GET /orders/requests/{requestId}}.
     *
     * @param requestId khoá chính, dạng {@code PR-<16 hex>}
     * @return trạng thái hiện tại, hoặc {@code null} khi không có request nào mang id này
     */
    PurchaseRequestResponse findByRequestId(String requestId);
}
