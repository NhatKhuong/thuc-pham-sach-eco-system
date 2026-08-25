package com.nss.ddd.infrastructure.persistence.mapper;

import com.nss.ddd.domain.model.entity.OrderItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Spring Data interface của {@code order_item} — hạ tầng thuần, không mang quy tắc nghiệp vụ.
 * <p>
 * <b>Không {@code JOIN FETCH} sang {@code product}</b>, và đó là một ràng buộc chứ không phải chỗ
 * quên: dòng hàng là <b>bản chụp</b> tại thời điểm đặt ({@code slug}, {@code name}, {@code image},
 * {@code unit}, {@code price}, {@code originalPrice} nằm sẵn trên chính dòng này), nên không có gì
 * để tra ngược. {@code product_id} còn cố ý không có khoá ngoại — xem javadoc của {@code OrderItem}.
 * Ai thêm một lời gọi tra sản phẩm ở tầng trên để "làm giàu" dòng hàng thì đang biến chứng từ thành
 * một khung nhìn động, và một đơn cũ sẽ đổi tên hàng sau lần admin đổi tên sản phẩm đầu tiên.
 */
public interface OrderItemJPAMapper extends JpaRepository<OrderItem, Long> {

    /**
     * Dòng hàng của <b>nhiều</b> đơn trong một lượt — chống N+1 cho {@code GET /orders/me}.
     * <p>
     * {@code ORDER BY oi.order.id ASC, oi.id ASC} để danh sách dòng hàng của mỗi đơn giữ đúng thứ
     * tự đã chèn, tức đúng thứ tự khách xếp trong giỏ. Không có ORDER BY thì thứ tự do MySQL quyết
     * định và một đơn hai món có thể hiện đảo chỗ giữa hai lần tải trang.
     * <p>
     * <b>Phía gọi phải chặn danh sách rỗng trước khi tới đây.</b> {@code IN :orderIds} với một
     * collection rỗng dịch ra {@code in ()}, và MySQL từ chối cú pháp đó — một lỗi 500 cho ca
     * "khách chưa có đơn nào", vốn là ca thường gặp nhất. Chỗ chặn nằm ở {@code OrderRepositoryImpl}.
     *
     * @param orderIds khoá chính của các đơn, <b>không rỗng</b>
     * @return các dòng hàng thuộc những đơn đó
     */
    @Query("SELECT oi FROM OrderItem oi"
            + " WHERE oi.order.id IN :orderIds"
            + " ORDER BY oi.order.id ASC, oi.id ASC")
    List<OrderItem> findByOrderIdIn(@Param("orderIds") Collection<Long> orderIds);
}
