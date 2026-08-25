package com.nss.ddd.domain.service.impl;

import com.nss.ddd.domain.model.entity.Coupon;
import com.nss.ddd.domain.model.entity.Order;
import com.nss.ddd.domain.model.entity.OrderItem;
import com.nss.ddd.domain.model.entity.OrderStatusHistory;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.domain.model.entity.User;
import com.nss.ddd.domain.repository.CouponRepository;
import com.nss.ddd.domain.repository.OrderRepository;
import com.nss.ddd.domain.repository.ProductRepository;
import com.nss.ddd.domain.repository.UserRepository;
import com.nss.ddd.domain.service.CouponDomainService;
import com.nss.ddd.domain.service.OrderDomainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Hiện thực domain service của {@code Order}.
 * <p>
 * Phụ thuộc là bốn port của domain — không có tham chiếu nào tới module infrastructure ở
 * compile-time.
 * <p>
 * <b>Mọi mốc thời gian đi vào file này là giờ UTC do tầng gọi truyền xuống</b>, không có
 * {@code LocalDateTime.now()} trần nào ở đây. Cột {@code created_at} lưu giờ UTC và JDBC URL đặt
 * {@code preserveInstants=false} để chuỗi đi qua nguyên vẹn; máy dev ở {@code Asia/Saigon} là UTC+7
 * nên một {@code now()} trần sẽ ghi lệch 7 tiếng — <b>và không có gì ném lỗi</b>. Cùng cái bẫy đã
 * cắn ở ticket 0008 và đã được {@code CouponDomainServiceImpl} ghi lại.
 * <p>
 * <b>Không {@code @Transactional} trên bất kỳ method nào.</b> Xem javadoc của
 * {@link OrderDomainService}: tất cả đều là mảnh của một transaction do tầng application mở.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderDomainServiceImpl implements OrderDomainService {

    /**
     * Ngưỡng miễn phí vận chuyển, số nguyên VNĐ (§Contract 2).
     * <p>
     * <b>{@code public} để test khoá được chính con số đi vào phép tính</b> thay vì chép lại nó —
     * một bản chép trong test sẽ đổi theo cùng lúc với bản trong code và không bắt được gì. Cùng
     * lý do khiến {@code CartMapper.WIRE_TYPE_*} là {@code public}.
     * <p>
     * Frontend giữ đúng con số này ở {@code lib/constants.ts#FREE_SHIPPING_THRESHOLD}. Lệch nhau
     * thì đơn quanh ngưỡng hiển thị một phí vận chuyển trước khi bấm đặt hàng và một phí khác sau
     * khi đặt, mà không có lỗi nào nổ ra.
     */
    public static final long FREE_SHIPPING_THRESHOLD = 500_000L;

    /** Phí vận chuyển khi chưa đạt ngưỡng, số nguyên VNĐ — khớp {@code lib/constants.ts#SHIPPING_FEE}. */
    public static final long SHIPPING_FEE = 30_000L;

    /** Tiền tố mã đơn (§Contract 6) — phần cố định của {@code NSS-YYYYMMDD-NNNN}. */
    public static final String ORDER_CODE_PREFIX = "NSS-";

    /** Dấu ngăn giữa phần ngày và phần số thứ tự của mã đơn. */
    private static final String ORDER_CODE_SEPARATOR = "-";

    /** Khuôn phần ngày của mã đơn — {@code YYYYMMDD} theo giờ UTC. */
    private static final DateTimeFormatter ORDER_CODE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** Khuôn phần số thứ tự — đệm 0 tới bốn chữ số, không cắt khi vượt 9999. */
    private static final String ORDER_CODE_SEQUENCE = "%04d";

    /** Mẫu số của phép tính phần trăm — mã {@code percent} lưu giá trị dạng {@code 10} nghĩa là 10%. */
    private static final BigDecimal PERCENT_DIVISOR = BigDecimal.valueOf(100L);

    private final OrderRepository orderRepository;

    private final ProductRepository productRepository;

    private final CouponRepository couponRepository;

    private final UserRepository userRepository;

    // ========== TIEN ==========

    @Override
    public long calcSubtotal(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return 0L;
        }
        long subtotal = 0L;
        for (OrderItem item : items) {
            long price = item.getPrice() == null ? 0L : item.getPrice();
            int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
            subtotal += price * quantity;
        }
        return subtotal;
    }

    @Override
    public long calcDiscount(Coupon coupon, long subtotal) {
        if (coupon == null || subtotal <= 0L) {
            return 0L;
        }
        long value = coupon.getValue() == null ? 0L : coupon.getValue();
        Integer type = coupon.getType();
        long discount;
        // So sanh qua bien da unbox tuong minh: `TYPE_PERCENT == coupon.getType()` tren mot Integer
        // rong se nem NullPointerException ngay giua duong tinh tien, chu khong tra ve 0.
        if (type != null && type == CouponDomainService.TYPE_PERCENT) {
            // §Contract 3: HALF_UP TUONG MINH tren BigDecimal. Math.round() cua JS la half-up voi
            // so duong, nen day la cho hai ben gap nhau. HALF_EVEN se lech 1 dong o ranh gioi.
            discount = BigDecimal.valueOf(subtotal)
                    .multiply(BigDecimal.valueOf(value))
                    .divide(PERCENT_DIVISOR, 0, RoundingMode.HALF_UP)
                    .longValueExact();
        } else {
            discount = value;
        }
        if (discount < 0L) {
            return 0L;
        }
        // Chan tren: mot ma fixed lon hon gia tri don se cho ra total am, tuc mot chung tu noi rang
        // cua hang no khach tien. minOrderValue thuong chan truoc, nhung "thuong" khong phai rang buoc.
        return Math.min(discount, subtotal);
    }

    @Override
    public long calcShippingFee(long amountAfterDiscount) {
        // §Contract 1: tham so la subtotal DA TRU giam gia, khong phai subtotal tran.
        return amountAfterDiscount >= FREE_SHIPPING_THRESHOLD ? 0L : SHIPPING_FEE;
    }

    @Override
    public long calcTotal(long subtotal, long discount, long shippingFee) {
        return subtotal - discount + shippingFee;
    }

    @Override
    public String genOrderCode(LocalDateTime nowUtc) {
        long sequence = orderRepository.countOrders() + 1L;
        return ORDER_CODE_PREFIX
                + ORDER_CODE_DATE.format(nowUtc)
                + ORDER_CODE_SEPARATOR
                + String.format(ORDER_CODE_SEQUENCE, sequence);
    }

    // ========== DOC ==========

    @Override
    public Map<Long, Product> findProductsByIds(Collection<Long> productIds) {
        return productRepository.findByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    @Override
    public User findOwnerById(Long userId) {
        if (userId == null) {
            // Don khach vang lai — mot cau tra loi hop le, khong phai mot thieu sot (§B.6, §D #2)
            return null;
        }
        return userRepository.findById(userId).orElse(null);
    }

    @Override
    public Order findByCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return orderRepository.findByCode(code.trim()).orElse(null);
    }

    @Override
    public List<Order> findByUserId(Long userId) {
        if (userId == null) {
            // Khong co chu don thi khong co don nao — va tuyet doi khong phai "tra ve tat ca".
            return List.of();
        }
        return orderRepository.findByUserId(userId);
    }

    @Override
    public Map<Long, List<OrderItem>> findItemsGroupedByOrderId(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return orderRepository.findItemsByOrderIds(orderIds).stream()
                .collect(Collectors.groupingBy(item -> item.getOrder().getId()));
    }

    // ========== GHI ==========

    @Override
    public boolean deductStock(Long productId, int quantity) {
        if (productId == null || quantity <= 0) {
            return false;
        }
        // Rows-affected la khai niem cua tang adapter; domain chi thay boolean (coding-conventions §12)
        boolean deducted = productRepository.decreaseStock(productId, quantity);
        if (!deducted) {
            log.warn("deductStock: not enough stock | productId={} quantity={}", productId, quantity);
        }
        return deducted;
    }

    @Override
    public boolean redeemCoupon(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        boolean redeemed = couponRepository.increaseUsedCount(code);
        if (!redeemed) {
            log.warn("redeemCoupon: usage limit reached | code={}", code);
        }
        return redeemed;
    }

    @Override
    public Order create(Order draft) {
        Order saved = orderRepository.save(draft);
        log.info("create: order persisted | orderId={} code={}", saved.getId(), saved.getCode());
        return saved;
    }

    @Override
    public List<OrderItem> createItems(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return orderRepository.saveItems(items);
    }

    @Override
    public OrderStatusHistory recordCreation(Order order, String changedBy, LocalDateTime createdAt) {
        OrderStatusHistory history = new OrderStatusHistory()
                .setOrder(order)
                // fromStatus de null: don khong di TU dau ca (§Contract 9 buoc 5)
                .setToStatus(STATUS_PENDING)
                .setChangedBy(changedBy)
                .setCreatedAt(createdAt);
        return orderRepository.saveHistory(history);
    }
}
