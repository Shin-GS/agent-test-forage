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
 * 거래내역 API. 특정 계좌의 거래내역 목록을 조회하며 공개(인증 불필요)다.
 * 거래내역은 계좌에 종속되며 {@link BankStore}에서 인메모리로 관리한다.
 * 각 거래내역은 입금(DEPOSIT)/출금(WITHDRAW) 유형과 금액, 발생 시각을 가진다.
 */
@RestController
@RequestMapping("/accounts/{accountId}/transactions")
@Tag(name = "거래내역", description = "계좌 거래내역 조회 API")
public class TransactionController {

    private final BankStore store;

    public TransactionController(BankStore store) {
        this.store = store;
    }

    @Operation(summary = "거래내역 목록 조회",
            description = "계좌 ID의 거래내역 목록을 조회한다. 각 내역은 유형(DEPOSIT/WITHDRAW)과 금액, 발생 시각을 포함한다. 계좌가 없으면 404.")
    @GetMapping
    public ResponseEntity<List<BankStore.Transaction>> list(@PathVariable Long accountId) {
        if (store.findAccount(accountId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(store.listTransactions(accountId));
    }
}
