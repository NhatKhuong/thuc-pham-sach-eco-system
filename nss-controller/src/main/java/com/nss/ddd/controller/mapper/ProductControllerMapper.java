package com.nss.ddd.controller.mapper;

import com.nss.ddd.application.model.command.CreateProductCommand;
import com.nss.ddd.application.model.command.UpdateProductCommand;
import com.nss.ddd.controller.dto.CreateProductRequest;
import com.nss.ddd.controller.dto.UpdateProductRequest;
import com.nss.ddd.controller.exception.InvalidFilterValueException;
import com.nss.ddd.domain.model.ProductFilter;
import com.nss.ddd.domain.model.ProductSort;
import com.nss.ddd.domain.model.PublicProductFilter;
import com.nss.ddd.domain.model.StockStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Converter ở ranh giới HTTP: {@code *Request} của controller sang {@code *Command} của application
 * (coding-conventions §7).
 * <p>
 * Class stateless, method {@code public static}, không phải Spring bean, luôn null-guard.
 * Không {@code BeanUtils.copyProperties} — hai kiểu trùng tên trường hôm nay không có nghĩa là
 * chúng sẽ trùng mãi, và bản chép ngầm sẽ im lặng khi chúng tách ra.
 * <p>
 * <b>Đây cũng là chỗ duy nhất dịch giá trị chuỗi trên dây thành enum của domain.</b> Hai enum
 * {@link StockStatus} và {@link ProductSort} dùng quy ước {@code UPPER_SNAKE} của Java, còn dây
 * dùng {@code lower_snake} của TypeScript ({@code 'low_stock'}, {@code 'price_asc'}); phép dịch là
 * một phép đổi hoa/thường, không phải một bảng ánh xạ phải bảo trì.
 * <p>
 * <b>Cố ý KHÔNG để Spring tự bind chuỗi sang enum.</b> Bộ chuyển đổi mặc định ném lỗi khi gặp giá
 * trị lạ và Spring biến nó thành <b>400</b>. Frontend thì coi giá trị lạ là chuyện bình thường —
 * {@code applySort} có nhánh {@code default}, {@code parseSort} ép về {@code 'newest'}, và
 * {@code applyFilters} có {@code default: break} cho {@code stockStatus}. Một 400 ở đây làm hỏng
 * một màn hình mà frontend coi là chạy đúng, chỉ vì ai đó sửa tay tham số trên URL.
 */
public final class ProductControllerMapper {

    /**
     * Class tiện ích, không có thể hiện.
     */
    private ProductControllerMapper() {
    }

    /**
     * Gom sáu tham số truy vấn của {@code GET /api/admin/products} thành điều kiện lọc của domain.
     *
     * @param q từ khoá tìm kiếm; rỗng là không tìm
     * @param category slug danh mục; rỗng là không lọc
     * @param stockStatus trạng thái tồn kho dạng chuỗi trên dây; rỗng hoặc lạ là không lọc
     * @param sort thứ tự sắp xếp dạng chuỗi trên dây; rỗng hoặc lạ là {@link ProductSort#DEFAULT}
     * @param page trang, đánh số từ 1
     * @param limit số phần tử mỗi trang
     * @return điều kiện lọc của domain, không bao giờ {@code null}
     */
    public static ProductFilter toFilter(String q, String category, String stockStatus,
                                         String sort, int page, int limit) {
        return ProductFilter.of(
                toNullIfBlank(q),
                toNullIfBlank(category),
                toStockStatus(stockStatus),
                toProductSort(sort),
                page,
                limit);
    }

    /**
     * Chuỗi trên dây thành {@link StockStatus}.
     * <p>
     * Giá trị lạ cho ra {@code null} — tức <b>không lọc</b>, khớp nhánh {@code default: break} của
     * {@code applyFilters} ({@code adminProducts.api.ts:79-80}).
     *
     * @param value chuỗi trên dây, ví dụ {@code "low_stock"}
     * @return trạng thái tương ứng, hoặc {@code null} khi rỗng hoặc không nhận ra
     */
    public static StockStatus toStockStatus(String value) {
        String normalized = toNullIfBlank(value);
        if (normalized == null) {
            return null;
        }
        try {
            return StockStatus.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // Gia tri la KHONG phai loi: frontend co nhanh default cho chinh ca nay
            return null;
        }
    }

    /**
     * Chuỗi trên dây thành {@link ProductSort}.
     * <p>
     * Giá trị lạ cho ra {@link ProductSort#DEFAULT} chứ không {@code null} — khớp cả
     * {@code applySort} lẫn {@code parseSort} của frontend, cả hai đều rơi về {@code 'newest'}.
     *
     * @param value chuỗi trên dây, ví dụ {@code "price_asc"}
     * @return thứ tự tương ứng; {@link ProductSort#DEFAULT} khi rỗng hoặc không nhận ra
     */
    public static ProductSort toProductSort(String value) {
        String normalized = toNullIfBlank(value);
        if (normalized == null) {
            return ProductSort.DEFAULT;
        }
        try {
            return ProductSort.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // Gia tri la KHONG phai loi: frontend ep moi gia tri la ve 'newest'
            return ProductSort.DEFAULT;
        }
    }

    /**
     * Gộp {@code null} và chuỗi toàn khoảng trắng thành một tín hiệu duy nhất.
     * <p>
     * Cần thiết vì hai thứ đó tới đây bằng hai đường khác nhau — tham số vắng mặt cho {@code null},
     * còn {@code ?q=} trên URL cho chuỗi rỗng — nhưng chúng cùng nghĩa "không lọc". Không gộp thì
     * chuỗi rỗng đi tiếp xuống dưới và trở thành một điều kiện lọc thật.
     *
     * @param value chuỗi thô
     * @return chuỗi đã {@code trim}, hoặc {@code null} khi rỗng
     */
    private static String toNullIfBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /** Năm giá trị hợp lệ của {@code sort}, dùng để dựng thông điệp 422 — khớp {@link ProductSort}. */
    private static final String VALID_SORT_VALUES = "newest, price_asc, price_desc, best_selling, rating";

    /**
     * Gom mười hai tham số truy vấn của {@code GET /api/products} công khai thành điều kiện lọc của
     * domain (API_CONTRACT §B.1).
     *
     * @param q từ khoá tìm kiếm; rỗng là không tìm
     * @param category slug danh mục; rỗng là không lọc
     * @param minPrice giá thấp nhất theo {@code effectivePrice}
     * @param maxPrice giá cao nhất theo {@code effectivePrice}
     * @param minRating điểm đánh giá thấp nhất
     * @param inStockOnly chỉ hiện còn hàng
     * @param onSaleOnly chỉ hiện đang giảm giá
     * @param isFeatured chỉ hiện nổi bật
     * @param isBestSeller chỉ hiện bán chạy
     * @param sort thứ tự sắp xếp dạng chuỗi trên dây
     * @param page trang, đánh số từ 1
     * @param limit số phần tử mỗi trang
     * @return điều kiện lọc của domain, không bao giờ {@code null}
     * @throws InvalidFilterValueException khi {@code sort} có giá trị không nhận ra (ADR 0007 vế 1)
     */
    public static PublicProductFilter toPublicFilter(String q, String category, Long minPrice, Long maxPrice,
                                                      BigDecimal minRating, Boolean inStockOnly, Boolean onSaleOnly,
                                                      Boolean isFeatured, Boolean isBestSeller, String sort,
                                                      int page, int limit) {
        return PublicProductFilter.of(
                toNullIfBlank(q),
                toNullIfBlank(category),
                minPrice,
                maxPrice,
                minRating,
                inStockOnly,
                onSaleOnly,
                isFeatured,
                isBestSeller,
                toPublicProductSort(sort),
                page,
                limit);
    }

    /**
     * Chuỗi trên dây thành {@link ProductSort} — <b>khác {@link #toProductSort(String)}, cố ý</b>.
     * <p>
     * {@code sort} là tham số <b>tập đóng</b> của {@code GET /products} công khai (ADR 0007 vế 1):
     * vắng mặt/rỗng vẫn rơi về {@link ProductSort#DEFAULT} — đó không phải một giá trị rác, chỉ là
     * "client không chọn gì" — nhưng một giá trị <b>có mặt mà không nhận ra được</b> phải ném
     * {@link InvalidFilterValueException} để {@code GlobalExceptionHandler} dịch thành {@code 422}
     * kèm map {@code errors}. Bên quản trị ({@link #toProductSort(String)}) vẫn khoan dung như cũ —
     * đó là phạm vi backlog 0025, không phải ticket này.
     *
     * @param value chuỗi trên dây, ví dụ {@code "price_asc"}; {@code null}/rỗng nghĩa là "không chọn"
     * @return thứ tự tương ứng; {@link ProductSort#DEFAULT} khi rỗng
     * @throws InvalidFilterValueException khi {@code value} khác rỗng nhưng không khớp giá trị nào
     */
    public static ProductSort toPublicProductSort(String value) {
        String normalized = toNullIfBlank(value);
        if (normalized == null) {
            return ProductSort.DEFAULT;
        }
        try {
            return ProductSort.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidFilterValueException("sort",
                    "Giá trị sắp xếp không hợp lệ. Giá trị hợp lệ: " + VALID_SORT_VALUES + ".");
        }
    }

    /**
     * Chuỗi {@code ids} trên dây thành danh sách khóa chính — {@code GET /products?ids=1,2,3}
     * (API_CONTRACT §B.1).
     * <p>
     * <b>{@code ids} là tham số tập MỞ (ADR 0007 vế 2): token không phải số bị bỏ qua trong im lặng,
     * không ném lỗi.</b> Một link {@code ?ids=1,abc,3} không phải một request sai — nó là một request
     * mà một trong ba id không tồn tại dưới dạng số, và hệ quả đúng là "sản phẩm đó không có trong
     * kết quả" (giống hệt một id số không khớp dòng nào), không phải {@code 400}/{@code 422} chặn cả
     * hai id hợp lệ còn lại.
     *
     * @param raw chuỗi thô, ví dụ {@code "1,2,3"}; {@code null}/rỗng cho ra danh sách rỗng
     * @return các khóa chính hợp lệ đã parse; danh sách rỗng khi không token nào là số
     */
    public static List<Long> toIdList(String raw) {
        String normalized = toNullIfBlank(raw);
        if (normalized == null) {
            return List.of();
        }
        String[] tokens = normalized.split(",");
        List<Long> ids = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.valueOf(trimmed));
            } catch (NumberFormatException e) {
                // Token khong phai so: bo qua trong im lang (ADR 0007 ve 2), khong nem loi
            }
        }
        return ids;
    }

    /**
     * @param request body của {@code POST /api/products}
     * @return lệnh tạo, hoặc {@code null} khi {@code request} rỗng
     */
    public static CreateProductCommand toCommand(CreateProductRequest request) {
        if (request == null) {
            return null;
        }
        return new CreateProductCommand()
                .setSlug(request.getSlug())
                .setName(request.getName())
                .setShortDescription(request.getShortDescription())
                .setDescription(request.getDescription())
                .setPrice(request.getPrice())
                .setSalePrice(request.getSalePrice())
                .setUnit(request.getUnit())
                .setOrigin(request.getOrigin())
                .setStock(request.getStock())
                .setIsFeatured(request.getIsFeatured())
                .setIsBestSeller(request.getIsBestSeller())
                .setCategoryId(request.getCategoryId())
                .setBrandId(request.getBrandId())
                .setImages(request.getImages());
    }

    /**
     * @param request body của {@code PUT /api/products/{id}}
     * @return lệnh cập nhật, hoặc {@code null} khi {@code request} rỗng
     */
    public static UpdateProductCommand toCommand(UpdateProductRequest request) {
        if (request == null) {
            return null;
        }
        return new UpdateProductCommand()
                .setSlug(request.getSlug())
                .setName(request.getName())
                .setShortDescription(request.getShortDescription())
                .setDescription(request.getDescription())
                .setPrice(request.getPrice())
                .setSalePrice(request.getSalePrice())
                .setUnit(request.getUnit())
                .setOrigin(request.getOrigin())
                .setStock(request.getStock())
                .setIsFeatured(request.getIsFeatured())
                .setIsBestSeller(request.getIsBestSeller())
                .setCategoryId(request.getCategoryId())
                .setBrandId(request.getBrandId())
                .setImages(request.getImages());
    }
}
