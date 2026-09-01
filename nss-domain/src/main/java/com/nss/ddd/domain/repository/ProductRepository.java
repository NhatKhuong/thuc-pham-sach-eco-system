package com.nss.ddd.domain.repository;

import com.nss.ddd.domain.model.PageResult;
import com.nss.ddd.domain.model.PriceRange;
import com.nss.ddd.domain.model.ProductFilter;
import com.nss.ddd.domain.model.PublicProductFilter;
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
     * Một trang sản phẩm còn hiệu lực <b>có lọc và có sắp xếp</b> — đường đọc của
     * {@code GET /admin/products} (API_CONTRACT §B.12.1).
     * <p>
     * <b>Tách khỏi {@link #findPublicPage(PublicProductFilter)} chứ không dùng chung, và lý do không
     * phải là ngại đụng chạm.</b> {@code GET /products} của trang cửa hàng mang bảy tiêu chí lọc mà
     * khu quản trị không có ({@code minPrice}, {@code maxPrice}, {@code minRating}, bốn cờ boolean),
     * còn khu quản trị mang {@code stockStatus} ba-trạng-thái mà trang cửa hàng không có; gộp hai
     * đường vào một chữ ký dùng chung là mở sẵn đường cho một bộ lọc quản trị rò sang endpoint công
     * khai (hoặc ngược lại) mà không ai phải quyết định gì.
     * <p>
     * <b>{@code filter.keyword} tới đây đã được domain service bỏ dấu.</b> Adapter chỉ dựng mẫu
     * {@code LIKE}; nó <b>không</b> được chuẩn hoá lại — xem javadoc {@link ProductFilter}.
     * <p>
     * <b>Kết quả rỗng là một câu trả lời hợp lệ, không phải lỗi.</b> Một {@code categorySlug} không
     * khớp danh mục nào cho ra 0 dòng, đúng như frontend làm.
     *
     * @param filter điều kiện lọc, sắp xếp và phân trang; {@code page} đánh số từ 1
     * @return các phần tử của trang kèm <b>tổng số dòng khớp điều kiện lọc</b> — không phải tổng số
     *         sản phẩm còn hiệu lực của cả bảng
     */
    PageResult<Product> findAdminPage(ProductFilter filter);

    /**
     * Đếm số sản phẩm khớp <b>đúng</b> điều kiện của {@link #findAdminPage(ProductFilter)}.
     * <p>
     * <b>Tồn tại để {@code lowStockCount} của §B.12.4 và {@code total} của
     * {@code GET /admin/products?stockStatus=low_stock} không bao giờ lệch nhau.</b> Hợp đồng chốt
     * hai chỗ đó dùng <i>đúng một</i> ngưỡng ({@code StockStatus.LOW_STOCK_THRESHOLD}); cách giữ
     * chúng đúng theo cấu tạo là cho cả hai đi qua một mệnh đề lọc duy nhất — xem
     * {@code ProductJPAMapper.ADMIN_FILTER}. Lệch nhau thì ô chỉ số nói một đằng, danh sách lọc ra
     * một nẻo, và không có lỗi nào nổ ra.
     *
     * @param filter điều kiện lọc; {@code sort}, {@code page} và {@code limit} bị bỏ qua
     * @return số dòng khớp điều kiện
     */
    long countAdminProducts(ProductFilter filter);

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

    /**
     * Hoàn tồn kho bằng UPDATE — đối xứng {@link #decreaseStock}, dùng khi đơn chuyển sang
     * {@code CANCELLED} (backlog 0035 Phase 2, Quyết định Owner #2).
     * <p>
     * <b>Không cần điều kiện {@code >=} vì đây là phép cộng</b> — khác {@link #decreaseStock}, không
     * có cách nào để một phép cộng làm tồn kho vượt quá một giới hạn cần bảo vệ. Vẫn giữ
     * {@code isActive = true} cùng điều kiện: một sản phẩm đã bị gỡ khỏi cửa hàng thì không cần hoàn
     * tồn kho hiển thị nữa (theo đúng quy ước xoá mềm của port này).
     *
     * @param id khoá chính của sản phẩm
     * @param quantity số lượng cần hoàn, phải dương
     * @return true khi có đúng một dòng được cộng lại; false khi id không tồn tại hoặc sản phẩm đã
     *         bị xoá mềm
     */
    boolean increaseStock(Long id, int quantity);

    /**
     * Một trang sản phẩm còn hiệu lực <b>có lọc và có sắp xếp</b> — đường đọc của
     * {@code GET /products} công khai (API_CONTRACT §B.1).
     * <p>
     * <b>Tách khỏi {@link #findAdminPage(ProductFilter)}, không dùng chung chữ ký</b> — xem javadoc
     * cấp class của {@link PublicProductFilter}.
     * <p>
     * <b>{@code filter.keyword} tới đây đã được domain service bỏ dấu</b>, cùng quy ước với
     * {@link #findAdminPage(ProductFilter)}.
     *
     * @param filter điều kiện lọc, sắp xếp và phân trang; {@code page} đánh số từ 1
     * @return các phần tử của trang kèm tổng số dòng khớp điều kiện lọc
     */
    PageResult<Product> findPublicPage(PublicProductFilter filter);

    /**
     * Sản phẩm "liên quan" — cùng danh mục, loại trừ chính nó — nguồn của
     * {@code GET /products/{slug}/related} (API_CONTRACT §B.1).
     * <p>
     * <b>Cùng danh mục CHÍNH XÁC, không kéo theo danh mục con/cha</b> — khác quy ước "một cấp" của
     * {@code categorySlug} ở {@link #findAdminPage} / {@link #findPublicPage}: sản phẩm liên quan là
     * gợi ý tại chỗ trên trang chi tiết, không phải một trang duyệt danh mục, nên phạm vi hẹp hơn là
     * đúng ý định.
     *
     * @param categoryId danh mục của sản phẩm gốc
     * @param excludeProductId id của sản phẩm gốc, không được xuất hiện trong kết quả
     * @param limit số sản phẩm tối đa cần lấy
     * @return sản phẩm cùng danh mục còn hiệu lực, tối đa {@code limit} phần tử
     */
    List<Product> findRelated(Long categoryId, Long excludeProductId, int limit);

    /**
     * Gợi ý tìm kiếm — nguồn của {@code GET /products/suggest} (API_CONTRACT §B.1).
     * <p>
     * Cùng phạm vi khớp với {@code q} của {@link #findPublicPage}: {@code name} HOẶC
     * {@code shortDescription}. Cùng quy ước với mọi method nhận từ khoá khác của port này —
     * {@code keyword} tới đây <b>đã được domain service bỏ dấu</b>; adapter tự dựng mẫu {@code LIKE}
     * và tự escape, không nhận một mẫu đã dựng sẵn.
     *
     * @param keyword từ khoá đã bỏ dấu; {@code null} hoặc rỗng là không tìm
     * @param limit số gợi ý tối đa cần lấy
     * @return sản phẩm còn hiệu lực khớp {@code keyword}, tối đa {@code limit} phần tử
     */
    List<Product> findSuggestions(String keyword, int limit);

    /**
     * Khoảng giá {@code MIN}/{@code MAX} của {@code effectivePrice} trên mọi sản phẩm còn hiệu lực —
     * nguồn của {@code GET /products/price-range} (API_CONTRACT §B.1).
     *
     * @return khoảng giá; hai biên {@code null} khi không có sản phẩm nào còn hiệu lực
     */
    PriceRange findPriceRange();
}
