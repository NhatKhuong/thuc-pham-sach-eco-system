package com.nss.ddd.domain.model;

/**
 * Ba trạng thái tồn kho của bộ lọc {@code stockStatus} (API_CONTRACT §B.12.1).
 * <p>
 * <b>Đây là một PHÂN HOẠCH, không phải ba bộ lọc rời nhau.</b> Ba khoảng phủ kín trục
 * {@code stock} và không giao nhau: {@code (..0] + [1..10] + [11..)}. Vì vậy tổng số dòng của
 * ba bộ lọc phải đúng bằng tổng số sản phẩm còn hiệu lực — đó là phép kiểm duy nhất bắt được lỗi
 * ranh giới ở đây.
 * <p>
 * <b>Ranh giới của {@link #IN_STOCK} là {@code > 10}, KHÔNG phải {@code > 0}.</b> Đây là chỗ dễ
 * sai nhất của cả bộ lọc và nó hỏng <i>trong im lặng</i>: viết {@code > 0} thì {@link #LOW_STOCK}
 * và {@link #IN_STOCK} chồng lên nhau ở khoảng {@code [1, 10]}, tổng ba tập vượt tổng số sản phẩm,
 * mọi request vẫn trả 200, và không ràng buộc nào của cơ sở dữ liệu hay của trình biên dịch phản
 * đối. Cộng ba tập rồi so với tổng là cách duy nhất phát hiện ra.
 * <p>
 * <b>Ngưỡng khai ở đây và chỉ ở đây.</b> {@link #LOW_STOCK_THRESHOLD} khớp đúng hằng cùng tên của
 * frontend ({@code src/lib/constants.ts:97}), và {@code lowStockCount} của §B.12.4 sau này còn dùng
 * lại chính nó. Hai nơi giữ hai con số thì bộ lọc "sắp hết" trả một tập còn nhãn hiển thị trên từng
 * dòng nói khác — lệch âm thầm, không lỗi nào nổ ra.
 * <p>
 * Hai biên trả về dạng {@code Integer} nullable: {@code null} nghĩa là <b>không chặn phía đó</b>,
 * chứ không phải 0. Nhờ vậy adapter chỉ cần <i>một</i> câu truy vấn với hai tham số
 * {@code minStock} / {@code maxStock}, thay vì ba nhánh SQL rời nhau — mà ba nhánh rời nhau chính
 * là hình dạng để lọt một ranh giới sai vào đúng một nhánh.
 */
public enum StockStatus {

    /** Còn hàng — {@code stock > LOW_STOCK_THRESHOLD}. */
    IN_STOCK,

    /** Sắp hết — {@code 0 < stock <= LOW_STOCK_THRESHOLD}; hết hàng KHÔNG phải sắp hết. */
    LOW_STOCK,

    /** Hết hàng — {@code stock <= 0}. */
    OUT_OF_STOCK;

    /**
     * Ngưỡng "sắp hết hàng" — khớp {@code LOW_STOCK_THRESHOLD} của frontend
     * ({@code src/lib/constants.ts:97}). Là <b>chỗ duy nhất</b> giữ con số này ở phía backend.
     */
    public static final int LOW_STOCK_THRESHOLD = 10;

    /**
     * Biên dưới của khoảng, <b>đã bao gồm</b>.
     *
     * @return số tồn kho nhỏ nhất còn thuộc trạng thái này, hoặc {@code null} khi không chặn dưới
     */
    public Integer getMinStock() {
        return switch (this) {
            case IN_STOCK -> LOW_STOCK_THRESHOLD + 1;
            case LOW_STOCK -> 1;
            case OUT_OF_STOCK -> null;
        };
    }

    /**
     * Biên trên của khoảng, <b>đã bao gồm</b>.
     *
     * @return số tồn kho lớn nhất còn thuộc trạng thái này, hoặc {@code null} khi không chặn trên
     */
    public Integer getMaxStock() {
        return switch (this) {
            case IN_STOCK -> null;
            case LOW_STOCK -> LOW_STOCK_THRESHOLD;
            case OUT_OF_STOCK -> 0;
        };
    }
}
