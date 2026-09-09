package com.testforge.demo.bank;

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

/**
 * 이체 API. 이체 실행(POST)은 세션 쿠키 인증이 필요하고, 이체 조회(GET)는 공개다.
 * 이체 성공 시 출금 계좌와 입금 계좌의 잔액을 갱신하고 거래내역 2건(출금/입금)을 기록한다.
 * 이체/거래내역 데이터는 {@link BankStore}에서 인메모리로 관리한다(실제 송금 없음).
 */
@RestController
@RequestMapping("/transfers")
@Tag(name = "이체", description = "계좌 간 이체 실행/조회 API")
public class TransferController {

    private final BankStore store;

    public TransferController(BankStore store) {
        this.store = store;
    }

    @Operation(summary = "이체 실행",
            description = "fromAccountId(출금 계좌)에서 toAccountId(입금 계좌)로 amount만큼 이체한다. "
                    + "계좌가 없으면 404, 잔액이 부족하면 409. 성공 시 양쪽 잔액을 갱신하고 거래내역 2건을 기록한 뒤 201로 이체 결과를 반환한다. "
                    + "실제 송금이 발생하는 작업이라 실행 전 사용자 확인이 필요하다. 인증 필요.")
    @TestForgeConfirm(message = "실제 이체가 발생합니다")
    @PostMapping
    public ResponseEntity<?> transfer(@RequestBody TransferRequest request) {
        if (request.fromAccountId() == null || request.toAccountId() == null || request.amount() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "fromAccountId, toAccountId, amount가 필요합니다."));
        }
        if (request.amount() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "amount는 0보다 커야 합니다."));
        }
        if (request.fromAccountId().equals(request.toAccountId())) {
            return ResponseEntity.badRequest().body(Map.of("message", "출금 계좌와 입금 계좌가 같을 수 없습니다."));
        }

        var fromFound = store.findAccount(request.fromAccountId());
        if (fromFound.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "출금 계좌를 찾을 수 없습니다: accountId=" + request.fromAccountId()));
        }
        var toFound = store.findAccount(request.toAccountId());
        if (toFound.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("message", "입금 계좌를 찾을 수 없습니다: accountId=" + request.toAccountId()));
        }

        BankStore.Account from = fromFound.get();
        BankStore.Account to = toFound.get();
        long amount = request.amount();
        if (from.balance() < amount) {
            return ResponseEntity.status(409).body(Map.of("message", "잔액이 부족합니다: accountId=" + from.accountId()));
        }

        // 잔액 갱신 + 거래내역 2건 기록
        store.updateBalance(from, from.balance() - amount);
        store.updateBalance(to, to.balance() + amount);
        store.recordTransaction(from.accountId(), "WITHDRAW", amount);
        store.recordTransaction(to.accountId(), "DEPOSIT", amount);

        BankStore.Transfer transfer = store.recordTransfer(from.accountId(), to.accountId(), amount, "COMPLETED");
        return ResponseEntity.status(201).body(transfer);
    }

    @Operation(summary = "이체 조회",
            description = "이체 ID로 이체 기록을 조회한다. 출금/입금 계좌, 금액, 상태(status)를 포함하며, 없으면 404.")
    @GetMapping("/{id}")
    public ResponseEntity<BankStore.Transfer> detail(@PathVariable Long id) {
        return store.findTransfer(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** 이체 요청 모델. fromAccountId 출금 계좌, toAccountId 입금 계좌, amount 이체 금액. */
    public record TransferRequest(Long fromAccountId, Long toAccountId, Long amount) {
    }
}
