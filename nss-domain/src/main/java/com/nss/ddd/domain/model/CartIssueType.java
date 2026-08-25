package com.nss.ddd.domain.model;

/**
 * Ba loại vấn đề mà việc đối chiếu giỏ hàng phát hiện được (API_CONTRACT §B.6).
 * <p>
 * <b>Đây là khái niệm của domain, không phải chuỗi trên dây.</b> Bảng dịch sang
 * {@code out_of_stock} / {@code insufficient_stock} / {@code price_changed} nằm đúng một chỗ ở
 * {@code CartMapper} thuộc tầng application — cùng nếp với bảng dịch {@code type} của
 * {@code CouponMapper}. Domain không biết bề mặt dây trông thế nào.
 * <p>
 * Hai loại đầu <b>chặn</b> thanh toán, loại thứ ba chỉ cảnh báo (§B.6). Việc cưỡng chế điều đó
 * thuộc {@code POST /orders} (phase 3); endpoint đối chiếu giỏ chỉ báo cáo.
 */
public enum CartIssueType {

    /** Không tìm thấy sản phẩm, hoặc hết hàng, hoặc đã bị xoá mềm — ba ca gộp làm một. */
    OUT_OF_STOCK,

    /** Còn hàng nhưng không đủ số lượng khách muốn; kèm tồn kho thật. */
    INSUFFICIENT_STOCK,

    /** Giá đã đổi so với lúc khách thêm vào giỏ; kèm cả giá hiện tại lẫn giá trong giỏ. */
    PRICE_CHANGED
}
