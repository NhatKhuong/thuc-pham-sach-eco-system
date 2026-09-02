package com.nss.ddd.infrastructure.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Thông tin giao hàng trong payload {@link PurchaseRequestedMessage} (backlog 0039 Phase 4) — khớp
 * {@code ShippingInfoCommand}.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseRequestedShippingMessage {

    private String fullName;

    private String phone;

    private String email;

    private String province;

    private String district;

    private String ward;

    private String street;

    private String note;
}
