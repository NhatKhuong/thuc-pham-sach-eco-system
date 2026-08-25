package com.nss.ddd.domain.repository;

import com.nss.ddd.domain.model.PageResult;
import com.nss.ddd.domain.model.UserFilter;
import com.nss.ddd.domain.model.entity.User;

import java.util.Optional;

/**
 * PORT của aggregate {@code User} — domain khai báo, infrastructure implement.
 * <p>
 * <b>Ràng buộc kiến trúc:</b> file này không được import bất cứ thứ gì thuộc
 * {@code org.springframework.data.*} (architecture/01-overview.md §1).
 * <p>
 * <b>Không có đường đọc nào trả {@code passwordHash} ra ngoài domain.</b> Entity mang trường đó vì
 * đối chiếu mật khẩu là việc của {@code AuthDomainService}; tầng application chỉ được nhìn thấy nó
 * qua {@code UserMapper.toResponse}, và mapper đó cố ý không map trường này (§B.4 #1).
 */
public interface UserRepository {

    /**
     * Tra tài khoản theo email — khóa đăng nhập, duy nhất toàn hệ ({@code uk_email}).
     *
     * @param email email đăng nhập
     * @return tài khoản, hoặc rỗng khi email chưa được đăng ký
     */
    Optional<User> findByEmail(String email);

    /**
     * Tra tài khoản theo khoá chính — đường dùng khi định danh đến từ claim {@code sub} của JWT.
     * <p>
     * Tồn tại vì {@code POST /orders} phải gắn chủ đơn cho một đơn hàng, và §C.2 nói định danh đó
     * <b>chỉ</b> đến từ token chứ không bao giờ từ body. Tra lại bằng id là bước duy nhất biến một
     * con số trong token thành một bản ghi có thật; bỏ bước đó và tin thẳng vào {@code sub} sẽ ghi
     * được khoá ngoại trỏ tới một tài khoản không còn tồn tại.
     *
     * @param id khoá chính, lấy từ claim {@code sub}
     * @return tài khoản, hoặc rỗng khi id không khớp dòng nào
     */
    Optional<User> findById(Long id);

    /**
     * Email đã có ai giữ chưa — cổng kiểm trước khi {@code INSERT} để trả 409 thay vì lỗi ràng buộc
     * {@code uk_email} ở tầng dưới.
     *
     * @param email email cần kiểm
     * @return true nếu email đã được đăng ký
     */
    boolean existsByEmail(String email);

    /**
     * Một trang tài khoản có lọc — đường đọc của {@code GET /admin/customers} (§B.12.3).
     * <p>
     * <b>Thứ tự cố định {@code id} tăng dần</b>, không có tham số {@code sort}: {@code User} phía
     * client không có {@code createdAt} nên không tồn tại mốc thời gian nào để xếp theo.
     *
     * @param filter điều kiện lọc; {@code keyword} đã được domain service bỏ dấu và
     *               {@code roleCode} đã rơi về mặc định
     * @return trang tài khoản kèm tổng số dòng khớp điều kiện
     */
    PageResult<User> findAdminPage(UserFilter filter);

    /**
     * Đếm số tài khoản khớp <b>đúng</b> điều kiện của {@link #findAdminPage(UserFilter)}.
     * <p>
     * <b>Tồn tại để {@code customerCount} của §B.12.4 và {@code total} của
     * {@code GET /admin/customers} không bao giờ lệch nhau.</b> Hợp đồng nói thẳng rằng hai chỗ này
     * là hai chỗ duy nhất trong tài liệu đếm người dùng và chúng phải đếm <i>cùng một tập</i>; lệch
     * nhau thì bảng ghi 11 dòng còn ô chỉ số ghi 12, và <b>không lỗi nào nổ ra</b>. Cách giữ chúng
     * đúng theo cấu tạo là cho cả hai đi qua một mệnh đề lọc duy nhất — xem
     * {@code UserJPAMapper.ADMIN_FILTER}.
     *
     * @param filter điều kiện lọc; {@code page} và {@code limit} bị bỏ qua
     * @return số dòng khớp điều kiện
     */
    long countAdminUsers(UserFilter filter);

    /**
     * Ghi tài khoản (chèn mới khi {@code id} rỗng, cập nhật khi đã có).
     *
     * @param user tài khoản cần ghi
     * @return bản ghi sau khi ghi, đã có id
     */
    User save(User user);
}
