package com.nss.ddd.domain.service.impl;

import com.nss.ddd.domain.model.DailyRevenue;
import com.nss.ddd.domain.model.OrderFilter;
import com.nss.ddd.domain.model.PageResult;
import com.nss.ddd.domain.model.StatusCount;
import com.nss.ddd.domain.model.TextNormalizer;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /**
     * <b>Bảng chuyển trạng thái hợp lệ — bản duy nhất của luật này trong toàn hệ</b> (§B.12.2,
     * khớp {@code ORDER_STATUS_TRANSITIONS} của frontend).
     * <p>
     * Khai bằng một {@code Map} bất biến chứ không bằng một chuỗi {@code if} vì hình dạng của nó
     * <i>đọc ra được</i> chính là bảng trong hợp đồng — đối chiếu hai bên là một phép so trực
     * quan, không phải một bài truy vết nhánh điều kiện.
     * <p>
     * <b>Hai dòng cuối cố ý là {@code Set.of()} rỗng, không phải vắng mặt.</b> {@code delivered} và
     * {@code cancelled} là trạng thái cuối; một dòng rỗng nói "đã liệt kê xong và không còn nước đi
     * nào", còn một khoá vắng mặt nói "chưa ai điền". Hai câu đó khác nhau khi có người đọc lại
     * bảng này để thêm trạng thái thứ sáu.
     */
    private static final Map<Integer, Set<Integer>> ALLOWED_TRANSITIONS = Map.of(
            STATUS_PENDING, Set.of(STATUS_CONFIRMED, STATUS_CANCELLED),
            STATUS_CONFIRMED, Set.of(STATUS_SHIPPING, STATUS_CANCELLED),
            STATUS_SHIPPING, Set.of(STATUS_DELIVERED, STATUS_CANCELLED),
            STATUS_DELIVERED, Set.of(),
            STATUS_CANCELLED, Set.of());

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

    // ========== LUONG TRANG THAI ==========

    @Override
    public boolean canTransition(int fromStatus, int toStatus) {
        // getOrDefault chu khong get: mot `status` la trong DB (du lieu cu, hoac ai do UPDATE tay)
        // phai cho ra false, khong duoc nem NullPointerException giua duong doi trang thai.
        return ALLOWED_TRANSITIONS.getOrDefault(fromStatus, Set.of()).contains(toStatus);
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
    public PageResult<Order> findAdminPage(OrderFilter filter) {
        // Dung mot OrderFilter MOI thay vi sua cai duoc truyen vao — xem javadoc cua interface
        return orderRepository.findAdminPage(OrderFilter.of(
                TextNormalizer.genSearchKeyword(filter.getKeyword()),
                filter.getStatus(),
                filter.getUserId(),
                filter.getPage(),
                filter.getLimit()));
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
        // Cot phai sinh full_name_normalized duoc dien O DAY, dung mot ham voi product.name_normalized
        // (coding-conventions §18). Dat o day chu khong o OrderMapper vi bo dau la quy tac nghiep vu,
        // va vi day la cho DUY NHAT mot don hang duoc ghi xuong lan dau.
        if (draft.getShipping() != null) {
            draft.getShipping().setFullNameNormalized(
                    TextNormalizer.genNormalized(draft.getShipping().getFullName()));
        }
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

    @Override
    public Order updateStatus(Order order, int toStatus, LocalDateTime nowUtc) {
        Order saved = orderRepository.save(order
                .setStatus(toStatus)
                .setUpdatedAt(nowUtc));
        log.info("updateStatus: order status changed | orderId={} code={} toStatus={}",
                saved.getId(), saved.getCode(), toStatus);
        return saved;
    }

    @Override
    public OrderStatusHistory recordTransition(Order order, Integer fromStatus, int toStatus,
                                               String changedBy, LocalDateTime createdAt) {
        OrderStatusHistory history = new OrderStatusHistory()
                .setOrder(order)
                .setFromStatus(fromStatus)
                .setToStatus(toStatus)
                .setChangedBy(changedBy)
                .setCreatedAt(createdAt);
        return orderRepository.saveHistory(history);
    }

    // ========== TONG HOP ==========

    @Override
    public List<LocalDate> genDateWindow(int days) {
        // "Hom nay" theo gio CUA HANG, khong theo gio may va khong theo UTC. Luc 01:00 gio Viet Nam
        // thi UTC van con la hom qua, nen mot LocalDate.now(ZoneOffset.UTC) o day se dung khung ngay
        // lech mot ngay so voi cot "Ngay dat" ma nguoi dung dang nhin.
        LocalDate lastDay = LocalDate.now(STORE_ZONE);
        LocalDate firstDay = lastDay.minusDays(days - 1L);
        List<LocalDate> window = new ArrayList<>(days);
        for (int index = 0; index < days; index++) {
            window.add(firstDay.plusDays(index));
        }
        return window;
    }

    @Override
    public List<DailyRevenue> findRevenueByDay(LocalDate fromDate, LocalDate toDate) {
        return orderRepository.sumRevenueByDay(
                genUtcStartOfDay(fromDate),
                // Bien tren MO: 00:00 cua ngay KE TIEP. Dung `<=` tren 23:59:59 se bo sot moi don
                // roi vao phan le duoi giay, va dung `<= toDate 23:59:59.999999` thi con so phu
                // thuoc vao do chinh xac cua cot — mot rang buoc khong ai nhin thay.
                genUtcStartOfDay(toDate.plusDays(1)),
                genStoreOffset(),
                STATUS_CANCELLED);
    }

    @Override
    public List<StatusCount> countOrdersByStatus(LocalDate fromDate, LocalDate toDate) {
        // CUNG cap moc thoi gian voi findRevenueByDay — do la thu giu bat bien
        // orderCount == sum(ordersByStatus[].count) dung theo cau tao.
        return orderRepository.countByStatus(
                genUtcStartOfDay(fromDate),
                genUtcStartOfDay(toDate.plusDays(1)));
    }

    /**
     * Đầu ngày <b>giờ cửa hàng</b>, quy về giờ UTC để so với cột {@code created_at}.
     * <p>
     * Đây là chỗ duy nhất trong dự án đổi giữa hai hệ quy chiếu đó. Cột lưu UTC (JDBC URL đặt
     * {@code preserveInstants=false} nên chuỗi đi qua nguyên vẹn), còn khoảng thời gian người dùng
     * chọn thì tính theo ngày của cửa hàng.
     *
     * @param storeDate ngày theo giờ cửa hàng
     * @return mốc 00:00 của ngày đó, biểu diễn theo giờ UTC
     */
    private LocalDateTime genUtcStartOfDay(LocalDate storeDate) {
        return storeDate.atStartOfDay(STORE_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    /**
     * Độ lệch múi giờ cửa hàng dạng {@code +07:00} — thứ duy nhất về múi giờ mà adapter được biết.
     * <p>
     * <b>Suy từ {@link #STORE_ZONE} chứ không viết cứng chuỗi {@code "+07:00"}.</b> Việt Nam không
     * có giờ mùa hè nên hai cách cho cùng kết quả <i>hôm nay</i>, nhưng một chuỗi viết cứng là bản
     * sao thứ hai của cùng một sự thật, và nó sẽ không đổi theo khi ai đó sửa hằng múi giờ.
     *
     * @return độ lệch hiện hành của múi giờ cửa hàng, dạng {@code +07:00}
     */
    private String genStoreOffset() {
        return STORE_ZONE.getRules().getOffset(Instant.now()).getId();
    }
}
