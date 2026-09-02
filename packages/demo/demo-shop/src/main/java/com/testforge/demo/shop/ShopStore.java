package com.testforge.demo.shop;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * demo-shop 공용 인메모리 스토어. 상품/사용자/주문/결제/세션을 한곳에서 관리한다.
 * DB/JPA 없이 {@link ConcurrentHashMap} 기반으로 동작하며, 기동 시 상품/사용자를 시드한다.
 *
 * <p>상품 가격은 주문 금액 계산에 필요하므로, 컨트롤러들이 이 스토어를 공유해 참조한다.
 */
@Component
public class ShopStore {

    /** 상품 저장소 */
    private final Map<Long, Product> products = new ConcurrentHashMap<>();
    /** 사용자 저장소 (username -> password) */
    private final Map<String, String> users = new ConcurrentHashMap<>();
    /** 주문 저장소 */
    private final Map<Long, Order> orders = new ConcurrentHashMap<>();
    /** 결제 저장소 */
    private final Map<Long, Payment> payments = new ConcurrentHashMap<>();
    /** 유효한 세션 토큰 집합 */
    private final Set<String> sessions = ConcurrentHashMap.newKeySet();

    private final AtomicLong orderIdSeq = new AtomicLong(1001);
    private final AtomicLong paymentIdSeq = new AtomicLong(5001);

    public ShopStore() {
        seedProducts();
        seedUsers();
    }

    // ---- 시드 ----

    private void seedProducts() {
        AtomicLong id = new AtomicLong(1);
        putProduct(id.getAndIncrement(), "무선마우스", 19900);
        putProduct(id.getAndIncrement(), "키보드", 49000);
        putProduct(id.getAndIncrement(), "모니터", 230000);
        putProduct(id.getAndIncrement(), "USB허브", 15000);
        putProduct(id.getAndIncrement(), "웹캠", 58000);
    }

    private void seedUsers() {
        users.put("demo", "demo1234");
    }

    private void putProduct(long id, String name, int price) {
        products.put(id, new Product(id, name, price));
    }

    // ---- 상품 ----

    public List<Product> listProducts(String keyword) {
        return products.values().stream()
                .filter(p -> keyword == null || p.name().contains(keyword))
                .sorted((a, b) -> Long.compare(a.id(), b.id()))
                .toList();
    }

    public Optional<Product> findProduct(long id) {
        return Optional.ofNullable(products.get(id));
    }

    // ---- 사용자 / 세션 ----

    /** 자격 증명 검증. 일치하면 true. */
    public boolean authenticate(String username, String password) {
        return username != null && password != null && password.equals(users.get(username));
    }

    /** 새 세션 토큰을 발급하고 저장한다. */
    public String createSession() {
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.add(token);
        return token;
    }

    /** 세션 토큰 유효성 검사. */
    public boolean isValidSession(String token) {
        return token != null && sessions.contains(token);
    }

    // ---- 주문 ----

    /** 주문 생성. 금액은 상품가격 * 수량으로 계산한다. */
    public Order createOrder(Product product, int quantity) {
        long id = orderIdSeq.getAndIncrement();
        int amount = product.price() * quantity;
        Order order = new Order(id, product.id(), quantity, amount, OrderStatus.CREATED);
        orders.put(id, order);
        return order;
    }

    public Optional<Order> findOrder(long id) {
        return Optional.ofNullable(orders.get(id));
    }

    /** 주문 상태를 변경한 새 스냅샷으로 교체 저장한다. */
    public Order updateOrderStatus(Order order, OrderStatus status) {
        Order updated = new Order(order.orderId(), order.productId(), order.quantity(), order.amount(), status);
        orders.put(order.orderId(), updated);
        return updated;
    }

    // ---- 결제 ----

    public Payment createPayment(long orderId, String method) {
        long id = paymentIdSeq.getAndIncrement();
        Payment payment = new Payment(id, orderId, method, "PAID");
        payments.put(id, payment);
        return payment;
    }

    public Optional<Payment> findPayment(long id) {
        return Optional.ofNullable(payments.get(id));
    }

    // ---- 모델 ----

    /** 상품 응답 모델 */
    public record Product(Long id, String name, int price) {
    }

    /** 주문 상태 */
    public enum OrderStatus {
        CREATED, PAID, CANCELLED
    }

    /** 주문 응답 모델 */
    public record Order(Long orderId, Long productId, int quantity, int amount, OrderStatus status) {
    }

    /** 결제 응답 모델 */
    public record Payment(Long paymentId, Long orderId, String method, String status) {
    }
}
