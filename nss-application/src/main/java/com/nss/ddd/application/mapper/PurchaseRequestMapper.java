package com.nss.ddd.application.mapper;

import com.nss.ddd.application.model.command.CartItemCommand;
import com.nss.ddd.application.model.command.CreateOrderCommand;
import com.nss.ddd.application.model.command.ShippingInfoCommand;
import com.nss.ddd.application.model.response.PurchaseRequestResponse;
import com.nss.ddd.domain.model.entity.PurchaseRequest;
import com.nss.ddd.infrastructure.mq.PurchaseRequestedItemMessage;
import com.nss.ddd.infrastructure.mq.PurchaseRequestedMessage;
import com.nss.ddd.infrastructure.mq.PurchaseRequestedShippingMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Converter của luồng async — {@code CreateOrderCommand} &lt;-&gt; {@code PurchaseRequestedMessage}
 * &lt;-&gt; {@code PurchaseRequestResponse} (backlog 0039).
 * <p>
 * Class stateless, method {@code public static}, không phải Spring bean, luôn null-guard
 * (coding-conventions §7).
 */
public final class PurchaseRequestMapper {

    /** Chuỗi trên dây của {@link PurchaseRequest#STATUS_PENDING}. */
    public static final String WIRE_STATUS_PENDING = "PENDING";

    /** Chuỗi trên dây của {@link PurchaseRequest#STATUS_SUCCESS}. */
    public static final String WIRE_STATUS_SUCCESS = "SUCCESS";

    /** Chuỗi trên dây của {@link PurchaseRequest#STATUS_FAILED}. */
    public static final String WIRE_STATUS_FAILED = "FAILED";

    private PurchaseRequestMapper() {
    }

    /**
     * @param command   lệnh gốc đã dựng ở tầng controller (userId đã lấy từ JWT)
     * @param requestId khoá vừa sinh cho request này
     * @return payload Kafka, hoặc {@code null} khi {@code command} rỗng
     */
    public static PurchaseRequestedMessage toMessage(CreateOrderCommand command, String requestId) {
        if (command == null) {
            return null;
        }
        List<PurchaseRequestedItemMessage> items = new ArrayList<>();
        if (command.getItems() != null) {
            for (CartItemCommand item : command.getItems()) {
                items.add(new PurchaseRequestedItemMessage()
                        .setProductId(item.getProductId())
                        .setName(item.getName())
                        .setQuantity(item.getQuantity())
                        .setPrice(item.getPrice()));
            }
        }
        return new PurchaseRequestedMessage()
                .setRequestId(requestId)
                .setUserId(command.getUserId())
                .setItems(items)
                .setShipping(toShippingMessage(command.getShipping()))
                .setPaymentMethod(command.getPaymentMethod())
                .setCouponCode(command.getCouponCode());
    }

    private static PurchaseRequestedShippingMessage toShippingMessage(ShippingInfoCommand shipping) {
        if (shipping == null) {
            return null;
        }
        return new PurchaseRequestedShippingMessage()
                .setFullName(shipping.getFullName())
                .setPhone(shipping.getPhone())
                .setEmail(shipping.getEmail())
                .setProvince(shipping.getProvince())
                .setDistrict(shipping.getDistrict())
                .setWard(shipping.getWard())
                .setStreet(shipping.getStreet())
                .setNote(shipping.getNote());
    }

    /**
     * @param message payload đã giải mã từ {@code outbox_event.payload}
     * @return lệnh tạo đơn dựng lại đúng như lúc submit, hoặc {@code null} khi {@code message} rỗng
     */
    public static CreateOrderCommand toCommand(PurchaseRequestedMessage message) {
        if (message == null) {
            return null;
        }
        List<CartItemCommand> items = new ArrayList<>();
        if (message.getItems() != null) {
            for (PurchaseRequestedItemMessage item : message.getItems()) {
                items.add(new CartItemCommand()
                        .setProductId(item.getProductId())
                        .setName(item.getName())
                        .setQuantity(item.getQuantity())
                        .setPrice(item.getPrice()));
            }
        }
        return new CreateOrderCommand()
                .setUserId(message.getUserId())
                .setItems(items)
                .setShipping(toShippingCommand(message.getShipping()))
                .setPaymentMethod(message.getPaymentMethod())
                .setCouponCode(message.getCouponCode());
    }

    private static ShippingInfoCommand toShippingCommand(PurchaseRequestedShippingMessage shipping) {
        if (shipping == null) {
            return null;
        }
        return new ShippingInfoCommand()
                .setFullName(shipping.getFullName())
                .setPhone(shipping.getPhone())
                .setEmail(shipping.getEmail())
                .setProvince(shipping.getProvince())
                .setDistrict(shipping.getDistrict())
                .setWard(shipping.getWard())
                .setStreet(shipping.getStreet())
                .setNote(shipping.getNote());
    }

    /**
     * @param status mã trạng thái nội bộ ({@link PurchaseRequest#STATUS_PENDING} …)
     * @return chuỗi trên dây, hoặc {@code null} khi mã lạ (không được rơi về một mặc định gây hiểu nhầm)
     */
    public static String toWireStatus(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case PurchaseRequest.STATUS_PENDING -> WIRE_STATUS_PENDING;
            case PurchaseRequest.STATUS_SUCCESS -> WIRE_STATUS_SUCCESS;
            case PurchaseRequest.STATUS_FAILED -> WIRE_STATUS_FAILED;
            default -> null;
        };
    }

    /**
     * @param request bản ghi domain
     * @return hình dạng trên dây, hoặc {@code null} khi {@code request} rỗng
     */
    public static PurchaseRequestResponse toResponse(PurchaseRequest request) {
        if (request == null) {
            return null;
        }
        return new PurchaseRequestResponse()
                .setRequestId(request.getRequestId())
                .setStatus(toWireStatus(request.getStatus()))
                .setOrderCode(request.getOrderCode())
                .setFailureCode(request.getFailureCode())
                .setFailureMessage(request.getFailureMessage());
    }
}
