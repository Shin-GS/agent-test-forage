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
 * 배송 API. 생성/상태변경은 세션 쿠키 인증이 필요하고, 단건 조회는 공개다.
 * 날짜 문자열(shippingDate, YYYY-MM-DD)과 불리언(express), 상태 enum 입력을 스펙에 노출한다.
 */
@RestController
@RequestMapping("/shipments")
@Tag(name = "배송", description = "배송 생성/조회/상태변경 API")
public class ShippingController {

    private final ShopStore store;

    public ShippingController(ShopStore store) {
        this.store = store;
    }

    @Operation(summary = "배송 생성",
            description = "주문에 대한 배송을 생성한다. shippingDate는 YYYY-MM-DD 문자열, express는 빠른배송 여부(불리언). "
                    + "상태는 PREPARING으로 시작. 인증 필요. 주문이 없으면 404.")
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateShipmentRequest request) {
        if (request.orderId() == null || request.address() == null || request.shippingDate() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "orderId, address, shippingDate가 필요합니다."));
        }

        var foundOrder = store.findOrder(request.orderId());
        if (foundOrder.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        boolean express = request.express() != null && request.express();
        ShopStore.Shipment shipment = store.createShipment(
                request.orderId(), request.address(), request.shippingDate(), express);
        return ResponseEntity.status(201).body(shipment);
    }

    @Operation(summary = "배송 조회", description = "배송 ID로 배송 상세를 조회한다. 인증 없이 조회 가능. 없으면 404.")
    @GetMapping("/{id}")
    public ResponseEntity<ShopStore.Shipment> detail(@PathVariable Long id) {
        return store.findShipment(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "배송 상태 변경",
            description = "배송 상태를 PREPARING/SHIPPED/DELIVERED 중 하나로 변경한다. 인증 필요. "
                    + "배송이 없으면 404, 잘못된 상태값이면 400.")
    @PostMapping("/{id}/status")
    public ResponseEntity<?> changeStatus(@PathVariable Long id, @RequestBody UpdateStatusRequest request) {
        if (request.status() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "status(PREPARING/SHIPPED/DELIVERED)가 필요합니다."));
        }

        var found = store.findShipment(id);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ShopStore.Shipment updated = store.updateShipmentStatus(found.get(), request.status());
        return ResponseEntity.ok(updated);
    }

    /** 배송 생성 요청 모델. shippingDate는 YYYY-MM-DD 문자열, express는 불리언. */
    public record CreateShipmentRequest(Long orderId, String address, String shippingDate, Boolean express) {
    }

    /** 배송 상태 변경 요청 모델. status는 enum. */
    public record UpdateStatusRequest(ShopStore.ShipmentStatus status) {
    }
}
