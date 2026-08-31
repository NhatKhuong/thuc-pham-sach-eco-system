package com.nss.ddd.application.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Payload của {@code GET /products/price-range} — {@code { min, max }} (API_CONTRACT §B.1).
 * <p>
 * DTO trần, không bọc {@code ResultMessage} — ADR 0001. Cả hai trường theo {@code effectivePrice},
 * không phải {@code price} (xem javadoc {@code PriceRange} của domain).
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class PriceRangeResponse {

    private Long min;

    private Long max;
}
