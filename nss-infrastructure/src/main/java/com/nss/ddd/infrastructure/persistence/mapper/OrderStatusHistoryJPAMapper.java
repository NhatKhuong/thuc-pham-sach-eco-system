package com.nss.ddd.infrastructure.persistence.mapper;

import com.nss.ddd.domain.model.entity.OrderStatusHistory;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data interface của {@code order_status_history} — hạ tầng thuần, không mang quy tắc
 * nghiệp vụ.
 * <p>
 * <b>Chỉ có các method thừa kế từ {@code JpaRepository}, và ở phase này chỉ {@code save} được
 * dùng.</b> Backlog 0014 chỉ ghi dòng nhật ký <i>đầu tiên</i> lúc tạo đơn; việc đọc lại nhật ký và
 * việc chuyển trạng thái thuộc {@code PATCH /admin/orders/{code}/status} của backlog 0013. Thêm sẵn
 * đường đọc ở đây là thêm code chưa có ai gọi, và nó sẽ được viết lần thứ hai theo nhu cầu thật của
 * ticket kia.
 * <p>
 * <b>Bảng này chỉ được ghi thêm, không bao giờ sửa hay xoá.</b> Nó tồn tại để trả lời "đơn đi qua
 * đâu, lúc nào, do ai" — một dòng bị sửa lại là một câu trả lời không còn kiểm chứng được, và mọi
 * khiếu nại về giao hàng lại thành lời khai một chiều.
 */
public interface OrderStatusHistoryJPAMapper extends JpaRepository<OrderStatusHistory, Long> {
}
