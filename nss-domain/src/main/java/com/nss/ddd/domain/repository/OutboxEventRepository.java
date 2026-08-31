package com.nss.ddd.domain.repository;

import com.nss.ddd.domain.model.entity.OutboxEvent;

import java.util.List;

/**
 * PORT của {@link OutboxEvent} — domain khai báo, infrastructure implement.
 * <p>
 * Ràng buộc kiến trúc giống {@code OrderRepository}: file này không import bất cứ thứ gì thuộc
 * {@code org.springframework.data.*}.
 */
public interface OutboxEventRepository {

    /**
     * Ghi một dòng outbox (chèn mới khi {@code id} rỗng).
     *
     * @param event dòng outbox cần ghi, {@code status} đã là {@link OutboxEvent#STATUS_PENDING}
     * @return bản ghi sau khi ghi, đã có id
     */
    OutboxEvent save(OutboxEvent event);

    /**
     * Một lô các dòng {@code PENDING}, cũ nhất trước — đầu vào của {@code OutboxPublisherJob}.
     *
     * @param limit số dòng tối đa lấy trong một chu kỳ
     * @return các dòng đang chờ publish; danh sách rỗng khi không có dòng nào
     */
    List<OutboxEvent> findPendingBatch(int limit);

    /**
     * Đánh dấu một dòng đã publish thành công — <b>chỉ gọi SAU KHI broker đã ACK</b> (quy tắc bất
     * biến số 4 của architecture/01-overview.md §6).
     *
     * @param id khoá chính của dòng outbox
     */
    void markPublished(Long id);
}
