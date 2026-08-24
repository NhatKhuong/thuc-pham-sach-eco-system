package com.nss;

import com.nss.ddd.domain.model.CartIssue;
import com.nss.ddd.domain.model.CartIssueType;
import com.nss.ddd.domain.model.CartLine;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.domain.repository.ProductRepository;
import com.nss.ddd.domain.service.CartDomainService;
import com.nss.ddd.domain.service.impl.CartDomainServiceImpl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Kiểm {@code CartDomainServiceImpl} — quy tắc thuần, repository là mock, không cần database.
 * <p>
 * Ba ca đáng giá nhất, và cả ba đều khoá thứ hỏng <i>trong im lặng</i>:
 * <ul>
 *   <li>{@link #outOfStockSuppressesEveryOtherCheck()} — quy tắc loại trừ. Nếu ai đó biến nó thành
 *       ba phép kiểm song song thì một sản phẩm hết hàng sẽ kèm thêm một cảnh báo đổi giá, và
 *       frontend hiển thị được cả hai nên không có gì trông như lỗi.</li>
 *   <li>{@link #oneLineCanProduceTwoIssuesInFixedOrder()} — chiều ngược lại. Hai phép kiểm sau
 *       <b>song song</b>; biến chúng thành loại trừ thì một sản phẩm vừa thiếu hàng vừa đổi giá chỉ
 *       báo một nửa sự thật.</li>
 *   <li>{@link #currentPriceComesFromGeneratedColumnNotFromJavaArithmetic()} — dữ liệu trong ca này
 *       cố ý <i>mâu thuẫn</i> ({@code effectivePrice} khác {@code COALESCE(salePrice, price)}) để
 *       phân biệt được hai cách cài đặt cho ra cùng kết quả trên mọi dữ liệu bình thường.</li>
 * </ul>
 * Không có ca nào về múi giờ ở đây, và đó là chủ ý: tồn kho và giá là trạng thái hiện tại của một
 * dòng, không phải một cửa sổ hiệu lực, nên đường này không so thời gian với bất cứ thứ gì.
 */
class CartDomainServiceTest {

    private final ProductRepository productRepository = mock(ProductRepository.class);

    private final CartDomainService cartDomainService = new CartDomainServiceImpl(productRepository);

    // ========== GIO RONG: 200 VOI MANG RONG, KHONG PHAI LOI ==========

    /**
     * Giỏ rỗng cho ra danh sách rỗng, và <b>không đi xuống repository</b>.
     * <p>
     * Vế thứ hai mới là vế đáng kiểm: {@code IN ()} với một collection rỗng là lỗi cú pháp của
     * MySQL, nên một cài đặt "cứ hỏi rồi tính" sẽ biến ca hợp lệ nhất của cả endpoint thành lỗi
     * 500. {@code verifyNoInteractions} bắt được điều đó ngay ở tầng này, trước cả khi có database.
     *
     * @param lines giỏ rỗng, ở cả hai dạng nó có thể tới
     */
    @ParameterizedTest(name = "gio rong -> mang rong, khong cham repository")
    @ValueSource(strings = {"empty", "null"})
    @DisplayName("Gio rong tra mang rong va khong ban truy van nao")
    void emptyCartReturnsEmptyListWithoutQuerying(String shape) {
        List<CartLine> lines = "null".equals(shape) ? null : List.of();

        List<CartIssue> issues = cartDomainService.findIssues(lines);

        assertTrue(issues.isEmpty());
        verifyNoInteractions(productRepository);
    }

    @Test
    @DisplayName("Gio hop le tra mang rong, KHONG phai null")
    void validCartReturnsEmptyListNotNull() {
        when(productRepository.findByIds(any())).thenReturn(List.of(genProduct(5L, 100, 39_000L)));

        List<CartIssue> issues = cartDomainService.findIssues(List.of(genLine(5L, 2, 39_000L)));

        assertEquals(List.of(), issues);
    }

    // ========== OUT_OF_STOCK: BA NGUYEN NHAN, MOT CAU TRA LOI ==========

    /**
     * Không tra được sản phẩm thì ra {@code OUT_OF_STOCK}, và issue vẫn mang tên do client gửi.
     * <p>
     * Vế tên là contract: đây đúng là ca mà DB <b>không còn tên nào để tra</b>, nên nếu tên không
     * đến từ client thì người dùng nhận một cảnh báo không nói được món nào cần bỏ khỏi giỏ.
     */
    @Test
    @DisplayName("Khong tra duoc san pham -> out_of_stock, ten lay tu dong client gui")
    void missingProductBecomesOutOfStockCarryingClientName() {
        when(productRepository.findByIds(any())).thenReturn(List.of());

        List<CartIssue> issues = cartDomainService.findIssues(
                List.of(genLine(999_999L, 1, 10_000L).setName("Món đã bị gỡ")));

        assertEquals(1, issues.size());
        assertEquals(CartIssueType.OUT_OF_STOCK, issues.get(0).getType());
        assertEquals(999_999L, issues.get(0).getProductId());
        assertEquals("Món đã bị gỡ", issues.get(0).getName());
    }

    /**
     * {@code stock <= 0} cũng ra {@code OUT_OF_STOCK}.
     * <p>
     * Kiểm cả {@code 0} lẫn một giá trị âm: tồn kho âm không nên tồn tại, nhưng nếu một ngày nào đó
     * nó xuất hiện thì {@code stock < quantity} vẫn đúng, và một cài đặt chỉ so {@code == 0} sẽ trả
     * {@code insufficient_stock} kèm {@code availableStock: -3} — một con số vô nghĩa hiển thị được.
     *
     * @param stock tồn kho không dương
     */
    @ParameterizedTest(name = "stock={0} -> out_of_stock")
    @ValueSource(ints = {0, -3})
    @DisplayName("Ton kho khong duong -> out_of_stock, khong phai insufficient_stock")
    void nonPositiveStockBecomesOutOfStock(int stock) {
        when(productRepository.findByIds(any())).thenReturn(List.of(genProduct(10L, stock, 28_000L)));

        List<CartIssue> issues = cartDomainService.findIssues(List.of(genLine(10L, 2, 28_000L)));

        assertEquals(1, issues.size());
        assertEquals(CartIssueType.OUT_OF_STOCK, issues.get(0).getType());
    }

    /**
     * <b>Quy tắc loại trừ</b>: một dòng đã {@code OUT_OF_STOCK} không sinh thêm issue nào nữa.
     * <p>
     * Dữ liệu của ca này thoả <i>cả ba</i> điều kiện cùng lúc — hết hàng, thiếu số lượng, và lệch
     * giá — nên nó phân biệt được "loại trừ" với "ba phép kiểm song song". Kiểm luôn rằng issue
     * không mang trường tuỳ chọn nào: một {@code availableStock: 0} gắn lên đây là con số sai mà
     * frontend vẫn hiển thị được.
     */
    @Test
    @DisplayName("out_of_stock chan moi kiem tra con lai — dung 1 issue, khong truong tuy chon nao")
    void outOfStockSuppressesEveryOtherCheck() {
        when(productRepository.findByIds(any())).thenReturn(List.of(genProduct(10L, 0, 28_000L)));

        List<CartIssue> issues = cartDomainService.findIssues(List.of(genLine(10L, 5, 25_000L)));

        assertEquals(1, issues.size());
        CartIssue issue = issues.get(0);
        assertEquals(CartIssueType.OUT_OF_STOCK, issue.getType());
        assertNull(issue.getAvailableStock());
        assertNull(issue.getCurrentPrice());
        assertNull(issue.getCartPrice());
    }

    // ========== INSUFFICIENT_STOCK ==========

    /**
     * {@code availableStock} là tồn kho <b>của DB</b>, không phải con số nào client gửi.
     * <p>
     * {@code CartLine} không có trường {@code stock} nên client không có đường nào ảnh hưởng vào
     * đây — ca này khoá lại điều đó bằng cách khẳng định con số trả về đúng bằng tồn kho của mock.
     */
    @Test
    @DisplayName("insufficient_stock mang dung ton kho cua DB")
    void insufficientStockCarriesDatabaseStock() {
        when(productRepository.findByIds(any())).thenReturn(List.of(genProduct(32L, 24, 620_000L)));

        List<CartIssue> issues = cartDomainService.findIssues(List.of(genLine(32L, 30, 620_000L)));

        assertEquals(1, issues.size());
        assertEquals(CartIssueType.INSUFFICIENT_STOCK, issues.get(0).getType());
        assertEquals(24, issues.get(0).getAvailableStock());
        assertNull(issues.get(0).getCurrentPrice());
    }

    /**
     * {@code quantity == stock} là mua vừa đủ, <b>không</b> phải thiếu hàng.
     * <p>
     * Ranh giới {@code >} với {@code >=} không làm gãy gì cả — nó chỉ khiến khách không mua nổi
     * món cuối cùng trong kho, và không có lỗi nào nổ ra.
     */
    @Test
    @DisplayName("quantity bang dung stock -> khong co issue nao")
    void buyingExactlyAllRemainingStockIsFine() {
        when(productRepository.findByIds(any())).thenReturn(List.of(genProduct(32L, 24, 620_000L)));

        List<CartIssue> issues = cartDomainService.findIssues(List.of(genLine(32L, 24, 620_000L)));

        assertTrue(issues.isEmpty());
    }

    // ========== PRICE_CHANGED ==========

    @Test
    @DisplayName("price_changed mang ca gia hien tai lan gia trong gio, khong mang availableStock")
    void priceChangedCarriesBothPrices() {
        when(productRepository.findByIds(any())).thenReturn(List.of(genProduct(5L, 200, 39_000L)));

        List<CartIssue> issues = cartDomainService.findIssues(List.of(genLine(5L, 2, 45_000L)));

        assertEquals(1, issues.size());
        CartIssue issue = issues.get(0);
        assertEquals(CartIssueType.PRICE_CHANGED, issue.getType());
        assertEquals(39_000L, issue.getCurrentPrice());
        assertEquals(45_000L, issue.getCartPrice());
        assertNull(issue.getAvailableStock());
    }

    /**
     * Giá hiện tại đọc từ cột sinh {@code effective_price}, <b>không</b> tính lại
     * {@code salePrice ?? price} ở Java.
     * <p>
     * <b>Dữ liệu của ca này cố ý mâu thuẫn:</b> {@code price = 100_000}, {@code salePrice = 80_000},
     * nhưng {@code effectivePrice = 39_000}. Một cột sinh STORED không bao giờ lệch như vậy trong
     * DB thật — chính vì thế nó là dữ liệu duy nhất phân biệt được hai cách cài đặt vốn cho ra cùng
     * kết quả trên mọi dòng bình thường. Giỏ gửi lên {@code 39_000}: đọc cột thì không có issue,
     * còn tính lại ở Java thì ra một {@code price_changed} với {@code currentPrice: 80000}.
     * <p>
     * Đây là coding-conventions §15 ở dạng cụ thể nhất của nó — cùng một con số tính ở hai nơi thì
     * quy ước tính là một phần của contract.
     */
    @Test
    @DisplayName("Gia hien tai lay tu cot effective_price, khong tinh lai salePrice ?? price o Java")
    void currentPriceComesFromGeneratedColumnNotFromJavaArithmetic() {
        Product contradictory = genProduct(5L, 200, 39_000L)
                .setPrice(100_000L)
                .setSalePrice(80_000L);
        when(productRepository.findByIds(any())).thenReturn(List.of(contradictory));

        List<CartIssue> issues = cartDomainService.findIssues(List.of(genLine(5L, 1, 39_000L)));

        assertTrue(issues.isEmpty());
    }

    // ========== HAI ISSUE TREN MOT DONG ==========

    /**
     * Một dòng vừa thiếu hàng vừa lệch giá sinh <b>hai</b> issue, và
     * {@code INSUFFICIENT_STOCK} đứng trước.
     * <p>
     * Cả hai vế đều là contract, và cả hai đều hỏng im lặng theo hai kiểu khác nhau: gộp thành một
     * thì khách sửa số lượng xong vẫn không biết giá đã đổi; đảo thứ tự thì frontend hiển thị danh
     * sách theo thứ tự nhận được và người dùng thấy cảnh báo phụ đứng trước cảnh báo chặn.
     */
    @Test
    @DisplayName("Mot dong sinh HAI issue: insufficient_stock truoc, price_changed sau")
    void oneLineCanProduceTwoIssuesInFixedOrder() {
        when(productRepository.findByIds(any())).thenReturn(List.of(genProduct(32L, 24, 620_000L)));

        List<CartIssue> issues = cartDomainService.findIssues(List.of(genLine(32L, 30, 600_000L)));

        assertEquals(2, issues.size());
        assertEquals(CartIssueType.INSUFFICIENT_STOCK, issues.get(0).getType());
        assertEquals(24, issues.get(0).getAvailableStock());
        assertEquals(CartIssueType.PRICE_CHANGED, issues.get(1).getType());
        assertEquals(620_000L, issues.get(1).getCurrentPrice());
        assertEquals(600_000L, issues.get(1).getCartPrice());
        assertEquals(32L, issues.get(0).getProductId());
        assertEquals(32L, issues.get(1).getProductId());
    }

    // ========== THU TU VA TRUNG LAP ==========

    /**
     * Thứ tự issue bám theo thứ tự dòng client gửi, kể cả khi repository trả về theo thứ tự khác.
     * <p>
     * Mock cố ý trả sản phẩm theo thứ tự <i>ngược</i> với giỏ: một cài đặt duyệt theo kết quả truy
     * vấn thay vì duyệt theo giỏ sẽ xanh trên dữ liệu sắp sẵn và đỏ ở đây.
     */
    @Test
    @DisplayName("Thu tu issue bam theo thu tu dong client gui, khong theo thu tu repository tra ve")
    void issueOrderFollowsRequestOrderNotQueryOrder() {
        when(productRepository.findByIds(any())).thenReturn(List.of(
                genProduct(42L, 0, 85_000L),
                genProduct(10L, 0, 28_000L)));

        List<CartIssue> issues = cartDomainService.findIssues(List.of(
                genLine(10L, 1, 28_000L),
                genLine(42L, 1, 85_000L)));

        assertEquals(2, issues.size());
        assertEquals(10L, issues.get(0).getProductId());
        assertEquals(42L, issues.get(1).getProductId());
    }

    /**
     * {@code productId} trùng lặp được xử lý từng dòng độc lập, <b>không gộp</b>.
     * <p>
     * Hai dòng cùng sản phẩm, mỗi dòng {@code quantity = 20} trên tồn kho 24: gộp lại thành 40 sẽ
     * ra một issue thiếu hàng, xử lý độc lập thì <i>không</i> dòng nào thiếu. Backend không được tự
     * chế quy ước gộp mà phía kia không biết — nếu tổng của giỏ vượt kho thì đó là việc của
     * {@code POST /orders}, nơi tồn kho thật sự bị trừ.
     */
    @Test
    @DisplayName("productId trung lap xu ly tung dong doc lap, khong cong don so luong")
    void duplicateProductIdsAreJudgedPerLine() {
        when(productRepository.findByIds(any())).thenReturn(List.of(genProduct(32L, 24, 620_000L)));

        List<CartIssue> issues = cartDomainService.findIssues(List.of(
                genLine(32L, 20, 620_000L),
                genLine(32L, 20, 600_000L)));

        assertEquals(1, issues.size());
        assertEquals(CartIssueType.PRICE_CHANGED, issues.get(0).getType());
    }

    /**
     * Cả giỏ đi xuống DB bằng <b>một</b> lượt, và id trùng lặp chỉ được hỏi một lần.
     * <p>
     * Một cài đặt tra từng dòng cho ra cùng kết quả trên mọi ca ở trên — chỉ có số lượt đi vòng tới
     * MySQL là khác, và con số đó không xuất hiện trong bất kỳ response nào. Đây là chỗ duy nhất
     * bắt được nó.
     */
    @Test
    @DisplayName("Ca gio hoi DB dung mot luot, id trung lap chi hoi mot lan")
    void wholeCartIsFetchedInASingleDeduplicatedQuery() {
        when(productRepository.findByIds(any())).thenReturn(List.of(
                genProduct(10L, 5, 28_000L),
                genProduct(32L, 24, 620_000L)));

        cartDomainService.findIssues(List.of(
                genLine(10L, 1, 28_000L),
                genLine(32L, 1, 620_000L),
                genLine(10L, 2, 28_000L)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(productRepository, times(1)).findByIds(captor.capture());
        verify(productRepository, never()).findById(any());
        assertEquals(List.of(10L, 32L), List.copyOf(captor.getValue()));
    }

    // ========== HELPERS ==========

    /**
     * @param id khóa chính
     * @param stock tồn kho
     * @param effectivePrice giá của cột sinh {@code effective_price}
     * @return sản phẩm mang đúng ba trường mà đường giỏ hàng đọc tới
     */
    private Product genProduct(Long id, int stock, Long effectivePrice) {
        return new Product()
                .setId(id)
                .setStock(stock)
                .setEffectivePrice(effectivePrice);
    }

    /**
     * @param productId id sản phẩm
     * @param quantity số lượng khách muốn mua
     * @param price giá giỏ hàng đang hiển thị
     * @return một dòng giỏ hàng
     */
    private CartLine genLine(Long productId, int quantity, Long price) {
        return new CartLine()
                .setProductId(productId)
                .setName("San pham " + productId)
                .setQuantity(quantity)
                .setPrice(price);
    }
}
