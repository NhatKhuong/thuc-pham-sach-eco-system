package com.nss;

import com.nss.ddd.controller.dto.ChangePasswordRequest;
import com.nss.ddd.controller.dto.UpdateProfileRequest;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm ràng buộc validate của hai body mới — bằng {@code Validator} thật, không Spring context.
 * <p>
 * <b>Ba cái bẫy của một trường TUỲ CHỌN, cả ba đều cho ra một 422 sai hoặc một 500.</b>
 * <ul>
 *   <li>{@code @NotBlank} <b>không dùng được</b>: nó từ chối {@code null}, mà {@code null} ở đây
 *       nghĩa là "giữ nguyên giá trị cũ". Dùng nó là biến mọi trường thành bắt buộc.</li>
 *   <li>{@code @Email} <b>chấp nhận chuỗi rỗng</b>. Không có ràng buộc chống-rỗng đi kèm thì
 *       {@code {"email": ""}} lọt qua validate rồi đâm vào cột {@code NOT NULL} — 500 thay vì
 *       422.</li>
 *   <li>{@code @Pattern} khớp <b>toàn chuỗi</b>; thiếu cờ {@code DOTALL} thì một giá trị chứa ký tự
 *       xuống dòng bị báo là "không được để trống" — một thông điệp sai trên một giá trị không hề
 *       rỗng.</li>
 * </ul>
 * Ba trạng thái {@code vắng mặt} / {@code null} / {@code ""} phải cho ra kết quả phân biệt được, và
 * đó chính là bằng chứng của ngữ nghĩa partial.
 */
class UpdateProfileRequestValidationTest {

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
    private Set<String> genViolatedFields(Object request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    // ========== PUT /auth/me ==========

    @Test
    @DisplayName("Body rong hoan toan hop le — vang mat nghia la giu nguyen, khong phai thieu")
    void emptyBodyIsValid() {
        assertTrue(genViolatedFields(new UpdateProfileRequest()).isEmpty(),
                "@NotBlank o day se lam ca nay do va giet chet ngu nghia partial");
    }

    @Test
    @DisplayName("null tuong minh cung hop le — cung nghia voi vang mat")
    void explicitNullIsValid() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName(null);
        request.setEmail(null);
        request.setPhone(null);

        assertTrue(genViolatedFields(request).isEmpty());
    }

    @Test
    @DisplayName("Chuoi rong va chuoi toan khoang trang deu bi tu choi tren ca ba truong")
    void blankStringsAreRejected() {
        UpdateProfileRequest empty = new UpdateProfileRequest();
        empty.setFullName("");
        empty.setPhone("");
        assertEquals(Set.of("fullName", "phone"), genViolatedFields(empty));

        UpdateProfileRequest spaces = new UpdateProfileRequest();
        spaces.setFullName("   ");
        spaces.setPhone("\t");
        assertEquals(Set.of("fullName", "phone"), genViolatedFields(spaces));
    }

    /**
     * {@code @Email} một mình coi chuỗi rỗng là hợp lệ. Ca này chứng minh ràng buộc chống-rỗng đi
     * kèm thật sự bắt được nó — nếu không, giá trị rỗng sẽ đi tiếp tới cột {@code NOT NULL}.
     */
    @Test
    @DisplayName("email rong bi tu choi — @Email mot minh chap nhan chuoi rong")
    void emptyEmailIsRejected() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setEmail("");

        assertTrue(genViolatedFields(request).contains("email"));
    }

    @Test
    @DisplayName("email sai dinh dang bi tu choi")
    void malformedEmailIsRejected() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setEmail("khong-phai-email");

        assertTrue(genViolatedFields(request).contains("email"));
    }

    /**
     * Bẫy {@code DOTALL}: {@code @Pattern} khớp toàn chuỗi và mặc định {@code .} không khớp
     * {@code \n}. Thiếu cờ đó thì giá trị dưới đây bị báo "must not be blank" — sai, vì nó không hề
     * rỗng.
     */
    @Test
    @DisplayName("Gia tri co ky tu xuong dong KHONG bi bao la de trong (bay DOTALL)")
    void multilineValueIsNotReportedAsBlank() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Nguyen\nAn");

        assertFalse(genViolatedFields(request).contains("fullName"),
                "thieu co DOTALL se cho ra mot thong diep sai tren mot gia tri hop le");
    }

    @Test
    @DisplayName("Vuot gioi han do dai cua cot thi bi tu choi")
    void overlongValuesAreRejected() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("a".repeat(129));
        request.setPhone("0".repeat(21));

        assertEquals(Set.of("fullName", "phone"), genViolatedFields(request));
    }

    // ========== PUT /auth/password ==========

    @Test
    @DisplayName("Body rong tra vi pham tren CA HAI truong mat khau")
    void emptyChangePasswordBodyViolatesBothFields() {
        assertEquals(Set.of("currentPassword", "newPassword"),
                genViolatedFields(new ChangePasswordRequest()));
    }

    /**
     * {@code currentPassword} <b>cố ý không có {@code min}</b>: đặt {@code min} ở đó biến một lần gõ
     * sai thành lỗi theo trường và qua đó làm lộ luật độ dài mật khẩu thật.
     */
    @Test
    @DisplayName("currentPassword ngan van hop le — cong do dai chi ap cho newPassword")
    void shortCurrentPasswordIsAccepted() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("a");
        request.setNewPassword("matkhaumoi");

        assertTrue(genViolatedFields(request).isEmpty());
    }

    @Test
    @DisplayName("newPassword chiu dung rang buoc nhu register: 6..72 ky tu")
    void newPasswordFollowsRegisterRule() {
        ChangePasswordRequest tooShort = new ChangePasswordRequest();
        tooShort.setCurrentPassword("123456");
        tooShort.setNewPassword("12345");
        assertEquals(Set.of("newPassword"), genViolatedFields(tooShort));

        ChangePasswordRequest tooLong = new ChangePasswordRequest();
        tooLong.setCurrentPassword("123456");
        tooLong.setNewPassword("a".repeat(73));
        assertEquals(Set.of("newPassword"), genViolatedFields(tooLong));
    }

    /**
     * Hợp đồng im lặng về ca này; thêm một ca thất bại không được khai là bắt frontend hiển thị một
     * lỗi nó không có câu chữ nào để hiển thị.
     */
    @Test
    @DisplayName("newPassword trung currentPassword duoc cho phep")
    void reusingTheSamePasswordIsAllowed() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("123456");
        request.setNewPassword("123456");

        assertTrue(genViolatedFields(request).isEmpty());
    }
}
