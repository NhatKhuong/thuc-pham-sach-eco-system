package com.nss.ddd.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Body của {@code POST /api/products/{id}/reviews} — khớp
 * {@code types/product.ts#CreateReviewPayload} (API_CONTRACT §B.8).
 * <p>
 * <b>{@link #productId} được NHẬN nhưng KHÔNG được dùng, và đó là một quyết định chứ không phải
 * một chỗ quên.</b> Đường dẫn đã mang {@code productId}, và Owner chốt 2026-08-26: <b>path là
 * nguồn chân lý, giá trị trong body bị bỏ qua trong im lặng, không báo lỗi</b>. Nhận theo body thì
 * {@code POST /api/products/7/reviews} kèm {@code {"productId": 9}} sẽ ghi đánh giá vào sản phẩm 9
 * và vẫn trả 201 — một chỗ dựng nhầm hoàn toàn im lặng. Trường vẫn nằm ở đây vì hợp đồng khai nó và
 * frontend vẫn gửi; bỏ hẳn nó khỏi payload là việc của board frontend (đã gửi qua CROSS-BOARD).
 * Kỷ luật "chặn cứng, không báo lỗi" lấy từ {@code rating} / {@code reviewCount} / {@code sold} ở
 * backlog 0008.
 * <p>
 * <b>Không có trường {@code userId}, và sẽ không bao giờ có</b> — danh tính lấy từ claim
 * {@code sub} của access token (ADR 0008, §C.4.1). Nhận nó từ client là để ai cũng đánh giá hộ
 * người khác được.
 * <p>
 * <b>{@code authorName} vẫn là chuỗi người dùng tự khai</b> — nó là <i>tên hiển thị</i>, không phải
 * danh tính, nên nó có thể khác tên trên tài khoản và ADR 0008 cố ý không kiểm.
 * <p>
 * <b>Giới hạn {@code @Size} lấy đúng độ dài cột</b>, không phải con số chọn cho đẹp — cùng lý do đã
 * viết ở {@code ShippingInfoRequest}: thiếu chúng thì một chuỗi quá dài đi qua được tầng validate
 * rồi hỏng ở tầng dưới. Ở endpoint này hậu quả còn tệ hơn một lỗi 500, vì đường ghi dùng
 * {@code INSERT IGNORE}: một chuỗi quá dài sẽ bị <b>cắt bớt trong im lặng</b> và vẫn trả 201.
 * <p>
 * Validation dùng <b>{@code jakarta.validation}</b>; thông điệp viết <b>tiếng Việt</b> ngay từ bản
 * đầu (coding-conventions §1) — Spring đặt chúng vào map {@code errors} của response <b>422</b> và
 * frontend dán thẳng từng câu vào ô nhập tương ứng, nên người đọc là <i>người dùng cuối</i>.
 * <p>
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} khai <b>tường minh</b> điều Spring Boot vốn
 * đã đặt mặc định, cùng lý do đã viết ở {@code CartItemRequest}.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateReviewRequest {

    /**
     * <b>BỊ BỎ QUA.</b> Sản phẩm được xác định bằng {@code {id}} trên đường dẫn — xem javadoc cấp
     * class. Không có ràng buộc validate nào ở đây: một giá trị sai cũng không gây hậu quả gì, vì
     * không chỗ nào đọc nó.
     */
    @Schema(description = "**Bị bỏ qua.** Sản phẩm được xác định bằng `{id}` trên đường dẫn; "
            + "giá trị gửi ở đây không được dùng và cũng không gây lỗi.",
            example = "11")
    private Long productId;

    @Schema(description = "Tên hiển thị của người đánh giá, do người dùng tự khai. "
            + "**Không phải danh tính** — danh tính lấy từ access token.",
            example = "Nguyễn Thị Mai", maxLength = 128)
    @NotBlank(message = "Vui lòng nhập tên hiển thị của bạn.")
    @Size(max = 128, message = "Tên hiển thị không được vượt quá 128 ký tự.")
    private String authorName;

    /**
     * Điểm đánh giá, số nguyên 1–5.
     * <p>
     * <b>{@code @NotNull} tách khỏi {@code @Min}/{@code @Max} là bắt buộc:</b> hai annotation dải
     * giá trị <i>bỏ qua</i> {@code null} theo đúng đặc tả Bean Validation, nên thiếu
     * {@code @NotNull} thì một body không có trường {@code rating} sẽ qua được tầng validate và
     * chết ở cột {@code NOT NULL} phía dưới.
     */
    @Schema(description = "Điểm đánh giá — số nguyên từ **1 đến 5**.",
            example = "5", minimum = "1", maximum = "5")
    @NotNull(message = "Vui lòng chọn số sao đánh giá.")
    @Min(value = 1, message = "Số sao đánh giá phải từ 1 đến 5.")
    @Max(value = 5, message = "Số sao đánh giá phải từ 1 đến 5.")
    private Integer rating;

    /**
     * Nội dung đánh giá, tối thiểu 10 ký tự (§B.8).
     * <p>
     * <b>Cận trên 5.000 ký tự không đến từ hợp đồng — nó đến từ cột.</b> {@code content} là
     * {@code TEXT}, tức 65.535 <i>byte</i>, mà một ký tự tiếng Việt chiếm tới 3 byte; 5.000 ký tự
     * nằm an toàn dưới trần đó với biên rộng. Không có cận trên thì một nội dung dài bất thường bị
     * {@code INSERT IGNORE} <b>cắt bớt trong im lặng</b> và endpoint vẫn trả 201.
     */
    @Schema(description = "Nội dung đánh giá — **tối thiểu 10 ký tự**.",
            example = "Cam mọng nước, vị ngọt đậm có chút chua nhẹ rất vừa miệng.",
            minLength = 10, maxLength = 5000)
    @NotBlank(message = "Vui lòng nhập nội dung đánh giá.")
    @Size(min = 10, max = 5000, message = "Nội dung đánh giá phải từ 10 đến 5000 ký tự.")
    private String content;
}
