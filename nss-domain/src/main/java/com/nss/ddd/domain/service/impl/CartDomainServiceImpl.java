package com.nss.ddd.domain.service.impl;

import com.nss.ddd.domain.model.CartIssue;
import com.nss.ddd.domain.model.CartLine;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.domain.repository.ProductRepository;
import com.nss.ddd.domain.service.CartDomainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Hiện thực quy tắc đối chiếu giỏ hàng.
 * <p>
 * Phụ thuộc duy nhất là port {@code ProductRepository} — <b>đúng chuỗi đọc sản phẩm đã có từ ticket
 * 0008</b>, không dựng chuỗi song song. Thứ duy nhất thêm vào port là một đường đọc theo lô
 * ({@link ProductRepository#findByIds}), vì giỏ hàng luôn hỏi nhiều sản phẩm cùng lúc.
 * <p>
 * <b>Một truy vấn cho cả giỏ, không phải một truy vấn cho mỗi dòng.</b> Giỏ 20 món mà tra từng dòng
 * là 20 lượt đi vòng tới MySQL cho một endpoint frontend gọi lại mỗi lần khách mở giỏ. Kéo cả lô về
 * rồi tra trong {@code Map} cho cùng một kết quả với đúng một lượt.
 * <p>
 * <b>Giá hiện tại đọc từ cột {@code effective_price}, tuyệt đối không tính lại
 * {@code salePrice ?? price} ở Java.</b> Cột đó là cột sinh STORED do MySQL tính (ticket 0004), và
 * cùng một con số được tính ở hai nơi theo hai quy ước là đúng thứ coding-conventions §15 cấm: bộ
 * lọc giá dùng cột, việc đối chiếu giỏ dùng biểu thức Java, rồi một ngày nào đó hai bên nói khác
 * nhau mà không có gì ném lỗi.
 * <p>
 * <b>Không có phép so thời gian nào trong file này</b> — đó là lý do nó không có {@code genNowUtc()}
 * như {@code CouponDomainServiceImpl}. Tồn kho và giá là trạng thái hiện tại của một dòng, không
 * phải một cửa sổ hiệu lực. Thêm bất kỳ phép so ngày nào vào đây thì phải dùng
 * {@code LocalDateTime.now(ZoneOffset.UTC)}, không bao giờ {@code now()} trần — bẫy lệch 7 tiếng của
 * ticket 0008.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartDomainServiceImpl implements CartDomainService {

    private final ProductRepository productRepository;

    @Override
    public List<CartIssue> findIssues(List<CartLine> lines) {
        if (lines == null || lines.isEmpty()) {
            // Gio rong la mot cau hoi hop le voi mot cau tra loi hop le: khong co van de nao (§B.6)
            return List.of();
        }
        Map<Long, Product> productsById = findProductsById(lines);
        List<CartIssue> issues = new ArrayList<>();
        for (CartLine line : lines) {
            collectIssues(line, productsById.get(line.getProductId()), issues);
        }
        log.info("findIssues: cart checked | lineCount={} issueCount={}", lines.size(), issues.size());
        return issues;
    }

    // ========== HELPERS ==========

    /**
     * Kéo mọi sản phẩm của giỏ về trong một lượt, đánh chỉ mục theo id.
     * <p>
     * {@code LinkedHashSet} chứ không {@code List}: một giỏ có {@code productId} trùng lặp không
     * được biến thành hai lần hỏi cùng một dòng. Thứ tự ổn định của nó không đổi kết quả, nhưng
     * khiến câu SQL sinh ra giống nhau giữa các lần chạy — và một câu SQL lặp lại được là một câu
     * SQL đọc từ log lên so sánh được.
     *
     * @param lines các dòng giỏ hàng, đã chắc chắn không rỗng
     * @return sản phẩm <b>còn hiệu lực</b> theo id; id vắng mặt nghĩa là không tồn tại hoặc đã bị
     *         xoá mềm
     */
    private Map<Long, Product> findProductsById(List<CartLine> lines) {
        Set<Long> productIds = lines.stream()
                .map(CartLine::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return productRepository.findByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    /**
     * Áp ba quy tắc lên một dòng và nối kết quả vào danh sách chung.
     * <p>
     * Thứ tự hai lệnh {@code add} cuối là <b>contract</b>, không phải thẩm mỹ: trong cùng một dòng
     * thì {@code INSUFFICIENT_STOCK} phải đứng trước {@code PRICE_CHANGED}.
     *
     * @param line dòng giỏ hàng đang xét
     * @param product sản phẩm còn hiệu lực tương ứng, hoặc {@code null} khi không tra được
     * @param issues danh sách gom kết quả, được ghi thêm tại chỗ
     */
    private void collectIssues(CartLine line, Product product, List<CartIssue> issues) {
        // 1. Quy tac LOAI TRU: khong tra duoc, het hang, hoac da xoa mem — mot cau tra loi duy nhat.
        //    `product == null` gop ca "khong ton tai" lan "is_active = 0" vi moi duong doc cua
        //    ProductRepository chi tra ve san pham con hieu luc (xem javadoc cua port do).
        if (product == null || product.getStock() == null || product.getStock() <= 0) {
            issues.add(CartIssue.outOfStock(line.getProductId(), line.getName()));
            return;
        }
        // 2. Ton kho: doc tu DB, KHONG bao gio tu con so client gui len (§C.1)
        int quantity = line.getQuantity() == null ? 0 : line.getQuantity();
        if (quantity > product.getStock()) {
            issues.add(CartIssue.insufficientStock(
                    line.getProductId(), line.getName(), product.getStock()));
        }
        // 3. Gia: so voi cot sinh effective_price. Phep kiem null khong phai phong thu thua — mot
        //    `price` rong khong di qua noi tang validate, va neu no den duoc day thi viec so no voi
        //    mot gia that se sinh ra issue price_changed THIEU `cartPrice`, tuc mot object sai hinh
        //    dang tren day. Bo qua phep kiem gia con do hon la tra ve hang loi.
        if (line.getPrice() != null && !line.getPrice().equals(product.getEffectivePrice())) {
            issues.add(CartIssue.priceChanged(
                    line.getProductId(), line.getName(), product.getEffectivePrice(), line.getPrice()));
        }
    }
}
