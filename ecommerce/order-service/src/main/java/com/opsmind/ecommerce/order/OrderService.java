package com.opsmind.ecommerce.order;

import com.opsmind.ecommerce.order.client.*;
import com.opsmind.ecommerce.order.dto.OrderResponse;
import com.opsmind.ecommerce.order.dto.PlaceOrderRequest;
import com.opsmind.ecommerce.order.event.*;
import com.opsmind.ecommerce.order.fault.OrderFaultState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates the checkout flow via real synchronous REST calls to catalog,
 * payment, and inventory services. This is a simpler design than a Kafka-driven
 * saga (which the original spec's Kafka event list implies for later phases) -
 * chosen deliberately for Phase 2 so the first working slice is easy to reason
 * about. Compensating transactions (e.g. auto-refund on inventory failure after a
 * successful charge) are NOT implemented yet - see OrderStatus javadoc.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CatalogClient catalogClient;
    private final PaymentClient paymentClient;
    private final InventoryClient inventoryClient;
    private final OrderEventPublisher eventPublisher;
    private final OrderFaultState faultState;

    @Transactional
    public OrderResponse placeOrder(PlaceOrderRequest request) {
        applyLatencyIfInjected();

        List<OrderItem> items = request.items().stream().map(reqItem -> {
            CatalogClient.ProductInfo product = catalogClient.getProduct(reqItem.productId());
            if (!product.active()) {
                throw new IllegalArgumentException("Product is not active: " + reqItem.productId());
            }
            return OrderItem.builder()
                    .productId(product.id())
                    .productName(product.name())
                    .quantity(reqItem.quantity())
                    .unitPrice(product.price())
                    .build();
        }).toList();

        BigDecimal total = items.stream()
                .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder()
                .customerId(request.customerId())
                .status(OrderStatus.CREATED)
                .totalAmount(total)
                .build();
        order = orderRepository.save(order);

        for (OrderItem item : items) {
            item.setOrderId(order.getId());
            orderItemRepository.save(item);
        }

        eventPublisher.publish(OrderTopics.ORDER_CREATED, order.getId().toString(),
                OrderEventEnvelope.of(OrderTopics.ORDER_CREATED, new OrderCreatedEvent(order.getId(), order.getCustomerId(), total)));

        // --- Payment ---
        PaymentClient.ChargeResult chargeResult;
        try {
            chargeResult = paymentClient.charge(order.getId(), order.getCustomerId(), total);
        } catch (PaymentServiceUnavailableException e) {
            return failOrder(order, OrderStatus.PAYMENT_FAILED, "payment_service_unreachable: " + e.getMessage(), items);
        }
        if (!chargeResult.success()) {
            return failOrder(order, OrderStatus.PAYMENT_FAILED, chargeResult.failureReason(), items);
        }
        order.setStatus(OrderStatus.PAID);
        order = orderRepository.save(order);

        // --- Inventory ---
        InventoryClient.ReserveResult reserveResult;
        try {
            reserveResult = inventoryClient.reserve(order.getId(), items);
        } catch (InventoryServiceUnavailableException e) {
            return failOrder(order, OrderStatus.INVENTORY_FAILED, "inventory_service_unreachable: " + e.getMessage(), items);
        }
        if (!reserveResult.reserved()) {
            return failOrder(order, OrderStatus.INVENTORY_FAILED, reserveResult.reason(), items);
        }

        order.setStatus(OrderStatus.CONFIRMED);
        order = orderRepository.save(order);

        eventPublisher.publish(OrderTopics.ORDER_CONFIRMED, order.getId().toString(),
                OrderEventEnvelope.of(OrderTopics.ORDER_CONFIRMED, new OrderConfirmedEvent(order.getId(), order.getCustomerId())));

        return toResponse(order, items);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new NotFoundException("Order not found: " + orderId));
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        return toResponse(order, items);
    }

    private OrderResponse failOrder(Order order, OrderStatus failedStatus, String reason, List<OrderItem> items) {
        order.setStatus(failedStatus);
        order.setFailureReason(reason);
        order = orderRepository.save(order);
        eventPublisher.publish(OrderTopics.ORDER_FAILED, order.getId().toString(),
                OrderEventEnvelope.of(OrderTopics.ORDER_FAILED, new OrderFailedEvent(order.getId(), order.getCustomerId(), failedStatus, reason)));
        return toResponse(order, items);
    }

    private void applyLatencyIfInjected() {
        long latency = faultState.getDbLatencyMillis();
        if (latency > 0) {
            try {
                Thread.sleep(latency);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private OrderResponse toResponse(Order order, List<OrderItem> items) {
        List<OrderResponse.OrderItemResponse> itemResponses = items.stream()
                .map(i -> new OrderResponse.OrderItemResponse(i.getProductId(), i.getProductName(), i.getQuantity(), i.getUnitPrice()))
                .toList();
        return new OrderResponse(order.getId(), order.getCustomerId(), order.getStatus(), order.getTotalAmount(),
                order.getFailureReason(), order.getCreatedAt(), order.getUpdatedAt(), itemResponses);
    }
}
