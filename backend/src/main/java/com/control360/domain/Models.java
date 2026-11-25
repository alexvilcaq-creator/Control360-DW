package com.control360.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public final class Models {
    private Models() {}

    public enum Role { ADMIN, VENDEDOR, ALMACEN }

    public enum Status {
        PENDIENTE, ACEPTADO, EN_PREPARACION, LISTO, ENTREGADO, CANCELADO;

        public boolean allows(Status next) {
            return switch (this) {
                case PENDIENTE -> next == ACEPTADO || next == CANCELADO;
                case ACEPTADO -> next == EN_PREPARACION || next == CANCELADO;
                case EN_PREPARACION -> next == LISTO || next == CANCELADO;
                case LISTO -> next == ENTREGADO || next == CANCELADO;
                case ENTREGADO, CANCELADO -> false;
            };
        }
    }

    public enum AttendanceAction { CORRECCION, APROBACION_EXTRA }

    public enum MovementType { AJUSTE, RESERVA, DEVOLUCION }

    public record Category(Long id, String name) {}
    public record Location(Long id, String zone, String aisle, String shelf) {}
    public record Customer(Long id, String name, String document, String email) {}
    public record Shift(Long id, String name, LocalTime startsAt, LocalTime endsAt, int graceMinutes, String days) {}
    public record Worker(Long id, String firstName, String lastName, String document, String email,
                         String position, boolean active, Long shiftId, LocalDate startDate) {}
    public record User(Long id, String username, String passwordHash, Role role, boolean active, Long workerId) {}
    public record UserProfile(Long id, String username, Role role, boolean active, Long workerId) {
        public static UserProfile from(User user) {
            return new UserProfile(user.id(), user.username(), user.role(), user.active(), user.workerId());
        }
    }

    public record Product(Long id, String sku, String name, BigDecimal price, int minStock, boolean active,
                          Long categoryId, Long locationId) {}
    public record Inventory(Long id, Long productId, int quantity) {}
    public record ProductView(Long id, String sku, String name, BigDecimal price, int minStock, boolean active,
                              Long categoryId, Long locationId, int quantity) {
        public static ProductView of(Product product, int quantity) {
            return new ProductView(product.id(), product.sku(), product.name(), product.price(), product.minStock(),
                    product.active(), product.categoryId(), product.locationId(), quantity);
        }

        public boolean lowStock() {
            return active && quantity <= minStock;
        }
    }

    public record Movement(Long id, Long productId, Long orderId, Long userId, int delta, int balance,
                           MovementType type, String note, LocalDateTime createdAt) {}
    public record Order(Long id, Long customerId, Long sellerId, Long warehouseWorkerId, Status status,
                        BigDecimal total, LocalDateTime createdAt) {}
    public record OrderRequest(Long id, Long userId, String requestKey, String fingerprint, Long orderId) {}
    public record Line(Long id, Long orderId, Long productId, int quantity, BigDecimal price) {}
    public record History(Long id, Long orderId, Long userId, Status status, LocalDateTime createdAt) {}
    public record OrderView(Order order, String customer, List<Line> items, List<History> history) {}
    public record Attendance(Long id, Long workerId, Long shiftId, LocalDate workDate, LocalDateTime entry,
                             LocalDateTime exit, LocalDateTime scheduledEnd, int lateMinutes,
                             int additionalMinutes, int approvedMinutes) {}
    public record AttendanceState(Long workerId, LocalDate today, String shiftName, Attendance open, Attendance latest) {}
    public record AttendanceValues(LocalDateTime entry, LocalDateTime exit, int lateMinutes, int additionalMinutes, int approvedMinutes) {
        public static AttendanceValues from(Attendance attendance) {
            return new AttendanceValues(attendance.entry(), attendance.exit(), attendance.lateMinutes(),
                    attendance.additionalMinutes(), attendance.approvedMinutes());
        }
    }
    public record AttendanceAudit(Long id, Long attendanceId, Long administratorId, String administratorName,
                                  AttendanceAction action, String reason, LocalDateTime createdAt,
                                  AttendanceValues before, AttendanceValues after) {}
    public record Notification(Long id, String type, String title, String message, Long orderId,
                               LocalDateTime createdAt) {}
    public record NotificationRead(Long id, Long notificationId, Long userId) {}

    public record LineRequest(Long productId, int quantity) {}
    public record Count(String name, long value) {}
    public record WorkerView(Worker worker, boolean available) {}
    public record Absence(Long workerId, String worker, LocalDate workDate, String shift) {}
    public record AttendanceSummary(long present, long atWork, long scheduled, long late, long absent, long approvedMinutes) {}
    public record Dashboard(long ordersToday, long pendingOrders, long lowStockCount, long activeWorkers,
                            AttendanceSummary attendance, List<OrderView> recentOrders,
                            List<ProductView> lowStock, List<Count> inventoryByCategory,
                            List<Count> ordersByStatus, List<Count> ordersByDate, List<Count> popularProducts) {}
}
