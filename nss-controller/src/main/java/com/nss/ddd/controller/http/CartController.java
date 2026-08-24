package com.nss.ddd.controller.http;

import com.nss.ddd.application.model.response.CartIssueResponse;
import com.nss.ddd.application.service.cart.CartAppService;
import com.nss.ddd.controller.dto.ValidateCartRequest;
import com.nss.ddd.controller.mapper.CartControllerMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Biên REST của việc đối chiếu giỏ hàng — API_CONTRACT §B.6.
 * <p>
 * Trả DTO trần, <b>không bọc {@code ResultMessage}</b> (ADR 0001, giống {@code ProductController} và
 * {@code CouponController}).
 * <p>
 * <b>Endpoint công khai.</b> Giỏ hàng sống trong localStorage của khách vãng lai và phải đối chiếu
 * được trước khi đăng nhập — §B.6 đánh dấu {@code validateCart} là ⬜. Đó không phải lựa chọn của
 * file này mà của {@code SecurityConfig}; method bên dưới cố ý <b>không</b> khai
 * {@code @SecurityRequirement} để Swagger nói đúng sự thật đó.
 * <p>
 * <b>Không có đường ghi nào ở đây, và đó là một ràng buộc chứ không phải chỗ còn thiếu.</b>
 * {@code POST} vì nó mang body — một giỏ 30 món không nhét vừa query string, và một danh sách object
 * lồng nhau thì không có cách mã hoá nào lên URL mà không tự chế quy ước. Nó <i>không</i> trừ kho,
 * không giữ chỗ, không ghi một dòng nào: frontend gọi lại nó mỗi lần khách mở giỏ hàng, nên một tác
 * dụng phụ ở đây sẽ nhân lên theo số lần người dùng bấm. Việc trừ kho thuộc phase 3, trong cùng
 * transaction với INSERT đơn.
 * <p>
 * <b>Endpoint này không có ca lỗi nghiệp vụ nào</b> — cột Lỗi của §B.6 để trống, và đó là chủ ý:
 * mọi tình huống "giỏ có vấn đề" đều là <i>dữ liệu</i> trong mảng trả về, không phải mã HTTP. Giỏ
 * rỗng trả 200 kèm {@code []}; sản phẩm không tồn tại trả 200 kèm một issue {@code out_of_stock}.
 * 404 hay 409 ở đây sẽ buộc frontend xử lý hai đường cho cùng một thứ.
 * <p>
 * Mọi {@code @RequestParam} / {@code @PathVariable} phải <b>khai tên tường minh</b> vì dự án không
 * bật cờ {@code -parameters} (xem javadoc {@code ProductController}). Method dưới đây không có tham
 * số nào như vậy, nhưng luật vẫn áp dụng khi thêm ca mới.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Giỏ hàng",
        description = "Đối chiếu giỏ hàng của khách với tồn kho và giá thật. Công khai, chỉ đọc.")
public class CartController {

    /** Mô tả dùng lại cho mọi response lỗi: mọi lỗi đều là ProblemDetail RFC 7807. */
    private static final String PROBLEM_JSON = "application/problem+json";

    private final CartAppService cartAppService;

    /**
     * @param request body đã qua validate
     * @return các vấn đề của giỏ, giữ đúng thứ tự dòng client gửi; mảng rỗng nghĩa là giỏ hợp lệ
     */
    @Operation(summary = "Đối chiếu giỏ hàng với tồn kho và giá hiện tại",
            description = """
                    Kiểm từng dòng giỏ hàng với dữ liệu thật trong database và trả về danh sách vấn \
                    đề. **Mảng rỗng nghĩa là giỏ hợp lệ** — không phải `null`, không phải object bọc.

                    Ba loại `type`, mỗi loại mang đúng tập trường của nó; **trường không áp dụng \
                    vắng mặt khỏi JSON**, không trả về 0:

                    - `out_of_stock` — không tìm thấy sản phẩm, **hoặc** hết hàng, **hoặc** đã bị \
                    gỡ khỏi hệ thống. Không mang trường tuỳ chọn nào.
                    - `insufficient_stock` — kèm `availableStock` là tồn kho **thật đọc từ DB**.
                    - `price_changed` — kèm `currentPrice` (giá hiện tại) và `cartPrice` (giá giỏ \
                    hàng đang hiển thị).

                    **Một dòng có thể sinh hai vấn đề** — vừa `insufficient_stock` vừa \
                    `price_changed`. Chỉ `out_of_stock` là loại trừ: một sản phẩm không mua được \
                    thì việc giá nó đổi bao nhiêu là thông tin vô nghĩa. Thứ tự trả về bám theo \
                    thứ tự dòng gửi lên, và trong cùng một dòng thì `insufficient_stock` đứng trước.

                    **Backend bỏ qua `stock`, `originalPrice`, `slug`, `image`, `unit` client gửi \
                    lên.** Tồn kho và giá luôn đọc lại từ database; `price` chỉ dùng để so sánh và \
                    dội lại trong `cartPrice`. `name` thì ngược lại — lấy từ chính dòng client gửi, \
                    vì một sản phẩm đã bị gỡ thì không còn tên nào trong database để tra.

                    **Giỏ rỗng (`items: []`) trả 200 kèm `[]`**, không phải 400.

                    **Endpoint này chỉ đọc.** Nó không trừ kho và không giữ chỗ; tồn kho chỉ thay \
                    đổi khi đơn hàng thật sự được tạo.""")
    @ApiResponse(responseCode = "200",
            description = "Danh sách vấn đề của giỏ; mảng rỗng khi giỏ hợp lệ")
    @ApiResponse(responseCode = "422",
            description = """
                    Body không hợp lệ — thiếu `items`, hoặc một dòng thiếu `productId` / `name` / \
                    `quantity` / `price`, hoặc `quantity` không dương. Kèm phần mở rộng `errors` \
                    map `tên trường → thông điệp`.

                    Đây là lỗi **hình dạng request**, không phải lỗi nghiệp vụ: một giỏ hợp lệ về \
                    cú pháp luôn trả 200, kể cả khi mọi món trong đó đều hết hàng.""",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/cart/validate")
    public List<CartIssueResponse> validateCart(@Valid @RequestBody ValidateCartRequest request) {
        log.info("CartController:->validateCart | itemCount={}", request.getItems().size());
        return cartAppService.validateCart(CartControllerMapper.toCommands(request.getItems()));
    }
}
