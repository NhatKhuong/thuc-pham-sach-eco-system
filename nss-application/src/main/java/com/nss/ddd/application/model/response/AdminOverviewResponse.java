package com.nss.ddd.application.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Số liệu tổng quan của khu quản trị — khớp đúng type {@code AdminOverview} của frontend
 * ({@code src/types/admin.ts}, API_CONTRACT §B.12.4).
 * <p>
 * <b>Đúng sáu trường.</b> Hợp đồng của bốn ticket 0004–0007 chốt shape này và ghi thẳng "ticket nào
 * thấy thiếu trường thì báo PM chứ không tự thêm".
 * <p>
 * <b>Bốn trường theo khoảng, hai trường là ảnh chụp — và sự pha trộn đó là contract:</b>
 * <ul>
 *   <li>{@link #revenue}, {@link #orderCount}, {@link #revenueByDay}, {@link #ordersByStatus} nằm
 *       trong <b>cùng một</b> khoảng {@code days};</li>
 *   <li>{@link #customerCount} và {@link #lowStockCount} là <b>ảnh chụp hiện tại</b> —
 *       {@code User} không có {@code createdAt} nên không có chiều thời gian nào để cắt, còn tồn
 *       kho thì chỉ có giá trị "ngay lúc này".</li>
 * </ul>
 * <b>Hai bất biến kiểm được thẳng từ chính payload này</b> — vi phạm là backend tính sai, không
 * phải client hiển thị sai:
 * <ul>
 *   <li>{@code revenue == sum(revenueByDay[].revenue)};</li>
 *   <li>{@code orderCount == sum(ordersByStatus[].count)}.</li>
 * </ul>
 * Chúng đúng <b>theo cấu tạo</b> chứ không theo may mắn: {@link #revenue} được <i>cộng từ chính</i>
 * {@link #revenueByDay} ở tầng application, và hai truy vấn tổng hợp dùng chung một cặp mốc thời
 * gian. Hai truy vấn độc lập rồi hy vọng chúng khớp là tự đặt một cái bẫy im lặng.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class AdminOverviewResponse {

    /**
     * Tổng {@code total} của các đơn <b>không</b> ở trạng thái {@code cancelled} trong khoảng, số
     * nguyên VNĐ.
     * <p>
     * Đơn huỷ <b>vẫn</b> vào {@link #orderCount} và vào cột {@code cancelled} của
     * {@link #ordersByStatus} — nó đã xảy ra — nhưng nó không phải tiền cửa hàng thu được.
     */
    private long revenue;

    /** Số đơn <b>mọi trạng thái</b> trong khoảng, kể cả đơn đã huỷ. */
    private long orderCount;

    /**
     * Số tài khoản {@code role == "customer"} — <b>ảnh chụp hiện tại</b>.
     * <p>
     * Phải bằng {@code total} của {@code GET /admin/customers} khi không kèm tham số nào (§B.12.3,
     * §B.12.4). Đây là hai chỗ duy nhất trong tài liệu đếm người dùng.
     */
    private long customerCount;

    /**
     * Số sản phẩm có {@code 0 < stock <= 10} — <b>ảnh chụp hiện tại</b>.
     * <p>
     * Phải bằng {@code total} của {@code GET /admin/products?stockStatus=low_stock}, và dùng đúng
     * {@code StockStatus.LOW_STOCK_THRESHOLD}.
     */
    private long lowStockCount;

    /**
     * Doanh thu theo ngày — <b>đúng {@code days} phần tử</b>, tăng dần, ngày không có đơn trả
     * {@code revenue: 0}.
     * <p>
     * <b>Zero-fill là việc của backend</b> (§B.12.4). Không zero-fill thì mọi client — web,
     * Android, iOS — phải tự dựng lại khung ngày y hệt nhau, và đường biểu đồ nối thẳng qua khoảng
     * trống sẽ đọc thành "doanh thu đều".
     */
    private List<DailyRevenueResponse> revenueByDay;

    /**
     * Số đơn theo trạng thái — <b>đủ cả 5 trạng thái</b>, kể cả trạng thái đang có {@code count: 0}.
     * <p>
     * Thư viện biểu đồ không vẽ gì cho một cột {@code count: 0}; client tự xử lý phần hiển thị đó,
     * nhưng nó chỉ làm được khi mốc rỗng <i>có mặt thật</i> trong dữ liệu.
     */
    private List<StatusCountResponse> ordersByStatus;

    /**
     * Một điểm của chuỗi doanh thu theo ngày.
     * <p>
     * Là nested class chứ không một file riêng: nó không có nghĩa ở đâu khác ngoài
     * {@link AdminOverviewResponse}, và đặt cạnh nhau thì shape sáu trường của hợp đồng đọc được
     * trọn vẹn trong một màn hình.
     */
    @Data
    @Accessors(chain = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyRevenueResponse {

        /**
         * Ngày dạng {@code YYYY-MM-DD}, theo <b>múi giờ cửa hàng</b> (§B.12.4).
         * <p>
         * Là {@code String} chứ không {@code LocalDate}, cùng lý do đã viết ở
         * {@code OrderResponse.createdAt}: chuỗi trên dây phải là thứ frontend đọc thẳng được, và
         * ở đây nó còn là <b>khoá</b> mà biểu đồ dùng để nối điểm.
         */
        private String date;

        /** Doanh thu của ngày đó, số nguyên VNĐ; {@code 0} khi ngày đó không có đơn nào. */
        private long revenue;
    }

    /**
     * Một cột của biểu đồ đơn theo trạng thái. Xem {@link DailyRevenueResponse} về việc vì sao nó
     * là nested class.
     */
    @Data
    @Accessors(chain = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusCountResponse {

        /**
         * {@code pending} / {@code confirmed} / {@code shipping} / {@code delivered} /
         * {@code cancelled} — <b>chuỗi trên dây</b>, không phải con số của DB. Bảng dịch nằm ở
         * {@code OrderMapper} và chỉ ở đó.
         */
        private String status;

        /** Số đơn ở trạng thái đó trong khoảng; {@code 0} khi không có đơn nào. */
        private long count;
    }
}
