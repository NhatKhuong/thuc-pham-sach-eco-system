package com.nss.ddd.application.mapper;

import com.nss.ddd.application.model.command.CreateProductCommand;
import com.nss.ddd.application.model.command.UpdateProductCommand;
import com.nss.ddd.application.model.response.ProductResponse;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.domain.model.entity.ProductImage;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * Converter viết tay giữa {@code Product} và các kiểu của tầng application.
 * <p>
 * Class stateless, method {@code public static}, <b>không phải Spring bean</b> và luôn null-guard
 * (coding-conventions §7). Không dùng MapStruct (architecture §2 cố ý loại) và không dùng
 * {@code BeanUtils.copyProperties} — đổi lấy tính minh bạch khi debug, và ở đây còn đổi lấy một
 * thứ nữa: danh sách trường được viết ra bằng tay chính là chỗ chặn {@code isActive} rò ra response.
 */
public final class ProductMapper {

    /**
     * Class tiện ích, không có thể hiện.
     */
    private ProductMapper() {
    }

    /**
     * Dựng bản nháp entity từ lệnh tạo.
     * <p>
     * Cố ý <b>không</b> đụng tới {@code nameNormalized}, {@code createdAt} / {@code updatedAt},
     * {@code isActive}, {@code rating}, {@code reviewCount}, {@code sold}, {@code category},
     * {@code brand} — tất cả đều do domain service điền.
     *
     * @param command lệnh tạo
     * @return bản nháp entity, hoặc {@code null} khi {@code command} rỗng
     */
    public static Product toEntity(CreateProductCommand command) {
        if (command == null) {
            return null;
        }
        return new Product()
                .setSlug(command.getSlug())
                .setName(command.getName())
                .setShortDescription(command.getShortDescription())
                .setDescription(command.getDescription())
                .setPrice(command.getPrice())
                .setSalePrice(command.getSalePrice())
                .setUnit(command.getUnit())
                .setOrigin(command.getOrigin())
                .setStock(command.getStock())
                .setIsFeatured(command.getIsFeatured())
                .setIsBestSeller(command.getIsBestSeller());
    }

    /**
     * Áp các trường của lệnh cập nhật lên một entity đã nạp từ DB.
     * <p>
     * Chỉ chạm đúng những trường người nhập; {@code createdAt}, các cột thống kê và {@code isActive}
     * giữ nguyên giá trị đang có trong DB.
     *
     * @param target entity đã nạp từ DB
     * @param command lệnh cập nhật
     * @return chính {@code target} sau khi áp, hoặc {@code null} khi một trong hai tham số rỗng
     */
    public static Product applyUpdate(Product target, UpdateProductCommand command) {
        if (target == null || command == null) {
            return null;
        }
        return target
                .setSlug(command.getSlug())
                .setName(command.getName())
                .setShortDescription(command.getShortDescription())
                .setDescription(command.getDescription())
                .setPrice(command.getPrice())
                .setSalePrice(command.getSalePrice())
                .setUnit(command.getUnit())
                .setOrigin(command.getOrigin())
                .setStock(command.getStock())
                .setIsFeatured(Boolean.TRUE.equals(command.getIsFeatured()))
                .setIsBestSeller(Boolean.TRUE.equals(command.getIsBestSeller()));
    }

    /**
     * Dựng payload cho bề mặt dây.
     *
     * @param product entity, đã nạp sẵn {@code category} và {@code brand}
     * @param images ảnh của sản phẩm, đã sắp theo thứ tự hiển thị; có thể rỗng
     * @return payload, hoặc {@code null} khi {@code product} rỗng
     */
    public static ProductResponse toResponse(Product product, List<ProductImage> images) {
        if (product == null) {
            return null;
        }
        return new ProductResponse()
                .setId(product.getId())
                .setSlug(product.getSlug())
                .setName(product.getName())
                .setPrice(product.getPrice())
                .setSalePrice(product.getSalePrice())
                .setImages(toImageUrls(images))
                .setCategoryId(product.getCategory() == null ? null : product.getCategory().getId())
                .setBrandId(product.getBrand() == null ? null : product.getBrand().getId())
                .setRating(product.getRating())
                .setReviewCount(product.getReviewCount())
                .setStock(product.getStock())
                .setSold(product.getSold())
                .setUnit(product.getUnit())
                .setOrigin(product.getOrigin())
                .setShortDescription(product.getShortDescription())
                .setDescription(product.getDescription())
                .setIsFeatured(product.getIsFeatured())
                .setIsBestSeller(product.getIsBestSeller())
                .setCreatedAt(toIsoUtc(product.getCreatedAt()));
    }

    /**
     * @param images bản ghi ảnh
     * @return danh sách đường dẫn tương đối; danh sách rỗng khi không có ảnh nào
     */
    private static List<String> toImageUrls(List<ProductImage> images) {
        if (images == null || images.isEmpty()) {
            return Collections.emptyList();
        }
        return images.stream()
                .map(ProductImage::getUrl)
                .toList();
    }

    /**
     * Cột lưu giờ UTC nên đóng dấu hậu tố {@code Z} vào chuỗi trả ra (§A.5) — thiếu nó, trình duyệt
     * đọc chuỗi như giờ địa phương và lệch 7 tiếng ở VN mà không có gì báo lỗi.
     *
     * @param value thời điểm lưu trong DB, hiểu là giờ UTC
     * @return chuỗi ISO 8601 dạng {@code 2026-07-02T00:00:00Z}, hoặc {@code null} khi đầu vào rỗng
     */
    private static String toIsoUtc(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return DateTimeFormatter.ISO_INSTANT.format(value.toInstant(ZoneOffset.UTC));
    }
}
