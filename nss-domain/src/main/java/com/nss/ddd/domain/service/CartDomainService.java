package com.nss.ddd.domain.service;

import com.nss.ddd.domain.model.CartIssue;
import com.nss.ddd.domain.model.CartLine;

import java.util.List;

/**
 * Domain service của việc đối chiếu giỏ hàng — nơi ở của quy tắc "giỏ này còn mua được không".
 * <p>
 * <b>Thất bại nghiệp vụ ở đây là giá trị trả về, không phải exception</b> (coding-conventions §11
 * Pattern A): kết quả là một danh sách vấn đề, danh sách rỗng nghĩa là giỏ hợp lệ. Không có ca lỗi
 * HTTP nào trên đường này — API_CONTRACT §B.6 để trống cột Lỗi của {@code validateCart}, kể cả cho
 * giỏ rỗng, vốn là một câu hỏi hợp lệ với một câu trả lời hợp lệ. Việc <i>chặn</i> giỏ rỗng thuộc
 * {@code POST /orders}.
 * <p>
 * <b>Chỉ đọc.</b> Đường này không trừ kho, không giữ chỗ, không ghi một dòng nào. Frontend gọi lại
 * nó mỗi khi khách mở lại giỏ hàng, nên bất kỳ tác dụng phụ nào ở đây cũng nhân lên theo số lần
 * người dùng bấm — cùng lý do khiến {@code POST /coupons/validate} không được tăng
 * {@code usedCount}.
 */
public interface CartDomainService {

    /**
     * Đối chiếu từng dòng giỏ hàng với dữ liệu thật trong DB.
     * <p>
     * <b>Ba quy tắc, và quan hệ giữa chúng cũng là contract:</b>
     * <ol>
     *   <li>Không tìm thấy {@code productId}, <b>hoặc</b> {@code stock <= 0}, <b>hoặc</b>
     *       {@code is_active = 0} thì ra {@code OUT_OF_STOCK}, và <b>bỏ qua mọi kiểm tra còn lại
     *       cho dòng đó</b>. Đây là quy tắc <i>loại trừ</i> duy nhất: một sản phẩm không mua được
     *       thì việc giá nó đổi bao nhiêu là thông tin vô nghĩa.</li>
     *   <li>Ngược lại, {@code quantity > stock} thì ra {@code INSUFFICIENT_STOCK} kèm tồn kho thật.</li>
     *   <li>Ngược lại <b>và cũng vậy</b>, {@code effective_price} khác {@code price} client gửi thì
     *       ra {@code PRICE_CHANGED}.</li>
     * </ol>
     * <b>Hai quy tắc sau song song, không loại trừ nhau: một dòng có thể sinh HAI issue.</b> Một
     * sản phẩm vừa thiếu hàng vừa đổi giá phải báo cả hai, vì khách sửa số lượng xong vẫn cần biết
     * mình sắp trả một cái giá khác. Đây là hành vi của {@code orders.api.ts:validateCart} phía
     * frontend, không phải lựa chọn của backend.
     * <p>
     * <b>Thứ tự trả về là contract:</b> giữ đúng thứ tự dòng client gửi, và trong cùng một dòng thì
     * {@code INSUFFICIENT_STOCK} đứng trước {@code PRICE_CHANGED}. Frontend hiển thị danh sách này
     * cạnh giỏ hàng theo đúng thứ tự nhận được; sắp xếp lại là bắt người dùng tự dò.
     * <p>
     * <b>{@code productId} trùng lặp</b> được xử lý từng dòng độc lập, không gộp. Client không nên
     * gửi giỏ như vậy, nhưng nếu có thì mỗi dòng nhận đúng phán quyết của nó — tự chế logic gộp ở
     * đây sẽ khiến số lượng cộng dồn theo một quy ước mà phía kia không biết.
     *
     * @param lines các dòng giỏ hàng; {@code null} hoặc rỗng đều cho ra danh sách rỗng
     * @return các vấn đề tìm thấy; <b>danh sách rỗng</b> nghĩa là giỏ hợp lệ
     */
    List<CartIssue> findIssues(List<CartLine> lines);
}
