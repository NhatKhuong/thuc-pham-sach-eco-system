package com.nss.ddd.domain.repository;

import com.nss.ddd.domain.model.PageResult;
import com.nss.ddd.domain.model.entity.Product;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * PORT của aggregate {@code Product} — domain khai báo, infrastructure implement.
 * <p>
 * <b>Ràng buộc kiến trúc:</b> file này không được import bất cứ thứ gì thuộc
 * {@code org.springframework.data.*}. Domain không biết {@code Pageable} / {@code Page} tồn tại —
 * đó là khái niệm của adapter. Mất ranh giới này là mất lý do chia module
 * (architecture/01-overview.md §1).
 * <p>
 * <b>Quy ước xoá mềm:</b> mọi đường <i>đọc</i> ở đây chỉ trả sản phẩm còn {@code isActive = true};
 * sản phẩm đã xoá mềm hành xử như thể không tồn tại. Ngoại lệ duy nhất là {@link #existsBySlug} —
 * xem javadoc của nó.
 */
public interface ProductRepository {

    /**
     * Tìm sản phẩm <b>còn hiệu lực</b> theo slug — khóa tra cứu của {@code GET /products/{slug}}.
     *
     * @param slug slug không dấu, duy nhất
     * @return sản phẩm, hoặc rỗng khi slug không tồn tại / sản phẩm đã bị xoá mềm
     */
    Optional<Product> findBySlug(String slug);

    /**
     * Tìm sản phẩm <b>còn hiệu lực</b> theo khóa chính — đường ghi ({@code PUT} / {@code DELETE})
     * thao tác theo id.
     *
     * @param id khóa chính
     * @return sản phẩm, hoặc rỗng khi id không tồn tại / sản phẩm đã bị xoá mềm
     */
    Optional<Product> findById(Long id);

    /**
     * Tìm nhiều sản phẩm <b>còn hiệu lực</b> trong một lượt — đường đọc của giỏ hàng.
     * <p>
     * Tồn tại vì {@code POST /api/cart/validate} luôn hỏi cả giỏ cùng lúc: gọi {@link #findById}
     * cho từng dòng biến một giỏ 20 món thành 20 lượt đi vòng tới MySQL, trên một endpoint mà
     * frontend gọi lại mỗi lần khách mở giỏ hàng.
     * <p>
     * <b>Kết quả có thể ít phần tử hơn {@code ids}, và đó là thông tin chứ không phải lỗi:</b> một
     * id vắng mặt nghĩa là không có sản phẩm nào như vậy <i>hoặc</i> sản phẩm đã bị xoá mềm. Hai ca
     * đó cố ý không phân biệt được từ đây — quy ước xoá mềm của port này nói sản phẩm đã xoá hành
     * xử như thể không tồn tại, và phía giỏ hàng cũng đối xử với chúng như nhau.
     * <p>
     * Thứ tự trả về <b>không</b> được đảm bảo; phía gọi tự đánh chỉ mục theo id.
     *
     * @param ids các khóa chính cần tra; {@code null} hoặc rỗng cho ra danh sách rỗng
     * @return các sản phẩm còn hiệu lực khớp {@code ids}; danh sách rỗng khi không khớp dòng nào
     */
    List<Product> findByIds(Collection<Long> ids);

    /**
     * Một trang sản phẩm còn hiệu lực, sắp xếp ổn định theo id tăng dần.
     * <p>
     * {@code page} <b>đánh số từ 1</b> (API_CONTRACT §A.4) và đi thẳng vào port ở dạng đó.
     * Việc trừ 1 để dựng {@code Pageable} là của adapter — gom vào <i>một</i> chỗ duy nhất
     * để lỗi off-by-one không có chỗ nào khác để trốn.
     *
     * @param page số trang, đánh số từ 1
     * @param limit số phần tử mỗi trang
     * @return các phần tử của trang kèm tổng số sản phẩm còn hiệu lực
     */
    PageResult<Product> findPage(int page, int limit);

    /**
     * Ghi sản phẩm (chèn mới khi {@code id} rỗng, cập nhật khi đã có).
     *
     * @param product sản phẩm cần ghi
     * @return bản ghi sau khi ghi, đã có id
     */
    Product save(Product product);

    /**
     * Slug đã có ai giữ chưa.
     * <p>
     * <b>Cố ý đếm cả bản ghi đã xoá mềm.</b> Ràng buộc {@code uk_slug} nằm trên toàn bảng, không
     * quan tâm {@code is_active} — bỏ qua dòng đã xoá mềm ở đây thì {@code POST} sẽ qua được cổng
     * kiểm rồi chết bằng lỗi ràng buộc ở tầng dưới.
     *
     * @param slug slug cần kiểm
     * @return true nếu đã có sản phẩm (còn hiệu lực hoặc đã xoá mềm) giữ slug này
     */
    boolean existsBySlug(String slug);

    /**
     * Xoá mềm: đặt {@code is_active = false}, <b>không xoá dòng</b>.
     *
     * @param id khóa chính
     * @param deletedAt thời điểm xoá, giờ UTC — ghi vào {@code updated_at}
     * @return true nếu có đúng một dòng chuyển trạng thái; false khi id không tồn tại hoặc
     *         sản phẩm đã bị xoá mềm từ trước
     */
    boolean softDelete(Long id, LocalDateTime deletedAt);

    /**
     * Trừ tồn kho bằng <b>conditional UPDATE</b>: {@code stock = stock - :quantity} với điều kiện
     * {@code stock >= :quantity} (backlog 0014 §Contract 8).
     * <p>
     * <b>Không đọc-rồi-ghi, và không {@code @Version}.</b> Điều kiện nằm trong chính câu UPDATE nên
     * hai người cùng mua món cuối cùng thì đúng một người thắng — không có cửa sổ nào giữa lúc đọc
     * và lúc ghi để lọt qua. Khoá lạc quan giải cùng bài toán bằng cách phát hiện xung đột
     * <i>sau khi</i> đã đọc sai, và coding-conventions §6 chốt là dự án này không dùng nó.
     * <p>
     * Vế {@code isActive = true} nằm cùng điều kiện: một sản phẩm đã xoá mềm hành xử như thể không
     * tồn tại theo đúng quy ước của port này, nên nó không bán được nữa dù kho còn hàng.
     * <p>
     * <b>Không đụng tới {@code sold}.</b> Cột thống kê đó không nằm trong phạm vi backlog 0014; gộp
     * nó vào đây là một thay đổi lặng lẽ về ý nghĩa của một con số đang hiển thị trên trang sản phẩm.
     *
     * @param id khoá chính của sản phẩm
     * @param quantity số lượng cần trừ, phải dương
     * @return true khi có đúng một dòng bị trừ; false khi không đủ tồn kho, id không tồn tại, hoặc
     *         sản phẩm đã bị xoá mềm
     */
    boolean decreaseStock(Long id, int quantity);
}
