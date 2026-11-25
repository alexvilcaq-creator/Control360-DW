package com.control360.infrastructure.persistence;

import com.control360.domain.Models.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Modelos del adaptador JPA. Cada entidad convierte hacia o desde el dominio. */
public final class Tables {
    private Tables() {}

    @MappedSuperclass
    public abstract static class Row<T> {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        protected Long id;

        abstract T toDomain();
        abstract void copyFrom(T domain);
    }

    @Entity(name = "CategoryRow")
    @Table(name = "categories")
    public static class CategoryRow extends Row<Category> {
        private String name;

        @Override
        Category toDomain() {
            return new Category(id, name);
        }

        @Override
        void copyFrom(Category value) {
            name = value.name();
        }
    }

    @Entity(name = "LocationRow")
    @Table(name = "locations")
    public static class LocationRow extends Row<Location> {
        private String zone;
        private String aisle;
        private String shelf;

        @Override
        Location toDomain() {
            return new Location(id, zone, aisle, shelf);
        }

        @Override
        void copyFrom(Location value) {
            zone = value.zone();
            aisle = value.aisle();
            shelf = value.shelf();
        }
    }

    @Entity(name = "CustomerRow")
    @Table(name = "customers")
    public static class CustomerRow extends Row<Customer> {
        private String name;
        private String document;
        private String email;

        @Override
        Customer toDomain() {
            return new Customer(id, name, document, email);
        }

        @Override
        void copyFrom(Customer value) {
            name = value.name();
            document = value.document();
            email = value.email();
        }
    }

    @Entity(name = "ShiftRow")
    @Table(name = "shifts")
    public static class ShiftRow extends Row<Shift> {
        private String name;
        private LocalTime startsAt;
        private LocalTime endsAt;
        private int graceMinutes;
        private String days;

        @Override
        Shift toDomain() {
            return new Shift(id, name, startsAt, endsAt, graceMinutes, days);
        }

        @Override
        void copyFrom(Shift value) {
            name = value.name();
            startsAt = value.startsAt();
            endsAt = value.endsAt();
            graceMinutes = value.graceMinutes();
            days = value.days();
        }
    }

    @Entity(name = "WorkerRow")
    @Table(name = "workers")
    public static class WorkerRow extends Row<Worker> {
        private String firstName;
        private String lastName;
        private String document;
        private String email;
        private String position;
        private boolean active;
        private Long shiftId;
        private LocalDate startDate;

        @Override
        Worker toDomain() {
            return new Worker(id, firstName, lastName, document, email, position, active, shiftId, startDate);
        }

        @Override
        void copyFrom(Worker value) {
            firstName = value.firstName();
            lastName = value.lastName();
            document = value.document();
            email = value.email();
            position = value.position();
            active = value.active();
            shiftId = value.shiftId();
            startDate = value.startDate();
        }
    }

    @Entity(name = "UserRow")
    @Table(name = "app_users")
    public static class UserRow extends Row<User> {
        private String username;
        private String passwordHash;
        @Enumerated(EnumType.STRING)
        @JdbcTypeCode(SqlTypes.VARCHAR)
        private Role role;
        private boolean active;
        private Long workerId;

        @Override
        User toDomain() {
            return new User(id, username, passwordHash, role, active, workerId);
        }

        @Override
        void copyFrom(User value) {
            username = value.username();
            passwordHash = value.passwordHash();
            role = value.role();
            active = value.active();
            workerId = value.workerId();
        }
    }

    @Entity(name = "ProductRow")
    @Table(name = "products")
    public static class ProductRow extends Row<Product> {
        private String sku;
        private String name;
        private BigDecimal price;
        private int minStock;
        private boolean active;
        private Long categoryId;
        private Long locationId;

        @Override
        Product toDomain() {
            return new Product(id, sku, name, price, minStock, active, categoryId, locationId);
        }

        @Override
        void copyFrom(Product value) {
            sku = value.sku();
            name = value.name();
            price = value.price();
            minStock = value.minStock();
            active = value.active();
            categoryId = value.categoryId();
            locationId = value.locationId();
        }
    }

    @Entity(name = "InventoryRow")
    @Table(name = "inventory")
    public static class InventoryRow extends Row<Inventory> {
        private Long productId;
        private int quantity;

        @Override
        Inventory toDomain() {
            return new Inventory(id, productId, quantity);
        }

        @Override
        void copyFrom(Inventory value) {
            productId = value.productId();
            quantity = value.quantity();
        }
    }

    @Entity(name = "MovementRow")
    @Table(name = "stock_movements")
    public static class MovementRow extends Row<Movement> {
        private Long productId;
        private Long orderId;
        private Long userId;
        private int delta;
        private int balance;
        @Enumerated(EnumType.STRING)
        @JdbcTypeCode(SqlTypes.VARCHAR)
        private MovementType type;
        private String note;
        private LocalDateTime createdAt;

        @Override
        Movement toDomain() {
            return new Movement(id, productId, orderId, userId, delta, balance, type, note, createdAt);
        }

        @Override
        void copyFrom(Movement value) {
            productId = value.productId();
            orderId = value.orderId();
            userId = value.userId();
            delta = value.delta();
            balance = value.balance();
            type = value.type();
            note = value.note();
            createdAt = value.createdAt();
        }
    }

    @Entity(name = "OrderRow")
    @Table(name = "orders")
    public static class OrderRow extends Row<Order> {
        private Long customerId;
        private Long sellerId;
        private Long warehouseWorkerId;
        @Enumerated(EnumType.STRING)
        @JdbcTypeCode(SqlTypes.VARCHAR)
        private Status status;
        private BigDecimal total;
        private LocalDateTime createdAt;

        @Override
        Order toDomain() {
            return new Order(id, customerId, sellerId, warehouseWorkerId, status, total, createdAt);
        }

        @Override
        void copyFrom(Order value) {
            customerId = value.customerId();
            sellerId = value.sellerId();
            warehouseWorkerId = value.warehouseWorkerId();
            status = value.status();
            total = value.total();
            createdAt = value.createdAt();
        }
    }

    @Entity(name = "LineRow")
    @Table(name = "order_lines")
    public static class LineRow extends Row<Line> {
        private Long orderId;
        private Long productId;
        private int quantity;
        private BigDecimal price;

        @Override
        Line toDomain() {
            return new Line(id, orderId, productId, quantity, price);
        }

        @Override
        void copyFrom(Line value) {
            orderId = value.orderId();
            productId = value.productId();
            quantity = value.quantity();
            price = value.price();
        }
    }

    @Entity(name = "HistoryRow")
    @Table(name = "order_history")
    public static class HistoryRow extends Row<History> {
        private Long orderId;
        private Long userId;
        @Enumerated(EnumType.STRING)
        @JdbcTypeCode(SqlTypes.VARCHAR)
        private Status status;
        private LocalDateTime createdAt;

        @Override
        History toDomain() {
            return new History(id, orderId, userId, status, createdAt);
        }

        @Override
        void copyFrom(History value) {
            orderId = value.orderId();
            userId = value.userId();
            status = value.status();
            createdAt = value.createdAt();
        }
    }

    @Entity(name = "AttendanceRow")
    @Table(name = "attendance")
    public static class AttendanceRow extends Row<Attendance> {
        private Long workerId;
        private Long shiftId;
        private LocalDate workDate;
        private LocalDateTime entry;
        @Column(name = "exited_at")
        private LocalDateTime exit;
        private LocalDateTime scheduledEnd;
        private int lateMinutes;
        private int additionalMinutes;
        private int approvedMinutes;

        @Override
        Attendance toDomain() {
            return new Attendance(id, workerId, shiftId, workDate, entry, exit, scheduledEnd, lateMinutes, additionalMinutes, approvedMinutes);
        }

        @Override
        void copyFrom(Attendance value) {
            workerId = value.workerId();
            shiftId = value.shiftId();
            workDate = value.workDate();
            entry = value.entry();
            exit = value.exit();
            scheduledEnd = value.scheduledEnd();
            lateMinutes = value.lateMinutes();
            additionalMinutes = value.additionalMinutes();
            approvedMinutes = value.approvedMinutes();
        }
    }

    @Entity(name = "NotificationRow")
    @Table(name = "notifications")
    public static class NotificationRow extends Row<Notification> {
        private String type;
        private String title;
        private String message;
        private Long orderId;
        private LocalDateTime createdAt;

        @Override
        Notification toDomain() {
            return new Notification(id, type, title, message, orderId, createdAt);
        }

        @Override
        void copyFrom(Notification value) {
            type = value.type();
            title = value.title();
            message = value.message();
            orderId = value.orderId();
            createdAt = value.createdAt();
        }
    }

    @Entity(name = "NotificationReadRow")
    @Table(name = "notification_reads")
    public static class NotificationReadRow extends Row<NotificationRead> {
        private Long notificationId;
        private Long userId;

        @Override
        NotificationRead toDomain() {
            return new NotificationRead(id, notificationId, userId);
        }

        @Override
        void copyFrom(NotificationRead value) {
            notificationId = value.notificationId();
            userId = value.userId();
        }
    }

    @Entity(name = "OrderRequestRow")
    @Table(name = "order_requests")
    public static class OrderRequestRow extends Row<OrderRequest> {
        private Long userId;
        private String requestKey;
        @JdbcTypeCode(SqlTypes.CHAR)
        @Column(length = 64)
        private String fingerprint;
        private Long orderId;

        @Override
        OrderRequest toDomain() {
            return new OrderRequest(id, userId, requestKey, fingerprint, orderId);
        }

        @Override
        void copyFrom(OrderRequest value) {
            userId = value.userId();
            requestKey = value.requestKey();
            fingerprint = value.fingerprint();
            orderId = value.orderId();
        }
    }
}
