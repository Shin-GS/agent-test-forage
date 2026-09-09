package com.testforge.demo.shop;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 쿠폰 API. 목록 조회는 공개, 주문 적용은 세션 쿠키 인증이 필요하다.
 * 할인 타입(PERCENT/FIXED) enum 입력을 스펙에 노출하는 것이 목적이며,
 * 실제 할인 계산은 서버에 저장된 쿠폰의 타입/값을 기준으로 수행한다.
 */
@RestController
@RequestMapping("/coupons")
@Tag(name = "쿠폰", description = "쿠폰 목록 조회 및 주문 적용 API")
public class CouponController {

    private final ShopStore store;

    public CouponController(ShopStore store) {
        this.store = store;
    }

    @Operation(summary = "쿠폰 목록 조회", description = "사용 가능한 쿠폰 목록을 조회한다. 인증 없이 조회 가능.")
    @GetMapping
    public List<ShopStore.Coupon> list() {
        return store.listCoupons();
    }

    @Operation(summary = "쿠폰 적용",
            description = "주문에 쿠폰을 적용해 할인 후 금액을 계산한다. discountType(PERCENT/FIXED)은 요청으로도 받지만 "
                    + "실제 계산은 서버에 저장된 쿠폰의 타입을 사용한다. 주문이 없으면 404, 쿠폰이 없으면 400.")
    @PostMapping("/apply")
    public ResponseEntity<?> apply(@RequestBody ApplyCouponRequest request) {
        if (request.orderId() == null || request.couponCode() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "orderId와 couponCode가 필요합니다."));
        }

        var foundOrder = store.findOrder(request.orderId());
        if (foundOrder.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var foundCoupon = store.findCoupon(request.couponCode());
        if (foundCoupon.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "존재하지 않는 쿠폰입니다: couponCode=" + request.couponCode()));
        }

        ShopStore.Order order = foundOrder.get();
        ShopStore.Coupon coupon = foundCoupon.get();
        int discountedAmount = store.applyDiscount(order.amount(), coupon);

        return ResponseEntity.ok(Map.of(
                "orderId", order.orderId(),
                "couponCode", coupon.code(),
                "discountType", coupon.discountType(),
                "originalAmount", order.amount(),
                "discountedAmount", discountedAmount));
    }

    /**
     * 쿠폰 적용 요청 모델. discountType은 enum 입력 노출 목적으로 받으며,
     * 실제 계산에는 서버 저장 쿠폰의 타입이 사용된다.
     */
    public record ApplyCouponRequest(Long orderId, String couponCode, ShopStore.DiscountType discountType) {
    }
}
