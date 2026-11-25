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
            seller = store.save(new User(null, "seller-" + tag, hash, Role.VENDEDOR, true, null));
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
