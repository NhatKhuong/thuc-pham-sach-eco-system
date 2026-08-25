package com.nss.ddd.application.service.order.impl;

import com.nss.ddd.application.mapper.OrderMapper;
import com.nss.ddd.application.model.command.CartItemCommand;
import com.nss.ddd.application.model.command.CreateOrderCommand;
import com.nss.ddd.application.model.response.CouponValidationResponse;
import com.nss.ddd.application.model.response.OrderMutationResponse;
import com.nss.ddd.application.model.response.OrderResponse;
import com.nss.ddd.application.service.order.OrderAppService;
import com.nss.ddd.domain.model.entity.Coupon;
import com.nss.ddd.domain.model.entity.Order;
import com.nss.ddd.domain.model.entity.OrderItem;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.domain.model.entity.ProductImage;
import com.nss.ddd.domain.model.entity.User;
import com.nss.ddd.domain.service.CouponDomainService;
import com.nss.ddd.domain.service.OrderDomainService;
import com.nss.ddd.domain.service.ProductDomainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Hiện thực use case đơn hàng.
 * <p>
 * Tầng này chỉ điều phối: hỏi domain service, rồi lắp kết quả thành kiểu của bề mặt dây. Không có
 * quy tắc nghiệp vụ nào nằm ở đây — kể cả phép tính tiền, vốn sống trong {@code OrderDomainService},
 * và kể cả luật "mã này dùng được không", vốn sống trong {@code CouponDomainService}. Thứ duy nhất
 * thuộc về file này là <b>trình tự</b> và <b>chuỗi tiếng Việt</b>.
 * <p>
 * <b>Vì sao {@code createOrder} vừa {@code @Transactional} vừa gọi {@code setRollbackOnly} tay.</b>
 * coding-conventions §11 Pattern A nói thất bại nghiệp vụ là <i>giá trị trả về</i>, không phải
 * exception — nên method này {@code return} bình thường ở mọi ca hỏng, và Spring <b>không thấy
 * exception nào để rollback</b>. Nhưng §Contract 9 lại đòi hỏng ở bước nào cũng không còn dấu vết:
 * bước 2 đã trừ kho xong thì một thất bại ở bước 3 phải hoàn lại con số đó. {@code setRollbackOnly}
 * là chỗ duy nhất nối hai quy ước ấy lại. Cách thay thế — ném một exception rồi bắt ở controller —
 * bị loại vì kiểu {@code *Exception} sống ở module controller (§3), mà application nằm <i>dưới</i>
 * controller trong chiều phụ thuộc nên không ném chúng được.
 * <p>
 * <b>Mọi nhánh thất bại sau khi transaction mở đều đi qua {@link #failedAndRollback}, kể cả những
 * nhánh chưa ghi gì.</b> Phân biệt "nhánh này đã ghi chưa" là loại phép suy luận đúng hôm nay và
 * sai vào lần chèn thêm một bước ghi ở giữa — một {@code setRollbackOnly} thừa trên transaction chỉ
 * đọc thì vô hại, còn một cái thiếu thì để lại kho bị trừ oan.
 * <p>
 * <b>Hai đường đọc không {@code @Transactional}:</b> mỗi đường chỉ đọc và chỉ có hai truy vấn cố
 * định (đơn, rồi dòng hàng của cả lô), nên không có gì để gói lại. coding-conventions §8 mục 5 cấm
 * khai {@code readOnly} khi không viết ra được lý do — ở đây không có lý do nào.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderAppServiceImpl implements OrderAppService {

    private static final String MESSAGE_EMPTY_ORDER =
            "Giỏ hàng đang trống, vui lòng thêm sản phẩm trước khi đặt hàng.";

    private static final String MESSAGE_INVALID_PAYMENT_METHOD =
            "Phương thức thanh toán không hợp lệ, vui lòng chọn lại.";

    private static final String MESSAGE_OWNER_NOT_FOUND =
            "Tài khoản của bạn không còn tồn tại, vui lòng đăng nhập lại.";

    private static final String MESSAGE_COUPON_NOT_FOUND =
            "Mã giảm giá không tồn tại, vui lòng kiểm tra lại.";

    private static final String MESSAGE_COUPON_NOT_REDEEMABLE =
            "Mã giảm giá này không còn hiệu lực.";

    private static final String MESSAGE_COUPON_USED_UP =
            "Mã giảm giá này vừa hết lượt sử dụng, vui lòng thử mã khác.";

    private final OrderDomainService orderDomainService;

    private final CouponDomainService couponDomainService;

    private final ProductDomainService productDomainService;

    // ========== GHI ==========

    /**
     * {@inheritDoc}
     * <p>
     * Năm bước dưới đây theo <b>đúng</b> thứ tự §Contract 9, và thứ tự đó là contract chứ không
     * phải thẩm mỹ. Đổi chỗ bước 2 với bước 3 sẽ đốt một lượt mã giảm giá cho một đơn rồi mới phát
     * hiện là hết hàng.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderMutationResponse createOrder(CreateOrderCommand command) {
        List<CartItemCommand> lines = command.getItems();
        // 0a. Gio rong -> 400. Day la cho DUY NHAT gio rong bi chan; POST /cart/validate co y tra
        //     200 kem mang rong cho cung tinh huong, vi cau hoi "gio nay co van de gi khong" van hop le.
        if (lines == null || lines.isEmpty()) {
            log.warn("createOrder: empty cart");
            return failedAndRollback(OrderMutationResponse.CODE_EMPTY_ORDER, MESSAGE_EMPTY_ORDER);
        }
        // 0b. Phuong thuc thanh toan: bang dich nam o OrderMapper, chuoi la cho ra null
        Integer paymentMethod = OrderMapper.toPaymentMethodCode(command.getPaymentMethod());
        if (paymentMethod == null) {
            log.warn("createOrder: unknown payment method | value={}", command.getPaymentMethod());
            return failedAndRollback(OrderMutationResponse.CODE_INVALID_ORDER_DATA,
                    MESSAGE_INVALID_PAYMENT_METHOD);
        }
        // 0c. Phan giai chu don. userId=null la don khach vang lai — hop le; userId co gia tri ma
        //     khong tra ra tai khoan nao thi token dang tro toi mot ban ghi da bien mat.
        User owner = orderDomainService.findOwnerById(command.getUserId());
        if (command.getUserId() != null && owner == null) {
            log.warn("createOrder: owner not found | userId={}", command.getUserId());
            return failedAndRollback(OrderMutationResponse.CODE_INVALID_ORDER_DATA,
                    MESSAGE_OWNER_NOT_FOUND);
        }

        // 1. Doc lai gia + ton kho + isActive cua tung productId TU DB (§Contract 9 buoc 1).
        //    Moi con so tien tu day tro di deu sinh ra tu du lieu nay; `price` client gui khong co
        //    duong nao di vao OrderMapper.toItem (§C.1).
        List<Long> productIds = genDistinctProductIds(lines);
        Map<Long, Product> productsById = orderDomainService.findProductsByIds(productIds);
        Map<Long, List<ProductImage>> imagesByProductId =
                productDomainService.findImagesGroupedByProductId(productIds);
        List<OrderItem> items = new ArrayList<>(lines.size());
        for (CartItemCommand line : lines) {
            Product product = productsById.get(line.getProductId());
            if (product == null) {
                log.warn("createOrder: product unavailable | productId={}", line.getProductId());
                return failedAndRollback(OrderMutationResponse.CODE_OUT_OF_STOCK,
                        genUnavailableMessage(line.getName()));
            }
            items.add(OrderMapper.toItem(product, line.getQuantity(),
                    genFirstImageUrl(imagesByProductId.get(product.getId()))));
        }

        // 2. Tru kho tung dong bang conditional UPDATE (§Contract 9 buoc 2). So dong anh huong
        //    khac 1 -> rollback + 409, khong @Version va khong doc-roi-ghi (§Contract 8).
        for (CartItemCommand line : lines) {
            if (!orderDomainService.deductStock(line.getProductId(), genQuantity(line))) {
                log.warn("createOrder: stock deduction failed | productId={} quantity={}",
                        line.getProductId(), line.getQuantity());
                return failedAndRollback(OrderMutationResponse.CODE_OUT_OF_STOCK,
                        genOutOfStockMessage(line.getName()));
            }
        }

        // 3. Xac thuc lai ma giam gia va tang usedCount (§Contract 9 buoc 3). Ba vi tu duoi day la
        //    CHINH nhung vi tu ma POST /coupons/validate goi — khong dung lai luat thu hai.
        long subtotal = orderDomainService.calcSubtotal(items);
        Coupon coupon = null;
        if (hasCouponCode(command.getCouponCode())) {
            coupon = couponDomainService.findByCode(command.getCouponCode());
            if (coupon == null) {
                log.warn("createOrder: coupon not found | code={}", command.getCouponCode());
                return failedAndRollback(CouponValidationResponse.CODE_COUPON_NOT_APPLICABLE,
                        MESSAGE_COUPON_NOT_FOUND);
            }
            if (!couponDomainService.isRedeemable(coupon)) {
                log.warn("createOrder: coupon not redeemable | code={}", coupon.getCode());
                return failedAndRollback(CouponValidationResponse.CODE_COUPON_NOT_APPLICABLE,
                        MESSAGE_COUPON_NOT_REDEEMABLE);
            }
            if (!couponDomainService.meetsMinOrderValue(coupon, subtotal)) {
                log.warn("createOrder: subtotal below minimum | code={} subtotal={} minOrderValue={}",
                        coupon.getCode(), subtotal, coupon.getMinOrderValue());
                return failedAndRollback(CouponValidationResponse.CODE_COUPON_NOT_APPLICABLE,
                        genMinOrderValueMessage(coupon.getMinOrderValue()));
            }
            // Cong doan cuoi la mot conditional UPDATE nua: giua luc isRedeemable tra ve true va
            // luc nay, mot don khac co the da lay mat luot cuoi.
            if (!orderDomainService.redeemCoupon(coupon.getCode())) {
                log.warn("createOrder: coupon usage limit reached | code={}", coupon.getCode());
                return failedAndRollback(CouponValidationResponse.CODE_COUPON_NOT_APPLICABLE,
                        MESSAGE_COUPON_USED_UP);
            }
        }
        long discount = orderDomainService.calcDiscount(coupon, subtotal);
        // §Contract 1: phi van chuyen tinh tren so DA TRU giam gia, KHONG tren subtotal tran.
        long shippingFee = orderDomainService.calcShippingFee(subtotal - discount);
        long total = orderDomainService.calcTotal(subtotal, discount, shippingFee);

        // 4. INSERT customer_order + order_item (§Contract 9 buoc 4)
        LocalDateTime nowUtc = genNowUtcToSecond();
        Order saved = orderDomainService.create(new Order()
                .setCode(orderDomainService.genOrderCode(nowUtc))
                .setUser(owner)
                .setShipping(OrderMapper.toShippingInfo(command.getShipping()))
                .setPaymentMethod(paymentMethod)
                .setStatus(OrderDomainService.STATUS_PENDING)
                .setSubtotal(subtotal)
                .setDiscount(discount)
                .setShippingFee(shippingFee)
                .setTotal(total)
                .setCouponCode(coupon == null ? null : coupon.getCode())
                .setCreatedAt(nowUtc)
                .setUpdatedAt(nowUtc));
        for (OrderItem item : items) {
            item.setOrder(saved);
        }
        List<OrderItem> savedItems = orderDomainService.createItems(items);

        // 5. INSERT dong dau order_status_history (§Contract 9 buoc 5)
        orderDomainService.recordCreation(saved, genChangedBy(command.getUserId()), nowUtc);

        log.info("createOrder: success | orderId={} code={} userId={} total={} itemCount={}",
                saved.getId(), saved.getCode(), command.getUserId(), total, savedItems.size());
        return OrderMutationResponse.success(OrderMapper.toResponse(saved, savedItems));
    }

    // ========== DOC ==========

    @Override
    public List<OrderResponse> findMyOrders(Long userId) {
        List<Order> orders = orderDomainService.findByUserId(userId);
        if (orders.isEmpty()) {
            log.info("findMyOrders: success | userId={} count=0", userId);
            return Collections.emptyList();
        }
        // Dong hang cua CA danh sach lay trong MOT truy van — hop dong tra Order[] khong phan trang,
        // nen hoi tung don la bien mot khach 30 don thanh 31 luot di vong toi MySQL.
        Map<Long, List<OrderItem>> itemsByOrderId = orderDomainService.findItemsGroupedByOrderId(
                orders.stream().map(Order::getId).toList());
        List<OrderResponse> responses = new ArrayList<>(orders.size());
        for (Order order : orders) {
            responses.add(OrderMapper.toResponse(order,
                    itemsByOrderId.getOrDefault(order.getId(), Collections.emptyList())));
        }
        log.info("findMyOrders: success | userId={} count={}", userId, responses.size());
        return responses;
    }

    @Override
    public OrderResponse findOrderByCode(String code) {
        Order order = orderDomainService.findByCode(code);
        if (order == null) {
            log.warn("findOrderByCode: not found | code={}", code);
            return null;
        }
        List<OrderItem> items = orderDomainService
                .findItemsGroupedByOrderId(List.of(order.getId()))
                .getOrDefault(order.getId(), Collections.emptyList());
        log.info("findOrderByCode: success | orderId={} code={}", order.getId(), order.getCode());
        return OrderMapper.toResponse(order, items);
    }

    // ========== HELPERS ==========

    /**
     * Dựng kết quả thất bại và <b>đánh dấu transaction phải rollback</b>.
     * <p>
     * Xem javadoc cấp class: Pattern A trả thất bại bằng giá trị nên Spring không thấy exception
     * nào, và nếu không có dòng này thì một đơn hỏng ở bước 3 vẫn để lại phần tồn kho đã trừ ở bước
     * 2. Hậu tố {@code failed*} theo coding-conventions §4 cho helper dựng response lỗi.
     *
     * @param code mã lỗi nghiệp vụ UPPER_SNAKE
     * @param message thông điệp tiếng Việt cho người dùng cuối
     * @return kết quả thất bại
     */
    private OrderMutationResponse failedAndRollback(String code, String message) {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        return OrderMutationResponse.failed(code, message);
    }

    /**
     * Mốc thời gian tạo đơn — giờ UTC, <b>cắt tới giây</b>.
     * <p>
     * <b>Phép cắt không phải để cho gọn, nó vá một chỗ hai endpoint nói khác nhau về cùng một
     * đơn.</b> {@code LocalDateTime.now()} của Java mang độ chính xác tới <i>nano</i> giây, còn cột
     * {@code DATETIME} của MySQL chỉ giữ tới <i>micro</i>. Không cắt thì {@code POST /orders} trả về
     * chuỗi Java vừa sinh ({@code ...:52.522409500Z}) trong khi {@code GET /orders/{code}} đọc lại
     * từ DB và trả chuỗi đã bị làm tròn ({@code ...:52.522410Z}) — <b>hai giá trị khác nhau cho
     * cùng một trường của cùng một đơn</b>, và không có gì báo lỗi. Đo thấy bằng request thật khi
     * làm backlog 0014 phase 3.
     * <p>
     * Cắt tới <b>giây</b> chứ không tới micro vì đó là thứ API_CONTRACT §A.5 minh hoạ
     * ({@code 2026-08-17T10:30:00Z}), là thứ dữ liệu seed đang dùng, và là thứ
     * {@code ProductResponse.createdAt} vẫn trả ra. Một mốc đặt hàng không cần độ chính xác dưới
     * giây; một hợp đồng nhất quán thì cần.
     * <p>
     * Luôn {@code ZoneOffset.UTC}, không bao giờ {@code now()} trần: máy dev ở {@code Asia/Saigon}
     * là UTC+7 và lệch đó <b>không ném lỗi</b> — cùng cái bẫy mà {@code CouponDomainServiceImpl}
     * đã ghi lại.
     *
     * @return thời điểm hiện tại theo giờ UTC, phần dưới giây bằng 0
     */
    private LocalDateTime genNowUtcToSecond() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    }

    /**
     * Các {@code productId} khác nhau của giỏ, giữ thứ tự xuất hiện.
     * <p>
     * {@code LinkedHashSet} chứ không {@code List}, cùng lý do đã viết ở {@code CartDomainServiceImpl}:
     * một giỏ có {@code productId} trùng lặp không được biến thành hai lần hỏi cùng một dòng, và
     * thứ tự ổn định khiến câu SQL sinh ra giống nhau giữa các lần chạy.
     *
     * @param lines các dòng giỏ hàng, đã chắc chắn không rỗng
     * @return danh sách id không trùng
     */
    private List<Long> genDistinctProductIds(List<CartItemCommand> lines) {
        Set<Long> ids = lines.stream()
                .map(CartItemCommand::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return List.copyOf(ids);
    }

    /**
     * @param images ảnh của một sản phẩm, đã sắp theo {@code sortOrder}; có thể rỗng
     * @return đường dẫn tương đối của ảnh đầu tiên, hoặc {@code null} khi sản phẩm chưa có ảnh nào
     */
    private String genFirstImageUrl(List<ProductImage> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        return images.get(0).getUrl();
    }

    /**
     * @param line dòng giỏ hàng
     * @return số lượng, hoặc 0 khi rỗng — {@code deductStock} biến 0 thành thất bại
     */
    private int genQuantity(CartItemCommand line) {
        return line.getQuantity() == null ? 0 : line.getQuantity();
    }

    /**
     * @param couponCode chuỗi mã client gửi
     * @return true khi khách thật sự có áp mã
     */
    private boolean hasCouponCode(String couponCode) {
        return couponCode != null && !couponCode.isBlank();
    }

    /**
     * Định danh tác nhân ghi vào {@code order_status_history.changed_by}.
     *
     * @param userId chủ đơn; {@code null} là khách vãng lai
     * @return id dạng chuỗi, hoặc {@code guest}
     */
    private String genChangedBy(Long userId) {
        return userId == null ? OrderDomainService.CHANGED_BY_GUEST : String.valueOf(userId);
    }

    /**
     * @param name tên món hàng do client gửi — DB không còn tên nào để tra khi sản phẩm đã bị gỡ
     * @return thông điệp tiếng Việt cho ca sản phẩm không còn bán
     */
    private String genUnavailableMessage(String name) {
        return "Sản phẩm \"" + name + "\" không còn được bán, vui lòng bỏ khỏi giỏ hàng.";
    }

    /**
     * @param name tên món hàng do client gửi
     * @return thông điệp tiếng Việt cho ca không đủ tồn kho
     */
    private String genOutOfStockMessage(String name) {
        return "Sản phẩm \"" + name + "\" không còn đủ hàng, vui lòng giảm số lượng hoặc bỏ khỏi giỏ.";
    }

    /**
     * Dựng thông điệp tiếng Việt cho lỗi chưa đạt giá trị đơn tối thiểu.
     * <p>
     * <b>Cùng khuôn câu với {@code CouponAppServiceImpl}, và cùng lý do khai dấu ngăn hàng nghìn
     * tường minh:</b> {@code DecimalFormat} không tham số đọc {@code Locale.getDefault()}, nên cùng
     * một dòng code cho ra {@code 500.000} trên máy dev Việt Nam, {@code 500,000} trên JVM
     * {@code en-US} và {@code 500 000} trên {@code fr-FR} — không có gì báo lỗi, chỉ có người dùng
     * đọc một con số lạ.
     *
     * @param minOrderValue giá trị đơn tối thiểu, số nguyên VNĐ
     * @return thông điệp tiếng Việt có nêu con số
     */
    private String genMinOrderValueMessage(Long minOrderValue) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator('.');
        DecimalFormat formatter = new DecimalFormat("#,##0", symbols);
        long safeValue = minOrderValue == null ? 0L : minOrderValue;
        return "Đơn hàng cần tối thiểu " + formatter.format(safeValue) + " ₫ để dùng mã này.";
    }
}
