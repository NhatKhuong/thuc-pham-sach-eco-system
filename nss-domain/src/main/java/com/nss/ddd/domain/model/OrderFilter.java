package com.nss.ddd.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Điều kiện lọc + phân trang của {@code GET /admin/orders} (API_CONTRACT §B.12.2).
 * <p>
 * Cùng khuôn với {@link ProductFilter} và cùng lý do: năm tham số luôn đi cùng nhau qua bốn tầng,
 * và năm tham số rời trên một chữ ký là năm chỗ để hoán vị nhầm hai giá trị cạnh nhau mà trình biên
 * dịch không phản đối.
 * <p>
 * <b>Không có trường {@code sort}, và sự vắng mặt đó là contract</b> (§B.12.2): thứ tự cố định là
 * {@code createdAt} giảm dần rồi {@code id} giảm dần. Đơn mới là đơn cần xử lý; thêm một ô sắp xếp
 * chỉ tạo ra một cách để bỏ sót đơn mới.
 * <p>
 * <b>{@link #keyword} mang hai nghĩa ở hai phía của domain service</b>, đúng như
 * {@code ProductFilter}: vào là chuỗi thô client gửi (còn nguyên dấu), ra là chuỗi <b>đã bỏ dấu và
 * hạ chữ thường</b> để so được với cột {@code full_name_normalized}. <b>Adapter tuyệt đối không
 * chuẩn hoá lại</b> — chuẩn hoá hai lần là hai bản sao của cùng một quy tắc.
 * <p>
 * Từ khoá đó được so khớp với <b>ba</b> trường: {@code code}, {@code shipping.fullNameNormalized}
 * và {@code shipping.phone} — đúng ba thứ nhân viên có trong tay khi khách gọi tới, và cả ba lấy
 * từ <i>đơn</i> chứ không từ hồ sơ tài khoản (đơn khách vãng lai không có tài khoản nào để tra).
 * <p>
 * Trường rỗng ({@code null}) nghĩa là <b>không lọc theo tiêu chí đó</b>.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class OrderFilter {

    /**
     * Trạng thái <b>không đơn nào có thể mang</b> — nghĩa là "client hỏi một trạng thái không tồn
     * tại", và câu trả lời đúng là <b>tập rỗng</b>.
     * <p>
     * <b>Vì sao không rơi về {@code null} (tức bỏ lọc) như {@code ProductControllerMapper} làm với
     * {@code stockStatus}:</b> hai ca này khác nhau ở <i>chiều sai</i>. Một {@code stockStatus} lạ
     * bỏ lọc thì admin thấy <i>toàn bộ</i> sản phẩm — đúng thứ frontend cũng làm
     * ({@code default: break}). Còn ở đây frontend làm điều ngược lại: {@code applyFilters} của
     * {@code adminOrders.api.ts:59-61} so bằng {@code order.status === query.status}, nên một
     * trạng thái lạ khớp <b>không dòng nào</b>. Bỏ lọc ở backend sẽ trả về <i>mọi</i> đơn cho một
     * câu hỏi kiểu "cho tôi các đơn ở trạng thái {@code xong_roi}" — một câu trả lời sai trông y
     * hệt một câu trả lời đúng.
     * <p>
     * Dùng một con số ngoài dải {@code 0..4} chứ không thêm một cờ boolean thứ hai: bộ lọc vẫn là
     * một phép so bằng duy nhất, và không có nhánh SQL nào mọc thêm.
     */
    public static final int STATUS_NONE = -1;

    /** Từ khoá tìm kiếm; {@code null} là không tìm. Xem javadoc cấp class về hai nghĩa của nó. */
    private String keyword;

    /**
     * Trạng thái cần lọc — con số {@code 0..4} của cột {@code status}, không phải chuỗi trên dây.
     * <p>
     * Phép dịch {@code "pending"} {@literal ->} {@code 0} nằm ở {@code OrderMapper} (tầng
     * application) vì đó là bảng dịch duy nhất của dự án; domain chỉ làm việc với con số.
     * {@code null} là không lọc — <b>và một chuỗi lạ cũng phải dừng ở tầng trên chứ không được
     * biến thành {@code null} ở đây</b>, nếu không {@code status=xong_roi} sẽ trả về mọi đơn.
     */
    private Integer status;

    /**
     * Chủ đơn cần lọc; {@code null} là không lọc.
     * <p>
     * <b>Đây là bộ lọc hợp lệ ở namespace {@code /admin} và chỉ ở đó</b> (§C.4.3b) — nó là lý do
     * namespace này tồn tại, và là lý do {@code /orders/me} không bao giờ cần mọc {@code ?userId=}.
     */
    private Long userId;

    /** Trang cần lấy, <b>đánh số từ 1</b> (§A.4). Phép trừ 1 nằm ở adapter. */
    private int page;

    /** Số phần tử tối đa mỗi trang. */
    private int limit;

    /**
     * Static factory — không {@code new} trực tiếp ở call site (coding-conventions §7).
     *
     * @param keyword từ khoá tìm kiếm, có thể {@code null}
     * @param status con số trạng thái, có thể {@code null}
     * @param userId chủ đơn, có thể {@code null}
     * @param page trang, đánh số từ 1
     * @param limit số phần tử mỗi trang
     * @return điều kiện lọc đã dựng xong
     */
    public static OrderFilter of(String keyword, Integer status, Long userId, int page, int limit) {
        return new OrderFilter()
                .setKeyword(keyword)
                .setStatus(status)
                .setUserId(userId)
                .setPage(page)
                .setLimit(limit);
    }
}
