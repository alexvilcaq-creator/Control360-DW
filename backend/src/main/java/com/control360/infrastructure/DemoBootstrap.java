package com.control360.infrastructure;

import com.control360.application.Operations;
import com.control360.application.Store;
import com.control360.domain.Models.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class DemoBootstrap implements ApplicationRunner {
    private final Store store;
    private final Operations operations;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final String adminName;
    private final String adminPassword;
    private final String sellerPassword;
    private final String warehousePassword;
    private final boolean demo;

    public DemoBootstrap(Store store, Operations operations, PasswordEncoder encoder, Clock clock,
                         @Value("${app.bootstrap.username}") String adminName,
                         @Value("${app.bootstrap.password}") String adminPassword,
                         @Value("${app.bootstrap.seller-password}") String sellerPassword,
                         @Value("${app.bootstrap.warehouse-password}") String warehousePassword,
                         @Value("${app.bootstrap.demo}") boolean demo) {
        this.store = store;
        this.operations = operations;
        this.encoder = encoder;
        this.clock = clock;
        this.adminName = adminName;
        this.adminPassword = adminPassword;
        this.sellerPassword = sellerPassword;
        this.warehousePassword = warehousePassword;
        this.demo = demo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminName.isBlank() && adminPassword.isBlank() && !demo) return;
        if (!adminName.matches("[A-Za-z0-9_.-]{3,80}"))
            throw new IllegalStateException("Configura ADMIN_USERNAME con entre 3 y 80 caracteres válidos");
        store.write(() -> {
            User administrator = store.user(adminName).orElseGet(() ->
                    store.save(new User(null, adminName, encode(adminPassword, "ADMIN_PASSWORD"), Role.ADMIN, true, null)));
            if (administrator.role() != Role.ADMIN)
                throw new IllegalStateException("ADMIN_USERNAME debe identificar una cuenta de administrador");
            if (demo && !administrator.username().equals("demo_vendedor") && !administrator.username().equals("demo_almacen")) {
                if (store.all(Category.class).isEmpty() && store.all(Worker.class).isEmpty()) seed(administrator);
                var worker = store.all(Worker.class).stream().filter(w -> w.document().equals("DEMO-01")).findFirst();
                if (!sellerPassword.isBlank() && store.user("demo_vendedor").isEmpty())
                    store.save(new User(null, "demo_vendedor", encode(sellerPassword, "DEMO_SELLER_PASSWORD"), Role.VENDEDOR, true, null));
                if (worker.isPresent() && !warehousePassword.isBlank() && store.user("demo_almacen").isEmpty())
                    store.save(new User(null, "demo_almacen", encode(warehousePassword, "DEMO_WAREHOUSE_PASSWORD"),
                            Role.ALMACEN, true, worker.get().id()));
            }
            return true;
        });
    }

    private String encode(String password, String setting) {
        if (password.isBlank() || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalStateException(setting + " requiere al menos 12 caracteres y como máximo 72 bytes");
        return encoder.encode(password);
    }

    private void seed(User admin) {
        LocalDate date = LocalDate.now(clock);
        LocalDateTime time = LocalDateTime.now(clock);
        List<Category> categories = List.of("Bebidas", "Abarrotes", "Limpieza", "Hogar").stream()
                .map(name -> store.save(new Category(null, name))).toList();
        List<Location> locations = new ArrayList<>();
        for (String zone : List.of("A", "B", "C")) {
            for (int aisle = 1; aisle <= 3; aisle++)
                locations.add(store.save(new Location(null, zone, String.valueOf(aisle), "1")));
        }
        List<Customer> customers = new ArrayList<>();
        for (String name : List.of("Carlos Ramos", "Lucía Torres", "Diego Huamán", "Ana Quispe", "Renzo Soto")) {
            customers.add(store.save(new Customer(null, name, "CLIENTE-DEMO-" + (customers.size() + 1), null)));
        }

        Shift shift = store.save(new Shift(null, "Turno general", LocalTime.MIDNIGHT, LocalTime.of(23, 59), 15, "1,2,3,4,5,6,7"));
        Shift morning = store.save(new Shift(null, "Turno mañana", LocalTime.of(6, 0), LocalTime.of(14, 0), 10, "1,2,3,4,5"));
        List<String> workers = List.of("Mateo Vega", "Julia Pérez", "Bruno Díaz", "María Rojas", "Elena Flores",
                "Luis García", "Sara Ruiz", "Pablo Silva", "Valeria León", "Andrés Cruz");
        List<Worker> team = new ArrayList<>();
        for (int i = 0; i < workers.size(); i++) {
            String[] name = workers.get(i).split(" ");
            team.add(store.save(new Worker(null, name[0], name[1], "DEMO-%02d".formatted(i + 1),
                    "equipo" + (i + 1) + "@example.com", i < 8 ? "Operario de almacén" : "Auxiliar", true, shift.id(), date.minusDays(1))));
            if (i > 0 && i < 9) {
                store.save(new Attendance(null, team.get(i).id(), shift.id(), date, date.atTime(0, i * 2),
                        null, date.atTime(23, 59), i == 8 ? 16 : 0, 0, 0));
                LocalDate previous = date.minusDays(1);
                store.save(new Attendance(null, team.get(i).id(), morning.id(), previous, previous.atTime(6, 0),
                        previous.atTime(14, 30), previous.atTime(14, 0), 0, 30, i <= 3 ? 30 : 0));
            }
        }
        record Sample(String sku, String name, String price, int min, int stock, int category, int location) {}
        List<Sample> samples = List.of(
                new Sample("BEB-001", "Agua mineral 600 ml", "2.50", 20, 164, 0, 0),
                new Sample("BEB-002", "Gaseosa 1.5 L", "7.50", 20, 4, 0, 0),
                new Sample("BEB-003", "Jugo de naranja 1 L", "8.50", 10, 73, 0, 1),
                new Sample("ABA-001", "Arroz extra 1 kg", "6.00", 20, 118, 1, 2),
                new Sample("ABA-002", "Aceite vegetal 1 L", "11.50", 10, 77, 1, 3),
                new Sample("ABA-003", "Azúcar rubia 1 kg", "5.00", 10, 8, 1, 3),
                new Sample("LIM-001", "Jabón líquido 1 L", "12.00", 10, 2, 2, 4),
                new Sample("LIM-002", "Detergente en polvo 1 kg", "16.00", 15, 95, 2, 5),
                new Sample("LIM-003", "Desinfectante 900 ml", "9.00", 10, 7, 2, 4),
                new Sample("HOG-001", "Papel higiénico x 4", "8.00", 12, 3, 3, 6),
                new Sample("HOG-002", "Toallas de cocina x 2", "10.00", 10, 84, 3, 7),
                new Sample("HOG-003", "Bolsas multiuso x 20", "6.50", 10, 35, 3, 8));
        List<ProductView> products = samples.stream().map(p ->
                operations.saveProduct(admin, new Product(null, p.sku(), p.name(), new BigDecimal(p.price()), p.min(),
                        true, categories.get(p.category()).id(), locations.get(p.location()).id()), p.stock())).toList();
        int[] stocked = {0, 2, 3, 4, 7, 10, 11};
        Status[] states = {Status.ENTREGADO, Status.LISTO, Status.PENDIENTE, Status.ACEPTADO, Status.EN_PREPARACION};
        for (int i = 0; i < 24; i++) {
            ProductView product = products.get(stocked[i % stocked.length]);
            LocalDateTime created = time.minusMinutes((24L - i) * 10);
            if (created.toLocalDate().isBefore(date)) created = date.atStartOfDay().plusNanos(i);
            var demoTime = new Operations(store, Clock.fixed(created.atZone(clock.getZone()).toInstant(), clock.getZone()));
            Order order = demoTime.createOrder(admin, customers.get(i % customers.size()).id(),
                    List.of(new LineRequest(product.id(), 4 + i % 7))).order();
            Status goal = i < 11 ? Status.PENDIENTE : states[i % states.length];
            if (goal == Status.PENDIENTE) continue;
            var updated = demoTime.transition(admin, order.id(), Status.ACEPTADO, team.get(i % 7).id()).order();
            for (Status next : List.of(Status.EN_PREPARACION, Status.LISTO, Status.ENTREGADO)) {
                if (updated.status() == goal) break;
                updated = demoTime.transition(admin, order.id(), next).order();
            }
        }
    }
}
