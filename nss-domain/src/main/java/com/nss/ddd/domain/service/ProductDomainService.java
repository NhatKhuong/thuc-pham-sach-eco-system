package com.nss.ddd.domain.service;

import com.nss.ddd.domain.model.PageResult;
import com.nss.ddd.domain.model.ProductFilter;
import com.nss.ddd.domain.model.StockStatus;
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
     * Một trang sản phẩm còn hiệu lực <b>có lọc và có sắp xếp</b> — {@code GET /admin/products}
     * (API_CONTRACT §B.12.1).
     * <p>
     * <b>Quy tắc nghiệp vụ nằm ở đây, không ở adapter: từ khoá {@code q} được bỏ dấu trước khi
     * xuống port.</b> Cột {@code name_normalized} lưu bản đã bỏ dấu, nên hai vế của phép so sánh
     * phải đi qua <i>cùng một</i> hàm chuẩn hoá. Chuẩn hoá ở adapter thì hàm ấy có hai bản sao, và
     * chúng lệch nhau vào đúng lúc chỉ một bên được sửa.
     *
     * @param filter điều kiện lọc, sắp xếp và phân trang; {@code keyword} còn nguyên dấu
     * @return các phần tử của trang kèm tổng số dòng khớp điều kiện lọc
     */
    PageResult<Product> findAdminPage(ProductFilter filter);

    /**
     * @param slug slug cần tra
     * @return sản phẩm còn hiệu lực, hoặc {@code null} khi không tồn tại / đã xoá mềm
     */
    Product findBySlug(String slug);

    /**
     * Số sản phẩm đang ở trạng thái <b>sắp hết hàng</b> — {@code lowStockCount} của §B.12.4.
     * <p>
     * <b>Dùng lại {@link StockStatus#LOW_STOCK} chứ không khai lại ngưỡng ở đâu khác.</b> Javadoc
     * của {@code StockStatus.LOW_STOCK_THRESHOLD} đã gọi đích danh ticket này: khai con số lần thứ
     * hai là tạo ra hai giá trị sẽ lệch nhau, và triệu chứng là ô chỉ số nói một đằng còn bộ lọc
     * {@code stockStatus=low_stock} ra một nẻo — <b>không lỗi nào nổ ra</b>.
     * <p>
     * Con số này đi qua <i>đúng</i> mệnh đề lọc mà {@code GET /admin/products} dùng, nên nó bằng
     * {@code total} của {@code ?stockStatus=low_stock} theo cấu tạo chứ không theo may mắn — xem
     * {@code ProductRepository.countAdminProducts}.
     * <p>
     * <b>Là ảnh chụp hiện tại, không phụ thuộc {@code days}</b> (§B.12.4): tồn kho chỉ có giá trị
     * "ngay lúc này".
     *
     * @return số sản phẩm còn bán có {@code 0 < stock <= 10}
     */
    long countLowStockProducts();

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
     * Slug cuối cùng của một sản phẩm — API_CONTRACT §B.12.1.
     * <p>
     * <b>Slug do client gửi CŨNG được slugify, không chỉ khi bỏ trống.</b> Đây là hành vi đo được ở
     * {@code adminProducts.api.ts:117} ({@code payload.slug?.trim() ? slugify(payload.slug) :
     * slugify(payload.name)}), không phải suy đoán: form quản trị cho admin gõ tự do vào ô slug,
     * nên "Cà Rốt Hữu Cơ" gõ vào ô đó phải ra {@code ca-rot-huu-co} chứ không phải một 422.
     * <p>
     * <b>Thuật toán là bảy bước theo đúng thứ tự này</b>, khớp {@code slugify} ở
     * {@code src/lib/utils.ts:21-32} — đổi thứ tự là đổi kết quả:
     * <ol>
     *   <li>tách tổ hợp NFD;</li>
     *   <li>bỏ dấu phụ;</li>
     *   <li>{@code đ} thành {@code d} và {@code Đ} thành {@code D} — NFD không tách được ký tự này
     *       vì nó là một chữ cái Latin riêng, không phải nguyên âm có dấu;</li>
     *   <li>hạ chữ thường;</li>
     *   <li>{@code trim} — <b>trước</b> bước đổi khoảng trắng, nếu không thì khoảng trắng hai đầu
     *       thành gạch ngang thừa;</li>
     *   <li>bỏ mọi ký tự ngoài {@code [a-z0-9], khoảng trắng, gạch ngang};</li>
     *   <li>khoảng trắng liên tiếp thành một gạch ngang, rồi gộp gạch ngang liên tiếp.</li>
     * </ol>
     * Bốn bước đầu <b>dùng lại đúng hàm sinh {@code name_normalized}</b> — hai bản sao của phép bỏ
     * dấu là hai thứ sẽ lệch nhau.
     * <p>
     * <b>Kết quả rỗng trả {@code null}, không trả chuỗi rỗng.</b> Một tên toàn ký tự bị loại (ví dụ
     * {@code "***"}) cho ra slug rỗng; ghi im lặng chuỗi rỗng xuống {@code uk_slug} nghĩa là sản
     * phẩm thứ hai như vậy chết bằng lỗi ràng buộc thay vì một thông điệp đọc được. Frontend cũng
     * ném lỗi ở đúng ca này ({@code adminProducts.api.ts:118}).
     *
     * @param requestedSlug slug client gửi; rỗng hoặc chỉ có khoảng trắng nghĩa là "tự sinh từ tên"
     * @param name tên hiển thị, dùng làm nguồn khi {@code requestedSlug} bỏ trống
     * @return slug đã chuẩn hoá, hoặc {@code null} khi không sinh ra được ký tự hợp lệ nào
     */
    String genSlug(String requestedSlug, String name);

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
