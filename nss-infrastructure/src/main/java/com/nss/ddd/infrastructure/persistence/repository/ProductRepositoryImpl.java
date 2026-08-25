package com.nss.ddd.infrastructure.persistence.repository;

import com.nss.ddd.domain.model.PageResult;
import com.nss.ddd.domain.model.ProductFilter;
import com.nss.ddd.domain.model.ProductSort;
import com.nss.ddd.domain.model.StockStatus;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.domain.repository.ProductRepository;
import com.nss.ddd.infrastructure.persistence.mapper.ProductJPAMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * ADAPTER cho port {@code ProductRepository}.
 * <p>
 * Đây là <b>ranh giới</b>: mọi khái niệm của Spring Data ({@code Pageable}, {@code Page},
 * rows-affected) dừng lại ở file này, phía trên chỉ thấy kiểu của domain.
 * <p>
 * Stereotype là {@code @Repository}, không phải {@code @Service} — dự án tham chiếu đặt
 * {@code @Service} lên 8/10 repository impl, coding-conventions §3 ghi rõ đó là chỗ làm sai,
 * đừng chép.
 */
@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    /** Ký tự escape của mệnh đề {@code LIKE}; phải khớp {@code ESCAPE} khai trong JPQL. */
    private static final char LIKE_ESCAPE = '!';

    /**
     * Khoá phụ giữ thứ tự <b>ổn định</b> khi khoá chính có giá trị trùng nhau.
     * <p>
     * <b>Không phải trang trí.</b> Không có khoá phụ, hai sản phẩm cùng {@code sold} (hoặc cùng
     * {@code rating}, hoặc cùng {@code effective_price} — chuyện rất thường) có thể được MySQL trả
     * theo thứ tự khác nhau ở hai lần chạy. Với phân trang {@code OFFSET}, điều đó nghĩa là một
     * dòng xuất hiện ở cả trang 1 lẫn trang 2 trong khi một dòng khác không xuất hiện ở đâu cả —
     * và không có gì báo lỗi. {@code id} là khoá chính nên nó luôn phá được thế hoà.
     */
    private static final Sort.Order TIE_BREAKER = Sort.Order.asc("id");

    private final ProductJPAMapper productJPAMapper;

    @Override
    public Optional<Product> findBySlug(String slug) {
        return productJPAMapper.findActiveBySlug(slug);
    }

    @Override
    public Optional<Product> findById(Long id) {
        return productJPAMapper.findActiveById(id);
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Phép chặn danh sách rỗng nằm ở đây, và nó là thứ duy nhất giữ ca "giỏ rỗng" không thành
     * lỗi 500.</b> {@code IN :ids} với collection rỗng dịch ra {@code in ()} — MySQL từ chối cú
     * pháp đó. Chặn tại adapter chứ không ở domain vì đây là ràng buộc của <i>SQL</i>, không phải
     * một quy tắc nghiệp vụ: domain nói "không có id nào để tra thì không có sản phẩm nào", và câu
     * đó đúng ở mọi cơ sở dữ liệu.
     */
    @Override
    public List<Product> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return productJPAMapper.findActiveByIdIn(ids);
    }

    @Override
    public PageResult<Product> findPage(int page, int limit) {
        // API_CONTRACT §A.4: `page` tren duong day danh so tu 1, Spring Data danh so tu 0.
        // Phep tru 1 nam DUY NHAT o dong nay — quen no thi page=1 tra ve trang thu hai
        // va khong co gi bao loi.
        Page<Product> result = productJPAMapper.findActivePage(PageRequest.of(page - 1, limit));
        return PageResult.of(result.getContent(), result.getTotalElements());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Ba phép dịch từ khái niệm của domain sang khái niệm của Spring Data, và cả ba đều chỉ được
     * xảy ra ở file này: {@link ProductSort} thành {@code Sort}, {@link StockStatus} thành cặp
     * {@code (minStock, maxStock)}, từ khoá thành mẫu {@code LIKE}. Cùng phép trừ 1 của
     * {@code page} như {@link #findPage(int, int)}.
     */
    @Override
    public PageResult<Product> findAdminPage(ProductFilter filter) {
        StockStatus stockStatus = filter.getStockStatus();
        // API_CONTRACT §A.4: `page` tren duong day danh so tu 1, Spring Data danh so tu 0.
        Page<Product> result = productJPAMapper.findAdminPage(
                genLikePattern(filter.getKeyword()),
                filter.getCategorySlug(),
                stockStatus == null ? null : stockStatus.getMinStock(),
                stockStatus == null ? null : stockStatus.getMaxStock(),
                PageRequest.of(filter.getPage() - 1, filter.getLimit(), toSort(filter.getSort())));
        return PageResult.of(result.getContent(), result.getTotalElements());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Đi qua <b>đúng</b> mẫu {@code LIKE} và <b>đúng</b> mệnh đề lọc mà
     * {@link #findAdminPage(ProductFilter)} dùng — đó là thứ giữ {@code lowStockCount} bằng
     * {@code total} của {@code ?stockStatus=low_stock} theo cấu tạo. {@code sort}, {@code page} và
     * {@code limit} của {@code filter} không tham gia phép đếm.
     */
    @Override
    public long countAdminProducts(ProductFilter filter) {
        StockStatus stockStatus = filter.getStockStatus();
        return productJPAMapper.countAdminProducts(
                genLikePattern(filter.getKeyword()),
                filter.getCategorySlug(),
                stockStatus == null ? null : stockStatus.getMinStock(),
                stockStatus == null ? null : stockStatus.getMaxStock());
    }

    @Override
    public Product save(Product product) {
        return productJPAMapper.save(product);
    }

    @Override
    public boolean existsBySlug(String slug) {
        return productJPAMapper.existsBySlug(slug);
    }

    @Override
    public boolean softDelete(Long id, LocalDateTime deletedAt) {
        // Rows-affected la khai niem cua tang nay; domain chi thay boolean (coding-conventions §12)
        return productJPAMapper.markInactive(id, deletedAt) > 0;
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>So sánh {@code != 1} chứ không {@code > 0}, và khác biệt đó là có thật:</b> một câu UPDATE
     * khoá theo khoá chính không bao giờ đụng được hai dòng, nên một kết quả khác 1 nghĩa là có gì
     * đó sai ở mức giả định chứ không chỉ là "không đủ hàng". Biến nó thành {@code false} ở đây, và
     * tầng trên rollback — đúng như §Contract 8 chốt: số dòng ảnh hưởng khác 1 thì huỷ cả đơn.
     */
    @Override
    public boolean decreaseStock(Long id, int quantity) {
        return productJPAMapper.decreaseStock(id, quantity) == 1;
    }

    // ========== DICH KHAI NIEM DOMAIN -> SPRING DATA ==========

    /**
     * Dịch {@link ProductSort} của domain sang {@code Sort} của Spring Data.
     * <p>
     * <b>Bảng ánh xạ này là chỗ duy nhất biết tên cột nào ứng với giá trị {@code sort} nào</b> —
     * domain cố ý không mang thông tin đó (architecture/01-overview.md §1).
     * <p>
     * <b>{@code PRICE_ASC} / {@code PRICE_DESC} sắp theo {@code effectivePrice}, KHÔNG theo
     * {@code price}.</b> Cột {@code effective_price} là cột sinh {@code STORED}
     * {@code COALESCE(sale_price, price)} và đã có sẵn {@code idx_effective_price} từ backlog 0004;
     * đây là truy vấn đầu tiên dùng tới nó. Sắp theo {@code price} thì một sản phẩm giảm giá sâu
     * vẫn nằm đúng chỗ giá gốc của nó, tức bộ lọc "giá tăng dần" nói sai về đúng con số người mua
     * phải trả.
     * <p>
     * <b>Cảnh báo phải giữ lại: hai công thức giá sau giảm KHÔNG giống nhau, và chúng chỉ trùng
     * nhau nhờ một thứ thứ ba.</b>
     * <ul>
     *   <li>Backend: {@code effective_price = COALESCE(sale_price, price)} — dùng {@code sale_price}
     *       bất cứ khi nào nó khác {@code null}.</li>
     *   <li>Frontend ({@code src/lib/format.ts:26}):
     *       {@code salePrice && salePrice < price ? salePrice : price} — chỉ dùng
     *       {@code salePrice} khi nó <i>vừa</i> khác rỗng <i>vừa</i> nhỏ hơn {@code price}.</li>
     * </ul>
     * Hai công thức lệch nhau ở đúng hai ca, và <b>cả hai ca đều đang bị chặn ở tầng khác</b>:
     * {@code salePrice >= price} bị {@code hasValidSalePrice} trả 422, còn {@code salePrice = 0}
     * bị {@code @Positive} trên DTO chặn (và trong JavaScript {@code 0} là giá trị falsy nên
     * frontend cũng rơi về {@code price}). Nghĩa là <b>nới bất kỳ luật nào trong hai luật đó sẽ làm
     * bộ sắp xếp này lệch với thứ người dùng nhìn thấy trên màn hình — âm thầm, không lỗi nào nổ
     * ra.</b> Ai nới thì sửa cả chỗ này trong cùng lần sửa.
     *
     * @param sort thứ tự client chọn; {@code null} được hiểu là {@link ProductSort#DEFAULT}
     * @return thứ tự sắp xếp, luôn kèm khoá phụ theo {@code id}
     */
    private static Sort toSort(ProductSort sort) {
        ProductSort effective = sort == null ? ProductSort.DEFAULT : sort;
        return switch (effective) {
            // Moi nhat truoc: khoa phu cung DESC de "moi nhat" van dung khi createdAt trung nhau
            case NEWEST -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
            case PRICE_ASC -> Sort.by(Sort.Order.asc("effectivePrice"), TIE_BREAKER);
            case PRICE_DESC -> Sort.by(Sort.Order.desc("effectivePrice"), TIE_BREAKER);
            case BEST_SELLING -> Sort.by(Sort.Order.desc("sold"), TIE_BREAKER);
            case RATING -> Sort.by(Sort.Order.desc("rating"), TIE_BREAKER);
        };
    }

    /**
     * Dựng mẫu {@code LIKE} chứa (contains) từ một từ khoá đã bỏ dấu.
     * <p>
     * <b>Escape {@code %}, {@code _} và chính ký tự escape trước khi bọc {@code %} hai đầu.</b>
     * Frontend so bằng {@code String.includes()} — một phép so <i>chuỗi con theo nghĩa đen</i>. Bỏ
     * bước escape thì {@code q=100%} biến thành một ký tự đại diện và trả về nhiều dòng hơn số dòng
     * thật sự khớp; đó là một kết quả <i>sai</i> trông y hệt một kết quả đúng.
     * <p>
     * <b>Không chuẩn hoá lại từ khoá ở đây.</b> Nó đã được domain service bỏ dấu — xem javadoc
     * {@code ProductFilter}.
     *
     * @param keyword từ khoá đã bỏ dấu; {@code null} hoặc rỗng nghĩa là không tìm
     * @return mẫu dạng {@code %tu-khoa%}, hoặc {@code null} khi không tìm
     */
    private static String genLikePattern(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return null;
        }
        StringBuilder escaped = new StringBuilder(keyword.length() + 8);
        escaped.append('%');
        for (char character : keyword.toCharArray()) {
            if (character == LIKE_ESCAPE || character == '%' || character == '_') {
                escaped.append(LIKE_ESCAPE);
            }
            escaped.append(character);
        }
        return escaped.append('%').toString();
    }
}
