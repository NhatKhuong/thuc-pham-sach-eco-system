package com.nss;

import com.nss.ddd.controller.config.ApiRateLimitTier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bảng phân tier của trần thông lượng {@code /api/**} (backlog 0021 Phase 1).
 * <p>
 * <b>Đây là test regression, không phải test tính năng.</b> Thứ nó khoá là <i>một đường dẫn thuộc
 * đúng một tier</i>, và ca dễ hỏng nhất là {@code POST /api/auth/login}: nó khớp cả luật auth lẫn
 * luật write, nên đảo thứ tự hai nhánh trong {@code resolve} sẽ cho mọi endpoint auth ghi chạy dưới
 * trần của {@code write} — cao gấp ba lần. Hỏng kiểu đó <b>không sinh lỗi nào</b>: request vẫn
 * chạy, vẫn 200, chỉ là lớp bảo vệ nới ra ba lần ở đúng chỗ chặt nhất.
 */
class ApiRateLimitTierTest {

    @ParameterizedTest
    @CsvSource({
            "/api/auth/login,POST",
            "/api/auth/register,POST",
            "/api/auth/refresh,POST",
            "/api/auth/me,PUT",
            "/api/auth/forgot-password,POST",
            "/api/auth/me,GET"
    })
    @DisplayName("auth thang moi luat khac khi hai luat cung khop")
    void resolveGivesAuthPriorityOverVerb(String path, String method) {
        assertEquals(ApiRateLimitTier.AUTH, ApiRateLimitTier.resolve(path, method));
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE", "post", "delete"})
    @DisplayName("verb ghi ngoai auth roi vao tier write, khong phan biet hoa thuong")
    void resolveGivesWriteForMutatingVerbs(String method) {
        assertEquals(ApiRateLimitTier.WRITE, ApiRateLimitTier.resolve("/api/admin/products", method));
    }

    @ParameterizedTest
    @CsvSource({
            "/api/products,GET",
            "/api/products/rau-cai,GET",
            "/api/hello,GET",
            "/api/admin/stats/overview,GET",
            "/api/products,HEAD",
            "/api/products,OPTIONS"
    })
    @DisplayName("phan con lai roi vao tier read")
    void resolveGivesReadForEverythingElse(String path, String method) {
        assertEquals(ApiRateLimitTier.READ, ApiRateLimitTier.resolve(path, method));
    }

    @Test
    @DisplayName("verb khong nhan ra roi vao tier rong nhat, khong phai tier chat nhat")
    void resolveGivesReadForUnknownVerb() {
        // Mot tran CHAT dat nham cho la mot su co tu choi dich vu do chinh minh gay ra; tran RONG dat
        // nham cho chi la mot lop bao ve long hon. Chon ca thu hai.
        assertEquals(ApiRateLimitTier.READ, ApiRateLimitTier.resolve("/api/products", null));
        assertEquals(ApiRateLimitTier.READ, ApiRateLimitTier.resolve("/api/products", "TRACE"));
    }

    @Test
    @DisplayName("duong dan auth khong co phan duoi van thuoc tier auth")
    void resolveGivesAuthForBareAuthPath() {
        assertEquals(ApiRateLimitTier.AUTH, ApiRateLimitTier.resolve("/api/auth", "POST"));
    }

    @Test
    @DisplayName("duong dan chi BAT DAU giong auth khong duoc muon tran cua auth")
    void resolveDoesNotMatchAuthPrefixLoosely() {
        // "/api/authors" bat dau bang "/api/auth" nhung khong phai endpoint auth. Kiem bang
        // startsWith("/api/auth") tran se keo no vao tier chat nhat va bop mot endpoint doc xuong
        // 10 request/giay.
        assertEquals(ApiRateLimitTier.READ, ApiRateLimitTier.resolve("/api/authors", "GET"));
    }
}
