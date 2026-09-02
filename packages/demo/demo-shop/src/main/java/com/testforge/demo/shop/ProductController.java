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

/**
 * 상품 조회 API. 인증 없이 조회만 노출한다.
 * 상품 데이터는 {@link ShopStore}에서 공유하며, 주문 금액 계산에 재사용된다.
 * OpenAPI summary/description은 AI 매칭 품질에 직결되므로 명확히 작성한다.
 */
@RestController
@RequestMapping("/products")
@Tag(name = "상품", description = "상품 조회 API")
public class ProductController {

    private final ShopStore store;

    public ProductController(ShopStore store) {
        this.store = store;
    }

    @Operation(summary = "상품 목록 조회", description = "등록된 상품 목록을 조회한다. keyword로 이름 부분 검색.")
    @GetMapping
    public List<ShopStore.Product> list(@RequestParam(required = false) String keyword) {
        return store.listProducts(keyword);
    }

    @Operation(summary = "상품 단건 조회", description = "상품 ID로 단건 상품을 조회한다. 없으면 404.")
    @GetMapping("/{id}")
    public ResponseEntity<ShopStore.Product> detail(@PathVariable Long id) {
        return store.findProduct(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
