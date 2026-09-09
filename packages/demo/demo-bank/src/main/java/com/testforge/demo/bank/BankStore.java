package com.testforge.demo.bank;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * demo-bank 공용 인메모리 스토어. 계좌/거래내역/이체기록/사용자/세션을 한곳에서 관리한다.
 * DB/JPA 없이 {@link ConcurrentHashMap} 기반으로 동작하며, 기동 시 계좌와 사용자를 시드한다.
 * 실제 송금은 일어나지 않는 데모용이며 잔액은 메모리에서만 갱신된다.
 */
@Component
public class BankStore {

    /** 계좌 저장소 (accountId -> account) */
    private final Map<Long, Account> accounts = new ConcurrentHashMap<>();
    /** 거래내역 저장소 (txId -> transaction) */
    private final Map<Long, Transaction> transactions = new ConcurrentHashMap<>();
    /** 이체 기록 저장소 (transferId -> transfer) */
    private final Map<Long, Transfer> transfers = new ConcurrentHashMap<>();
    /** 사용자 저장소 (username -> password) */
    private final Map<String, String> users = new ConcurrentHashMap<>();
    /** 유효한 세션 토큰 집합 */
    private final Set<String> sessions = ConcurrentHashMap.newKeySet();

    private final AtomicLong accountIdSeq = new AtomicLong(1001);
    private final AtomicLong txIdSeq = new AtomicLong(5001);
    private final AtomicLong transferIdSeq = new AtomicLong(7001);

    public BankStore() {
        seedAccounts();
        seedUsers();
    }

    // ---- 시드 ----

    private void seedAccounts() {
        putAccount("김철수", 1_000_000L);
        putAccount("이영희", 500_000L);
        putAccount("박민수", 2_500_000L);
        putAccount("최지은", 750_000L);
    }

    private void seedUsers() {
        users.put("demo", "demo1234");
    }

    // ---- 계좌 ----

    /** 계좌 생성. ownerName 예금주명, balance 초기 잔액. */
    public Account putAccount(String ownerName, long balance) {
        long id = accountIdSeq.getAndIncrement();
        Account account = new Account(id, ownerName, balance);
        accounts.put(id, account);
        return account;
    }

    public List<Account> listAccounts() {
        return accounts.values().stream()
                .sorted(Comparator.comparingLong(Account::accountId))
                .toList();
    }

    public Optional<Account> findAccount(long accountId) {
        return Optional.ofNullable(accounts.get(accountId));
    }

    /** 계좌 잔액을 변경한 새 스냅샷으로 교체 저장한다. */
    public Account updateBalance(Account account, long newBalance) {
        Account updated = new Account(account.accountId(), account.ownerName(), newBalance);
        accounts.put(account.accountId(), updated);
        return updated;
    }

    // ---- 거래내역 ----

    /** 거래내역 기록. type 은 DEPOSIT(입금)/WITHDRAW(출금), amount 는 금액. */
    public Transaction recordTransaction(long accountId, String type, long amount) {
        long id = txIdSeq.getAndIncrement();
        Transaction tx = new Transaction(id, accountId, type, amount, LocalDateTime.now().toString());
        transactions.put(id, tx);
        return tx;
    }

    public List<Transaction> listTransactions(long accountId) {
        List<Transaction> result = new ArrayList<>();
        for (Transaction tx : transactions.values()) {
            if (tx.accountId() == accountId) {
                result.add(tx);
            }
        }
        result.sort(Comparator.comparingLong(Transaction::txId));
        return result;
    }

    // ---- 이체 ----

    /** 이체 기록 저장. status 는 COMPLETED(완료). */
    public Transfer recordTransfer(long fromAccountId, long toAccountId, long amount, String status) {
        long id = transferIdSeq.getAndIncrement();
        Transfer transfer = new Transfer(id, fromAccountId, toAccountId, amount, status);
        transfers.put(id, transfer);
        return transfer;
    }

    public Optional<Transfer> findTransfer(long transferId) {
        return Optional.ofNullable(transfers.get(transferId));
    }

    // ---- 사용자 / 세션 ----

    public boolean authenticate(String username, String password) {
        return username != null && password != null && password.equals(users.get(username));
    }

    public String createSession() {
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.add(token);
        return token;
    }

    public boolean isValidSession(String token) {
        return token != null && sessions.contains(token);
    }

    // ---- 모델 ----

    /** 계좌 응답 모델 */
    public record Account(Long accountId, String ownerName, Long balance) {
    }

    /** 거래내역 응답 모델. type 은 DEPOSIT 또는 WITHDRAW. */
    public record Transaction(Long txId, Long accountId, String type, Long amount, String createdAt) {
    }

    /** 이체 기록 응답 모델. status 는 COMPLETED. */
    public record Transfer(Long transferId, Long fromAccountId, Long toAccountId, Long amount, String status) {
    }

    /** 공용 응답 헬퍼: 메시지 맵 생성 */
    public static Map<String, Object> message(String message) {
        return Map.of("message", message);
    }
}
