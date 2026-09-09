package com.testforge.demo.shop;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 장바구니 API. 조회는 공개, 담기/체크아웃은 세션 쿠키 인증이 필요하다.
 * 데모라 사용자 구분 없이 전역 단일 장바구니를 사용한다.
 * checkout의 productIds 배열 입력(멀티 선택)을 스펙에 노출하는 것이 목적이다.
 */
@RestController
@RequestMapping("/cart")
@Tag(name = "장바구니", description = "장바구니 조회/담기/체크아웃 API")
public class CartController {

    private final ShopStore store;

    public CartController(ShopStore store) {
        this.store = store;
    }

    @Operation(summary = "장바구니 조회", description = "현재 장바구니에 담긴 항목 목록을 조회한다. 인증 없이 조회 가능.")
    @GetMapping
    public List<ShopStore.CartItem> get() {
        return store.getCart();
    }

    @Operation(summary = "장바구니 담기",
            description = "상품을 장바구니에 담는다. 인증 필요. 존재하지 않는 상품이면 400.")
    @PostMapping("/items")
    public ResponseEntity<?> addItem(@RequestBody AddCartItemRequest request) {
        if (request.productId() == null || request.quantity() == null || request.quantity() < 1) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "productId와 1 이상의 quantity가 필요합니다."));
        }

        if (store.findProduct(request.productId()).isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "존재하지 않는 상품입니다: productId=" + request.productId()));
        }

        store.addCartItem(request.productId(), request.quantity());
        return ResponseEntity.status(201).body(store.getCart());
    }

    @Operation(summary = "장바구니 일괄 주문",
            description = "productIds 배열의 각 상품으로 주문을 일괄 생성한다(각 수량 1). couponCode는 선택. 인증 필요. "
                    + "존재하지 않는 상품이 포함되면 400. 생성된 주문 목록을 반환한다.")
    @PostMapping("/checkout")
    public ResponseEntity<?> checkout(@RequestBody CheckoutRequest request) {
        if (request.productIds() == null || request.productIds().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "productIds 배열이 필요합니다."));
        }

        // 사전 검증: 하나라도 없는 상품이면 주문을 만들지 않고 400
        List<ShopStore.Product> resolved = new ArrayList<>();
        for (Long productId : request.productIds()) {
            var found = productId == null ? java.util.Optional.<ShopStore.Product>empty() : store.findProduct(productId);
            if (found.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "존재하지 않는 상품입니다: productId=" + productId));
            }
            resolved.add(found.get());
        }

        List<ShopStore.Order> createdOrders = new ArrayList<>();
        for (ShopStore.Product product : resolved) {
            createdOrders.add(store.createOrder(product, 1));
        }
        store.clearCart();

        return ResponseEntity.status(201).body(Map.of(
                "couponCode", request.couponCode() == null ? "" : request.couponCode(),
                "orders", createdOrders));
    }

    /** 장바구니 담기 요청 모델 */
    public record AddCartItemRequest(Long productId, Integer quantity) {
    }

    /** 체크아웃 요청 모델. productIds는 정수 배열(멀티 선택), couponCode는 선택. */
    public record CheckoutRequest(List<Long> productIds, String couponCode) {
    }
}
