package com.nss.ddd.application.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Hình dạng trên dây cho cả hai endpoint của luồng async (backlog 0039 §Contract):
 * {@code POST /orders/async} (202) và {@code GET /orders/requests/{requestId}} (200).
 * <p>
 * <b>Payload trần, không envelope</b> (ADR 0001) — trả thẳng ra HTTP, không bọc gì thêm.
 * {@code orderCode}/{@code failureCode}/{@code failureMessage} là {@code null} khi
 * {@code status = PENDING}; đúng một trong hai cặp có giá trị khi đã resolve
 * ({@code orderCode} cho {@code SUCCESS}, cặp {@code failureCode}/{@code failureMessage} cho
 * {@code FAILED}).
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRequestResponse {

    /** {@code PR-<16 hex>} — do backend sinh, dùng để poll ở {@code GET /orders/requests/{requestId}}. */
    private String requestId;

    /** {@code "PENDING"} | {@code "SUCCESS"} | {@code "FAILED"} — chuỗi trên dây, không phải int nội bộ. */
    private String status;

    /** Mã đơn đã tạo; chỉ có giá trị khi {@code status = "SUCCESS"}. */
    private String orderCode;

    /** Mã lỗi nghiệp vụ UPPER_SNAKE; chỉ có giá trị khi {@code status = "FAILED"}. */
    private String failureCode;

    /** Thông điệp tiếng Việt cho người dùng cuối; chỉ có giá trị khi {@code status = "FAILED"}. */
    private String failureMessage;
}
