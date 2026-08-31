package com.nss.ddd.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Khoảng giá {@code MIN}/{@code MAX} của {@code effectivePrice} trên toàn bộ sản phẩm còn hiệu lực —
 * nguồn của {@code GET /products/price-range} (API_CONTRACT §B.1).
 * <p>
 * <b>Theo {@code effectivePrice}, không theo {@code price}</b> — cùng luật đã ghim ở
 * {@link ProductFilter} và {@link PublicProductFilter}: một thanh trượt giá dựng từ {@code price} sẽ
 * cho biên sai với con số người dùng thực sự nhìn thấy trên thẻ sản phẩm.
 * <p>
 * {@code min} / {@code max} là {@code null} khi không có sản phẩm nào còn hiệu lực — {@code MIN()} /
 * {@code MAX()} trên tập rỗng của MySQL trả {@code NULL}, và kiểu này giữ nguyên tín hiệu đó thay vì
 * giả một cặp {@code 0}.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class PriceRange {

    /** Giá thấp nhất trong các sản phẩm còn hiệu lực; {@code null} khi không có sản phẩm nào. */
    private Long min;

    /** Giá cao nhất trong các sản phẩm còn hiệu lực; {@code null} khi không có sản phẩm nào. */
    private Long max;

    /**
     * Static factory — không {@code new} trực tiếp ở call site (coding-conventions §7).
     *
     * @param min giá thấp nhất, có thể {@code null}
     * @param max giá cao nhất, có thể {@code null}
     * @return khoảng giá đã dựng xong
     */
    public static PriceRange of(Long min, Long max) {
        return new PriceRange().setMin(min).setMax(max);
    }
}
