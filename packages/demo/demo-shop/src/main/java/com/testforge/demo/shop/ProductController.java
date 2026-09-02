package com.testforge.demo.shop;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 상품 조회 API. 1단계 검증용으로 인증 없이 조회만 노출한다.
 * OpenAPI summary/description은 AI 매칭 품질에 직결되므로 명확히 작성한다.
 */
@RestController
@RequestMapping("/products")
@Tag(name = "상품", description = "상품 조회 API")
public class ProductController {

    /** 인메모리 상품 저장소 (기동 시 시드) */
    private final Map<Long, Product> products = new ConcurrentHashMap<>();

    public ProductController() {
        seed();
    }

    private void seed() {
        AtomicLong id = new AtomicLong(1);
        put(id.getAndIncrement(), "무선마우스", 19900);
        put(id.getAndIncrement(), "키보드", 49000);
        put(id.getAndIncrement(), "모니터", 230000);
        put(id.getAndIncrement(), "USB허브", 15000);
        put(id.getAndIncrement(), "웹캠", 58000);
    }

    private void put(long id, String name, int price) {
        products.put(id, new Product(id, name, price));
    }

    @Operation(summary = "상품 목록 조회", description = "등록된 상품 목록을 조회한다. keyword로 이름 부분 검색.")
    @GetMapping
    public List<Product> list(@RequestParam(required = false) String keyword) {
        return products.values().stream()
                .filter(p -> keyword == null || p.name().contains(keyword))
                .sorted((a, b) -> Long.compare(a.id(), b.id()))
                .toList();
    }

    @Operation(summary = "상품 단건 조회", description = "상품 ID로 단건 상품을 조회한다. 없으면 404.")
    @GetMapping("/{id}")
    public ResponseEntity<Product> detail(@PathVariable Long id) {
        Product p = products.get(id);
        return p == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(p);
    }

    /** 상품 응답 모델 */
    public record Product(Long id, String name, int price) {
    }
}
