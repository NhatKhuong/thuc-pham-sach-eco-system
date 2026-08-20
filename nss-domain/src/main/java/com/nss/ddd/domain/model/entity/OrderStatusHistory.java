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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

/**
 * Nhật ký chuyển trạng thái của một đơn hàng — mỗi lần đổi trạng thái ghi một dòng.
 * <p>
 * Đây là thứ trả lời yêu cầu "quản lý trạng thái đơn hàng tốt". {@code customer_order.status}
 * chỉ cho biết đơn <b>đang</b> ở đâu; không có bảng này thì không ai trả lời được đơn
 * <b>đi qua đâu, lúc nào, do ai</b> — nghĩa là mọi khiếu nại về giao hàng đều thành lời khai một chiều.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(indexes = @Index(name = "idx_order_id", columnList = "order_id"))
@Comment("Nhat ky chuyen trang thai cua don hang")
public class OrderStatusHistory {

    /** Tương ứng cột {@code id}. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("Khoa chinh")
    private Long id;

    /** Tương ứng cột {@code order_id}. Quan hệ một chiều LAZY, sinh khóa ngoại thật. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false, foreignKey = @ForeignKey(name = "fk_order_status_history_order"))
    @Comment("Don hang duoc chuyen trang thai")
    private Order order;

    /**
     * Tương ứng cột {@code from_status} — {@code null} ở dòng đầu tiên, lúc đơn vừa được tạo.
     * <p>
     * {@code // 0=PENDING, 1=CONFIRMED, 2=SHIPPING, 3=DELIVERED, 4=CANCELLED}
     */
    @Comment("Trang thai truoc khi chuyen; null la dong dau tien luc tao don")
    private Integer fromStatus;

    /**
     * Tương ứng cột {@code to_status}.
     * <p>
     * {@code // 0=PENDING, 1=CONFIRMED, 2=SHIPPING, 3=DELIVERED, 4=CANCELLED}
     */
    @Column(nullable = false)
    @Comment("Trang thai sau khi chuyen: 0=PENDING, 1=CONFIRMED, 2=SHIPPING, 3=DELIVERED, 4=CANCELLED")
    private Integer toStatus;

    /** Tương ứng cột {@code note} — lý do chuyển trạng thái, ví dụ lý do hủy đơn. */
    @Column(length = 255)
    @Comment("Ghi chu ly do chuyen trang thai")
    private String note;

    /**
     * Tương ứng cột {@code changed_by} — ai thực hiện.
     * <p>
     * Chuỗi trần chứ không khóa ngoại về {@link User}, vì tác nhân không phải lúc nào cũng là
     * một tài khoản: có thể là {@code SYSTEM} (job tự hủy đơn quá hạn) hoặc một định danh vận hành.
     */
    @Column(length = 128)
    @Comment("Dinh danh nguoi hoac he thong thuc hien chuyen trang thai")
    private String changedBy;

    /** Tương ứng cột {@code created_at} — <b>lưu giờ UTC</b>, set bằng {@code LocalDateTime.now(ZoneOffset.UTC)}. */
    @Column(nullable = false)
    @Comment("Thoi diem chuyen trang thai, luu theo gio UTC")
    private LocalDateTime createdAt;
}
