package com.nss.ddd.domain.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

/**
 * Token đặt lại mật khẩu đã phát cho một yêu cầu {@code POST /auth/forgot-password}.
 * <p>
 * Bảng này tồn tại vì ADR 0004: bảng token duy nhất trước đó ({@code refresh_token}) dành cho phiên
 * đăng nhập — khác vòng đời, khác cách dùng một lần, khác cách vô hiệu hoá. Nhét trạng thái tạm này
 * vào bảng {@code user} đã được cân nhắc và loại: nó giới hạn cứng mỗi tài khoản đúng một yêu cầu
 * đang mở, và vẫn là đổi schema.
 *
 * <h2>Vì sao cột là {@code token_hash} chứ không phải {@code token} — cố ý đi khác tiền lệ</h2>
 * {@code refresh_token} lưu chuỗi <b>thô</b>. Bảng này thì <b>không</b>, và sự bất đối xứng đó là
 * một quyết định có lý do, không phải một chỗ quên đồng bộ (backlog 0017 §Contract điều 4):
 * <ul>
 *   <li>Rò một lần đọc DB trên {@code refresh_token} cho kẻ tấn công các <i>phiên đang mở</i> — đã
 *       tệ, nhưng mỗi dòng chết khi hết hạn hoặc khi người dùng đăng xuất, và cơ chế xoay vòng của
 *       {@code refresh} liên tục thu hẹp cửa sổ.</li>
 *   <li>Rò cùng một lần đọc trên bảng này cho nó <b>quyền chiếm mọi tài khoản đang có yêu cầu đặt
 *       lại mở</b>: mỗi dòng là một tấm vé đổi mật khẩu, và ở đây <i>không có</i> cơ chế xoay vòng
 *       nào thu hẹp cửa sổ.</li>
 * </ul>
 * Hệ quả bắt buộc: chuỗi thô chỉ tồn tại trong bộ nhớ đúng một lần — lúc sinh ra — rồi đi thẳng vào
 * email. <b>Không bao giờ ghi nó xuống DB, và không bao giờ đưa vào log.</b>
 * <p>
 * <b>Hash là SHA-256, KHÔNG phải bcrypt, và đó không phải sơ suất.</b> Bcrypt cố ý chậm và mang
 * salt riêng cho từng dòng, nên không tra ngược được bằng một câu {@code WHERE token_hash = ?} —
 * muốn tìm sẽ phải quét cả bảng và {@code matches} từng dòng. Thứ bcrypt bảo vệ là bí mật <i>entropy
 * thấp</i> do người đặt; token ở đây là 256 bit ngẫu nhiên mã hoá, nên không có từ điển nào để dò và
 * một hàm băm nhanh là đủ. Xem {@code AuthDomainServiceImpl.genTokenHash}.
 * <p>
 * Mô hình dòng theo đúng {@code RefreshToken}: một cột cờ đánh dấu đã tiêu ({@code is_used}) thay vì
 * xoá dòng, để lần dùng thứ hai phân biệt được với "chưa từng tồn tại" ở tầng dữ liệu.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        uniqueConstraints = @UniqueConstraint(name = "uk_token_hash", columnNames = "token_hash"),
        indexes = @Index(name = "idx_user_id", columnList = "user_id")
)
@Comment("Token dat lai mat khau, dung mot lan, luu duoi dang hash")
public class PasswordResetToken {

    /** Tương ứng cột {@code id}. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("Khoa chinh")
    private Long id;

    /** Tương ứng cột {@code user_id} — chủ của yêu cầu đặt lại. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false, foreignKey = @ForeignKey(name = "fk_password_reset_token_user"))
    @Comment("Nguoi dung yeu cau dat lai mat khau")
    private User user;

    /**
     * Tương ứng cột {@code token_hash} — <b>SHA-256 của chuỗi thô, viết hex thường, đúng 64 ký tự</b>.
     * <p>
     * Chuỗi thô không có mặt ở bất kỳ cột nào của bảng này; xem javadoc cấp class.
     */
    @Column(nullable = false, length = 64)
    @Comment("SHA-256 cua token dang hex, duy nhat; chuoi tho khong bao gio duoc luu")
    private String tokenHash;

    /** Tương ứng cột {@code expires_at} — <b>lưu giờ UTC</b>, set bằng {@code LocalDateTime.now(ZoneOffset.UTC)} cộng thời hạn. */
    @Column(nullable = false)
    @Comment("Thoi diem het han, luu theo gio UTC")
    private LocalDateTime expiresAt;

    /**
     * Tương ứng cột {@code is_used} — {@code true} sau khi token đã đổi được một mật khẩu.
     * <p>
     * Cột này là thứ khiến "dùng đúng một lần" trở thành một ràng buộc của <i>dữ liệu</i> chứ không
     * phải một lời hứa của tầng service: phép tiêu token là một UPDATE có điều kiện
     * {@code SET is_used = true WHERE ... AND is_used = false}, nên hai request đồng thời cầm cùng
     * một chuỗi thì đúng một cái thắng.
     */
    @Column(nullable = false)
    @Comment("Da dung hay chua; dat mat khau thanh cong dat cot nay thanh true")
    private Boolean isUsed;

    /** Tương ứng cột {@code created_at} — <b>lưu giờ UTC</b>, set bằng {@code LocalDateTime.now(ZoneOffset.UTC)}. */
    @Column(nullable = false)
    @Comment("Thoi diem phat token, luu theo gio UTC")
    private LocalDateTime createdAt;
}
