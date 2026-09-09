package com.testforge.demo.shop;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * demo-shop 공용 인메모리 스토어. 상품/사용자/주문/결제/세션/쿠폰/배송/장바구니를 한곳에서 관리한다.
 * DB/JPA 없이 {@link ConcurrentHashMap} 기반으로 동작하며, 기동 시 상품/사용자/쿠폰을 시드한다.
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
    /** 쿠폰 저장소 (code -> Coupon) */
    private final Map<String, Coupon> coupons = new ConcurrentHashMap<>();
    /** 배송 저장소 */
    private final Map<Long, Shipment> shipments = new ConcurrentHashMap<>();
    /** 유효한 세션 토큰 집합 */
    private final Set<String> sessions = ConcurrentHashMap.newKeySet();
    /** 전역 단일 장바구니 (데모라 사용자 구분 없음) */
    private final List<CartItem> cart = new CopyOnWriteArrayList<>();

    private final AtomicLong orderIdSeq = new AtomicLong(1001);
    private final AtomicLong paymentIdSeq = new AtomicLong(5001);
    private final AtomicLong shipmentIdSeq = new AtomicLong(7001);

    public ShopStore() {
        seedProducts();
        seedUsers();
        seedCoupons();
    }

    // ---- 시드 ----

    private void seedProducts() {
        AtomicLong id = new AtomicLong(1);
        putProduct(id.getAndIncrement(), "무선마우스", 19900, "ELECTRONICS", true);
        putProduct(id.getAndIncrement(), "키보드", 49000, "ELECTRONICS", true);
        putProduct(id.getAndIncrement(), "모니터", 230000, "ELECTRONICS", false);
        putProduct(id.getAndIncrement(), "USB허브", 15000, "OFFICE", true);
        putProduct(id.getAndIncrement(), "웹캠", 58000, "ETC", false);
    }

    private void seedUsers() {
        users.put("demo", "demo1234");
    }

    private void seedCoupons() {
        putCoupon("WELCOME10", DiscountType.PERCENT, 10);
        putCoupon("FIX5000", DiscountType.FIXED, 5000);
    }

    private void putProduct(long id, String name, int price, String category, boolean inStock) {
        products.put(id, new Product(id, name, price, category, inStock));
    }

    private void putCoupon(String code, DiscountType type, int value) {
        coupons.put(code, new Coupon(code, type, value));
    }

    // ---- 상품 ----

    /** 상품 목록 조회. keyword는 이름 부분검색, category는 카테고리 필터. 둘 다 null이면 전체. */
    public List<Product> listProducts(String keyword, String category) {
        return products.values().stream()
                .filter(p -> keyword == null || p.name().contains(keyword))
                .filter(p -> category == null || category.equalsIgnoreCase(p.category()))
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

    // ---- 쿠폰 ----

    public List<Coupon> listCoupons() {
        return coupons.values().stream()
                .sorted((a, b) -> a.code().compareTo(b.code()))
                .toList();
    }

    public Optional<Coupon> findCoupon(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(coupons.get(code));
    }

    /** 쿠폰을 금액에 적용해 할인 후 금액을 계산한다. 0 미만이 되지 않도록 보정한다. */
    public int applyDiscount(int amount, Coupon coupon) {
        int discounted = switch (coupon.discountType()) {
            case PERCENT -> amount - (amount * coupon.value() / 100);
            case FIXED -> amount - coupon.value();
        };
        return Math.max(discounted, 0);
    }

    // ---- 배송 ----

    /** 배송 생성. 상태는 PREPARING으로 시작한다. */
    public Shipment createShipment(long orderId, String address, String shippingDate, boolean express) {
        long id = shipmentIdSeq.getAndIncrement();
        Shipment shipment = new Shipment(id, orderId, address, shippingDate, express, ShipmentStatus.PREPARING);
        shipments.put(id, shipment);
        return shipment;
    }

    public Optional<Shipment> findShipment(long id) {
        return Optional.ofNullable(shipments.get(id));
    }

    /** 배송 상태를 변경한 새 스냅샷으로 교체 저장한다. */
    public Shipment updateShipmentStatus(Shipment shipment, ShipmentStatus status) {
        Shipment updated = new Shipment(shipment.shipmentId(), shipment.orderId(), shipment.address(),
                shipment.shippingDate(), shipment.express(), status);
        shipments.put(shipment.shipmentId(), updated);
        return updated;
    }

    // ---- 장바구니 ----

    /** 장바구니에 상품을 담는다. */
    public void addCartItem(long productId, int quantity) {
        cart.add(new CartItem(productId, quantity));
    }

    /** 현재 장바구니 스냅샷을 반환한다. */
    public List<CartItem> getCart() {
        return new ArrayList<>(cart);
    }

    /** 장바구니를 비운다. */
    public void clearCart() {
        cart.clear();
    }

    // ---- 모델 ----

    /** 상품 응답 모델 */
    public record Product(Long id, String name, int price, String category, boolean inStock) {
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

    /** 할인 타입 */
    public enum DiscountType {
        PERCENT, FIXED
    }

    /** 쿠폰 응답 모델 */
    public record Coupon(String code, DiscountType discountType, int value) {
    }

    /** 배송 상태 */
    public enum ShipmentStatus {
        PREPARING, SHIPPED, DELIVERED
    }

    /** 배송 응답 모델 */
    public record Shipment(Long shipmentId, Long orderId, String address, String shippingDate,
                           boolean express, ShipmentStatus status) {
    }

    /** 장바구니 항목 모델 */
    public record CartItem(Long productId, int quantity) {
    }
}
