package com.nss.ddd.infrastructure.persistence.mapper;

import com.nss.ddd.domain.model.entity.OutboxEvent;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data interface của {@code outbox_event} — hạ tầng thuần, không mang quy tắc nghiệp vụ.
 */
public interface OutboxEventJPAMapper extends JpaRepository<OutboxEvent, Long> {

    /**
     * Một lô dòng cùng {@code status}, cũ nhất trước — dùng {@link Pageable} để giới hạn số dòng
     * thay vì {@code LIMIT} viết tay (coding-conventions §12: {@code Pageable} cho JPQL).
     *
     * @param status {@link OutboxEvent#STATUS_PENDING} khi gọi từ {@code OutboxPublisherJob}
     * @param pageable trang cần lấy — chỉ dùng phần {@code size} để giới hạn số dòng của một chu kỳ
     * @return các dòng khớp {@code status}, tăng dần theo {@code createdAt}
     */
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(Integer status, Pageable pageable);
}
