package com.nss.ddd.infrastructure.persistence.repository;

import com.nss.ddd.domain.model.entity.OutboxEvent;
import com.nss.ddd.domain.repository.OutboxEventRepository;
import com.nss.ddd.infrastructure.persistence.mapper.OutboxEventJPAMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ADAPTER cho port {@link OutboxEventRepository}. Mọi khái niệm của Spring Data dừng lại ở file
 * này, phía trên chỉ thấy kiểu của domain.
 */
@Repository
@RequiredArgsConstructor
public class OutboxEventRepositoryImpl implements OutboxEventRepository {

    private final OutboxEventJPAMapper outboxEventJPAMapper;

    @Override
    public OutboxEvent save(OutboxEvent event) {
        return outboxEventJPAMapper.save(event);
    }

    @Override
    public List<OutboxEvent> findPendingBatch(int limit) {
        return outboxEventJPAMapper.findByStatusOrderByCreatedAtAsc(
                OutboxEvent.STATUS_PENDING, PageRequest.of(0, limit));
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Đọc rồi ghi, cố ý.</b> {@code OutboxPublisherJob} luôn gọi method này ngay sau khi vừa đọc
     * đúng dòng đó từ {@link #findPendingBatch}, và job chạy tuần tự trên một dòng tại một thời
     * điểm — không có người viết thứ hai tranh chấp cùng dòng, khác hẳn {@code deductStock} nơi
     * nhiều request đọc-ghi đồng thời buộc phải dùng conditional UPDATE.
     */
    @Override
    public void markPublished(Long id) {
        outboxEventJPAMapper.findById(id)
                .ifPresent(event -> outboxEventJPAMapper.save(event.setStatus(OutboxEvent.STATUS_PUBLISHED)));
    }
}
