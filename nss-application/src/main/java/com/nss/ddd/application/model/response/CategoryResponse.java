package com.nss.ddd.application.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Payload của một danh mục trên bề mặt dây (API_CONTRACT §B.2).
 * <p>
 * DTO trần, không bọc {@code ResultMessage} — ADR 0001.
 * <p>
 * <b>{@code productCount} do backend tính, không phải cột lưu sẵn</b> — javadoc cấp class của entity
 * {@code Category} giải thích lý do: với danh mục gốc con số này phải gồm cả sản phẩm của danh mục
 * con, và một cột lưu sẵn sẽ sai ngay khi thêm/bớt một sản phẩm ở nhánh con. Xem
 * {@code CategoryDomainService#countProducts}.
 * <p>
 * <b>{@code parentId} không nằm trong §B.2 dạng bảng của mirror doc</b> (tài liệu chỉ ghim
 * {@code productCount}), nhưng frontend cần nó để dựng cây từ danh sách phẳng của
 * {@code GET /categories} — không có nó thì {@code getCategories()} và
 * {@code getRootCategories()} trả về hai kiểu dữ liệu không thể ghép lại thành một cây. Suy trực
 * tiếp từ entity {@code Category.parent}, không phải một trường bịa thêm.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {

    private Long id;

    private String slug;

    private String name;

    private String description;

    /** Đường dẫn ảnh <b>tương đối</b> dạng {@code /images/...} (§A.5); {@code null} khi không có ảnh. */
    private String image;

    /** {@code null} khi đây là danh mục gốc. */
    private Long parentId;

    /**
     * Số sản phẩm còn hiệu lực thuộc danh mục này — với danh mục gốc, gồm cả sản phẩm của danh mục
     * con một cấp (§B.2).
     */
    private long productCount;
}
