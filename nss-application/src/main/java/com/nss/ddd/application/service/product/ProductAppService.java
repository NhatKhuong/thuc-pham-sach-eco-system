package com.nss.ddd.application.service.product;

import com.nss.ddd.application.model.command.CreateProductCommand;
import com.nss.ddd.application.model.command.UpdateProductCommand;
import com.nss.ddd.application.model.response.PaginatedResponse;
import com.nss.ddd.application.model.response.ProductMutationResponse;
import com.nss.ddd.application.model.response.ProductResponse;
import com.nss.ddd.domain.model.ProductFilter;

/**
 * Use case CRUD sản phẩm — điều phối giữa domain service và các kiểu của bề mặt dây.
 * <p>
 * Không có quy tắc nghiệp vụ nào ở đây: quy tắc sống trong {@code ProductDomainService}, tầng này
 * chỉ hỏi domain rồi lắp kết quả thành response.
 * <p>
 * <b>Hai đường đọc danh sách, phục vụ hai bề mặt khác nhau.</b> {@link #findProducts(int, int)} là
 * trang cửa hàng công khai (§B.1): chỉ {@code page} và {@code limit}, thứ tự cố định.
 * {@link #findAdminProducts(ProductFilter)} là bảng quản trị (§B.12.1): có lọc, có sắp xếp, và nằm
 * sau hàng rào {@code /api/admin/**}. Chúng cố ý không dùng chung chữ ký — xem javadoc
 * {@code ProductRepository#findAdminPage}.
 * <p>
 * Trang cửa hàng đọc bằng {@code slug}, khu quản trị đọc bằng {@code id} (§B.12.1): frontend dựng
 * URL cửa hàng từ slug, còn admin sửa được chính cái slug đó nên đường dẫn màn sửa không được treo
 * vào một trường có thể đổi.
 */
public interface ProductAppService {

    /**
     * Một trang sản phẩm còn hiệu lực.
     *
     * @param page số trang, <b>đánh số từ 1</b>; giá trị nhỏ hơn 1 được kéo về 1
     * @param limit số phần tử mỗi trang; giá trị nhỏ hơn 1 được kéo về mặc định
     * @return trang sản phẩm theo dạng {@code Paginated<Product>} của §A.4
     */
    PaginatedResponse<ProductResponse> findProducts(int page, int limit);

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
}
