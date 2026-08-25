package com.nss.ddd.application.service.cart;

import com.nss.ddd.application.model.command.CartItemCommand;
import com.nss.ddd.application.model.response.CartIssueResponse;

import java.util.List;

/**
 * Use case đối chiếu giỏ hàng — API_CONTRACT §B.6.
 * <p>
 * Không có quy tắc nghiệp vụ nào ở đây: quy tắc sống trong {@code CartDomainService}, tầng này chỉ
 * hỏi domain rồi lắp kết quả thành kiểu của bề mặt dây.
 * <p>
 * <b>Đường này CHỈ ĐỌC.</b> Đối chiếu một giỏ không được phép trừ kho, không giữ chỗ, không đặt
 * lệnh nào — cùng lý do khiến {@code POST /coupons/validate} không tăng {@code usedCount}: frontend
 * gọi lại nó mỗi lần khách mở giỏ hàng, nên bất kỳ tác dụng phụ nào cũng nhân lên theo số lần bấm.
 * Việc trừ kho thuộc phase 3, trong cùng transaction với INSERT đơn.
 */
public interface CartAppService {

    /**
     * Đối chiếu giỏ hàng với dữ liệu thật trong DB.
     *
     * @param items các dòng giỏ hàng client gửi; {@code null} hoặc rỗng đều hợp lệ
     * @return các vấn đề tìm thấy, giữ đúng thứ tự dòng client gửi; <b>danh sách rỗng</b> nghĩa là
     *         giỏ hợp lệ — không bao giờ {@code null}
     */
    List<CartIssueResponse> validateCart(List<CartItemCommand> items);
}
