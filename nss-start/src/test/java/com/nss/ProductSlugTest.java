package com.nss;

import com.nss.ddd.domain.repository.BrandRepository;
import com.nss.ddd.domain.repository.CategoryRepository;
import com.nss.ddd.domain.repository.ProductImageRepository;
import com.nss.ddd.domain.repository.ProductRepository;
import com.nss.ddd.domain.service.impl.ProductDomainServiceImpl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Kiểm phép sinh slug của {@code ProductDomainServiceImpl} — API_CONTRACT §B.12.1.
 * <p>
 * <b>Đây là một hàm được hiện thực ở HAI engine</b>: {@code slugify} trong
 * {@code src/lib/utils.ts:21-32} của frontend, và {@code genSlug} ở đây. Chừng nào lớp mock của
 * frontend còn sinh slug cho dữ liệu cục bộ thì hai bản phải cho ra <i>cùng một chuỗi</i> — lệch
 * nhau nghĩa là cùng một sản phẩm mang hai đường dẫn khác nhau tuỳ theo nó được tạo ở đâu, và
 * triệu chứng là một link 404 chứ không phải một lỗi ở chỗ sinh ra nó.
 * <p>
 * Vì vậy các ca dưới đây <b>pin từng bước và pin cả thứ tự giữa các bước</b>, không chỉ pin kết quả
 * của một ca đẹp. Xem {@code coding-conventions.md} §18.
 */
@ExtendWith(MockitoExtension.class)
class ProductSlugTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BrandRepository brandRepository;

    @InjectMocks
    private ProductDomainServiceImpl productDomainService;

    /**
     * Bỏ trống slug thì sinh từ {@code name}, có dấu thì bỏ dấu.
     *
     * @param name tên hiển thị
     * @param expected slug mong đợi
     */
    @ParameterizedTest(name = "name=\"{0}\" -> {1}")
    @CsvSource({
            "Cam hữu cơ,            cam-huu-co",
            "Cà rốt hữu cơ,         ca-rot-huu-co",
            "Rau muống,             rau-muong",
            "Táo Mỹ,                tao-my"
    })
    @DisplayName("Slug bo trong thi sinh tu name, bo dau va noi bang gach ngang")
    void generatesSlugFromNameWhenSlugIsBlank(String name, String expected) {
        assertEquals(expected, productDomainService.genSlug(null, name));
        assertEquals(expected, productDomainService.genSlug("", name));
        assertEquals(expected, productDomainService.genSlug("   ", name));
    }

    /**
     * <b>Chữ {@code đ} là bước riêng, không phải hệ quả của NFD.</b>
     * <p>
     * {@code Normalizer} tách được dấu thanh khỏi nguyên âm, nhưng {@code đ} là một chữ cái Latin
     * riêng chứ không phải {@code d} có dấu — NFD không đụng tới nó. Thiếu bước này thì "Đậu Hà
     * Lan" ra {@code dau-ha-lan} <i>chỉ khi</i> có bước hạ chữ thường bắt được {@code Đ}, còn
     * {@code đ} thường thì lọt lại và bị bước 6 xoá mất, cho ra {@code au-ha-lan}. Cả hai kiểu hỏng
     * đều tạo ra một slug trông vẫn hợp lệ.
     *
     * @param name tên có chứa đ hoặc Đ
     * @param expected slug mong đợi
     */
    @ParameterizedTest(name = "name=\"{0}\" -> {1}")
    @CsvSource({
            "Đậu Hà Lan,        dau-ha-lan",
            "Đậu đũa,           dau-dua",
            "Dưa hấu Đà Lạt,    dua-hau-da-lat"
    })
    @DisplayName("Chu d va D duoc doi thanh d/D — NFD khong tach duoc ky tu nay")
    void mapsVietnameseDCharacterExplicitly(String name, String expected) {
        assertEquals(expected, productDomainService.genSlug(null, name));
    }

    /**
     * <b>Slug do client gửi CŨNG được slugify — không chỉ khi bỏ trống.</b>
     * <p>
     * Đo được ở {@code adminProducts.api.ts:117}. Đây là lý do {@code @NotBlank} và
     * {@code @Pattern("^[a-z0-9-]+$")} phải được gỡ khỏi hai DTO: form quản trị cho admin gõ tự do
     * vào ô slug, nên "Cà Rốt Hữu Cơ" gõ vào đó phải thành một slug chứ không thành 422.
     *
     * @param requested slug client gửi
     * @param expected slug mong đợi
     */
    @ParameterizedTest(name = "slug=\"{0}\" -> {1}")
    @CsvSource({
            "Cà Rốt Hữu Cơ,     ca-rot-huu-co",
            "CA-ROT,            ca-rot",
            "  ca-rot  ,        ca-rot",
            "Ca Rot!!!,         ca-rot",
            "ca-rot,            ca-rot"
    })
    @DisplayName("Slug client gui CUNG duoc slugify, khong chi khi bo trong")
    void slugifiesClientSuppliedSlugToo(String requested, String expected) {
        // name co y khac han slug de chung minh nguon duoc dung la `requested`
        assertEquals(expected, productDomainService.genSlug(requested, "Một tên hoàn toàn khác"));
    }

    /**
     * Thứ tự bảy bước — mỗi ca dưới đây đỏ nếu một bước bị đảo chỗ.
     *
     * @param source chuỗi nguồn
     * @param expected slug mong đợi
     */
    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            // trim chay TRUOC khi doi khoang trang -> khong co gach ngang thua o hai dau
            "'   Cà rốt   ',        ca-rot",
            // gop nhieu gach ngang lien tiep thanh mot
            "'Cà rốt - loại 1',     ca-rot-loai-1",
            // nhieu khoang trang lien tiep -> mot gach ngang
            "'Cà    rốt',           ca-rot",
            // ky tu ngoai [a-z0-9] bi bo, KHONG bi doi thanh gach ngang
            "'Cà rốt (hữu cơ)',     ca-rot-huu-co",
            "'Táo 100% sạch',       tao-100-sach",
            // chu so duoc giu
            "'Combo 3 loại',        combo-3-loai"
    })
    @DisplayName("Bay buoc chay dung thu tu: trim truoc, bo ky tu la, roi moi gop gach ngang")
    void appliesTheSevenStepsInOrder(String source, String expected) {
        assertEquals(expected, productDomainService.genSlug(null, source));
    }

    /**
     * <b>Không sinh ra được ký tự hợp lệ nào thì trả {@code null}, không trả chuỗi rỗng.</b>
     * <p>
     * Tầng application dịch {@code null} thành 422 kèm thông điệp tiếng Việt. Trả chuỗi rỗng thay
     * vào đó nghĩa là ghi im lặng một slug rỗng xuống cột {@code uk_slug} — sản phẩm thứ hai như
     * vậy chết bằng lỗi ràng buộc ở tầng dữ liệu, tức một 500 thay cho một thông điệp đọc được.
     * Frontend ném lỗi ở đúng ca này ({@code adminProducts.api.ts:118}).
     *
     * @param source chuỗi nguồn không còn ký tự hợp lệ nào
     */
    @ParameterizedTest(name = "\"{0}\" -> null")
    @ValueSource(strings = {"***", "!!!", "   ", "%%%", "@@@"})
    @DisplayName("Nguon khong con ky tu hop le nao thi tra null, KHONG tra chuoi rong")
    void returnsNullWhenNothingUsableRemains(String source) {
        assertNull(productDomainService.genSlug(null, source),
                "Phai tra null de tang tren dich thanh 422, khong duoc ghi slug rong xuong DB");
    }

    @Test
    @DisplayName("Ca slug lan name deu rong thi tra null, khong nem NullPointerException")
    void returnsNullWhenBothSourcesAreNull() {
        assertNull(productDomainService.genSlug(null, null));
    }

    /**
     * Slug sinh ra luôn hợp mẫu cũ {@code ^[a-z0-9-]+$}.
     * <p>
     * <b>Đây là chỗ chứng minh việc gỡ {@code @Pattern} khỏi DTO không phải là nới luật.</b> Hình
     * dạng slug vẫn được cưỡng chế y như trước, chỉ là ở tầng khác và bằng cách <i>sửa</i> đầu vào
     * hợp lý thay vì <i>từ chối</i> nó.
     *
     * @param source chuỗi nguồn bất kỳ
     */
    @ParameterizedTest(name = "\"{0}\" cho ra slug hop mau cu")
    @ValueSource(strings = {
            "Cà Rốt Hữu Cơ!", "Đậu   Hà  Lan", "Táo 100% sạch", "  Rau muống  ", "ABC_XYZ"
    })
    @DisplayName("Slug sinh ra luon hop mau ^[a-z0-9-]+$ — go @Pattern khong phai noi luat")
    void generatedSlugAlwaysMatchesTheOldPattern(String source) {
        String slug = productDomainService.genSlug(null, source);
        assertEquals(slug, slug == null ? null : slug.replaceAll("[^a-z0-9-]", ""),
                "Slug \"" + slug + "\" chua ky tu ngoai mau ^[a-z0-9-]+$");
    }
}
