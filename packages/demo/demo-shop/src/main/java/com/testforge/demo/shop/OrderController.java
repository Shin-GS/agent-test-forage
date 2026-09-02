package com.testforge.demo.shop;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 주문 API. 세션 쿠키 인증이 필요하다({@link SessionInterceptor}가 /orders/** 보호).
 * 주문 금액은 {@link ShopStore}의 상품 가격 * 수량으로 계산한다.
 */
@RestController
@RequestMapping("/orders")
@Tag(name = "주문", description = "주문 생성/조회/취소 API (인증 필요)")
public class OrderController {

    private final ShopStore store;

    public OrderController(ShopStore store) {
        this.store = store;
    }

    @Operation(summary = "주문 생성",
            description = "productId와 quantity로 주문을 생성한다. 금액은 상품가격*수량으로 계산되며 상태는 CREATED. 존재하지 않는 상품이면 400.")
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateOrderRequest request) {
        if (request.productId() == null || request.quantity() == null || request.quantity() < 1) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "productId와 1 이상의 quantity가 필요합니다."));
        }

        return store.findProduct(request.productId())
                .<ResponseEntity<?>>map(product -> {
                    ShopStore.Order order = store.createOrder(product, request.quantity());
                    return ResponseEntity.status(201).body(order);
                })
                .orElseGet(() -> ResponseEntity.badRequest()
                        .body(Map.of("message", "존재하지 않는 상품입니다: productId=" + request.productId())));
    }

    @Operation(summary = "주문 조회", description = "주문 ID로 주문 상세를 조회한다. 없으면 404.")
    @GetMapping("/{id}")
    public ResponseEntity<ShopStore.Order> detail(@PathVariable Long id) {
        return store.findOrder(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "주문 취소",
            description = "주문 상태를 CANCELLED로 변경한다. 주문이 없으면 404, 이미 취소된 주문이면 409.")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id) {
        var found = store.findOrder(id);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ShopStore.Order order = found.get();
        if (order.status() == ShopStore.OrderStatus.CANCELLED) {
            return ResponseEntity.status(409)
                    .body(Map.of("message", "이미 취소된 주문입니다: orderId=" + id));
        }
        ShopStore.Order cancelled = store.updateOrderStatus(order, ShopStore.OrderStatus.CANCELLED);
        return ResponseEntity.ok(cancelled);
    }

    /** 주문 생성 요청 모델 */
    public record CreateOrderRequest(Long productId, Integer quantity) {
    }
}
