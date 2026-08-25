package com.nss.ddd.application.service.stats;

import com.nss.ddd.application.model.response.AdminOverviewResponse;

/**
 * Use case <b>số liệu tổng quan</b> của khu quản trị — API_CONTRACT §B.12.4, một endpoint của
 * backlog 0019 phase 3.
 * <p>
 * <b>Đây là endpoint CHỈ ĐỌC và sẽ luôn chỉ đọc</b> (§B.12.4): mọi con số ở đây được <i>suy ra</i>
 * từ đơn hàng, sản phẩm và tài khoản, không phải một bản ghi ai đó sửa được. Một use case ghi vào
 * đây là dấu hiệu số liệu đang được nhập tay ở đâu đó thay vì tính ra.
 * <p>
 * <b>Số liệu do backend gộp, không do client</b> — cùng lý do với §C.3: gộp ở trình duyệt nghĩa là
 * tải toàn bộ đơn hàng của mọi khách về máy người dùng.
 */
public interface StatsAppService {

    /**
     * Số liệu tổng quan trong {@code days} ngày gần nhất — {@code GET /admin/stats/overview}.
     * <p>
     * <b>{@code days} phải nằm trong dải hợp lệ TRƯỚC khi tới đây.</b> §B.12.4 chốt {@code days} là
     * preset chứ không phải khoảng tuỳ ý, và ngoài dải thì trả <b>400</b> — <i>không âm thầm kẹp
     * giá trị</i>: một khoảng khác thứ người dùng yêu cầu là một câu trả lời sai im lặng. Phép kiểm
     * nằm ở tầng biên vì mã HTTP là khái niệm của tầng đó.
     * <p>
     * <b>Zero-fill xảy ra ở tầng application, không ở SQL</b> (§B.12.4): truy vấn chỉ biết những
     * dòng có thật, còn khung {@code days} ngày và đủ 5 trạng thái là hình dạng của bề mặt dây.
     *
     * @param days số ngày của khoảng, <b>đã được kiểm</b> nằm trong dải hợp lệ
     * @return số liệu tổng quan, không bao giờ {@code null}
     */
    AdminOverviewResponse findOverview(int days);
}
