package com.nss.ddd.controller.http;

import com.nss.ddd.application.model.response.CategoryResponse;
import com.nss.ddd.application.service.category.CategoryAppService;
import com.nss.ddd.controller.exception.CategoryNotFoundException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Biên REST của danh mục — API_CONTRACT §B.2.
 * <p>
 * Trả DTO trần, <b>không bọc {@code ResultMessage}</b> (ADR 0001), giống {@link ProductController}.
 * <p>
 * <b>CHỈ ĐỌC, và chỉ công khai.</b> Khái niệm danh mục đã tồn tại ở backend từ backlog 0008 (entity,
 * repository, mapper ở tầng domain/infrastructure), phục vụ {@code categoryId} của sản phẩm — ticket
 * này (backlog 0024 §B.2) chỉ <b>phơi</b> nó ra HTTP, không dựng khái niệm mới. Không có endpoint
 * ghi nào ở đây và cũng không có kế hoạch thêm: quản trị danh mục nằm ngoài phạm vi §B.2.
 * <p>
 * <b>Ba đường công khai dùng chung một tiền tố {@code /api/categories}</b>, nên chỉ cần một dòng
 * {@code permitAll} kiểu mẫu ở {@code SecurityConfig} (giống {@code PATHS_PRODUCT_READ}), không
 * phải khai riêng từng đường.
 * <p>
 * Mọi {@code @RequestParam} / {@code @PathVariable} đều <b>khai tên tường minh</b> — cùng lý do đã
 * ghi ở {@link ProductController}: BOM {@code spring-boot-dependencies} không bật cờ
 * {@code -parameters}, nên tên tham số không còn trong bytecode và springdoc cần tên khai tường minh
 * để không hiện {@code arg0}.
 */
@Slf4j
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Danh mục",
        description = "Đọc cây danh mục sản phẩm — công khai, không cần token. "
                + "Không có endpoint ghi nào; quản trị danh mục nằm ngoài phạm vi hiện tại.")
public class CategoryController {

    private static final String MESSAGE_CATEGORY_NOT_FOUND = "Không tìm thấy danh mục bạn đang tìm.";

    /** Mô tả dùng lại cho mọi response lỗi: mọi lỗi đều là ProblemDetail RFC 7807. */
    private static final String PROBLEM_JSON = "application/problem+json";

    private final CategoryAppService categoryAppService;

    /**
     * <b>Một handler cho cả {@code GET /categories} và {@code GET /categories?root=true}</b> — hai
     * hàm frontend ({@code getCategories}, {@code getRootCategories}) cùng trả {@code Category[]},
     * nên không cần hai {@code @GetMapping} phân biệt bằng {@code params} như cặp
     * {@code getProducts}/{@code getProductsByIds} ở {@link ProductController} (nơi hình dạng
     * response khác nhau). Ở đây chỉ cần rẽ nhánh nội bộ theo {@code root}.
     *
     * @param root {@code true} thì chỉ trả danh mục gốc; vắng mặt hoặc bất kỳ giá trị nào khác
     *             {@code true} thì trả toàn bộ cây (phẳng)
     * @return {@code Category[]} theo §B.2
     */
    @Operation(summary = "Danh sách danh mục",
            description = """
                    Trả toàn bộ danh mục dạng danh sách phẳng, sắp theo tên.

                    - **`root=true`**: chỉ trả danh mục gốc (`parentId = null`).
                    - Vắng tham số `root`, hoặc `root` khác `true`: trả **toàn bộ** cây, gồm cả \
                    danh mục con.
                    - **`productCount`** là số sản phẩm còn hiệu lực thuộc danh mục; với danh mục \
                    gốc, con số này **gồm cả sản phẩm của danh mục con một cấp**.
                    - **`parentId`** là `null` với danh mục gốc — frontend tự dựng cây từ danh sách \
                    phẳng này.""")
    @ApiResponse(responseCode = "200", description = "Danh sách danh mục; rỗng khi chưa có danh mục nào")
    @GetMapping
    public List<CategoryResponse> getCategories(
            @Parameter(description = "`true` để chỉ lấy danh mục gốc; vắng mặt lấy toàn bộ.",
                    example = "true")
            @RequestParam(name = "root", required = false) Boolean root) {
        log.info("CategoryController:->getCategories | root={}", root);
        if (Boolean.TRUE.equals(root)) {
            return categoryAppService.findRootCategories();
        }
        return categoryAppService.findAll();
    }

    /**
     * @param slug slug của danh mục
     * @return danh mục
     * @throws CategoryNotFoundException khi slug không tồn tại
     */
    @Operation(summary = "Chi tiết một danh mục theo slug",
            description = "Tra danh mục bằng `slug` — chuỗi không dấu nối bằng gạch ngang.")
    @ApiResponse(responseCode = "200", description = "Danh mục khớp slug")
    @ApiResponse(responseCode = "404",
            description = "Slug không tồn tại; `detail` viết tiếng Việt",
            content = @Content(mediaType = PROBLEM_JSON,
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{slug}")
    public CategoryResponse getCategoryBySlug(
            @Parameter(description = "Slug của danh mục, ví dụ `rau-cu`.", example = "rau-cu")
            @PathVariable("slug") String slug) {
        log.info("CategoryController:->getCategoryBySlug | slug={}", slug);
        CategoryResponse category = categoryAppService.findBySlug(slug);
        if (category == null) {
            throw new CategoryNotFoundException(MESSAGE_CATEGORY_NOT_FOUND);
        }
        return category;
    }
}
