package com.nss.ddd.infrastructure.persistence.mapper;

import com.nss.ddd.domain.model.entity.User;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Spring Data interface của bảng {@code user} — hạ tầng thuần, không mang quy tắc nghiệp vụ.
 * <p>
 * Hai method đầu suy được từ tên nên dùng <b>derived query</b> (coding-conventions §12): không có
 * gì để viết bằng JPQL mà rõ hơn. Hai method của khu quản trị thì không suy được từ tên nào cả.
 * <p>
 * Cố ý <b>không</b> có đường đọc nào kèm {@code JOIN FETCH} vai trò: claim {@code roles} lấy bằng
 * truy vấn riêng ở {@code UserRoleJPAMapper}, nên {@code User} đọc lên không mang theo
 * {@code Role} — đúng ràng buộc "vai trò không rò ra response" trong javadoc của {@code User}.
 */
public interface UserJPAMapper extends JpaRepository<User, Long> {

    /**
     * Mệnh đề lọc của {@code GET /admin/customers} — <b>khai một lần, dùng cho CẢ BA truy vấn</b>:
     * trang, đếm-của-trang, và {@code customerCount} của §B.12.4.
     * <p>
     * <b>Ba chỗ dùng chung một chuỗi là điều kiện để hợp đồng đúng, không phải một sự gọn gàng.</b>
     * §B.12.3 và §B.12.4 nói thẳng: {@code GET /admin/customers} không kèm tham số và
     * {@code customerCount} là <i>hai chỗ duy nhất trong tài liệu đếm người dùng</i>, và chúng phải
     * đếm <b>cùng một tập</b> — lệch nhau thì bảng ghi 11 dòng còn ô chỉ số ghi 12, <b>không lỗi
     * nào nổ ra và không chỗ nào nói ra là vì sao</b>. Ba bản viết tay thì bản thứ ba là bản sẽ bị
     * quên.
     * <p>
     * <b>Ba trường của {@code q} dùng chung một tham số {@code :pattern}</b>, đúng như
     * {@code adminUsers.api.ts:63-71}: họ tên <i>đã bỏ dấu</i>, email <i>đã hạ chữ thường</i>, và
     * số điện thoại (khớp cả đoạn giữa — người ta hay gõ {@code "345678"}).
     * <p>
     * <b>{@code LOWER(u.email)} viết tường minh</b> dù collation {@code utf8mb4_unicode_ci} vốn đã
     * không phân biệt hoa thường: nó nói ra ý định, và nó giữ câu truy vấn đúng nếu cột có ngày bị
     * đổi sang một collation phân biệt hoa thường.
     * <p>
     * <b>Lọc vai trò bằng {@code EXISTS} chứ không bằng {@code JOIN}.</b> Một
     * {@code JOIN user_role} sẽ <i>nhân bản dòng</i> khi một tài khoản mang nhiều vai trò — bảng
     * {@code user_role} là quan hệ nhiều-nhiều và {@code uk_user_id_role_id} chỉ chặn trùng
     * <i>cặp</i>, không chặn một người có hai vai trò. Hệ quả của việc nhân bản: một người hiện hai
     * dòng trong bảng, và {@code total} đếm sai — đúng con số mà §B.12.4 đòi phải khớp.
     * <p>
     * <b>{@code :roleCode} KHÔNG có nhánh {@code IS NULL}</b>, khác hai {@code *JPAMapper} kia:
     * §B.12.3 chốt "{@code role} bỏ trống ⇒ {@code customer}", nên phép rơi về mặc định xảy ra ở
     * tầng biên và xuống tới đây thì luôn có giá trị. Thêm một nhánh {@code IS NULL} ở đây là mở
     * sẵn đường cho bảng khách hàng lặng lẽ mọc lại tài khoản quản trị.
     */
    String ADMIN_FILTER = " WHERE (:pattern IS NULL"
            + "      OR u.fullNameNormalized LIKE :pattern ESCAPE '!'"
            + "      OR LOWER(u.email) LIKE :pattern ESCAPE '!'"
            + "      OR u.phone LIKE :pattern ESCAPE '!')"
            + " AND EXISTS (SELECT 1 FROM UserRole ur"
            + "             WHERE ur.user = u AND ur.role.code = :roleCode)";

    /**
     * @param email email đăng nhập
     * @return tài khoản, hoặc rỗng
     */
    Optional<User> findByEmail(String email);

    /**
     * @param email email cần kiểm
     * @return true nếu email đã có tài khoản giữ
     */
    boolean existsByEmail(String email);

    /**
     * Một trang tài khoản có lọc — đường đọc của {@code GET /admin/customers} (§B.12.3).
     * <p>
     * <b>{@code ORDER BY u.id ASC} nằm trong chuỗi truy vấn</b>, không đi qua {@code Sort}: §B.12.3
     * <i>không có</i> tham số {@code sort}, thứ tự là cố định. Và là {@code id} <b>tăng dần</b>,
     * không phải "mới nhất trước" — {@code types/user.ts#User} không có {@code createdAt} nên không
     * tồn tại mốc thời gian nào để xếp theo. {@code id} là khoá chính nên thứ tự này luôn ổn định,
     * không cần khoá phụ.
     * <p>
     * <b>Không {@code JOIN FETCH} vai trò</b>, và đó không phải chỗ quên: vai trò được đọc theo lô
     * bằng {@code UserRoleJPAMapper.findByUserIdIn}. Fetch join qua một quan hệ to-many sẽ nhân bản
     * dòng và <b>phá phân trang</b> — Hibernate phải nạp cả tập rồi cắt trang trong bộ nhớ, và nó
     * chỉ cảnh báo trong log chứ không hỏng.
     * <p>
     * {@code countQuery} khai tường minh vì Spring Data không suy được câu đếm từ một truy vấn có
     * {@code ORDER BY} nhúng cứng kèm subquery — và vì cả hai phải dùng chung
     * {@link #ADMIN_FILTER}.
     *
     * @param pattern mẫu {@code LIKE} đã bọc {@code %} và đã escape; {@code null} là không tìm
     * @param roleCode mã vai trò UPPER_SNAKE, <b>không bao giờ {@code null}</b>
     * @param pageable trang cần lấy, <b>đã đánh số từ 0</b>; <b>không</b> mang {@code Sort}
     * @return trang tài khoản kèm tổng số dòng khớp điều kiện
     */
    @Query(value = "SELECT u FROM User u" + ADMIN_FILTER + " ORDER BY u.id ASC",
            countQuery = "SELECT COUNT(u) FROM User u" + ADMIN_FILTER)
    Page<User> findAdminPage(@Param("pattern") String pattern,
                             @Param("roleCode") String roleCode,
                             Pageable pageable);

    /**
     * Đếm tài khoản khớp <b>đúng</b> {@link #ADMIN_FILTER} — nguồn của {@code customerCount}
     * (§B.12.4).
     * <p>
     * Xem javadoc của {@link #ADMIN_FILTER} về việc vì sao nó phải dùng chung chuỗi với
     * {@link #findAdminPage} thay vì lặp lại điều kiện.
     *
     * @param pattern mẫu {@code LIKE}; {@code null} là không tìm — {@code customerCount} luôn truyền
     *                {@code null}
     * @param roleCode mã vai trò UPPER_SNAKE
     * @return số dòng khớp điều kiện
     */
    @Query("SELECT COUNT(u) FROM User u" + ADMIN_FILTER)
    long countAdminUsers(@Param("pattern") String pattern, @Param("roleCode") String roleCode);
}
