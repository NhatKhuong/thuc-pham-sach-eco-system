package com.nss;

import com.nss.ddd.controller.dto.CreateReviewRequest;
import com.nss.ddd.application.model.command.CreateReviewCommand;
import com.nss.ddd.controller.mapper.ReviewControllerMapper;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.domain.model.entity.Review;
import com.nss.ddd.domain.repository.ProductRepository;
import com.nss.ddd.domain.repository.ReviewRepository;
import com.nss.ddd.domain.service.impl.ReviewDomainServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm ba luật của đánh giá, không cần Spring context và không cần MySQL.
 * <ul>
 *   <li><b>§C.3 làm tròn HALF-UP</b> — có ca ghim sản phẩm 11, xem
 *       {@link #ratingOfProductElevenIsFourPointThree()}.</li>
 *   <li><b>Trùng thì trả {@code null}, KHÔNG ghi thêm gì</b> — quyết định đến từ 0 dòng bị ảnh
 *       hưởng của ràng buộc DB, không từ một phép đọc chạy trước.</li>
 *   <li><b>Bẫy #7 — path thắng body</b>, xem {@link #pathProductIdWinsOverBodyProductId()}.</li>
 * </ul>
 */
class ReviewDomainServiceTest {

    private ReviewRepository reviewRepository;

    private ProductRepository productRepository;

    private ReviewDomainServiceImpl reviewDomainService;

    @BeforeEach
    void setUp() {
        reviewRepository = mock(ReviewRepository.class);
        productRepository = mock(ProductRepository.class);
        reviewDomainService = new ReviewDomainServiceImpl(reviewRepository, productRepository);
    }

    // ========== §C.3 + coding-conventions §15: HALF_UP ==========

    /**
     * <b>Đây là ca mà backlog 0006 đã tiên đoán bằng tên, và nó là lý do §15 tồn tại.</b>
     * <p>
     * Sản phẩm 11 "Cam sành hữu cơ" có bốn đánh giá seed ở các mức {@code 5, 4, 5, 3} — tổng
     * {@code 17}, tức {@code AVG} đúng bằng <b>{@code 4.2500}</b>. Đó là sản phẩm <i>duy nhất</i>
     * trong 42 rơi vào ranh giới {@code .x5}.
     * <p>
     * Seed tính bằng {@code ROUND(AVG(rating),1)} của MySQL — half-up — và ghi <b>{@code 4.3}</b>
     * xuống DB. Nếu service dùng mặc định {@code HALF_EVEN} của {@code BigDecimal#setScale} thì lần
     * tính lại đầu tiên sẽ hạ nó xuống {@code 4.2} <b>mà không có đánh giá nào được thêm vào</b> —
     * một giá trị nhảy một bước 0.1 vào lúc không ai đang nhìn, và triệu chứng trông y hệt một cái
     * bug ở phía frontend.
     * <p>
     * {@code assertNotEquals} phía dưới là phần <b>không thể bỏ</b>: nó nói ra đúng giá trị sai mà
     * ca này tồn tại để chặn.
     */
    @Test
    @DisplayName("San pham 11: tinh lai KHONG them danh gia nao van ra 4.3, khong phai 4.2")
    void ratingOfProductElevenIsFourPointThree() {
        // Bon danh gia seed cua san pham 11: 5 + 4 + 5 + 3 = 17, AVG = 4.2500 dung ranh gioi .x5
        BigDecimal rating = reviewDomainService.genRating(17L, 4L);

        assertEquals(new BigDecimal("4.3"), rating,
                "HALF_UP: 4.25 -> 4.3, dung con so seed da ghi xuong DB");
        assertNotEquals(new BigDecimal("4.2"), rating,
                "4.2 la ket qua cua HALF_EVEN — no lam rating tut mot buoc 0.1 ma khong ai them danh gia nao");
    }

    /**
     * Sản phẩm 5 "Cà rốt hữu cơ" ({@code 5+5+4+5 = 19}, {@code AVG = 4.75}) <b>không</b> phân biệt
     * được hai chế độ làm tròn — cả half-up lẫn half-even đều cho {@code 4.8}. Ghi ca này ra để
     * người sau không tưởng nó là một ca đối chứng: chỉ sản phẩm 11 mới là ca ghim.
     */
    @Test
    @DisplayName("San pham 5 KHONG phan biet duoc hai che do lam tron — ghi ra de khong ai nham")
    void productFiveDoesNotDiscriminateRoundingMode() {
        assertEquals(new BigDecimal("4.8"), reviewDomainService.genRating(19L, 4L));
    }

    /**
     * @param sumRating tổng điểm
     * @param count số đánh giá
     * @param expected giá trị mong đợi, dạng chuỗi để giữ đúng scale
     */
    @ParameterizedTest(name = "sum={0} count={1} -> {2}")
    @CsvSource({
            "17, 4, 4.3",
            "19, 4, 4.8",
            "9,  4, 2.3",
            "7,  4, 1.8",
            "5,  1, 5.0",
            "1,  1, 1.0",
            "10, 3, 3.3",
            "11, 3, 3.7"
    })
    @DisplayName("genRating lam tron HALF_UP, dung mot chu so thap phan")
    void genRatingRoundsHalfUp(long sumRating, long count, String expected) {
        assertEquals(new BigDecimal(expected), reviewDomainService.genRating(sumRating, count));
    }

    /**
     * Sản phẩm chưa có đánh giá nào là ca <b>thường gặp nhất</b> — 24/42 sản phẩm trong seed. Nó
     * phải cho ra {@code 0.0} chứ không phải một phép chia cho 0.
     */
    @Test
    @DisplayName("Khong co danh gia nao -> 0.0, dung mot chu so thap phan")
    void genRatingWithoutReviews() {
        assertEquals(new BigDecimal("0.0"), reviewDomainService.genRating(0L, 0L));
        assertEquals(1, reviewDomainService.genRating(0L, 0L).scale(),
                "Cot la DECIMAL(2,1) nen scale phai dung 1");
    }

    @Test
    @DisplayName("recalcRatingStats ghi lai ca rating lan reviewCount tu bang review")
    void recalcRatingStatsWritesBothFields() {
        Product product = new Product().setId(11L).setRating(new BigDecimal("0.0")).setReviewCount(0);
        when(reviewRepository.countByProductId(11L)).thenReturn(4L);
        when(reviewRepository.sumRatingByProductId(11L)).thenReturn(17L);
        when(productRepository.save(any(Product.class))).thenAnswer(call -> call.getArgument(0));

        Product saved = reviewDomainService.recalcRatingStats(product);

        assertEquals(new BigDecimal("4.3"), saved.getRating());
        assertEquals(4, saved.getReviewCount());
        verify(productRepository).save(product);
    }

    // ========== ADR 0008: trung thi 409, va quyet dinh den tu DB ==========

    @Test
    @DisplayName("Ghi duoc -> doc lai ban ghi vua ghi va tra ve")
    void createReturnsSavedReview() {
        Review stored = new Review().setId(99L).setProduct(new Product().setId(11L));
        when(reviewRepository.insertIfAbsent(eq(11L), eq(7L), anyString(), anyInt(), anyString(),
                any(LocalDateTime.class))).thenReturn(true);
        when(reviewRepository.findByProductIdAndUserId(11L, 7L)).thenReturn(Optional.of(stored));

        Review saved = reviewDomainService.create(11L, 7L, "Mai", 5, "Ngon lam that su");

        assertNotNull(saved);
        assertEquals(99L, saved.getId());
    }

    /**
     * <b>Khẳng định quan trọng nhất của ca này là {@code verify(..., never())}.</b> Trùng thì phải
     * dừng ngay ở kết quả của ràng buộc DB — không đọc thêm, không ghi thêm. Một phép đọc "để chắc"
     * ở đây là bước đầu tiên quay lại lối đọc-rồi-ghi mà {@code bugs/0004} đã loại.
     */
    @Test
    @DisplayName("0 dong anh huong -> tra null, va KHONG doc/ghi them gi")
    void createReturnsNullOnDuplicate() {
        when(reviewRepository.insertIfAbsent(anyLong(), anyLong(), anyString(), anyInt(), anyString(),
                any(LocalDateTime.class))).thenReturn(false);

        Review saved = reviewDomainService.create(11L, 7L, "Mai", 5, "Ngon lam that su");

        assertNull(saved, "Trung thi phai tra null de tang tren dich thanh 409");
        verify(reviewRepository, never()).findByProductIdAndUserId(anyLong(), anyLong());
        verify(productRepository, never()).save(any(Product.class));
    }

    /**
     * {@code created_at} phải là giờ <b>UTC</b>, không phải giờ máy. Trên máy dev ở
     * {@code Asia/Saigon} (UTC+7) hai giá trị lệch nhau 7 tiếng và <b>không có gì báo lỗi</b> —
     * đánh giá vừa gửi sẽ mang mốc thời gian ở tương lai so với mọi bản ghi seed.
     */
    @Test
    @DisplayName("created_at lay theo gio UTC, khong phai gio may")
    void createdAtIsUtc() {
        when(reviewRepository.insertIfAbsent(anyLong(), anyLong(), anyString(), anyInt(), anyString(),
                any(LocalDateTime.class))).thenReturn(true);
        when(reviewRepository.findByProductIdAndUserId(anyLong(), anyLong()))
                .thenReturn(Optional.of(new Review().setId(1L)));
        LocalDateTime beforeUtc = LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(1);

        reviewDomainService.create(11L, 7L, "Mai", 5, "Ngon lam that su");

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(reviewRepository).insertIfAbsent(anyLong(), anyLong(), anyString(), anyInt(),
                anyString(), captor.capture());
        LocalDateTime afterUtc = LocalDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(1);

        assertEquals(true, captor.getValue().isAfter(beforeUtc) && captor.getValue().isBefore(afterUtc),
                "created_at phai nam trong cua so gio UTC, khong phai gio may (lech 7 tieng o VN)");
    }

    // ========== BAY #7: path thang body ==========

    /**
     * <b>{@code POST /api/products/7/reviews} kèm body {@code {"productId": 9}} phải ghi vào sản
     * phẩm 7.</b>
     * <p>
     * Nhận theo body thì request đó ghi đánh giá vào sản phẩm 9 và <i>vẫn trả 201</i> — không
     * exception, không log, không cách nào nhận ra từ phía client. Owner chốt 2026-08-26: path là
     * nguồn chân lý, giá trị trong body bị bỏ qua trong im lặng.
     */
    @Test
    @DisplayName("Bay #7: path productId=7 thang body productId=9")
    void pathProductIdWinsOverBodyProductId() {
        CreateReviewRequest request = new CreateReviewRequest();
        request.setProductId(9L);
        request.setAuthorName("Mai");
        request.setRating(5);
        request.setContent("Ngon lam that su");

        CreateReviewCommand command = ReviewControllerMapper.toCommand(request, 7L, 3L);

        assertEquals(7L, command.getProductId(),
                "productId phai lay tu PATH; lay tu body la ghi danh gia vao san pham khac va van tra 201");
        assertEquals(3L, command.getUserId(), "userId phai lay tu claim sub, khong tu body");
        assertNotEquals(request.getProductId(), command.getProductId(),
                "Ca nay chi co nghia khi hai gia tri KHAC nhau");
    }

    @Test
    @DisplayName("ReviewControllerMapper null-guard")
    void controllerMapperNullGuard() {
        assertNull(ReviewControllerMapper.toCommand(null, 7L, 3L));
    }
}
