package com.nss.ddd.application.service.cart.impl;

import com.nss.ddd.application.mapper.CartMapper;
import com.nss.ddd.application.model.command.CartItemCommand;
import com.nss.ddd.application.model.response.CartIssueResponse;
import com.nss.ddd.application.service.cart.CartAppService;
import com.nss.ddd.domain.model.CartIssue;
import com.nss.ddd.domain.service.CartDomainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Hiện thực use case đối chiếu giỏ hàng.
 * <p>
 * Tầng này chỉ điều phối: dịch lệnh sang kiểu của domain, hỏi domain, rồi dịch kết quả sang kiểu
 * của dây. Không có quy tắc nghiệp vụ nào nằm ở đây — kể cả cái nhìn có vẻ vô hại như "giỏ rỗng thì
 * khỏi hỏi domain": đó vẫn là một quyết định nghiệp vụ, và nó sống trong {@code CartDomainService}.
 * <p>
 * <b>Không {@code @Transactional}:</b> đường này chỉ đọc và chỉ có đúng một truy vấn, nên không có
 * gì để gói lại. coding-conventions §8 mục 5 cấm khai {@code readOnly} khi không viết ra được lý
 * do — ở đây không có lý do nào.
 * <p>
 * <b>Không có chuỗi tiếng Việt nào trong file này</b>, khác với {@code CouponAppServiceImpl}. Đó
 * không phải chỗ còn thiếu: {@code CartIssue} không mang thông điệp, nó mang <i>dữ kiện</i>
 * ({@code type}, {@code availableStock}, hai mức giá). Câu chữ hiển thị cho người dùng do frontend
 * dựng từ những dữ kiện đó, vì chỉ frontend mới biết nó đang vẽ một dòng trong giỏ hay một hộp
 * thoại xác nhận.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartAppServiceImpl implements CartAppService {

    private final CartDomainService cartDomainService;

    @Override
    public List<CartIssueResponse> validateCart(List<CartItemCommand> items) {
        List<CartIssue> issues = cartDomainService.findIssues(CartMapper.toLines(items));
        log.info("validateCart: success | itemCount={} issueCount={}",
                items == null ? 0 : items.size(), issues.size());
        return CartMapper.toResponses(issues);
    }
}
