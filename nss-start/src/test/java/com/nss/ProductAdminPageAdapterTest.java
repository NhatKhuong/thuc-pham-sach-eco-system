package com.nss;

import com.nss.ddd.domain.model.PageResult;
import com.nss.ddd.domain.model.ProductFilter;
import com.nss.ddd.domain.model.ProductSort;
import com.nss.ddd.domain.model.StockStatus;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.infrastructure.persistence.mapper.ProductJPAMapper;
import com.nss.ddd.infrastructure.persistence.repository.ProductRepositoryImpl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm ba phép dịch của {@code ProductRepositoryImpl#findAdminPage} — chỗ duy nhất khái niệm domain
 * gặp khái niệm Spring Data.
 * <p>
 * Cả ba đều thuộc loại <b>hỏng trong im lặng</b>, nên chúng được kiểm bằng cách bắt lấy đúng tham
 * số đi xuống Spring Data thay vì nhìn kết quả trả về:
 * <ul>
 *   <li>quên phép trừ 1 của {@code page} thì trang 1 trả về trang thứ hai;</li>
 *   <li>quên khoá phụ theo {@code id} thì phân trang trả trùng dòng ở hai trang khác nhau;</li>
 *   <li>quên escape {@code %} thì bộ lọc trả nhiều dòng hơn số dòng thật sự khớp.</li>
 * </ul>
 * Không có ca nào trong ba ca đó ném exception, và cả ba đều trả HTTP 200.
 */
@ExtendWith(MockitoExtension.class)
class ProductAdminPageAdapterTest {

    @Mock
    private ProductJPAMapper productJPAMapper;

    @InjectMocks
    private ProductRepositoryImpl productRepository;

    /**
     * Chạy adapter với một filter và trả về {@code Pageable} mà nó dựng.
     * <p>
     * Dùng {@code atLeastOnce()} rồi lấy giá trị <b>cuối cùng</b> chứ không {@code verify()} trần:
     * một ca gọi helper này hai lần (để so trang 1 với trang 2) sẽ làm {@code verify()} trần đỏ vì
     * "thừa lời gọi", mà đó là một thất bại của khung đo chứ không phải của thứ đang được đo.
     *
     * @param filter điều kiện lọc
     * @return pageable của lời gọi gần nhất
     */
    private Pageable capturePageable(ProductFilter filter) {
        when(productJPAMapper.findAdminPage(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        productRepository.findAdminPage(filter);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productJPAMapper, atLeastOnce())
                .findAdminPage(any(), any(), any(), any(), captor.capture());
        List<Pageable> all = captor.getAllValues();
        return all.get(all.size() - 1);
    }

    /**
     * Chạy adapter với một từ khoá và trả về mẫu {@code LIKE} mà nó dựng.
     *
     * @param keyword từ khoá đã bỏ dấu
     * @return mẫu LIKE của lời gọi gần nhất
     */
    private String capturePattern(String keyword) {
        when(productJPAMapper.findAdminPage(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        productRepository.findAdminPage(ProductFilter.of(keyword, null, null, null, 1, 12));
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(productJPAMapper, atLeastOnce())
                .findAdminPage(captor.capture(), any(), any(), any(), any());
        List<String> all = captor.getAllValues();
        return all.get(all.size() - 1);
    }

    // ========== PHAN TRANG ==========

    @Test
    @DisplayName("page danh so tu 1 tren day tru 1 khi xuong Spring Data")
    void convertsOneBasedPageToZeroBased() {
        assertEquals(0, capturePageable(ProductFilter.of(null, null, null, null, 1, 12)).getPageNumber());
        assertEquals(1, capturePageable(ProductFilter.of(null, null, null, null, 2, 12)).getPageNumber());
    }

    @Test
    @DisplayName("limit di thang xuong pageSize")
    void passesLimitAsPageSize() {
        assertEquals(24, capturePageable(ProductFilter.of(null, null, null, null, 1, 24)).getPageSize());
    }

    // ========== SAP XEP ==========

    /**
     * Mỗi giá trị {@code sort} ánh xạ vào đúng cột và đúng chiều.
     *
     * @param sort giá trị của domain
     * @param property tên thuộc tính entity mong đợi
     * @param descending chiều mong đợi
     */
    @ParameterizedTest(name = "{0} -> {1} {2}")
    @CsvSource({
            "NEWEST,        createdAt,      true",
            "PRICE_ASC,     effectivePrice, false",
            "PRICE_DESC,    effectivePrice, true",
            "BEST_SELLING,  sold,           true",
            "RATING,        rating,         true"
    })
    @DisplayName("Nam gia tri sort anh xa dung cot va dung chieu")
    void mapsEachSortToItsColumnAndDirection(ProductSort sort, String property, boolean descending) {
        Sort actual = capturePageable(ProductFilter.of(null, null, null, sort, 1, 12)).getSort();
        Sort.Order primary = actual.iterator().next();

        assertEquals(property, primary.getProperty());
        assertEquals(descending, primary.isDescending(),
                sort + " phai sap theo chieu " + (descending ? "giam" : "tang"));
    }

    /**
     * <b>{@code price_asc} sắp theo {@code effectivePrice}, KHÔNG theo {@code price}.</b>
     * <p>
     * Viết riêng thành một ca vì đây là chỗ dễ sai nhất và nó sai <i>hợp lý</i>: {@code price} là
     * cái tên hiển nhiên hơn, và một sản phẩm không giảm giá thì hai cột bằng nhau, nên lỗi chỉ lộ
     * ra ở đúng những sản phẩm đang giảm giá — tức đúng những sản phẩm người dùng để ý nhất.
     */
    @Test
    @DisplayName("price_asc sap theo effectivePrice, KHONG theo price")
    void priceSortUsesEffectivePriceNotPrice() {
        Sort actual = capturePageable(
                ProductFilter.of(null, null, null, ProductSort.PRICE_ASC, 1, 12)).getSort();

        assertNotNull(actual.getOrderFor("effectivePrice"), "Phai sap theo gia sau giam");
        assertNull(actual.getOrderFor("price"), "KHONG duoc sap theo gia goc");
    }

    /**
     * <b>Mọi thứ tự đều kèm khoá phụ theo {@code id}.</b>
     * <p>
     * Không có nó, hai sản phẩm cùng {@code sold} (hoặc cùng {@code rating}, hoặc cùng giá — chuyện
     * rất thường) có thể được trả theo thứ tự khác nhau ở hai lần chạy. Với phân trang
     * {@code OFFSET}, hệ quả là một dòng xuất hiện ở cả trang 1 lẫn trang 2 còn một dòng khác không
     * xuất hiện ở đâu — và không có gì báo lỗi.
     *
     * @param sort giá trị của domain
     */
    @ParameterizedTest(name = "{0} kem khoa phu theo id")
    @CsvSource({"NEWEST", "PRICE_ASC", "PRICE_DESC", "BEST_SELLING", "RATING"})
    @DisplayName("Moi thu tu deu kem khoa phu theo id de phan trang on dinh")
    void everySortCarriesAnIdTieBreaker(ProductSort sort) {
        Sort actual = capturePageable(ProductFilter.of(null, null, null, sort, 1, 12)).getSort();

        assertNotNull(actual.getOrderFor("id"), sort + " thieu khoa phu theo id");
        assertEquals(2, actual.stream().count(), sort + " phai co dung 2 khoa sap xep");
    }

    @Test
    @DisplayName("sort rong roi ve NEWEST, khong nem loi")
    void nullSortFallsBackToDefault() {
        Sort actual = capturePageable(ProductFilter.of(null, null, null, null, 1, 12)).getSort();

        assertNotNull(actual.getOrderFor("createdAt"));
        assertTrue(actual.getOrderFor("createdAt").isDescending());
    }

    // ========== TON KHO ==========

    /**
     * Cặp biên đi xuống truy vấn đúng bằng cặp biên của enum.
     *
     * @param status trạng thái cần lọc
     */
    @ParameterizedTest(name = "{0} -> (min, max) dung bang bien cua enum")
    @CsvSource({"IN_STOCK", "LOW_STOCK", "OUT_OF_STOCK"})
    @DisplayName("stockStatus dich thanh cap (minStock, maxStock) cua chinh enum")
    void translatesStockStatusToItsOwnBounds(StockStatus status) {
        when(productJPAMapper.findAdminPage(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        productRepository.findAdminPage(ProductFilter.of(null, null, status, null, 1, 12));

        ArgumentCaptor<Integer> min = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> max = ArgumentCaptor.forClass(Integer.class);
        verify(productJPAMapper).findAdminPage(any(), any(), min.capture(), max.capture(), any());

        assertEquals(status.getMinStock(), min.getValue());
        assertEquals(status.getMaxStock(), max.getValue());
    }

    @Test
    @DisplayName("Khong loc ton kho thi ca hai bien la null")
    void nullStockStatusSendsNoBounds() {
        when(productJPAMapper.findAdminPage(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        productRepository.findAdminPage(ProductFilter.of(null, null, null, null, 1, 12));

        ArgumentCaptor<Integer> min = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> max = ArgumentCaptor.forClass(Integer.class);
        verify(productJPAMapper).findAdminPage(any(), any(), min.capture(), max.capture(), any());

        assertNull(min.getValue());
        assertNull(max.getValue());
    }

    // ========== MAU LIKE ==========

    @Test
    @DisplayName("Tu khoa duoc boc % hai dau")
    void wrapsKeywordInWildcards() {
        assertEquals("%ca rot%", capturePattern("ca rot"));
    }

    @Test
    @DisplayName("Khong co tu khoa thi mau la null — de menh de :pattern IS NULL bo qua bo loc")
    void noKeywordMeansNullPattern() {
        assertNull(capturePattern(null));
    }

    /**
     * <b>Ký tự đại diện trong đầu vào người dùng phải được escape.</b>
     * <p>
     * Frontend so bằng {@code String.includes()} — một phép so chuỗi con theo nghĩa đen. Không
     * escape thì {@code q=100%} biến {@code %} thành ký tự đại diện và trả về nhiều dòng hơn số
     * dòng thật sự khớp: một kết quả <i>sai</i> trông y hệt một kết quả đúng.
     *
     * @param keyword từ khoá chứa ký tự đặc biệt
     * @param expected mẫu mong đợi
     */
    @ParameterizedTest(name = "q={0} -> {1}")
    @CsvSource({
            "'100%',        '%100!%%'",
            "'a_b',         '%a!_b%'",
            "'a!b',         '%a!!b%'",
            "'%_%',         '%!%!_!%%'"
    })
    @DisplayName("Ky tu dai dien % _ va chinh ky tu escape deu duoc escape bang '!'")
    void escapesWildcardCharacters(String keyword, String expected) {
        assertEquals(expected, capturePattern(keyword));
    }

    // ========== KET QUA ==========

    @Test
    @DisplayName("total lay tu totalElements, khong phai so phan tu cua trang")
    void totalComesFromTotalElementsNotPageSize() {
        Product product = new Product().setId(1L);
        when(productJPAMapper.findAdminPage(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(product), Pageable.ofSize(12), 42));

        PageResult<Product> result = productRepository.findAdminPage(
                ProductFilter.of(null, null, null, null, 1, 12));

        assertEquals(1, result.getItems().size());
        assertEquals(42, result.getTotal(), "total phai la tong so dong khop bo loc");
    }
}
