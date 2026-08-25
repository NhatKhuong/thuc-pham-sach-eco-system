package com.nss.ddd.infrastructure.persistence.repository;

import com.nss.ddd.domain.model.entity.Order;
import com.nss.ddd.domain.model.entity.OrderItem;
import com.nss.ddd.domain.model.entity.OrderStatusHistory;
import com.nss.ddd.domain.repository.OrderRepository;
import com.nss.ddd.infrastructure.persistence.mapper.OrderItemJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.OrderJPAMapper;
import com.nss.ddd.infrastructure.persistence.mapper.OrderStatusHistoryJPAMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * ADAPTER cho port {@code OrderRepository}.
 * <p>
 * Đây là <b>ranh giới</b>: mọi khái niệm của Spring Data dừng lại ở file này, phía trên chỉ thấy
 * kiểu của domain.
 * <p>
 * <b>Ba {@code *JPAMapper} cho một port là đúng, không phải thừa.</b> Ba bảng của aggregate
 * {@code Order} chỉ có nghĩa cùng nhau nên chúng chung một port (xem javadoc của port đó), nhưng
 * Spring Data ràng một interface với đúng một entity — nên bên dưới ranh giới vẫn phải là ba
 * interface. Chính vì vậy việc gộp chúng lại phải xảy ra ở <i>đây</i>, chứ không ở tầng service:
 * một service cầm ba mapper là một service biết ba bảng.
 * <p>
 * Stereotype là {@code @Repository}, không phải {@code @Service} (coding-conventions §3).
 */
@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJPAMapper orderJPAMapper;

    private final OrderItemJPAMapper orderItemJPAMapper;

    private final OrderStatusHistoryJPAMapper orderStatusHistoryJPAMapper;

    @Override
    public Order save(Order order) {
        return orderJPAMapper.save(order);
    }

    @Override
    public List<OrderItem> saveItems(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return orderItemJPAMapper.saveAll(items);
    }

    @Override
    public OrderStatusHistory saveHistory(OrderStatusHistory history) {
        return orderStatusHistoryJPAMapper.save(history);
    }

    @Override
    public Optional<Order> findByCode(String code) {
        return orderJPAMapper.findByCodeWithUser(code);
    }

    @Override
    public List<Order> findByUserId(Long userId) {
        return orderJPAMapper.findByUserIdWithUser(userId);
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Phép chặn danh sách rỗng nằm ở đây</b>, cùng khuôn với
     * {@code ProductRepositoryImpl.findByIds} và cùng lý do: {@code IN :orderIds} với collection
     * rỗng dịch ra {@code in ()}, mà MySQL từ chối cú pháp đó. Chặn tại adapter chứ không ở domain
     * vì đây là ràng buộc của <i>SQL</i>, không phải một quy tắc nghiệp vụ — câu "không có đơn nào
     * thì không có dòng hàng nào" đúng ở mọi cơ sở dữ liệu.
     */
    @Override
    public List<OrderItem> findItemsByOrderIds(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        return orderItemJPAMapper.findByOrderIdIn(orderIds);
    }

    @Override
    public long countOrders() {
        return orderJPAMapper.count();
    }
}
