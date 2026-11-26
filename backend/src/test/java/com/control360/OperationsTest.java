package com.control360;

import com.control360.application.Operations;
import com.control360.application.Store;
import com.control360.domain.Models.*;
import com.control360.domain.RuleViolation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OperationsTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-25T13:25:00Z"), ZoneId.of("America/Lima"));
    private static final User SELLER = new User(1L, "vendedor", "hash", Role.VENDEDOR, true, null);
    private static final User ADMIN = new User(2L, "admin", "hash", Role.ADMIN, true, null);
    private static final Product PRODUCT = new Product(10L, "SKU", "Producto de prueba", new BigDecimal("8.25"), 2, true, 1L, 1L, null);
    private final Store store = mock(Store.class);
    private final Operations operations = new Operations(store, CLOCK);

    @BeforeEach
    void executeWrites() {
        lenient().when(store.write(any())).thenAnswer(call -> call.<Supplier<?>>getArgument(0).get());
    }

    @Test
    void rejectsNegativeOrRepeatedUnitsBeforeWriting() {
        assertEquals(RuleViolation.Kind.INVALID, assertThrows(RuleViolation.class,
                () -> operations.createOrder(SELLER, 1L, List.of(new LineRequest(10L, -2)))).kind());
        assertEquals(RuleViolation.Kind.INVALID, assertThrows(RuleViolation.class,
                () -> operations.createOrder(SELLER, 1L, List.of(new LineRequest(10L, 1), new LineRequest(10L, 1)))).kind());
        verifyNoInteractions(store);
    }

    @Test
    void insufficientStockRejectsTheWholeOrder() {
        when(store.find(Customer.class, 1L)).thenReturn(Optional.of(new Customer(1L, "Cliente", "DOC", null)));
        when(store.inventory(10L, true)).thenReturn(Optional.of(new Inventory(1L, 10L, 1)));
        when(store.find(Product.class, 10L)).thenReturn(Optional.of(PRODUCT));
        RuleViolation failure = assertThrows(RuleViolation.class,
                () -> operations.createOrder(SELLER, 1L, List.of(new LineRequest(10L, 2))));
        assertEquals(RuleViolation.Kind.CONFLICT, failure.kind());
        verify(store, never()).save(any());
    }

    @Test
    void aDeliveredOrderCannotBeCancelledOrRestocked() {
        when(store.lockOrder(1L)).thenReturn(Optional.of(new Order(1L, 1L, 1L, 3L,
                Status.ENTREGADO, new BigDecimal("8.25"), LocalDateTime.now(CLOCK))));
        assertEquals(RuleViolation.Kind.CONFLICT, assertThrows(RuleViolation.class,
                () -> operations.transition(ADMIN, 1L, Status.CANCELADO)).kind());
        verify(store, never()).inventory(anyLong(), anyBoolean());
        verify(store, never()).save(any());
    }

    @Test
    void calculatesLateMinutesFromShiftAndDoesNotApproveExtraTime() {
        var warehouse = new User(3L, "almacen", "hash", Role.ALMACEN, true, 42L);
        var worker = new Worker(42L, "Alex", "Vega", "DOC", "test@example.com", "Almacén", true, 12L, LocalDate.now(CLOCK));
        when(store.lockWorker(42L)).thenReturn(Optional.of(worker));
        when(store.find(Shift.class, 12L)).thenReturn(Optional.of(
                new Shift(12L, "Mañana", LocalTime.of(8, 0), LocalTime.of(17, 0), 10, "1,2,3,4,5")));
        when(store.all(Attendance.class)).thenReturn(List.of());
        when(store.save(any(Attendance.class))).thenAnswer(call -> call.getArgument(0));
        Attendance attendance = operations.enter(warehouse, null);
        assertEquals(25, attendance.lateMinutes());
        assertEquals(0, attendance.additionalMinutes());
        assertEquals(0, attendance.approvedMinutes());
        assertEquals(LocalDateTime.of(2026, 11, 25, 17, 0), attendance.scheduledEnd());
        assertEquals(RuleViolation.Kind.FORBIDDEN, assertThrows(RuleViolation.class,
                () -> operations.enter(warehouse, 43L)).kind());
    }

    @Test
    void approvalCannotExceedAdditionalMinutes() {
        var day = LocalDate.now(CLOCK);
        var entry = new Attendance(6L, 42L, 12L, day, day.atTime(8, 0), day.atTime(17, 12),
                day.atTime(17, 0), 0, 12, 0);
        var worker = new Worker(42L, "Alex", "Vega", "DOC", "test@example.com", "Almacén", true, 12L, LocalDate.now(CLOCK));
        when(store.find(Attendance.class, 6L)).thenReturn(Optional.of(entry));
        when(store.lockWorker(42L)).thenReturn(Optional.of(worker));
        assertEquals(RuleViolation.Kind.INVALID, assertThrows(RuleViolation.class, () -> operations.approve(ADMIN, 6L, 13)).kind());
        verify(store, never()).save(any());
    }
}
