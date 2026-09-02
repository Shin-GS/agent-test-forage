package com.testforge.demo.shop;

import com.testforge.client.annotation.TestForgeConfirm;
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
import java.util.Set;

/**
 * 결제 API. 세션 쿠키 인증이 필요하다({@link SessionInterceptor}가 /payments/** 보호).
 * 결제 시 대상 주문의 상태를 PAID로 전이시킨다.
 */
@RestController
@RequestMapping("/payments")
@Tag(name = "결제", description = "결제 생성/조회 API (인증 필요)")
public class PaymentController {

    /** 허용 결제 수단 */
    private static final Set<String> METHODS = Set.of("CARD", "BANK");

    private final ShopStore store;

    public PaymentController(ShopStore store) {
        this.store = store;
    }

    @Operation(summary = "결제",
            description = "주문에 대해 결제를 생성하고 주문 상태를 PAID로 변경한다. 되돌릴 수 없는 작업이라 실행 전 사용자 확인이 필요하다. "
                    + "method는 CARD 또는 BANK. 주문이 없으면 404, 이미 취소된 주문이면 409.")
    @TestForgeConfirm(message = "실제 결제가 발생합니다")
    @PostMapping
    public ResponseEntity<?> pay(@RequestBody PaymentRequest request) {
        if (request.orderId() == null || !METHODS.contains(request.method())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "orderId와 결제수단(CARD/BANK)이 필요합니다."));
        }

        var found = store.findOrder(request.orderId());
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ShopStore.Order order = found.get();
        if (order.status() == ShopStore.OrderStatus.CANCELLED) {
            return ResponseEntity.status(409)
                    .body(Map.of("message", "취소된 주문은 결제할 수 없습니다: orderId=" + request.orderId()));
        }

        ShopStore.Payment payment = store.createPayment(order.orderId(), request.method());
        store.updateOrderStatus(order, ShopStore.OrderStatus.PAID);
        return ResponseEntity.status(201).body(payment);
    }

    @Operation(summary = "결제 조회", description = "결제 ID로 결제 상세를 조회한다. 없으면 404.")
    @GetMapping("/{id}")
    public ResponseEntity<ShopStore.Payment> detail(@PathVariable Long id) {
        return store.findPayment(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** 결제 요청 모델 */
    public record PaymentRequest(Long orderId, String method) {
    }
}
