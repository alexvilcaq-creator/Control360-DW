package com.control360;

import com.control360.application.Store;
import com.control360.domain.Models.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.bootstrap.demo=false", "app.bootstrap.username=", "app.bootstrap.password="})
@AutoConfigureMockMvc
class Control360MysqlIT {
    private static final Instant START = Instant.parse("2026-11-25T13:25:00Z");
    private static final AtomicReference<Instant> TIME = new AtomicReference<>(START);
    private static final String PASSWORD = UUID.randomUUID().toString();

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private Store store;
    @Autowired private PasswordEncoder encoder;
    private String tag;
    private User admin;
    private User seller;
    private User picker;
    private User secondPicker;
    private Category category;
    private Location location;
    private Customer customer;

    @TestConfiguration
    static class TestClock {
        @Bean
        @Primary
        Clock adjustableClock() {
            return new Clock() {
                @Override public ZoneId getZone() { return ZoneId.of("America/Lima"); }
                @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
                @Override public Instant instant() { return TIME.get(); }
            };
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String name = System.getenv("CONTROL360_TEST_DATABASE");
        if (name == null || !name.matches("[A-Za-z0-9_]+_test"))
            throw new IllegalStateException("CONTROL360_TEST_DATABASE debe ser un esquema de pruebas con sufijo _test");
        String host = System.getenv().getOrDefault("MYSQL_HOST", "127.0.0.1");
        String port = System.getenv().getOrDefault("MYSQL_PORT", "3306");
        properties.add("spring.datasource.url", () -> "jdbc:mysql://" + host + ":" + port + "/" + name);
    }

    @BeforeEach
    void fixture() {
        TIME.set(START);
        tag = UUID.randomUUID().toString().substring(0, 8);
        store.write(() -> {
            String hash = encoder.encode(PASSWORD);
            var shift = store.save(new Shift(null, "Turno " + tag, LocalTime.of(8, 0), LocalTime.of(17, 0), 10, "1,2,3,4,5"));
            var worker = worker("a", shift.id());
            var second = worker("b", shift.id());
            admin = store.save(new User(null, "admin-" + tag, hash, Role.ADMIN, true, null));
            seller = store.save(new User(null, "seller-" + tag, hash, Role.VENDEDOR, true, worker("s", shift.id()).id()));
            picker = store.save(new User(null, "picker-" + tag, hash, Role.ALMACEN, true, worker.id()));
            secondPicker = store.save(new User(null, "picker2-" + tag, hash, Role.ALMACEN, true, second.id()));
            category = store.save(new Category(null, "Categoría " + tag));
            location = store.save(new Location(null, tag, "2", "4"));
            customer = store.save(new Customer(null, "Cliente " + tag, tag, null));
            return true;
        });
    }

    private Worker worker(String prefix, long shiftId) {
        return store.save(new Worker(null, "Equipo", prefix, prefix + tag, prefix + tag + "@example.com", "Operario", true, shiftId, LocalDate.of(2026, 11, 25)));
    }

    @Test
    void usesRealSessionsCsrfAndRolePermissions() throws Exception {
        mvc.perform(get("/api/productos")).andExpect(status().isUnauthorized());
        mvc.perform(body(post("/api/auth/login"), Map.of("username", admin.username(), "password", PASSWORD)))
                .andExpect(status().isForbidden());
        mvc.perform(body(post("/api/auth/login").with(csrf()), Map.of("username", admin.username(), "password", "incorrecta")))
                .andExpect(status().isUnauthorized());
        var session = login(seller);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("VENDEDOR")).andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(body(post("/api/productos").session(session).with(csrf()), product("1.50", true)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reportes/resumen").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void administratorCreatesEmployeeAccountsAndDeactivationInvalidatesExistingSessions() throws Exception {
        var adminSession = login(admin);
        var shiftId = store.find(Worker.class, seller.workerId()).orElseThrow().shiftId();
        for (var role : List.of(Role.VENDEDOR, Role.ALMACEN)) {
            var employee = store.write(() -> worker(role == Role.VENDEDOR ? "v" : "p", shiftId));
            String username = "new-" + role.name().toLowerCase(java.util.Locale.ROOT) + "-" + tag;
            var identifier = body(post("/api/usuarios").session(adminSession).with(csrf()),
                    Map.of("username", username, "password", PASSWORD, "role", role.name(), "active", true, "workerId", employee.id()));
            mvc.perform(identifier).andExpect(status().isCreated()).andExpect(jsonPath("$.workerId").value(employee.id()))
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());
            var created = store.user(username).orElseThrow();
            assertTrue(encoder.matches(PASSWORD, created.passwordHash()));
            assertNotEquals(PASSWORD, created.passwordHash());
            var employeeSession = login(created);
            mvc.perform(get("/api/usuarios").session(employeeSession)).andExpect(status().isForbidden());
            mvc.perform(get("/api/reportes/resumen").session(employeeSession)).andExpect(status().isForbidden());
            mvc.perform(body(put("/api/usuarios/{id}", created.id()).session(adminSession).with(csrf()),
                            Map.of("username", username, "role", role.name(), "active", false, "workerId", employee.id())))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
            mvc.perform(get("/api/productos").session(employeeSession)).andExpect(status().isUnauthorized());
            mvc.perform(body(post("/api/auth/login").with(csrf()), Map.of("username", username, "password", PASSWORD)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void sellerMarksOnlyOwnAttendanceAndAdministratorCorrectionsKeepAuditHistory() throws Exception {
        var sellerSession = login(seller);
        var adminSession = login(admin);
        var pickerSession = login(picker);
        mvc.perform(get("/api/asistencias/estado").session(sellerSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.workerId").value(seller.workerId()));
        mvc.perform(body(post("/api/asistencias/entradas").session(sellerSession).with(csrf()),
                Map.of("workerId", picker.workerId()))).andExpect(status().isForbidden());
        var result = mvc.perform(body(post("/api/asistencias/entradas").session(sellerSession).with(csrf()), Map.of()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.lateMinutes").value(25)).andReturn();
        long id = parse(result).get("id").asLong();
        mvc.perform(get("/api/asistencias/estado").session(sellerSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.open.id").value(id));
        mvc.perform(get("/api/asistencias").session(pickerSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        TIME.set(Instant.parse("2026-11-25T22:35:00Z"));
        mvc.perform(body(post("/api/asistencias/salidas").session(sellerSession).with(csrf()), Map.of()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.additionalMinutes").value(35))
                .andExpect(jsonPath("$.approvedMinutes").value(0));
        mvc.perform(body(put("/api/asistencias/{id}/aprobar", id).session(sellerSession).with(csrf()), Map.of("minutes", 25)))
                .andExpect(status().isForbidden());
        mvc.perform(body(put("/api/asistencias/{id}/aprobar", id).session(adminSession).with(csrf()), Map.of("minutes", 25)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.approvedMinutes").value(25));
        var correction = Map.of("entry", "2026-11-25T08:00:00", "exit", "2026-11-25T17:10:00", "reason", "Verificación de marcación");
        mvc.perform(body(put("/api/asistencias/{id}/corregir", id).session(sellerSession).with(csrf()), correction))
                .andExpect(status().isForbidden());
        mvc.perform(body(put("/api/asistencias/{id}/corregir", id).session(adminSession).with(csrf()),
                Map.of("entry", "2026-11-25T08:00:00", "exit", "2026-11-25T18:00:00", "reason", "Salida futura")))
                .andExpect(status().isBadRequest());
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(body(put("/api/asistencias/{id}/corregir", id).session(adminSession).with(csrf()), correction))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.lateMinutes").value(0))
                    .andExpect(jsonPath("$.additionalMinutes").value(10)).andExpect(jsonPath("$.approvedMinutes").value(10));
        }
        mvc.perform(get("/api/asistencias/{id}/auditoria", id).session(sellerSession)).andExpect(status().isForbidden());
        mvc.perform(get("/api/asistencias/{id}/auditoria", id).session(adminSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].administratorName").value(admin.username()))
                .andExpect(jsonPath("$[0].reason").value("Verificación de marcación"))
                .andExpect(jsonPath("$[0].before.approvedMinutes").value(25))
                .andExpect(jsonPath("$[0].after.approvedMinutes").value(10))
                .andExpect(jsonPath("$[1].action").value("APROBACION_EXTRA"));
        mvc.perform(get("/api/asistencias").session(sellerSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].exit").value("2026-11-25T17:10:00"));
    }

    @Test
    void persistsProductCrudAndValidatesQuantity() throws Exception {
        var session = login(admin);
        var product = createProduct(session);
        long id = product.get("id").asLong();
        mvc.perform(body(post("/api/productos").session(session).with(csrf()), product("-1.00", true)))
                .andExpect(status().isBadRequest());
        mvc.perform(body(put("/api/productos/{id}", id).session(session).with(csrf()), product("9.00", false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.quantity").value(10));
        mvc.perform(get("/api/productos").session(session).param("q", tag))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].price").value(9.0));
        mvc.perform(body(post("/api/inventario/{id}/ajustes", id).session(session).with(csrf()), Map.of("delta", -11, "note", "Prueba")))
                .andExpect(status().isConflict());
        mvc.perform(body(post("/api/inventario/{id}/ajustes", id).session(session).with(csrf()), Map.of("delta", -2, "note", "Recuento")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.quantity").value(8));
        mvc.perform(get("/api/productos").session(session).param("stock", "activo").param("q", tag))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void reservesOnlyOnceAndReturnsUnitsOnCancellation() throws Exception {
        long product = createProduct(login(admin)).get("id").asLong();
        var session = login(seller);
        mvc.perform(body(post("/api/pedidos").session(session).with(csrf()), order(product, 11)))
                .andExpect(status().isConflict());
        mvc.perform(body(post("/api/pedidos").session(session).with(csrf()), order(product, -1)))
                .andExpect(status().isBadRequest());
        long orderId = createOrder(session, product, 4);
        assertEquals(6, store.inventory(product, false).orElseThrow().quantity());
        mvc.perform(body(put("/api/pedidos/{id}/estado", orderId).session(session).with(csrf()), Map.of("status", "CANCELADO")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.history.length()").value(2));
        mvc.perform(body(put("/api/pedidos/{id}/estado", orderId).session(session).with(csrf()), Map.of("status", "CANCELADO")))
                .andExpect(status().isConflict());
        assertEquals(10, store.inventory(product, false).orElseThrow().quantity());
        mvc.perform(get("/api/inventario/movimientos").session(login(admin)).param("productId", Long.toString(product)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].type").value("DEVOLUCION"));
    }

    @Test
    void concurrentRetriesWithTheSameKeyReserveOnlyOnce() throws Exception {
        long product = createProduct(login(admin)).get("id").asLong();
        String key = UUID.randomUUID().toString();
        var threads = Executors.newFixedThreadPool(2);
        var first = login(seller);
        var second = login(seller);
        try {
            Callable<Long> request = () -> postWithKey(first, product, key);
            var attempts = threads.invokeAll(List.of(request, () -> postWithKey(second, product, key)), 10, TimeUnit.SECONDS);
            assertEquals(attempts.get(0).get(), attempts.get(1).get());
            assertEquals(7, store.inventory(product, false).orElseThrow().quantity());
            mvc.perform(body(post("/api/pedidos").session(first).with(csrf()).header("Idempotency-Key", key), order(product, 4)))
                    .andExpect(status().isConflict());
        } finally {
            threads.shutdownNow();
        }
    }

    private long postWithKey(MockHttpSession session, long product, String key) throws Exception {
        return parse(mvc.perform(body(post("/api/pedidos").session(session).with(csrf())
                .header("Idempotency-Key", key), order(product, 3)))
                .andExpect(status().isCreated()).andReturn()).get("order").get("id").asLong();
    }

    @Test
    void concurrentWarehouseWorkersCannotBothAcceptAnOrder() throws Exception {
        var sellerSession = login(seller);
        long product = createProduct(login(admin)).get("id").asLong();
        long orderId = createOrder(sellerSession, product, 2);
        var firstSession = login(picker);
        var secondSession = login(secondPicker);
        var threads = Executors.newFixedThreadPool(2);
        try {
            var attempts = threads.invokeAll(List.<Callable<Integer>>of(
                    () -> accept(firstSession, orderId), () -> accept(secondSession, orderId)), 10, TimeUnit.SECONDS);
            int first = attempts.get(0).get();
            int second = attempts.get(1).get();
            assertTrue((first == 200 && second == 409) || (first == 409 && second == 200), first + " / " + second);
            var winner = first == 200 ? firstSession : secondSession;
            mvc.perform(body(put("/api/pedidos/{id}/estado", orderId).session(winner).with(csrf()), Map.of("status", "EN_PREPARACION")))
                    .andExpect(status().isOk());
            mvc.perform(body(put("/api/pedidos/{id}/estado", orderId).session(winner).with(csrf()), Map.of("status", "LISTO")))
                    .andExpect(status().isOk());
            mvc.perform(body(put("/api/pedidos/{id}/estado", orderId).session(winner).with(csrf()), Map.of("status", "ENTREGADO")))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/notificaciones").session(sellerSession))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0].notification.type").value("PEDIDO_LISTO"))
                    .andExpect(jsonPath("$[0].notification.orderId").value(orderId));
            mvc.perform(body(put("/api/pedidos/{id}/estado", orderId).session(sellerSession).with(csrf()), Map.of("status", "ENTREGADO")))
                    .andExpect(status().isConflict());
            mvc.perform(body(post("/api/pedidos/{id}/pago", orderId).session(sellerSession).with(csrf()),
                    Map.of("method", "YAPE", "confirmed", false))).andExpect(status().isBadRequest());
            mvc.perform(body(post("/api/pedidos/{id}/pago", orderId).session(sellerSession).with(csrf()),
                    Map.of("method", "YAPE", "confirmed", true))).andExpect(status().isCreated());
            mvc.perform(body(put("/api/pedidos/{id}/estado", orderId).session(sellerSession).with(csrf()), Map.of("status", "ENTREGADO")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.history.length()").value(5));
            assertEquals(8, store.inventory(product, false).orElseThrow().quantity());
        } finally {
            threads.shutdownNow();
        }
    }

    @Test
    void tracksAttendanceReportsAndPerUserNotifications() throws Exception {
        var first = login(picker);
        var administrator = login(admin);
        mvc.perform(body(post("/api/asistencias/entradas").session(first).with(csrf()), Map.of("workerId", secondPicker.workerId())))
                .andExpect(status().isForbidden());
        var entry = parse(mvc.perform(body(post("/api/asistencias/entradas").session(first).with(csrf()), Map.of()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.lateMinutes").value(25)).andReturn());
        mvc.perform(body(post("/api/asistencias/entradas").session(first).with(csrf()), Map.of())).andExpect(status().isConflict());
        TIME.set(Instant.parse("2026-11-25T22:35:00Z"));
        mvc.perform(body(post("/api/asistencias/salidas").session(first).with(csrf()), Map.of()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.additionalMinutes").value(35))
                .andExpect(jsonPath("$.approvedMinutes").value(0));
        mvc.perform(body(put("/api/asistencias/{id}/aprobar", entry.get("id").asLong()).session(administrator).with(csrf()), Map.of("minutes", 25)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.approvedMinutes").value(25));
        mvc.perform(get("/api/asistencias").session(first)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        var absent = login(secondPicker);
        mvc.perform(get("/api/asistencias").session(absent)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/asistencias/faltas").session(absent).param("from", "2026-11-25").param("to", "2026-11-25"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].workerId").value(secondPicker.workerId()));
        mvc.perform(get("/api/asistencias/faltas").session(first).param("from", "2026-11-25").param("to", "2026-11-25"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/reportes/resumen").session(administrator).param("from", "2026-11-25").param("to", "2026-11-25"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attendance.approvedMinutes").isNumber());
        long order = createOrder(login(seller), createProduct(administrator).get("id").asLong(), 2);
        mvc.perform(get("/api/notificaciones").session(administrator)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].notification.orderId").value(order)).andExpect(jsonPath("$[0].read").value(false));
        mvc.perform(post("/api/notificaciones/leer").session(administrator).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/notificaciones").session(administrator)).andExpect(jsonPath("$[0].read").value(true));
        mvc.perform(get("/api/notificaciones").session(first)).andExpect(jsonPath("$[0].read").value(false));
    }

    @Test
    void catalogFindsBarcodeAndCashPaymentIsIdempotentAndIndependentFromStock() throws Exception {
        var administrator = login(admin);
        var vendor = login(seller);
        var warehouse = login(picker);
        var productBody = new java.util.HashMap<>(product("8.25", true));
        String barcode = "000" + tag;
        productBody.put("barcode", barcode);
        long productId = parse(mvc.perform(body(post("/api/productos").session(administrator).with(csrf()), productBody))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.barcode").value(barcode)).andReturn()).get("id").asLong();
        mvc.perform(body(put("/api/productos/{id}", productId).session(administrator).with(csrf()), product("8.25", true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.barcode").value(barcode));
        mvc.perform(get("/api/productos").session(vendor).param("q", barcode)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(productId));
        long order = createOrder(vendor, productId, 3);
        mvc.perform(get("/api/pedidos/{id}/pago", order).session(warehouse)).andExpect(status().isForbidden());
        mvc.perform(get("/api/pagos").session(warehouse)).andExpect(status().isForbidden());
        mvc.perform(get("/api/pedidos/{id}/pago", order).session(vendor)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDIENTE"));
        mvc.perform(body(post("/api/pedidos/{id}/pago", order).session(vendor).with(csrf()),
                Map.of("method", "EFECTIVO", "received", 24))).andExpect(status().isBadRequest());
        long expectedId = -1;
        for (int attempt = 0; attempt < 2; attempt++) {
            var paid = parse(mvc.perform(body(post("/api/pedidos/{id}/pago", order).session(vendor).with(csrf()),
                    Map.of("method", "EFECTIVO", "received", 30))).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.payment.orderId").value(order)).andExpect(jsonPath("$.payment.amount").value(24.75))
                    .andExpect(jsonPath("$.payment.cashChange").value(5.25)).andReturn());
            long paidId = paid.get("payment").get("id").asLong();
            if (attempt == 0) expectedId = paidId;
            else assertEquals(expectedId, paidId);
        }
        mvc.perform(body(post("/api/pedidos/{id}/pago", order).session(vendor).with(csrf()),
                Map.of("method", "PLIN", "confirmed", true))).andExpect(status().isConflict());
        mvc.perform(get("/api/pagos").session(vendor)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].status").value("PAGADO"));
        assertEquals(7, store.inventory(productId, false).orElseThrow().quantity());
        assertEquals(Status.PENDIENTE, store.find(Order.class, order).orElseThrow().status());

        mvc.perform(body(put("/api/pedidos/{id}/estado", order).session(vendor).with(csrf()), Map.of("status", "CANCELADO")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/pedidos/{id}/pago", order).session(vendor)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANULADO")).andExpect(jsonPath("$.payment.voidedBy").value(seller.id()));
        assertEquals(10, store.inventory(productId, false).orElseThrow().quantity());
        mvc.perform(body(post("/api/pedidos/{id}/pago", order).session(vendor).with(csrf()),
                Map.of("method", "EFECTIVO", "received", 30))).andExpect(status().isConflict());
    }

    @Test
    void warehouseStockAlertsAndIncidentsUseRealInventoryAndEnforceRoles() throws Exception {
        var administrator = login(admin);
        var vendor = login(seller);
        var warehouse = login(picker);
        var otherWarehouse = login(secondPicker);
        long productId = createProduct(administrator).get("id").asLong();
        mvc.perform(get("/api/productos").session(warehouse).param("q", tag).param("stock", "bajo"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        mvc.perform(body(post("/api/inventario/{id}/ajustes", productId).session(administrator).with(csrf()),
                Map.of("delta", -10, "note", "Recuento para la prueba"))).andExpect(status().isCreated());
        mvc.perform(get("/api/productos").session(warehouse).param("q", tag).param("stock", "bajo"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].quantity").value(0))
                .andExpect(jsonPath("$[0].minStock").value(2)).andExpect(jsonPath("$[0].locationId").value(location.id()));

        var report = Map.of("productId", productId, "type", "PRODUCTO_AGOTADO", "observation", "Estante vacío", "requestKey", UUID.randomUUID().toString());
        long incident = parse(mvc.perform(body(post("/api/incidencias").session(warehouse).with(csrf()), report))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDIENTE")).andReturn()).get("id").asLong();
        mvc.perform(body(post("/api/incidencias").session(warehouse).with(csrf()), report))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(incident));
        mvc.perform(get("/api/incidencias").session(warehouse)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/incidencias").session(otherWarehouse)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/incidencias").session(vendor)).andExpect(status().isForbidden());
        mvc.perform(body(put("/api/incidencias/{id}", incident).session(warehouse).with(csrf()),
                Map.of("status", "RESUELTA", "note", "Revisado"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/notificaciones").session(administrator)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].notification.type").value("NUEVA_INCIDENCIA"));

        mvc.perform(body(put("/api/incidencias/{id}", incident).session(administrator).with(csrf()),
                Map.of("status", "EN_REVISION", "note", "Validando ubicación"))).andExpect(status().isOk());
        mvc.perform(body(put("/api/incidencias/{id}", incident).session(administrator).with(csrf()),
                Map.of("status", "RESUELTA", "note", "Conteo comprobado"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewer").value(admin.username())).andExpect(jsonPath("$.reviewNote").value("Conteo comprobado"));
        mvc.perform(get("/api/incidencias").session(warehouse)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("RESUELTA"));
        mvc.perform(body(post("/api/inventario/{id}/ajustes", productId).session(warehouse).with(csrf()),
                Map.of("delta", 1, "note", "No autorizado"))).andExpect(status().isForbidden());
        assertEquals(0, store.inventory(productId, false).orElseThrow().quantity());
    }

    private int accept(MockHttpSession session, long id) throws Exception {
        return mvc.perform(body(post("/api/pedidos/{id}/aceptar", id).session(session).with(csrf()), Map.of()))
                .andReturn().getResponse().getStatus();
    }

    private MockHttpSession login(User user) throws Exception {
        var result = mvc.perform(body(post("/api/auth/login").with(csrf()), Map.of("username", user.username(), "password", PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private long createOrder(MockHttpSession session, long productId, int quantity) throws Exception {
        var result = mvc.perform(body(post("/api/pedidos").session(session).with(csrf()), order(productId, quantity)))
                .andExpect(status().isCreated()).andReturn();
        return parse(result).get("order").get("id").asLong();
    }

    private JsonNode createProduct(MockHttpSession session) throws Exception {
        return parse(mvc.perform(body(post("/api/productos").session(session).with(csrf()), product("8.25", true)))
                .andExpect(status().isCreated()).andReturn());
    }

    private Map<String, Object> product(String price, boolean active) {
        return Map.of("sku", "SKU-" + tag, "name", "Producto " + tag, "price", price, "minStock", 2,
                "active", active, "initialStock", 10, "categoryId", category.id(), "locationId", location.id());
    }

    private Map<String, Object> order(long product, int quantity) {
        return Map.of("customerId", customer.id(), "items", List.of(Map.of("productId", product, "quantity", quantity)));
    }

    private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder builder, Object payload) throws Exception {
        return builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload));
    }

    private JsonNode parse(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }
}
