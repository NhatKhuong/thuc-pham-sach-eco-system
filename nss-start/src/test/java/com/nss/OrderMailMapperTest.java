package com.nss;

import com.nss.ddd.application.mapper.OrderMailMapper;
import com.nss.ddd.domain.service.OrderDomainService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm {@code OrderMailMapper} — phần hiển thị của email trạng thái đơn hàng (backlog 0032).
 * <p>
 * <b>Ca đáng giá nhất ở đây là {@link #eachStatusHasItsOwnColor()}.</b> Badge màu theo trạng thái
 * chỉ thật sự phân biệt được nếu năm màu đó KHÁC NHAU — một bảng {@code switch} chép nhầm cùng một
 * màu cho hai trạng thái vẫn biên dịch được, vẫn chạy được, và email vẫn "có badge".
 */
class OrderMailMapperTest {

    @ParameterizedTest(name = "status={0} -> mau hex hop le")
    @CsvSource({
            "0", "1", "2", "3", "4"
    })
    @DisplayName("genStatusColor tra ve mot ma hex hop le cho ca 5 trang thai")
    void genStatusColorReturnsValidHexForEveryKnownStatus(int status) {
        String color = OrderMailMapper.genStatusColor(status);
        assertTrue(color.matches("^#[0-9A-Fa-f]{6}$"), "khong phai ma hex hop le: " + color);
    }

    @Test
    @DisplayName("Ca 5 trang thai co 5 mau KHAC NHAU — badge phai phan biet duoc bang mat")
    void eachStatusHasItsOwnColor() {
        Set<String> colors = new HashSet<>();
        colors.add(OrderMailMapper.genStatusColor(OrderDomainService.STATUS_PENDING));
        colors.add(OrderMailMapper.genStatusColor(OrderDomainService.STATUS_CONFIRMED));
        colors.add(OrderMailMapper.genStatusColor(OrderDomainService.STATUS_SHIPPING));
        colors.add(OrderMailMapper.genStatusColor(OrderDomainService.STATUS_DELIVERED));
        colors.add(OrderMailMapper.genStatusColor(OrderDomainService.STATUS_CANCELLED));

        assertEquals(5, colors.size(), "co it nhat hai trang thai dung chung mau: " + colors);
    }

    @Test
    @DisplayName("genStatusColor voi gia tri la/null tra ve mau trung tinh, khong nem loi")
    void genStatusColorHandlesUnknownAndNullGracefully() {
        String unknown = OrderMailMapper.genStatusColor(99);
        String nullStatus = OrderMailMapper.genStatusColor(null);

        assertTrue(unknown.matches("^#[0-9A-Fa-f]{6}$"));
        assertTrue(nullStatus.matches("^#[0-9A-Fa-f]{6}$"));
        assertEquals(unknown, nullStatus, "gia tri la va null nen roi ve cung mot mau trung tinh");
    }

    @Test
    @DisplayName("genSubject neu thang ma don va nhan trang thai tieng Viet")
    void genSubjectIncludesOrderCodeAndVietnameseLabel() {
        String subject = OrderMailMapper.genSubject("NSS-20260831-K7M2QX9P4T",
                OrderDomainService.STATUS_CANCELLED);

        assertTrue(subject.contains("NSS-20260831-K7M2QX9P4T"), subject);
        assertTrue(subject.contains("Đã huỷ"), subject);
    }
}
