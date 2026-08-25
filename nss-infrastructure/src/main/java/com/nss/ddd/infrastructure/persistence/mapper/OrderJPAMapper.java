package com.nss.ddd.infrastructure.persistence.mapper;

import com.nss.ddd.domain.model.entity.Order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data interface của {@code customer_order} — hạ tầng thuần, không mang quy tắc nghiệp vụ.
 * <p>
 * <b>Cả hai đường đọc đều {@code LEFT JOIN FETCH} {@code user}, và lý do phải viết ra:</b>
 * {@code open-in-view: false} nên session đóng ngay khi repository trả về, còn
 * {@code OrderResponse.userId} thì cần đọc id của chủ đơn. Quan hệ này {@code nullable} nên
 * Hibernate không dùng được proxy rẻ tiền cho nó — để lazy thì mỗi đơn trong danh sách sinh thêm
 * một truy vấn, đúng kiểu N+1 mà {@code GET /orders/me} (trả mảng <b>không phân trang</b>) khuếch
 * đại lên theo số đơn của khách. {@code @ManyToOne} nên fetch join không nhân bản dòng.
 * <p>
 * <b>Không có method nào liệt kê đơn của mọi người dùng.</b> Đó không phải chỗ còn thiếu:
 * API_CONTRACT §C.4.3b nói việc truy vấn chéo người dùng thuộc namespace {@code /admin} và phải là
 * một endpoint song sinh riêng. Một {@code findAll()} thêm vào đây là mở sẵn đường cho ai đó nối nó
 * vào {@code /orders/me} bằng một tham số lọc.
 */
public interface OrderJPAMapper extends JpaRepository<Order, Long> {

    /**
     * Tra đơn theo mã hiển thị — khoá tra cứu của {@code GET /orders/{code}}.
     * <p>
     * <b>So khớp chính xác, cố ý không {@code UPPER()} như {@code CouponJPAMapper}.</b> Mã đơn do
     * backend sinh ra chứ không do người dùng gõ tự do, và nó đi vào URL: hai chuỗi khác hoa thường
     * là hai đường dẫn khác nhau. Nới ra ở đây sẽ khiến một mã gõ sai kiểu vẫn mở được đơn, tức
     * mở rộng bề mặt đoán mã trên một endpoint vốn đã công khai (§Contract 6).
     *
     * @param code mã đơn dạng {@code NSS-20260817-0001}
     * @return đơn hàng kèm chủ đơn đã nạp, hoặc rỗng
     */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.user WHERE o.code = :code")
    Optional<Order> findByCodeWithUser(@Param("code") String code);

    /**
     * Các đơn của <b>đúng một</b> người dùng, mới nhất trước.
     * <p>
     * {@code ORDER BY o.createdAt DESC, o.id DESC} — vế thứ hai không phải trang trí: hai đơn đặt
     * trong cùng một giây có {@code created_at} bằng nhau, và không có vế phá hoà thì MySQL được
     * phép đảo thứ tự giữa hai lần gọi, khiến danh sách nhảy chỗ trước mắt người dùng và khiến test
     * so khớp đỏ ngẫu nhiên.
     *
     * @param userId chủ đơn, lấy từ claim {@code sub} của JWT (§C.4.1)
     * @return các đơn của người dùng này kèm chủ đơn đã nạp
     */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.user"
            + " WHERE o.user.id = :userId"
            + " ORDER BY o.createdAt DESC, o.id DESC")
    List<Order> findByUserIdWithUser(@Param("userId") Long userId);
}
