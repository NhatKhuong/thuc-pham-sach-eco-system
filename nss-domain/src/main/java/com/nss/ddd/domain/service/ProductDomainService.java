package com.nss.ddd.domain.service;

import com.nss.ddd.domain.model.PageResult;
import com.nss.ddd.domain.model.entity.Brand;
import com.nss.ddd.domain.model.entity.Category;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.domain.model.entity.ProductImage;

import java.util.List;
import java.util.Map;

/**
 * Domain service của aggregate {@code Product} — nơi ở của quy tắc nghiệp vụ.
 * <p>
 * Chỉ biết port ({@code ProductRepository}, {@code ProductImageRepository},
 * {@code CategoryRepository}, {@code BrandRepository}), không biết adapter nào đang được nối vào.
 * <p>
 * <b>Thất bại nghiệp vụ ở đây là giá trị trả về, không phải exception</b>
 * (coding-conventions §11 Pattern A): {@code null} / {@code false} thay cho "không tồn tại" và
 * "không hợp lệ". Việc dịch chúng thành mã HTTP là của tầng controller — kiểu {@code *Exception}
 * sống ở module controller (§3) nên domain không thể, và không nên, ném chúng.
 */
public interface ProductDomainService {

    /**
     * Một trang sản phẩm còn hiệu lực.
     *
     * @param page số trang, <b>đánh số từ 1</b>
     * @param limit số phần tử mỗi trang
     * @return các phần tử của trang kèm tổng số sản phẩm còn hiệu lực
     */
    PageResult<Product> findPage(int page, int limit);

    /**
     * @param slug slug cần tra
     * @return sản phẩm còn hiệu lực, hoặc {@code null} khi không tồn tại / đã xoá mềm
     */
    Product findBySlug(String slug);

    /**
     * @param id khóa chính
     * @return sản phẩm còn hiệu lực, hoặc {@code null} khi không tồn tại / đã xoá mềm
     */
    Product findById(Long id);

    /**
     * @param slug slug cần kiểm
     * @return true nếu slug đã có người giữ — kể cả bản ghi đã xoá mềm, vì {@code uk_slug} nằm
     *         trên toàn bảng
     */
    boolean hasSlugTaken(String slug);

    /**
     * Quy tắc giá: {@code salePrice} được phép rỗng (nghĩa là không giảm giá, §A.5 cấm dùng 0 hay
     * chuỗi rỗng thay cho {@code null}); có giá trị thì phải <b>nhỏ hơn</b> {@code price}.
     *
     * @param price giá gốc
     * @param salePrice giá khuyến mãi, có thể {@code null}
     * @return true nếu cặp giá hợp lệ
     */
    boolean hasValidSalePrice(Long price, Long salePrice);

    /**
     * @param id khóa chính của danh mục
     * @return danh mục, hoặc {@code null} khi id không tồn tại
     */
    Category findCategoryById(Long id);

    /**
     * @param id khóa chính của thương hiệu
     * @return thương hiệu, hoặc {@code null} khi id không tồn tại
     */
    Brand findBrandById(Long id);

    /**
     * Tạo sản phẩm mới: điền {@code nameNormalized}, {@code createdAt} / {@code updatedAt} theo
     * <b>giờ UTC</b>, {@code isActive = true}, và các cột thống kê ({@code rating},
     * {@code reviewCount}, {@code sold}) về mốc 0 — chúng do luồng đánh giá / đặt hàng sinh ra,
     * không nhận từ client.
     *
     * @param draft bản nháp dựng từ command, chưa có id và chưa có trường server tự tính
     * @param category danh mục đã phân giải, bắt buộc
     * @param brand thương hiệu đã phân giải, có thể {@code null}
     * @return sản phẩm đã ghi, đã có id
     */
    Product create(Product draft, Category category, Brand brand);

    /**
     * Cập nhật sản phẩm đã có: tính lại {@code nameNormalized} và đặt {@code updatedAt} theo giờ UTC.
     * Không đụng tới {@code createdAt}, các cột thống kê, hay {@code isActive}.
     *
     * @param product sản phẩm đã nạp từ DB và đã áp các trường mới
     * @param category danh mục đã phân giải, bắt buộc
     * @param brand thương hiệu đã phân giải, có thể {@code null}
     * @return sản phẩm sau khi ghi
     */
    Product update(Product product, Category category, Brand brand);

    /**
     * Xoá mềm — đặt {@code is_active = false}, dòng vẫn nằm nguyên trong bảng.
     *
     * @param id khóa chính
     * @return true nếu có đúng một dòng chuyển trạng thái
     */
    boolean softDelete(Long id);

    /**
     * @param productId khóa chính của sản phẩm
     * @return ảnh của sản phẩm, đã sắp theo thứ tự hiển thị
     */
    List<ProductImage> findImages(Long productId);

    /**
     * Ảnh của nhiều sản phẩm, gom sẵn theo {@code productId} — đường đọc của trang danh sách.
     *
     * @param productIds tập khóa chính
     * @return map {@code productId -> danh sách ảnh đã sắp thứ tự}; sản phẩm không có ảnh thì
     *         không có khóa trong map
     */
    Map<Long, List<ProductImage>> findImagesGroupedByProductId(List<Long> productIds);

    /**
     * Thay trọn mảng ảnh của một sản phẩm bằng danh sách đường dẫn mới.
     * <p>
     * {@code sortOrder} lấy theo đúng vị trí trong danh sách — thứ tự client gửi lên là thứ tự
     * hiển thị trong gallery.
     *
     * @param product sản phẩm sở hữu ảnh, đã có id
     * @param urls danh sách đường dẫn tương đối; {@code null} hoặc rỗng nghĩa là xoá hết ảnh
     * @return các bản ghi ảnh sau khi ghi
     */
    List<ProductImage> replaceImages(Product product, List<String> urls);
}
