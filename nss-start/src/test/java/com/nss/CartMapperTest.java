package com.nss;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nss.ddd.application.mapper.CartMapper;
import com.nss.ddd.application.model.command.CartItemCommand;
import com.nss.ddd.application.model.response.CartIssueResponse;
import com.nss.ddd.domain.model.CartIssue;
import com.nss.ddd.domain.model.CartIssueType;
import com.nss.ddd.domain.model.CartLine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm {@code CartMapper} — logic thuần, không cần Spring context và không cần database.
 * <p>
 * Phép kiểm đáng giá nhất ở đây là <b>ba ca tuần tự hoá thật bằng Jackson</b>
 * ({@link #outOfStockCarriesExactlyThreeKeys()} và hai ca anh em của nó). Chúng đọc <i>khoá JSON
 * thật sự đi lên dây</i> thay vì đọc danh sách field bằng mắt, nên chúng bắt được cả trường hợp ai
 * đó gỡ {@code @JsonInclude} khỏi {@code CartIssueResponse} về sau — lúc đó mọi issue sẽ mang
 * {@code "availableStock": null} và frontend đọc ra một tồn kho không tồn tại.
 * <p>
 * <b>Mỗi ca khẳng định cả hai chiều</b> — đúng tập khoá phải có, và tập khoá không được có — vì
 * "không có X" chỉ có nghĩa khi phép đo <i>bắt được</i> X. Một {@code assertFalse(has(...))} viết
 * sai tên khoá cũng xanh.
 */
class CartMapperTest {

    /** Ba khoá bắt buộc của mọi {@code CartIssue} phía client. */
    private static final Set<String> REQUIRED_KEYS = Set.of("productId", "name", "type");

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ========== BANG DICH TYPE ==========

    /**
     * Bảng dịch enum sang chuỗi của dây, cả ba giá trị trong <b>một</b> ca.
     * <p>
     * Gộp lại là có chủ ý: ba khẳng định này chỉ có nghĩa khi cùng đúng. Một mapper trả
     * {@code "out_of_stock"} cho mọi loại vẫn qua được một phần ba phép kiểm nếu tách rời — và hậu
     * quả của đúng lỗi đó là frontend chặn thanh toán vì một cảnh báo lẽ ra chỉ để tham khảo.
     */
    @Test
    @DisplayName("Ba loai issue dich ra dung ba chuoi cua day")
    void wireTypeTranslatesAllThreeValues() {
        assertEquals("out_of_stock",
                CartMapper.toResponse(CartIssue.outOfStock(1L, "x")).getType());
        assertEquals("insufficient_stock",
                CartMapper.toResponse(CartIssue.insufficientStock(1L, "x", 3)).getType());
        assertEquals("price_changed",
                CartMapper.toResponse(CartIssue.priceChanged(1L, "x", 1L, 2L)).getType());
    }

    /**
     * Ba hằng {@code WIRE_TYPE_*} mang đúng chuỗi mà {@code src/types/cart.ts} khai.
     * <p>
     * Viết literal ở đây chứ không tham chiếu hằng: một ca so hằng với chính nó luôn xanh.
     */
    @Test
    @DisplayName("Ba hang WIRE_TYPE mang dung chuoi cua hop dong")
    void wireTypeConstantsMatchContract() {
        assertEquals("out_of_stock", CartMapper.WIRE_TYPE_OUT_OF_STOCK);
        assertEquals("insufficient_stock", CartMapper.WIRE_TYPE_INSUFFICIENT_STOCK);
        assertEquals("price_changed", CartMapper.WIRE_TYPE_PRICE_CHANGED);
    }

    /**
     * Loại không nằm trong bảng dịch trả {@code null} chứ không đoán bừa.
     * <p>
     * Cùng lý do đã viết ở {@code CouponMapper}: rơi về một trong ba chuỗi cũ sẽ khiến frontend xử
     * lý sai loại mà <i>vẫn hiển thị một cái gì đó</i>.
     */
    @Test
    @DisplayName("type rong -> null, khong doan ve mot trong ba chuoi cu")
    void nullTypeTranslatesToNull() {
        CartIssue issue = new CartIssue().setProductId(1L).setName("x").setType(null);

        assertNull(CartMapper.toResponse(issue).getType());
    }

    // ========== HINH DANG JSON: BA LOAI, BA TAP KHOA ==========

    /**
     * {@code out_of_stock} mang <b>đúng ba</b> khoá — không {@code availableStock}, không hai giá.
     * <p>
     * Bước control đứng trước là bắt buộc: khẳng định ba khoá bắt buộc <i>có mặt</i> chứng minh
     * phép đo nhìn thấy được khoá trên object này, rồi khẳng định ba khoá tuỳ chọn vắng mặt mới có
     * nghĩa. Không có bước đó, một {@code assertFalse(has(...))} viết sai tên cũng xanh.
     *
     * @throws Exception khi Jackson lỗi
     */
    @Test
    @DisplayName("out_of_stock tuan tu hoa ra DUNG ba khoa, khong truong tuy chon nao")
    void outOfStockCarriesExactlyThreeKeys() throws Exception {
        JsonNode json = toJson(CartIssue.outOfStock(10L, "Bắp cải trắng hữu cơ"));

        assertAllRequiredKeysPresent(json);
        assertEquals(3, json.size());
        assertFalse(json.has("availableStock"));
        assertFalse(json.has("currentPrice"));
        assertFalse(json.has("cartPrice"));
        assertEquals(10L, json.get("productId").asLong());
        assertEquals("Bắp cải trắng hữu cơ", json.get("name").asText());
        assertEquals("out_of_stock", json.get("type").asText());
    }

    /**
     * {@code insufficient_stock} mang {@code availableStock} và <b>chỉ</b> nó.
     * <p>
     * {@code currentPrice} / {@code cartPrice} phải vắng mặt: một issue thiếu hàng kèm hai mức giá
     * bằng nhau đọc như một cảnh báo đổi giá giả.
     *
     * @throws Exception khi Jackson lỗi
     */
    @Test
    @DisplayName("insufficient_stock mang availableStock va KHONG mang gia nao")
    void insufficientStockCarriesOnlyAvailableStock() throws Exception {
        JsonNode json = toJson(CartIssue.insufficientStock(32L, "Cá hồi Na Uy phi lê", 24));

        assertAllRequiredKeysPresent(json);
        assertTrue(json.has("availableStock"));
        assertEquals(24, json.get("availableStock").asInt());
        assertEquals(4, json.size());
        assertFalse(json.has("currentPrice"));
        assertFalse(json.has("cartPrice"));
    }

    /**
     * {@code price_changed} mang hai giá và <b>không</b> mang {@code availableStock}.
     * <p>
     * Đây là ca mà brief của ticket gọi tên thẳng: không trả {@code availableStock: 0} trên một
     * issue đổi giá — một con số sai vẫn là một con số sai, và frontend hiển thị được nó.
     *
     * @throws Exception khi Jackson lỗi
     */
    @Test
    @DisplayName("price_changed mang hai gia va KHONG mang availableStock")
    void priceChangedCarriesOnlyBothPrices() throws Exception {
        JsonNode json = toJson(CartIssue.priceChanged(5L, "Cà rốt hữu cơ", 39_000L, 45_000L));

        assertAllRequiredKeysPresent(json);
        assertTrue(json.has("currentPrice"));
        assertTrue(json.has("cartPrice"));
        assertEquals(39_000L, json.get("currentPrice").asLong());
        assertEquals(45_000L, json.get("cartPrice").asLong());
        assertEquals(5, json.size());
        assertFalse(json.has("availableStock"));
    }

    /**
     * Ba khoá bắt buộc <b>không</b> thừa hưởng luật {@code NON_NULL}.
     * <p>
     * {@code @JsonInclude} cố ý đặt trên từng trường tuỳ chọn chứ không ở cấp class: đặt ở cấp
     * class thì một {@code name} rỗng sẽ <i>âm thầm biến mất</i> và response trông vẫn hợp lệ. Ca
     * này khoá lựa chọn đó lại — một trường bắt buộc rỗng phải hiện ra thành {@code null}, tức sai
     * một cách nhìn thấy được.
     *
     * @throws Exception khi Jackson lỗi
     */
    @Test
    @DisplayName("Truong bat buoc rong hien ra thanh null, KHONG bien mat")
    void requiredKeysAreNotSubjectToNonNullInclusion() throws Exception {
        CartIssue nameless = CartIssue.outOfStock(10L, null);

        JsonNode json = toJson(nameless);

        assertTrue(json.has("name"));
        assertTrue(json.get("name").isNull());
    }

    // ========== DANH SACH: THU TU VA MANG RONG ==========

    @Test
    @DisplayName("toResponses giu nguyen thu tu va tra mang rong, khong bao gio null")
    void toResponsesPreservesOrderAndNeverReturnsNull() {
        List<CartIssueResponse> responses = CartMapper.toResponses(List.of(
                CartIssue.outOfStock(10L, "a"),
                CartIssue.priceChanged(5L, "b", 1L, 2L)));

        assertEquals(2, responses.size());
        assertEquals(10L, responses.get(0).getProductId());
        assertEquals(5L, responses.get(1).getProductId());
        assertEquals(List.of(), CartMapper.toResponses(List.of()));
        assertEquals(List.of(), CartMapper.toResponses(null));
    }

    @Test
    @DisplayName("toLines chep du bon truong va giu nguyen thu tu")
    void toLinesCopiesEveryFieldAndKeepsOrder() {
        CartItemCommand command = new CartItemCommand()
                .setProductId(5L)
                .setName("Cà rốt hữu cơ")
                .setQuantity(2)
                .setPrice(45_000L);

        List<CartLine> lines = CartMapper.toLines(List.of(command));

        assertEquals(1, lines.size());
        CartLine line = lines.get(0);
        assertEquals(5L, line.getProductId());
        assertEquals("Cà rốt hữu cơ", line.getName());
        assertEquals(2, line.getQuantity());
        assertEquals(45_000L, line.getPrice());
        assertEquals(List.of(), CartMapper.toLines(null));
    }

    /**
     * Null-guard của cả bốn method (coding-conventions §7).
     */
    @Test
    @DisplayName("toLine / toResponse null-guard, khong nem NullPointerException")
    void mapperMethodsNullGuard() {
        assertNull(CartMapper.toLine(null));
        assertNull(CartMapper.toResponse(null));
    }

    // ========== HELPERS ==========

    /**
     * @param issue vấn đề của domain
     * @return cây JSON đúng như nó đi lên dây
     * @throws Exception khi Jackson lỗi
     */
    private JsonNode toJson(CartIssue issue) throws Exception {
        CartIssueResponse response = CartMapper.toResponse(issue);
        assertNotNull(response);
        return objectMapper.readTree(objectMapper.writeValueAsString(response));
    }

    /**
     * POSITIVE CONTROL — chứng minh phép đo nhìn thấy được khoá trên chính object này, trước khi
     * khẳng định bất kỳ khoá nào vắng mặt.
     *
     * @param json cây JSON của một issue
     */
    private void assertAllRequiredKeysPresent(JsonNode json) {
        for (String key : REQUIRED_KEYS) {
            assertTrue(json.has(key), "thieu khoa bat buoc: " + key);
        }
    }

    /**
     * Enum của domain không rò ra dây dưới dạng tên hằng.
     * <p>
     * Một {@code CartIssueResponse.type} khai kiểu {@code CartIssueType} thay vì {@code String} sẽ
     * tuần tự hoá thành {@code "OUT_OF_STOCK"} — vẫn là một chuỗi, vẫn hợp lệ về JSON, và frontend
     * {@code switch} trượt mọi nhánh.
     *
     * @throws Exception khi Jackson lỗi
     */
    @Test
    @DisplayName("type tren day la chuoi thuong, khong phai ten hang cua enum")
    void wireTypeIsLowerCaseStringNotEnumName() throws Exception {
        JsonNode json = toJson(CartIssue.insufficientStock(1L, "x", 3));

        assertEquals("insufficient_stock", json.get("type").asText());
        assertFalse(CartIssueType.INSUFFICIENT_STOCK.name().equals(json.get("type").asText()));
    }
}
