package com.nss.ddd.controller.mapper;

import com.nss.ddd.application.model.command.CreateProductCommand;
import com.nss.ddd.application.model.command.UpdateProductCommand;
import com.nss.ddd.controller.dto.CreateProductRequest;
import com.nss.ddd.controller.dto.UpdateProductRequest;
import com.nss.ddd.domain.model.ProductFilter;
import com.nss.ddd.domain.model.ProductSort;
import com.nss.ddd.domain.model.StockStatus;

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
