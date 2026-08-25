package com.nss.ddd.infrastructure.persistence.mapper;

import com.nss.ddd.domain.model.entity.Product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data interface của {@code product} — hạ tầng thuần, không mang quy tắc nghiệp vụ.
 * <p>
 * Mọi đường đọc <b>phục vụ {@code ProductResponse}</b> đều {@code LEFT JOIN FETCH} {@code category}
 * và {@code brand}. Lý do phải viết ra: {@code open-in-view: false} nên session đóng ngay khi
 * repository trả về, và {@code ProductResponse} cần {@code categoryId} / {@code brandId}. Để lazy
 * thì việc đọc id đi qua proxy — hoặc ném {@code LazyInitializationException}, hoặc bắn thêm một
 * truy vấn cho mỗi sản phẩm. Quan hệ {@code @ManyToOne} nên fetch join không nhân bản dòng và không
 * phá phân trang.
 * <p>
 * <b>Ngoại lệ có chủ ý: {@link #findActiveByIdIn}</b> cố ý <i>không</i> fetch join. Nó phục vụ việc
 * đối chiếu giỏ hàng, vốn chỉ đọc {@code id}, {@code stock} và {@code effectivePrice} — ba cột vô
 * hướng nằm sẵn trên chính dòng {@code product}, không đi qua proxy nào. Kéo thêm hai bảng cho mỗi
 * món trong giỏ là trả giá cho dữ liệu không ai đọc. Ai thêm một trường quan hệ vào đường giỏ hàng
 * sau này thì phải thêm fetch join <i>trong cùng lần sửa</i>, nếu không
 * {@code LazyInitializationException} sẽ nổ ở tầng trên.
 */
public interface ProductJPAMapper extends JpaRepository<Product, Long> {

    /**
     * Một trang sản phẩm còn hiệu lực.
     * <p>
     * {@code ORDER BY p.id} nằm trong câu truy vấn chứ không nằm ở {@code Pageable}: thứ tự phải
     * <b>ổn định</b> thì phân trang mới có nghĩa — không có ORDER BY, MySQL được phép trả cùng một
     * dòng ở hai trang khác nhau và bỏ sót dòng khác, mà không có gì báo lỗi.
     * <p>
     * {@code countQuery} khai tường minh vì Spring Data không suy được câu đếm từ truy vấn có
     * {@code JOIN FETCH}.
     *
     * @param pageable trang cần lấy, <b>đã đánh số từ 0</b> — adapter là nơi trừ 1
     * @return trang sản phẩm kèm tổng số dòng
     */
    @Query(value = "SELECT p FROM Product p"
            + " LEFT JOIN FETCH p.category"
            + " LEFT JOIN FETCH p.brand"
            + " WHERE p.isActive = true"
            + " ORDER BY p.id ASC",
            countQuery = "SELECT COUNT(p) FROM Product p WHERE p.isActive = true")
    Page<Product> findActivePage(Pageable pageable);

    /**
     * @param slug slug cần tra
     * @return sản phẩm còn hiệu lực, hoặc rỗng
     */
    @Query("SELECT p FROM Product p"
            + " LEFT JOIN FETCH p.category"
            + " LEFT JOIN FETCH p.brand"
            + " WHERE p.slug = :slug AND p.isActive = true")
    Optional<Product> findActiveBySlug(@Param("slug") String slug);

    /**
     * @param id khóa chính
     * @return sản phẩm còn hiệu lực, hoặc rỗng
     */
    @Query("SELECT p FROM Product p"
            + " LEFT JOIN FETCH p.category"
            + " LEFT JOIN FETCH p.brand"
            + " WHERE p.id = :id AND p.isActive = true")
    Optional<Product> findActiveById(@Param("id") Long id);

    /**
     * Nhiều sản phẩm còn hiệu lực trong một lượt — đường đọc của {@code POST /api/cart/validate}.
     * <p>
     * <b>Không {@code JOIN FETCH}</b>: xem javadoc của interface. Không {@code ORDER BY} vì phía
     * gọi đánh chỉ mục theo id chứ không duyệt tuần tự, và một {@code ORDER BY} thừa trên đường
     * nóng chỉ tốn công sắp xếp cho thứ tự không ai đọc.
     * <p>
     * <b>Phía gọi phải chặn danh sách rỗng trước khi tới đây.</b> {@code IN :ids} với một
     * collection rỗng dịch ra {@code in ()}, và MySQL từ chối cú pháp đó bằng một
     * {@code SQLSyntaxErrorException} — một lỗi 500 cho ca giỏ rỗng, vốn là ca hợp lệ nhất trong cả
     * endpoint. Chỗ chặn nằm ở {@code ProductRepositoryImpl}.
     *
     * @param ids các khóa chính cần tra, <b>không rỗng</b>
     * @return các sản phẩm còn hiệu lực khớp {@code ids}, thứ tự không đảm bảo
     */
    @Query("SELECT p FROM Product p WHERE p.id IN :ids AND p.isActive = true")
    List<Product> findActiveByIdIn(@Param("ids") Collection<Long> ids);

    /**
     * Đếm cả bản ghi đã xoá mềm — {@code uk_slug} nằm trên toàn bảng, không quan tâm {@code is_active}.
     *
     * @param slug slug cần kiểm
     * @return true nếu đã có dòng nào giữ slug này
     */
    boolean existsBySlug(String slug);

    /**
     * Xoá mềm bằng UPDATE có điều kiện — {@code AND p.isActive = true} khiến lần gọi thứ hai trả 0
     * dòng, nhờ đó tầng trên phân biệt được "đã xoá xong" với "không có gì để xoá".
     *
     * @param id khóa chính
     * @param deletedAt thời điểm xoá, giờ UTC
     * @return số dòng bị ảnh hưởng
     */
    @Modifying
    @Transactional
    @Query("UPDATE Product p SET p.isActive = false, p.updatedAt = :deletedAt"
            + " WHERE p.id = :id AND p.isActive = true")
    int markInactive(@Param("id") Long id, @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * Trừ tồn kho bằng <b>conditional UPDATE</b> (backlog 0014 §Contract 8).
     * <p>
     * <b>Vế {@code p.stock >= :quantity} là toàn bộ cơ chế chống bán quá kho, và nó phải nằm trong
     * chính câu UPDATE này.</b> Đọc tồn kho, so trong Java, rồi mới ghi sẽ để lại một cửa sổ giữa
     * hai bước: hai request đồng thời cùng đọc thấy còn 1 sẽ cùng ghi thành -1, và không ràng buộc
     * nào của MySQL chặn lại. Ở đây engine khoá dòng ngay trong UPDATE, nên bên thua nhận về 0 dòng
     * ảnh hưởng và tầng trên biến nó thành 409 kèm rollback.
     * <p>
     * <b>Không {@code clearAutomatically}, không {@code flushAutomatically}.</b> Luồng tạo đơn đọc
     * {@code Product} <i>trước</i> bước này và không bao giờ đọc lại {@code stock} sau đó, nên con
     * số tồn kho cũ còn nằm trong persistence context không đi vào phép tính nào. Bật
     * {@code clearAutomatically} sẽ đẩy mọi entity khác của cùng transaction ra khỏi context và
     * biến một luồng đang đúng thành một luồng phải nạp lại tất cả.
     *
     * @param id khóa chính của sản phẩm
     * @param quantity số lượng cần trừ, phải dương
     * @return số dòng bị ảnh hưởng — {@code 1} là trừ được; {@code 0} là không đủ tồn kho, id không
     *         tồn tại, hoặc sản phẩm đã bị xoá mềm
     */
    @Modifying
    @Transactional
    @Query("UPDATE Product p SET p.stock = p.stock - :quantity"
            + " WHERE p.id = :id AND p.isActive = true AND p.stock >= :quantity")
    int decreaseStock(@Param("id") Long id, @Param("quantity") int quantity);
}
