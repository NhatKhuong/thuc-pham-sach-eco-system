package com.nss;

import com.nss.ddd.controller.dto.CreateReviewRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm ràng buộc validate của {@code CreateReviewRequest} — bằng {@code Validator} thật, không
 * Spring context.
 * <p>
 * <b>Hai thứ được khoá ở đây, và cả hai đều là contract:</b>
 * <ul>
 *   <li><b>Ranh giới 422.</b> §B.8 khai đúng hai ca: nội dung dưới 10 ký tự, và số sao ngoài 1–5.
 *       Chúng phải trượt <i>ở tầng validate</i> để response mang map {@code errors} — frontend phân
 *       biệt lỗi ô nhập với lỗi nghiệp vụ bằng sự có mặt của khoá đó, không bằng mã HTTP (§A.3).</li>
 *   <li><b>Thông điệp phải là TIẾNG VIỆT ngay từ bản đầu</b> (coding-conventions §1). Backlog 0023
 *       vừa dịch 95 thông điệp trong 15 DTO; một DTO mới viết tiếng Anh là đi lùi ngay ngày hôm
 *       sau. Chuỗi này người dùng cuối đọc nguyên văn trong ô nhập.</li>
 * </ul>
 */
class CreateReviewRequestValidationTest {

    /** Đúng 10 ký tự — cận dưới hợp lệ của {@code content}. */
    private static final String CONTENT_MIN = "Ngon lam!!";

    /** Đúng 9 ký tự — ngay dưới cận, ca mà §B.8 gọi đích danh. */
    private static final String CONTENT_BELOW_MIN = "Ngon lam!";

    private static ValidatorFactory validatorFactory;

    private static Validator validator;

    @BeforeAll
    static void openValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    /**
     * @param request body cần kiểm
     * @return tên các trường có vi phạm
     */
    private Set<String> genViolatedFields(CreateReviewRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    /**
     * @param request body cần kiểm
     * @return thông điệp đầu tiên của mỗi trường có vi phạm
     */
    private Map<String, String> genMessages(CreateReviewRequest request) {
        return validator.validate(request).stream()
                .collect(Collectors.toMap(
                        v -> v.getPropertyPath().toString(),
                        ConstraintViolation::getMessage,
                        (first, second) -> first));
    }

    /**
     * @return body hợp lệ hoàn toàn — mỗi ca test chỉ làm hỏng đúng một trường của nó
     */
    private CreateReviewRequest genValidRequest() {
        CreateReviewRequest request = new CreateReviewRequest();
        request.setAuthorName("Nguyễn Thị Mai");
        request.setRating(5);
        request.setContent("Cam mọng nước, vị ngọt đậm rất vừa miệng.");
        return request;
    }

    @Test
    @DisplayName("Body hop le khong co vi pham nao")
    void validRequestPasses() {
        assertTrue(genViolatedFields(genValidRequest()).isEmpty(),
                "Body hop le khong duoc co vi pham nao");
    }

    @Test
    @DisplayName("productId trong body KHONG bi rang buoc — no bi bo qua chu khong bi tu choi")
    void bodyProductIdIsNeverValidated() {
        CreateReviewRequest request = genValidRequest();
        request.setProductId(-999L);

        assertTrue(genViolatedFields(request).isEmpty(),
                "productId trong body phai bi BO QUA trong im lang, khong duoc sinh loi validate");
    }

    // ========== §B.8: content toi thieu 10 ky tu ==========

    @Test
    @DisplayName("content 9 ky tu truot — day la can duoi cua §B.8")
    void contentBelowTenCharactersIsRejected() {
        CreateReviewRequest request = genValidRequest();
        request.setContent(CONTENT_BELOW_MIN);

        assertEquals(9, CONTENT_BELOW_MIN.length(), "Chuoi mau phai dung 9 ky tu");
        assertTrue(genViolatedFields(request).contains("content"), "content duoi 10 ky tu phai truot");
    }

    @Test
    @DisplayName("content dung 10 ky tu di qua — can duoi la BAO GOM")
    void contentAtExactlyTenCharactersPasses() {
        CreateReviewRequest request = genValidRequest();
        request.setContent(CONTENT_MIN);

        assertEquals(10, CONTENT_MIN.length(), "Chuoi mau phai dung 10 ky tu");
        assertFalse(genViolatedFields(request).contains("content"),
                "content dung 10 ky tu phai di qua — can duoi la >= 10, khong phai > 10");
    }

    @Test
    @DisplayName("content trong truot")
    void blankContentIsRejected() {
        CreateReviewRequest request = genValidRequest();
        request.setContent("   ");

        assertTrue(genViolatedFields(request).contains("content"), "content toan khoang trang phai truot");
    }

    // ========== §B.8: rating trong 1..5 ==========

    /**
     * <b>0 và 6 là hai ca ngay ngoai bien</b> — chúng là thứ phân biệt một ràng buộc đúng với một
     * ràng buộc chỉ chặn số âm hoặc chỉ chặn số rất lớn.
     *
     * @param rating điểm nằm ngoài dải hợp lệ
     */
    @ParameterizedTest(name = "rating={0} truot")
    @ValueSource(ints = {0, 6, -1, 100})
    @DisplayName("rating ngoai dai 1..5 truot")
    void ratingOutsideRangeIsRejected(int rating) {
        CreateReviewRequest request = genValidRequest();
        request.setRating(rating);

        assertTrue(genViolatedFields(request).contains("rating"),
                "rating=" + rating + " phai truot");
    }

    /**
     * @param rating điểm hợp lệ
     */
    @ParameterizedTest(name = "rating={0} di qua")
    @ValueSource(ints = {1, 2, 3, 4, 5})
    @DisplayName("rating trong dai 1..5 di qua, ke ca hai bien")
    void ratingInsideRangePasses(int rating) {
        CreateReviewRequest request = genValidRequest();
        request.setRating(rating);

        assertFalse(genViolatedFields(request).contains("rating"),
                "rating=" + rating + " phai di qua");
    }

    /**
     * <b>{@code @Min}/{@code @Max} bỏ qua {@code null} theo đúng đặc tả Bean Validation.</b> Thiếu
     * {@code @NotNull} đi kèm thì một body không có trường {@code rating} sẽ qua được tầng validate
     * và chết ở cột {@code NOT NULL} phía dưới — một 500 cho thứ lẽ ra là 422 kèm tên trường.
     */
    @Test
    @DisplayName("rating vang mat truot — @Min/@Max mot minh KHONG bat duoc null")
    void missingRatingIsRejected() {
        CreateReviewRequest request = genValidRequest();
        request.setRating(null);

        assertTrue(genViolatedFields(request).contains("rating"),
                "rating vang mat phai truot o tang validate, khong duoc de no roi xuong cot NOT NULL");
    }

    // ========== coding-conventions §1: thong diep TIENG VIET ==========

    /**
     * <b>Khẳng định trên NỘI DUNG chuỗi, không phải trên số lượng vi phạm.</b> Một thông điệp tiếng
     * Anh vẫn cho ra đúng một vi phạm ở đúng một trường, nên một phép đếm sẽ xanh trên chính thứ nó
     * phải bắt.
     * <p>
     * Pattern dùng là <b>literal</b>, không phải bracket expression: backlog 0026 đo được rằng
     * {@code [...]} trên UTF-8 nhiều byte khớp theo từng byte trong locale C và trả 0 hit trên
     * chính thứ nó phải tìm.
     */
    @Test
    @DisplayName("Moi thong diep validate deu la tieng Viet, khong phai tieng Anh")
    void everyMessageIsVietnamese() {
        CreateReviewRequest empty = new CreateReviewRequest();
        Map<String, String> messages = genMessages(empty);

        assertEquals(Set.of("authorName", "rating", "content"), messages.keySet(),
                "Body rong phai truot o dung ba truong bat buoc");

        Map<String, String> expected = Map.of(
                "authorName", "Vui lòng nhập tên hiển thị của bạn.",
                "rating", "Vui lòng chọn số sao đánh giá.",
                "content", "Vui lòng nhập nội dung đánh giá.");
        assertEquals(expected, messages,
                "Thong diep validate phai la tieng Viet nguyen van (coding-conventions §1)");
    }

    @Test
    @DisplayName("Thong diep cua content ngan va rating ngoai dai cung la tieng Viet")
    void rangeMessagesAreVietnamese() {
        CreateReviewRequest shortContent = genValidRequest();
        shortContent.setContent(CONTENT_BELOW_MIN);
        assertEquals("Nội dung đánh giá phải từ 10 đến 5000 ký tự.",
                genMessages(shortContent).get("content"));

        CreateReviewRequest badRating = genValidRequest();
        badRating.setRating(6);
        assertEquals("Số sao đánh giá phải từ 1 đến 5.", genMessages(badRating).get("rating"));
    }

    /**
     * Control dương của phép kiểm tiếng Việt ở trên: nếu <i>không</i> có chuỗi nào mang dấu tiếng
     * Việt thì mọi khẳng định "là tiếng Việt" ở trên đều vô nghĩa mà vẫn xanh.
     */
    @Test
    @DisplayName("Control duong: ca ba thong diep deu mang ky tu ngoai ASCII")
    void positiveControlVietnameseDiacriticsExist() {
        Map<String, String> messages = genMessages(new CreateReviewRequest());

        long withDiacritic = messages.values().stream()
                .filter(message -> message.chars().anyMatch(c -> c > 127))
                .count();
        assertEquals(3, withDiacritic,
                "Ca ba thong diep phai mang dau tieng Viet — 0 thi phep kiem tren la mot control cam");
    }
}
