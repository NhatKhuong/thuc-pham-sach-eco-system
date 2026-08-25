package com.nss.ddd.infrastructure.persistence.repository;

import com.nss.ddd.domain.model.PageResult;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.domain.repository.ProductRepository;
import com.nss.ddd.infrastructure.persistence.mapper.ProductJPAMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
}
