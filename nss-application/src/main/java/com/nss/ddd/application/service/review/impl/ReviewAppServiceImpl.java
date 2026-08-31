package com.nss.ddd.application.service.review.impl;

import com.nss.ddd.application.mapper.ReviewMapper;
import com.nss.ddd.application.model.command.CreateReviewCommand;
import com.nss.ddd.application.model.response.ReviewMutationResponse;
import com.nss.ddd.application.model.response.ReviewResponse;
import com.nss.ddd.application.model.response.ReviewSummaryResponse;
import com.nss.ddd.application.service.review.ReviewAppService;
import com.nss.ddd.domain.model.RatingCount;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.domain.model.entity.Review;
import com.nss.ddd.domain.model.entity.User;
import com.nss.ddd.domain.service.ProductDomainService;
import com.nss.ddd.domain.service.ReviewDomainService;
import com.nss.ddd.domain.service.UserDomainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.math.BigDecimal;
import java.util.List;

/**
 * Hiện thực use case đánh giá sản phẩm.
 * <p>
 * Tầng này chỉ điều phối: hỏi domain service, rồi lắp kết quả thành kiểu của bề mặt dây. Thứ duy
 * nhất thuộc về file này là <b>trình tự</b> và <b>chuỗi tiếng Việt</b>.
 * <p>
 * <b>Vì sao {@code createReview} vừa {@code @Transactional} vừa gọi {@code setRollbackOnly} tay</b>
 * — cùng lý do đã viết ở {@code OrderAppServiceImpl}: Pattern A trả thất bại bằng <i>giá trị</i>,
 * nên Spring không thấy exception nào để rollback. Mọi nhánh thất bại sau khi transaction mở đều đi
 * qua {@link #failedAndRollback}, <b>kể cả những nhánh chưa ghi gì</b> — phân biệt "nhánh này đã
 * ghi chưa" là loại suy luận đúng hôm nay và sai vào lần chèn thêm một bước ghi ở giữa.
 * <p>
 * <b>Hai đường đọc không {@code @Transactional}:</b> mỗi đường chỉ đọc và chỉ có hai truy vấn cố
 * định (tra sản phẩm, rồi đọc đánh giá), nên không có gì để gói lại. coding-conventions §8 mục 5
 * cấm khai {@code readOnly} khi không viết ra được lý do — ở đây không có lý do nào.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewAppServiceImpl implements ReviewAppService {

    private static final String MESSAGE_PRODUCT_NOT_FOUND = "Không tìm thấy sản phẩm bạn đang tìm.";

    private static final String MESSAGE_DUPLICATE_REVIEW =
            "Bạn đã đánh giá sản phẩm này rồi, mỗi tài khoản chỉ đánh giá một lần.";

    /**
     * Token hợp lệ nhưng trỏ tới một tài khoản không còn tồn tại.
     * <p>
     * Cùng ca và cùng mã HTTP (422) với {@code OrderMutationResponse.CODE_INVALID_ORDER_DATA} ở
     * {@code POST /orders}. <b>Không dùng 401</b>: access token vẫn đúng chữ ký và còn hạn, mà 401
     * là tín hiệu {@code client.ts} phản ứng bằng cách gọi {@code /auth/refresh} rồi đăng xuất —
     * một vòng lặp vô ích cho một tài khoản đã biến mất.
     */
    private static final String MESSAGE_USER_NOT_FOUND =
            "Tài khoản của bạn không còn tồn tại, vui lòng đăng nhập lại.";

    private final ReviewDomainService reviewDomainService;

    private final ProductDomainService productDomainService;

    private final UserDomainService userDomainService;

    // ========== READ ==========

    @Override
    public List<ReviewResponse> findReviews(Long productId) {
        // 1. San pham da xoa mem hanh xu nhu the no khong ton tai — dung lai duong tra id+isActive
        //    san co (ProductJPAMapper#findActiveById), KHONG dung duong tra cua khu quan tri: cai
        //    do nhin thay ca san pham da xoa mem va se phoi danh gia cua chung ra cua cong khai.
        if (productDomainService.findById(productId) == null) {
            log.warn("findReviews: product not found | productId={}", productId);
            return null;
        }
        List<Review> reviews = reviewDomainService.findByProductId(productId);
        log.info("findReviews: success | productId={} count={}", productId, reviews.size());
        return ReviewMapper.toResponses(reviews);
    }

    @Override
    public ReviewSummaryResponse findSummary(Long productId) {
        if (productDomainService.findById(productId) == null) {
            log.warn("findSummary: product not found | productId={}", productId);
            return null;
        }
        // 1. MOT truy van GROUP BY, khong ba: tong so luot va tong diem deu suy duoc tu chinh ket
        //    qua do, va suy tu cung mot tap dong la thu giu `average` khong lech `total`.
        List<RatingCount> counts = reviewDomainService.countGroupedByRating(productId);
        long total = 0L;
        long sumRating = 0L;
        for (RatingCount count : counts) {
            total += count.getCount();
            sumRating += (long) count.getRating() * count.getCount();
        }
        // 2. Phep lam tron co dung MOT ban, o domain service (§15) — khong tinh lai o day.
        BigDecimal average = reviewDomainService.genRating(sumRating, total);
        log.info("findSummary: success | productId={} total={} average={}", productId, total, average);
        return ReviewMapper.toSummaryResponse(counts, total, average);
    }

    // ========== WRITE ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewMutationResponse createReview(CreateReviewCommand command) {
        // 1. San pham phai ton tai va con hieu luc — 404. Phai chay TRUOC buoc ghi: INSERT IGNORE
        //    ha moi loi toan ven xuong thanh canh bao, nen mot khoa ngoai hong se bi bao cao nham
        //    thanh "ban da danh gia roi" neu khong chan o day.
        Product product = productDomainService.findById(command.getProductId());
        if (product == null) {
            log.warn("createReview: product not found | productId={}", command.getProductId());
            return failedAndRollback(ReviewMutationResponse.CODE_PRODUCT_NOT_FOUND,
                    MESSAGE_PRODUCT_NOT_FOUND);
        }
        // 2. Tai khoan cua claim `sub` phai con ton tai — cung ly do khoa ngoai nhu tren
        User user = userDomainService.findById(command.getUserId());
        if (user == null) {
            log.warn("createReview: user not found | userId={}", command.getUserId());
            return failedAndRollback(ReviewMutationResponse.CODE_INVALID_REVIEW_DATA,
                    MESSAGE_USER_NOT_FOUND);
        }
        // 3. Ghi, va de rang buoc uk_review_product_user quyet dinh co trung hay khong (ADR 0008).
        //    KHONG SELECT roi INSERT — do la doc-roi-ghi, dung co che cua bugs/0004.
        Review saved = reviewDomainService.create(command.getProductId(), command.getUserId(),
                command.getAuthorName(), command.getRating(), command.getContent());
        if (saved == null) {
            log.warn("createReview: duplicate | productId={} userId={}",
                    command.getProductId(), command.getUserId());
            return failedAndRollback(ReviewMutationResponse.CODE_DUPLICATE_REVIEW,
                    MESSAGE_DUPLICATE_REVIEW);
        }
        // 4. §C.3 — tinh lai rating/reviewCount cua san pham trong CUNG transaction. Khong gop o
        //    day thi the san pham o /cua-hang va bieu do o trang chi tiet noi hai dieu khac nhau
        //    ve cung mot san pham.
        Product updated = reviewDomainService.recalcRatingStats(product);
        log.info("createReview: success | reviewId={} productId={} userId={} rating={} reviewCount={}",
                saved.getId(), command.getProductId(), command.getUserId(),
                updated.getRating(), updated.getReviewCount());
        return ReviewMutationResponse.success(ReviewMapper.toResponse(saved));
    }

    // ========== HELPERS ==========

    /**
     * Dựng kết quả thất bại và <b>đánh dấu transaction phải rollback</b>.
     * <p>
     * Xem javadoc cấp class. Hậu tố {@code failed*} theo coding-conventions §4 cho helper dựng
     * response lỗi.
     *
     * @param code mã lỗi nghiệp vụ UPPER_SNAKE
     * @param message thông điệp tiếng Việt cho người dùng cuối
     * @return kết quả thất bại
     */
    private ReviewMutationResponse failedAndRollback(String code, String message) {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        return ReviewMutationResponse.failed(code, message);
    }
}
