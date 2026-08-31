package com.nss.ddd.application.service.product;

import com.nss.ddd.application.model.command.CreateProductCommand;
import com.nss.ddd.application.model.command.UpdateProductCommand;
import com.nss.ddd.application.model.response.PaginatedResponse;
import com.nss.ddd.application.model.response.PriceRangeResponse;
import com.nss.ddd.application.model.response.ProductMutationResponse;
import com.nss.ddd.application.model.response.ProductResponse;
import com.nss.ddd.domain.model.ProductFilter;
import com.nss.ddd.domain.model.PublicProductFilter;

import java.util.List;

/**
 * Use case CRUD sản phẩm — điều phối giữa domain service và các kiểu của bề mặt dây.
 * <p>
 * Không có quy tắc nghiệp vụ nào ở đây: quy tắc sống trong {@code ProductDomainService}, tầng này
 * chỉ hỏi domain rồi lắp kết quả thành response.
 * <p>
 * <b>Hai đường đọc danh sách CÓ PHÂN TRANG, phục vụ hai bề mặt khác nhau.</b> {@link #findProducts}
 * là trang cửa hàng công khai (§B.1): mười hai tham số lọc/sắp xếp/phân trang.
 * {@link #findAdminProducts(ProductFilter)} là bảng quản trị (§B.12.1): sáu tham số, nằm sau hàng
 * rào {@code /api/admin/**}. Chúng cố ý không dùng chung chữ ký — xem javadoc
 * {@code ProductRepository#findAdminPage} và {@code PublicProductFilter}.
 * <p>
 * Trang cửa hàng đọc bằng {@code slug}, khu quản trị đọc bằng {@code id} (§B.12.1): frontend dựng
 * URL cửa hàng từ slug, còn admin sửa được chính cái slug đó nên đường dẫn màn sửa không được treo
 * vào một trường có thể đổi.
 */
public interface ProductAppService {

    /**
     * Một trang sản phẩm còn hiệu lực, có lọc và có sắp xếp — {@code GET /products} công khai (§B.1).
     *
     * @param filter điều kiện lọc; {@code page} nhỏ hơn 1 được kéo về 1, {@code limit} nhỏ hơn 1
     *               được kéo về mặc định 12
     * @return trang sản phẩm theo dạng {@code Paginated<Product>} của §A.4
     */
    PaginatedResponse<ProductResponse> findProducts(PublicProductFilter filter);

    /**
     * Một trang sản phẩm cho bảng quản trị — có lọc, có sắp xếp (§B.12.1).
     *
     * @param filter điều kiện lọc; {@code page} nhỏ hơn 1 được kéo về 1, {@code limit} nhỏ hơn 1
     *               được kéo về mặc định 12
     * @return trang sản phẩm theo dạng {@code Paginated<Product>} của §A.4; {@code total} là tổng
     *         số dòng <b>khớp bộ lọc</b>
     */
    PaginatedResponse<ProductResponse> findAdminProducts(ProductFilter filter);

    /**
     * @param slug slug của sản phẩm
     * @return sản phẩm, hoặc {@code null} khi không tồn tại / đã bị xoá mềm
     */
    ProductResponse findProductBySlug(String slug);

    /**
     * Một sản phẩm theo <b>khóa chính</b> — đường đọc của {@code GET /admin/products/{id}}.
     * <p>
     * Khác {@link #findProductBySlug(String)} ở khoá tra cứu, không ở dữ liệu trả về: cùng
     * {@code ProductResponse}, cùng quy ước "đã xoá mềm thì coi như không tồn tại".
     *
     * @param id khóa chính
     * @return sản phẩm, hoặc {@code null} khi không tồn tại / đã bị xoá mềm
     */
    ProductResponse findProductById(Long id);

    /**
     * Tạo sản phẩm mới cùng mảng ảnh của nó trong <b>một</b> transaction.
     *
     * @param command lệnh tạo
     * @return kết quả mang sản phẩm đã tạo, hoặc mã lỗi nghiệp vụ kèm thông điệp tiếng Việt
     */
    ProductMutationResponse createProduct(CreateProductCommand command);

    /**
     * Cập nhật sản phẩm còn hiệu lực; mảng ảnh mới thay trọn mảng cũ, cùng một transaction.
     *
     * @param id khóa chính
     * @param command lệnh cập nhật
     * @return kết quả mang sản phẩm sau khi sửa, hoặc mã lỗi nghiệp vụ kèm thông điệp tiếng Việt
     */
    ProductMutationResponse updateProduct(Long id, UpdateProductCommand command);

    /**
     * Xoá mềm sản phẩm — đặt {@code is_active = false}, dòng vẫn nằm nguyên trong bảng.
     *
     * @param id khóa chính
     * @return true nếu có một sản phẩm còn hiệu lực vừa được xoá mềm; false khi không tìm thấy
     */
    boolean deleteProduct(Long id);

    /**
     * Nhiều sản phẩm còn hiệu lực theo một tập id — {@code GET /products?ids=} (§B.1).
     *
     * @param ids các khóa chính cần tra; {@code null} hoặc rỗng cho ra danh sách rỗng
     * @return các sản phẩm còn hiệu lực khớp {@code ids}; thứ tự không đảm bảo
     */
    List<ProductResponse> findProductsByIds(List<Long> ids);

    /**
     * Sản phẩm "liên quan" tới một sản phẩm gốc — {@code GET /products/{slug}/related} (§B.1).
     *
     * @param slug slug của sản phẩm gốc
     * @param limit số sản phẩm tối đa cần lấy; giá trị nhỏ hơn 1 được kéo về mặc định 4
     * @return danh sách sản phẩm liên quan, hoặc {@code null} khi {@code slug} không tồn tại /
     *         đã bị xoá mềm — tín hiệu để controller trả {@code 404}
     */
    List<ProductResponse> findRelatedProducts(String slug, int limit);

    /**
     * Gợi ý tìm kiếm — {@code GET /products/suggest} (§B.1).
     *
     * @param q từ khoá thô client gửi, có thể {@code null}
     * @param limit số gợi ý tối đa cần lấy; giá trị nhỏ hơn 1 được kéo về mặc định 5
     * @return sản phẩm còn hiệu lực khớp {@code q}
     */
    List<ProductResponse> findSuggestions(String q, int limit);

    /**
     * Khoảng giá {@code MIN}/{@code MAX} trên mọi sản phẩm còn hiệu lực — {@code GET /products/price-range} (§B.1).
     *
     * @return khoảng giá theo {@code effectivePrice}
     */
    PriceRangeResponse findPriceRange();
}
