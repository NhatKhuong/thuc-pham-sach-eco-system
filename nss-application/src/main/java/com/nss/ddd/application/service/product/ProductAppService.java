package com.nss.ddd.application.service.product;

import com.nss.ddd.application.model.command.CreateProductCommand;
import com.nss.ddd.application.model.command.UpdateProductCommand;
import com.nss.ddd.application.model.response.PaginatedResponse;
import com.nss.ddd.application.model.response.ProductMutationResponse;
import com.nss.ddd.application.model.response.ProductResponse;

/**
 * Use case CRUD sản phẩm — điều phối giữa domain service và các kiểu của bề mặt dây.
 * <p>
 * Không có quy tắc nghiệp vụ nào ở đây: quy tắc sống trong {@code ProductDomainService}, tầng này
 * chỉ hỏi domain rồi lắp kết quả thành response.
 * <p>
 * Đọc bằng {@code slug}, ghi bằng {@code id} — đọc theo slug là contract §B.1 (frontend dựng URL
 * từ slug), ghi theo id vì admin thao tác trên khóa chính và slug thì sửa được.
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
     * @param slug slug của sản phẩm
     * @return sản phẩm, hoặc {@code null} khi không tồn tại / đã bị xoá mềm
     */
    ProductResponse findProductBySlug(String slug);

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
