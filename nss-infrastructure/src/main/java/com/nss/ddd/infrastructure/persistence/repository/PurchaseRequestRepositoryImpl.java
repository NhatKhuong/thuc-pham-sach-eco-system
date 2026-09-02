package com.nss.ddd.infrastructure.persistence.repository;

import com.nss.ddd.domain.model.entity.PurchaseRequest;
import com.nss.ddd.domain.repository.PurchaseRequestRepository;
import com.nss.ddd.infrastructure.persistence.mapper.PurchaseRequestJPAMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * ADAPTER cho port {@link PurchaseRequestRepository} (backlog 0039). Mọi khái niệm của Spring Data
 * dừng lại ở file này, phía trên chỉ thấy kiểu của domain.
 */
@Repository
@RequiredArgsConstructor
public class PurchaseRequestRepositoryImpl implements PurchaseRequestRepository {

    private final PurchaseRequestJPAMapper purchaseRequestJPAMapper;

    @Override
    public Optional<PurchaseRequest> findByIdempotencyKey(String idempotencyKey) {
        return purchaseRequestJPAMapper.findByIdempotencyKey(idempotencyKey);
    }

    @Override
    public Optional<PurchaseRequest> findByRequestId(String requestId) {
        return purchaseRequestJPAMapper.findById(requestId);
    }

    @Override
    public boolean tryInsert(PurchaseRequest request) {
        Long userId = request.getUser() == null ? null : request.getUser().getId();
        return purchaseRequestJPAMapper.tryInsert(request.getRequestId(), request.getIdempotencyKey(),
                userId, request.getStatus(), request.getCreatedAt(), request.getUpdatedAt()) > 0;
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Đọc rồi ghi, cố ý</b> — cùng lý do của {@code OutboxEventRepositoryImpl#markPublished}:
     * consumer xử lý tuần tự đúng một request tại một thời điểm cho cùng một {@code requestId}
     * (khoá idempotency đã chặn xử lý trùng ở tầng trên), không có người viết thứ hai tranh chấp.
     */
    @Override
    public void markSuccess(String requestId, String orderCode, LocalDateTime updatedAt) {
        purchaseRequestJPAMapper.findById(requestId).ifPresent(request ->
                purchaseRequestJPAMapper.save(request
                        .setStatus(PurchaseRequest.STATUS_SUCCESS)
                        .setOrderCode(orderCode)
                        .setUpdatedAt(updatedAt)));
    }

    @Override
    public void markFailed(String requestId, String failureCode, String failureMessage, LocalDateTime updatedAt) {
        purchaseRequestJPAMapper.findById(requestId).ifPresent(request ->
                purchaseRequestJPAMapper.save(request
                        .setStatus(PurchaseRequest.STATUS_FAILED)
                        .setFailureCode(failureCode)
                        .setFailureMessage(failureMessage)
                        .setUpdatedAt(updatedAt)));
    }

    @Override
    public Optional<LocalDateTime> findOldestPendingCreatedAt() {
        return purchaseRequestJPAMapper.findFirstByStatusOrderByCreatedAtAsc(PurchaseRequest.STATUS_PENDING)
                .map(PurchaseRequest::getCreatedAt);
    }
}
