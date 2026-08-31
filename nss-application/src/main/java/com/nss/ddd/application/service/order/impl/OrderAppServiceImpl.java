package com.nss.ddd.application.service.order.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nss.ddd.application.mapper.OrderMapper;
import com.nss.ddd.application.model.command.CartItemCommand;
import com.nss.ddd.application.model.command.CreateOrderCommand;
import com.nss.ddd.application.model.response.CouponValidationResponse;
import com.nss.ddd.application.model.response.OrderMutationResponse;
import com.nss.ddd.application.model.response.OrderResponse;
import com.nss.ddd.application.model.response.PaginatedResponse;
import com.nss.ddd.application.service.order.OrderAppService;
import com.nss.ddd.domain.model.OrderFilter;
import com.nss.ddd.domain.model.PageResult;
import com.nss.ddd.domain.model.entity.Coupon;
import com.nss.ddd.domain.model.entity.Order;
import com.nss.ddd.domain.model.entity.OrderItem;
import com.nss.ddd.domain.model.entity.OutboxEvent;
import com.nss.ddd.domain.model.entity.Product;
import com.nss.ddd.domain.model.entity.ProductImage;
import com.nss.ddd.domain.model.entity.User;
import com.nss.ddd.domain.repository.OutboxEventRepository;
import com.nss.ddd.domain.service.CouponDomainService;
import com.nss.ddd.domain.service.OrderDomainService;
import com.nss.ddd.domain.service.ProductDomainService;
import com.nss.ddd.infrastructure.mq.OrderStatusChangedMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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

    /** §A.4 — mặc định 12 đơn mỗi trang, khớp {@code ORDERS_PER_PAGE} của bảng quản trị. */
    private static final int DEFAULT_ADMIN_LIMIT = 12;

    private static final String MESSAGE_ORDER_NOT_FOUND =
            "Không tìm thấy đơn hàng với mã này.";

    /**
     * Thông điệp cho ca {@code status} là một chuỗi không nằm trong bảng dịch.
     * <p>
     * <b>Tách khỏi thông điệp "chuyển không hợp lệ" dù cả hai cùng ra 422</b>: một cái nói "chuỗi
     * bạn gửi không phải một trạng thái", cái kia nói "trạng thái đó có thật nhưng đơn không đi
     * được tới đó". Gộp làm một sẽ bắt người đọc tự đoán mình sai kiểu nào.
     */
    private static final String MESSAGE_UNKNOWN_STATUS =
            "Trạng thái đơn hàng không hợp lệ, vui lòng chọn lại.";

    /** Loại event ghi vào {@code outbox_event.event_type} — khớp §Contract của backlog 0032. */
    private static final String EVENT_TYPE_ORDER_STATUS_CHANGED = "OrderStatusChanged";

    private final OrderDomainService orderDomainService;

    private final CouponDomainService couponDomainService;

    private final ProductDomainService productDomainService;

    private final OutboxEventRepository outboxEventRepository;

    private final ObjectMapper objectMapper;

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

        // 6. Outbox event OrderStatusChanged (backlog 0032) — CUNG transaction, KHONG goi Kafka
        //    truc tiep. fromStatus=null: don vua duoc tao, khong di TU dau ca.
        genOutboxOrderStatusChangedEvent(saved, null, OrderDomainService.STATUS_PENDING, nowUtc);

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

    // ========== KHU QUAN TRI (§B.12.2) ==========

    @Override
    public PaginatedResponse<OrderResponse> findAdminOrders(OrderFilter filter) {
        // 1. Keo tham so ve khoang dung duoc — dung mot luat voi bang san pham (§A.4)
        int safePage = Math.max(filter.getPage(), 1);
        int safeLimit = filter.getLimit() < 1 ? DEFAULT_ADMIN_LIMIT : filter.getLimit();
        // 2. Dung filter MOI thay vi sua cai duoc truyen vao: doi tuong cua phia goi khong duoc
        //    am tham doi nghia giua chung mot lan xu ly
        OrderFilter safeFilter = OrderFilter.of(filter.getKeyword(), filter.getStatus(),
                filter.getUserId(), safePage, safeLimit);
        PageResult<Order> pageResult = orderDomainService.findAdminPage(safeFilter);
        List<Order> orders = pageResult.getItems();
        // 3. Dong hang cua CA trang lay trong MOT truy van — cung ly do da viet o findMyOrders
        Map<Long, List<OrderItem>> itemsByOrderId = orderDomainService.findItemsGroupedByOrderId(
                orders.stream().map(Order::getId).toList());
        List<OrderResponse> items = new ArrayList<>(orders.size());
        for (Order order : orders) {
            items.add(OrderMapper.toResponse(order,
                    itemsByOrderId.getOrDefault(order.getId(), Collections.emptyList())));
        }
        log.info("findAdminOrders: success | q={} status={} userId={} page={} limit={} total={}",
                safeFilter.getKeyword(), safeFilter.getStatus(), safeFilter.getUserId(),
                safePage, safeLimit, pageResult.getTotal());
        return PaginatedResponse.of(items, pageResult.getTotal(), safePage, safeLimit);
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Bốn cổng, và thứ tự của chúng là contract chứ không phải thẩm mỹ.</b> Đơn phải tồn tại
     * trước khi bàn tới trạng thái (404 chứ không 422); chuỗi phải là một trạng thái có thật trước
     * khi hỏi máy trạng thái (nếu không thì không có gì để so). Đảo lại sẽ trả 422 cho một mã đơn
     * không tồn tại — một câu trả lời nói sai về nguyên nhân.
     * <p>
     * <b>KHÔNG có {@code setRollbackOnly} như {@code createOrder}, và đó không phải chỗ quên:</b>
     * cả bốn cổng thất bại đều chạy <i>trước</i> lời ghi đầu tiên, nên không có gì để hoàn lại.
     * Khác hẳn {@code createOrder}, nơi bước trừ kho nằm giữa các cổng. Ai chèn thêm một bước ghi
     * vào giữa method này thì phải mang {@code failedAndRollback} sang cùng lúc.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderMutationResponse changeOrderStatus(String code, String wireStatus, String changedBy) {
        // 1. Don phai ton tai — 404, khong phai 422
        Order order = orderDomainService.findByCode(code);
        if (order == null) {
            log.warn("changeOrderStatus: order not found | code={}", code);
            return OrderMutationResponse.failed(OrderMutationResponse.CODE_ORDER_NOT_FOUND,
                    MESSAGE_ORDER_NOT_FOUND);
        }
        // 2. Chuoi phai la mot trang thai co that. Bang dich nam o OrderMapper; chuoi la cho ra
        //    null va TUYET DOI khong roi ve mot mac dinh nao — mot PATCH go sai se doi that trang
        //    thai cua mot chung tu sang gia tri admin khong he chon.
        Integer toStatus = OrderMapper.toStatusCode(wireStatus);
        if (toStatus == null) {
            log.warn("changeOrderStatus: unknown status | code={} value={}", code, wireStatus);
            return OrderMutationResponse.failed(OrderMutationResponse.CODE_INVALID_ORDER_DATA,
                    MESSAGE_UNKNOWN_STATUS);
        }
        // 3. May trang thai — luat song o domain, o day chi dich `false` thanh ma loi.
        //    Chuyen sang CHINH trang thai hien tai cung roi vao day: no khong nam trong bang.
        Integer fromStatus = order.getStatus();
        if (fromStatus == null || !orderDomainService.canTransition(fromStatus, toStatus)) {
            log.warn("changeOrderStatus: transition rejected | code={} from={} to={}",
                    code, fromStatus, toStatus);
            return OrderMutationResponse.failed(OrderMutationResponse.CODE_INVALID_ORDER_DATA,
                    genInvalidTransitionMessage(fromStatus, toStatus));
        }
        // 4. Ba write trong CUNG transaction: cot status cua don, mot dong nhat ky moi, va outbox
        //    event OrderStatusChanged (backlog 0032) — KHONG goi Kafka truc tiep trong request.
        LocalDateTime nowUtc = genNowUtcToSecond();
        Order saved = orderDomainService.updateStatus(order, toStatus, nowUtc);
        orderDomainService.recordTransition(saved, fromStatus, toStatus, changedBy, nowUtc);
        genOutboxOrderStatusChangedEvent(saved, fromStatus, toStatus, nowUtc);

        List<OrderItem> items = orderDomainService
                .findItemsGroupedByOrderId(List.of(saved.getId()))
                .getOrDefault(saved.getId(), Collections.emptyList());
        log.info("changeOrderStatus: success | orderId={} code={} from={} to={} changedBy={}",
                saved.getId(), saved.getCode(), fromStatus, toStatus, changedBy);
        return OrderMutationResponse.success(OrderMapper.toResponse(saved, items));
    }

    // ========== HELPERS ==========

    /**
     * Ghi một dòng {@code outbox_event} cho event {@code OrderStatusChanged} (backlog 0032, §6).
     * <p>
     * <b>Chỉ gọi {@code outboxEventRepository.save(...)} — không gọi Kafka.</b> Bean này chạy trong
     * cùng transaction do {@code createOrder}/{@code changeOrderStatus} đã mở (cả hai đều
     * {@code @Transactional}), nên một lời gọi method thường tới bean khác vẫn tham gia đúng
     * transaction đó — không cần {@code TransactionTemplate} như pattern self-call/lambda ở blueprint
     * §6, vì đây không phải self-call.
     * <p>
     * <b>Ném {@code IllegalStateException} thay vì nuốt lỗi serialize.</b> Một
     * {@code OrderStatusChangedMessage} không serialize được là lỗi lập trình, không phải một thất
     * bại nghiệp vụ dự kiến được — để nó rollback cả transaction còn hơn ghi một đơn hàng mà không
     * ai được báo trạng thái.
     *
     * @param order đơn đã ghi (đã có id) tại thời điểm chuyển trạng thái này
     * @param fromStatus trạng thái trước khi chuyển; {@code null} khi đơn vừa được tạo
     * @param toStatus trạng thái sau khi chuyển
     * @param changedAt thời điểm chuyển, giờ UTC — cùng mốc với bản ghi nghiệp vụ
     */
    private void genOutboxOrderStatusChangedEvent(Order order, Integer fromStatus, Integer toStatus,
                                                   LocalDateTime changedAt) {
        OrderStatusChangedMessage message = new OrderStatusChangedMessage()
                .setOrderId(order.getId())
                .setCode(order.getCode())
                .setFromStatus(fromStatus)
                .setToStatus(toStatus)
                .setShippingEmail(order.getShipping() == null ? null : order.getShipping().getEmail())
                .setChangedAt(DateTimeFormatter.ISO_INSTANT.format(changedAt.toInstant(ZoneOffset.UTC)));
        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Khong the serialize OrderStatusChangedMessage cho don " + order.getCode(), e);
        }
        outboxEventRepository.save(new OutboxEvent()
                .setAggregateId(order.getCode())
                .setEventType(EVENT_TYPE_ORDER_STATUS_CHANGED)
                .setPayload(payload)
                .setStatus(OutboxEvent.STATUS_PENDING)
                .setCreatedAt(changedAt));
        log.info("genOutboxOrderStatusChangedEvent: outbox ghi | orderId={} code={} from={} to={}",
                order.getId(), order.getCode(), fromStatus, toStatus);
    }

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
     * Dựng thông điệp tiếng Việt cho ca chuyển trạng thái không hợp lệ.
     * <p>
     * <b>Nêu ĐÍCH DANH cả hai đầu bằng nhãn tiếng Việt</b> ({@code OrderMapper.toStatusLabel}):
     * chuỗi này đi thẳng vào {@code detail} của {@code ProblemDetail} và frontend hiển thị nguyên
     * văn (§A.3), nên một câu chung chung kiểu "thao tác không hợp lệ" buộc người dùng phải đoán
     * mình vừa bấm sai chỗ nào. Ca hay gặp nhất — bấm lại đúng trạng thái đơn đang có — cũng đọc ra
     * đúng nghĩa: "không thể chuyển từ Đã giao sang Đã giao".
     *
     * @param fromStatus trạng thái hiện tại
     * @param toStatus trạng thái được yêu cầu
     * @return thông điệp tiếng Việt
     */
    private String genInvalidTransitionMessage(Integer fromStatus, Integer toStatus) {
        return "Không thể chuyển đơn hàng từ trạng thái \"" + OrderMapper.toStatusLabel(fromStatus)
                + "\" sang \"" + OrderMapper.toStatusLabel(toStatus) + "\".";
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
