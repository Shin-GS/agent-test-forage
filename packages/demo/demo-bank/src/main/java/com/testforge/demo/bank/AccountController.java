package com.testforge.demo.bank;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 계좌 API. 계좌 목록과 단건 잔액 조회를 제공하며 모두 공개(인증 불필요)다.
 * 계좌 데이터는 {@link BankStore}에서 인메모리로 관리한다.
 * OpenAPI summary/description은 AI 매칭 품질에 직결되므로 명확히 작성한다.
 */
@RestController
@RequestMapping("/accounts")
@Tag(name = "계좌", description = "계좌 목록/잔액 조회 API")
public class AccountController {

    private final BankStore store;

    public AccountController(BankStore store) {
        this.store = store;
    }

    @Operation(summary = "계좌 목록 조회",
            description = "전체 계좌 목록을 조회한다. 각 계좌의 accountId, 예금주명(ownerName), 잔액(balance)을 포함한다.")
    @GetMapping
    public List<BankStore.Account> list() {
        return store.listAccounts();
    }

    @Operation(summary = "계좌 단건 조회",
            description = "계좌 ID로 단건 계좌를 조회한다. 잔액(balance)을 포함하며, 계좌가 없으면 404.")
    @GetMapping("/{id}")
    public ResponseEntity<BankStore.Account> detail(@PathVariable Long id) {
        return store.findAccount(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
