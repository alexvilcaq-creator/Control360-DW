package com.control360.application;

import com.control360.domain.Models.*;
import com.control360.domain.RuleViolation;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.control360.domain.RuleViolation.*;

public final class Operations implements OrderUseCases, AttendanceUseCases {
    private final Store store;
    private final Clock clock;

    public Operations(Store store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Override
    public LocalDate today() { return LocalDate.now(clock); }
    private LocalDateTime now() { return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS); }

    private <T> T required(Class<T> type, long id) {
        return store.find(type, id).orElseThrow(() -> missing("No se encontró el registro solicitado"));
    }

    public <T> List<T> list(Class<T> type) { return store.all(type); }

    public Customer createCustomer(Customer customer) {
        return store.write(() -> store.save(customer));
    }
    public Category saveCategory(Category category) {
        return store.write(() -> store.save(category));
    }
    public Location saveLocation(Location location) {
        return store.write(() -> store.save(location));
    }
    public Shift saveShift(Shift shift) {
        if (shift.startsAt().equals(shift.endsAt())) throw invalid("La entrada y salida programadas deben ser distintas");
        if (new HashSet<>(List.of(shift.days().split(","))).size() != shift.days().split(",").length)
            throw invalid("Los días del turno deben ser únicos");
        return store.write(() -> store.save(shift));
    }

    public Worker saveWorker(Worker worker) {
        return store.write(() -> {
            LocalDate startDate = worker.id() == null ? today() : store.lockWorker(worker.id())
                    .orElseThrow(() -> missing("Trabajador inexistente")).startDate();
            if (worker.shiftId() != null) required(Shift.class, worker.shiftId());
            return store.save(new Worker(worker.id(), worker.firstName(), worker.lastName(), worker.document(),
                    worker.email(), worker.position(), worker.active(), worker.shiftId(), startDate));
        });
    }

    public List<WorkerView> workers() {
        return store.read(() -> {
            Map<Long, Shift> shifts = byId(store.all(Shift.class), Shift::id);
            LocalDateTime time = now();
            return store.all(Worker.class).stream().map(worker -> {
                Shift shift = shifts.get(worker.shiftId());
                boolean available = worker.active() && shift != null && inShift(shift, time);
                return new WorkerView(worker, available);
            }).toList();
        });
    }

    public UserProfile saveUser(User user, User actor) {
        requireAdministrator(actor);
        return store.write(() -> {
            User old = user.id() == null ? null : store.lockUser(user.id()).orElseThrow(() -> missing("Usuario inexistente"));
            if (old != null && old.id().equals(actor.id()) && (!user.active() || user.role() != Role.ADMIN))
                throw conflict("No puedes quitar el acceso de tu propia cuenta de administrador");
            boolean existingUnlinkedSeller = old != null && old.role() == Role.VENDEDOR && old.workerId() == null
                    && user.role() == Role.VENDEDOR;
            if (user.role() != Role.ADMIN && user.workerId() == null && !existingUnlinkedSeller)
                throw invalid("Vincula un trabajador para que la cuenta pueda registrar su asistencia");
            if (user.workerId() != null) required(Worker.class, user.workerId());
            String hash = user.passwordHash() != null ? user.passwordHash() : old == null ? null : old.passwordHash();
            if (hash == null) throw invalid("La contraseña es obligatoria para cuentas nuevas");
            return UserProfile.from(store.save(new User(user.id(), user.username(), hash, user.role(), user.active(), user.workerId())));
        });
    }

    public List<UserProfile> users() { return store.all(User.class).stream().map(UserProfile::from).toList(); }

    public List<ProductView> products(String query, Long category, String stock) {
        return store.read(() -> {
            String search = query == null ? "" : normalizeSearch(query).strip();
            return productViews().stream()
                    .filter(p -> normalizeSearch(p.name()).contains(search)
                            || normalizeSearch(p.sku()).contains(search)
                            || (p.barcode() != null && normalizeSearch(p.barcode()).contains(search)))
                    .filter(p -> category == null || category.equals(p.categoryId()))
                    .filter(p -> switch (stock == null ? "" : stock) {
                        case "bajo" -> p.lowStock();
                        case "agotado" -> p.active() && p.quantity() == 0;
                        case "activo" -> p.active();
                        default -> true;
                    }).toList();
        });
    }

    private String normalizeSearch(String value) {
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private List<ProductView> productViews() {
        Map<Long, Inventory> inventory = byId(store.all(Inventory.class), Inventory::productId);
        return store.all(Product.class).stream().map(product ->
                ProductView.of(product, inventory.get(product.id()).quantity())).toList();
    }

    public ProductView saveProduct(User actor, Product value, int initialStock) {
        return store.write(() -> {
            required(Category.class, value.categoryId());
            required(Location.class, value.locationId());
            if (value.id() != null) {
                Inventory stock = lockedStock(value.id());
                Product previous = required(Product.class, value.id());
                String barcode = value.barcode() == null ? previous.barcode() : optionalBarcode(value.barcode());
                Product saved = store.save(value.withBarcode(barcode));
                if ((!previous.active() || stock.quantity() > previous.minStock()) && saved.active()
                        && stock.quantity() <= saved.minStock()) stockNotice(saved, stock.quantity());
                return ProductView.of(saved, stock.quantity());
            }
            Product product = store.save(value.withBarcode(optionalBarcode(value.barcode())));
            Inventory stock = store.save(new Inventory(null, product.id(), 0));
            if (initialStock != 0)
                stock = changeStock(product, stock, initialStock, MovementType.AJUSTE, actor, null, "Existencia inicial");
            if (product.active() && initialStock <= product.minStock()) stockNotice(product, initialStock);
            return ProductView.of(product, stock.quantity());
        });
    }

    private String optionalBarcode(String barcode) {
        return barcode == null || barcode.isBlank() ? null : barcode.strip();
    }

    public ProductView adjustStock(User actor, long productId, int delta, String note) {
        if (delta == 0) throw invalid("El movimiento debe cambiar la existencia");
        return store.write(() -> {
            Inventory stock = lockedStock(productId);
            Product product = required(Product.class, productId);
            return ProductView.of(product, changeStock(product, stock, delta, MovementType.AJUSTE, actor, null, note).quantity());
        });
    }

    public List<Movement> movements(Long productId) {
        return store.all(Movement.class).stream()
                .filter(m -> productId == null || productId.equals(m.productId()))
                .sorted(Comparator.comparing(Movement::id).reversed()).toList();
    }

    private Inventory lockedStock(long productId) {
        return store.inventory(productId, true).orElseThrow(() -> missing("Producto inexistente"));
    }

    private Inventory changeStock(Product product, Inventory stock, int delta, MovementType type, User actor,
                                  Long orderId, String note) {
        long updated = (long) stock.quantity() + delta;
        if (updated < 0) throw conflict("Existencia insuficiente para " + product.name());
        if (updated > Integer.MAX_VALUE) throw invalid("La existencia supera el máximo permitido");
        int balance = (int) updated;
        store.save(new Movement(null, product.id(), orderId, actor.id(), delta, balance, type, note, now()));
        if (product.active() && stock.quantity() > product.minStock() && balance <= product.minStock()) {
            stockNotice(product, balance);
        }
        return store.save(new Inventory(stock.id(), product.id(), balance));
    }

    private void stockNotice(Product product, int quantity) {
        notify("STOCK_BAJO", "Stock bajo", product.name() + " · " + quantity + " unidades disponibles", null);
    }

    private void notify(String type, String title, String message, Long orderId) {
        store.save(new Notification(null, type, title, message, orderId, now()));
    }

    public OrderView createOrder(User actor, long customerId, List<LineRequest> lines) {
        return createOrder(actor, customerId, lines, null);
    }

    @Override
    public OrderView createOrder(User actor, long customerId, List<LineRequest> lines, String requestKey) {
        if (actor.role() == Role.ALMACEN) throw forbidden("Tu rol no puede crear pedidos");
        if (lines == null || lines.isEmpty() || lines.size() > 100) throw invalid("Incluye de 1 a 100 productos");
        // Un orden de bloqueo compartido evita interbloqueos entre reservas y devoluciones.
        Map<Long, Integer> requested = new TreeMap<>();
        for (LineRequest line : lines) {
            if (line.productId() == null || line.quantity() <= 0 || line.quantity() > 1_000_000)
                throw invalid("Indica un producto y una cantidad válida");
            if (requested.putIfAbsent(line.productId(), line.quantity()) != null)
                throw invalid("Cada producto puede aparecer una sola vez");
        }
        if (requestKey != null && !requestKey.matches("[A-Za-z0-9_-]{16,80}"))
            throw invalid("La clave de idempotencia es inválida");
        return store.write(() -> {
            String payloadFingerprint = requestKey == null ? null : fingerprint(customerId, requested);
            OrderRequest request = requestKey == null ? null
                    : store.reserveOrderRequest(actor.id(), requestKey, payloadFingerprint);
            if (request != null) {
                if (!request.fingerprint().equals(payloadFingerprint))
                    throw conflict("La clave de idempotencia ya se usó con otro pedido");
                if (request.orderId() != null) return view(required(Order.class, request.orderId()), true);
            }
            required(Customer.class, customerId);
            record Reservation(Product product, Inventory stock, int quantity) {}
            List<Reservation> reservations = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            for (var item : requested.entrySet()) {
                Inventory stock = lockedStock(item.getKey());
                Product product = required(Product.class, item.getKey());
                if (!product.active()) throw conflict("El producto " + product.name() + " está desactivado");
                if (stock.quantity() < item.getValue()) throw conflict("Existencia insuficiente para " + product.name());
                total = total.add(product.price().multiply(BigDecimal.valueOf(item.getValue())));
                reservations.add(new Reservation(product, stock, item.getValue()));
            }
            if (total.precision() > 18) throw invalid("El total supera el máximo permitido");
            Order order = store.save(new Order(null, customerId, actor.id(), null, Status.PENDIENTE, total, now()));
            for (Reservation r : reservations) {
                store.save(new Line(null, order.id(), r.product().id(), r.quantity(), r.product().price()));
                changeStock(r.product(), r.stock(), -r.quantity(), MovementType.RESERVA, actor, order.id(), "Reserva del pedido");
            }
            if (request != null) store.save(new OrderRequest(request.id(), actor.id(), requestKey, request.fingerprint(), order.id()));
            addHistory(actor, order);
            notify("PEDIDO_NUEVO", "Pedido nuevo", code(order.id()) + " está pendiente de preparación", order.id());
            return view(order, true);
        });
    }

    private String fingerprint(long customerId, Map<Long, Integer> quantities) {
        try {
            byte[] content = (customerId + ":" + quantities).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 no está disponible", error);
        }
    }

    public OrderView transition(User actor, long orderId, Status next) {
        return transition(actor, orderId, next, null);
    }

    @Override
    public OrderView transition(User actor, long orderId, Status next, Long assignedWorkerId) {
        return store.write(() -> {
            Order order = store.lockOrder(orderId).orElseThrow(() -> missing("Pedido inexistente"));
            if (!visibleTo(actor, order)) {
                if (actor.role() == Role.ALMACEN && next == Status.ACEPTADO)
                    throw conflict("Otro trabajador ya aceptó este pedido");
                throw forbidden("No tienes permiso para modificar este pedido");
            }
            boolean roleAllowed = switch (actor.role()) {
                case ADMIN -> true;
                case VENDEDOR -> next == Status.ENTREGADO
                        || (next == Status.CANCELADO && (order.status() == Status.PENDIENTE || order.status() == Status.CANCELADO));
                case ALMACEN -> next == Status.ACEPTADO || next == Status.EN_PREPARACION || next == Status.LISTO;
            };
            if (!roleAllowed) throw forbidden("Tu rol no puede aplicar ese estado");
            if (!order.status().allows(next)) throw conflict("El cambio no está permitido desde " + order.status());
            if (assignedWorkerId != null && (actor.role() != Role.ADMIN || next != Status.ACEPTADO))
                throw forbidden("Solo el administrador puede asignar un trabajador al aceptar");
            Long workerId = order.warehouseWorkerId();
            if (next == Status.ACEPTADO) {
                workerId = actor.role() == Role.ALMACEN ? actor.workerId() : assignedWorkerId;
                if (workerId == null) throw invalid("Selecciona un trabajador de almacén disponible");
                Worker worker = store.lockWorker(workerId).orElseThrow(() -> missing("Trabajador inexistente"));
                if (!worker.active() || worker.shiftId() == null || !inShift(required(Shift.class, worker.shiftId()), now()))
                    throw conflict("El trabajador no está disponible en su turno");
            }
            if (next == Status.ENTREGADO && store.paymentForOrder(order.id())
                    .filter(payment -> payment.status() == PaymentStatus.PAGADO).isEmpty())
                throw conflict("Registra el pago antes de entregar el pedido");
            if (next == Status.CANCELADO) {
                store.paymentForOrder(order.id()).filter(payment -> payment.status() == PaymentStatus.PAGADO)
                        .ifPresent(payment -> store.save(payment.annulled(actor.id(), now())));
                List<Line> lines = store.orderLines(order.id()).stream().sorted(Comparator.comparing(Line::productId)).toList();
                for (Line line : lines) {
                    Inventory stock = lockedStock(line.productId());
                    changeStock(required(Product.class, line.productId()), stock, line.quantity(), MovementType.DEVOLUCION,
                            actor, order.id(), "Cancelación del pedido");
                }
            }
            Order updated = store.save(new Order(order.id(), order.customerId(), order.sellerId(), workerId,
                    next, order.total(), order.createdAt()));
            addHistory(actor, updated);
            switch (next) {
                case ACEPTADO -> notify("PEDIDO_ACEPTADO", "Pedido aceptado", code(orderId) + " fue asignado al almacén", orderId);
                case LISTO -> notify("PEDIDO_LISTO", "Pedido listo", code(orderId) + " está listo para entregar", orderId);
                case ENTREGADO -> notify("PEDIDO_ENTREGADO", "Pedido entregado", code(orderId) + " finalizó", orderId);
                default -> { }
            }
            return view(updated, true);
        });
    }

    private void addHistory(User actor, Order order) {
        store.save(new History(null, order.id(), actor.id(), order.status(), now()));
    }

    private boolean visibleTo(User actor, Order order) {
        return switch (actor.role()) {
            case ADMIN -> true;
            case VENDEDOR -> order.sellerId().equals(actor.id());
            case ALMACEN -> order.status() == Status.PENDIENTE
                    || (actor.workerId() != null && actor.workerId().equals(order.warehouseWorkerId()));
        };
    }

    private List<Order> visibleOrders(User actor) {
        return store.all(Order.class).stream().filter(o -> visibleTo(actor, o))
                .sorted(Comparator.comparing(Order::id).reversed()).toList();
    }

    @Override
    public List<OrderView> orders(User actor) {
        return store.read(() -> visibleOrders(actor).stream().map(o -> view(o, false)).toList());
    }

    @Override
    public OrderView order(User actor, long id) {
        return store.read(() -> {
            Order order = required(Order.class, id);
            if (!visibleTo(actor, order)) throw forbidden("No tienes acceso a ese pedido");
            return view(order, true);
        });
    }

    private OrderView view(Order order, boolean detail) {
        String customer = required(Customer.class, order.customerId()).name();
        return new OrderView(order, customer, detail ? store.orderLines(order.id()) : List.of(),
                detail ? store.orderHistory(order.id()) : List.of());
    }

    private String code(long id) { return "PED-%04d".formatted(id); }

    private boolean scheduled(Shift shift, LocalDate date) {
        return List.of(shift.days().split(",")).contains(String.valueOf(date.getDayOfWeek().getValue()));
    }

    private LocalDate shiftDate(Shift shift, LocalDateTime time) {
        if (shift.endsAt().isBefore(shift.startsAt()) && time.toLocalTime().isBefore(shift.endsAt()))
            return time.toLocalDate().minusDays(1);
        return time.toLocalDate();
    }

    private LocalDateTime shiftEnd(Shift shift, LocalDate date) {
        return (shift.endsAt().isBefore(shift.startsAt()) ? date.plusDays(1) : date).atTime(shift.endsAt());
    }

    private boolean inShift(Shift shift, LocalDateTime time) {
        LocalDate date = shiftDate(shift, time);
        return scheduled(shift, date) && !time.isBefore(date.atTime(shift.startsAt())) && time.isBefore(shiftEnd(shift, date));
    }

    private long attendanceWorker(User actor, Long workerId) {
        Long selected = actor.role() == Role.ADMIN && workerId != null ? workerId : actor.workerId();
        if (selected == null) throw conflict("La cuenta no está vinculada a un trabajador");
        if (workerId != null && !workerId.equals(selected)) throw forbidden("Solo puedes marcar tu asistencia");
        return selected;
    }

    @Override
    public Attendance enter(User actor, Long workerId) {
        long selected = attendanceWorker(actor, workerId);
        return store.write(() -> {
            Worker worker = store.lockWorker(selected).orElseThrow(() -> missing("Trabajador inexistente"));
            if (!worker.active() || worker.shiftId() == null) throw conflict("El trabajador no tiene un turno activo");
            Shift shift = required(Shift.class, worker.shiftId());
            LocalDateTime time = now();
            LocalDate date = shiftDate(shift, time);
            if (!scheduled(shift, date) || !time.isBefore(shiftEnd(shift, date)))
                throw conflict("No hay un turno programado disponible para marcar entrada");
            boolean duplicate = store.all(Attendance.class).stream().anyMatch(a ->
                    a.workerId().equals(selected) && (a.exit() == null || a.workDate().equals(date)));
            if (duplicate) throw conflict("Ya existe una entrada para el turno o una salida pendiente");
            return store.save(new Attendance(null, selected, shift.id(), date, time, null, shiftEnd(shift, date),
                    lateMinutes(shift, date, time), 0, 0));
        });
    }

    @Override
    public Attendance leave(User actor, Long workerId) {
        long selected = attendanceWorker(actor, workerId);
        return store.write(() -> {
            store.lockWorker(selected).orElseThrow(() -> missing("Trabajador inexistente"));
            Attendance entry = store.all(Attendance.class).stream()
                    .filter(a -> a.workerId().equals(selected) && a.exit() == null).findFirst()
                    .orElseThrow(() -> conflict("No hay una entrada abierta"));
            LocalDateTime time = now();
            if (time.isBefore(entry.entry())) throw conflict("La salida no puede ser anterior a la entrada");
            int extra = additionalMinutes(entry.scheduledEnd(), time);
            return store.save(new Attendance(entry.id(), selected, entry.shiftId(), entry.workDate(), entry.entry(),
                    time, entry.scheduledEnd(), entry.lateMinutes(), extra, 0));
        });
    }

    private int lateMinutes(Shift shift, LocalDate workDate, LocalDateTime entry) {
        long minutes = ChronoUnit.MINUTES.between(workDate.atTime(shift.startsAt()), entry);
        return minutes <= shift.graceMinutes() ? 0 : Math.toIntExact(minutes);
    }

    private int additionalMinutes(LocalDateTime scheduledEnd, LocalDateTime exit) {
        if (exit == null) return 0;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, ChronoUnit.MINUTES.between(scheduledEnd, exit)));
    }

    private void requireAdministrator(User actor) {
        if (actor.role() != Role.ADMIN) throw forbidden("Esta operación requiere una cuenta de administrador");
    }

    @Override
    public AttendanceState attendanceState(User actor) {
        Long workerId = actor.workerId();
        if (workerId == null) return new AttendanceState(null, today(), null, null, null);
        return store.read(() -> {
            Worker worker = required(Worker.class, workerId);
            String shiftName = worker.shiftId() == null ? null : required(Shift.class, worker.shiftId()).name();
            List<Attendance> records = store.all(Attendance.class).stream().filter(item -> item.workerId().equals(workerId))
                    .sorted(Comparator.comparing(Attendance::entry).reversed()).toList();
            Attendance open = records.stream().filter(item -> item.exit() == null).findFirst().orElse(null);
            return new AttendanceState(workerId, today(), shiftName, open, records.isEmpty() ? null : records.get(0));
        });
    }

    @Override
    public Attendance approve(User actor, long id, int minutes) {
        requireAdministrator(actor);
        Long workerId = required(Attendance.class, id).workerId();
        return store.write(() -> {
            store.lockWorker(workerId).orElseThrow(() -> missing("Trabajador inexistente"));
            Attendance entry = required(Attendance.class, id);
            if (entry.exit() == null || minutes < 0 || minutes > entry.additionalMinutes())
                throw invalid("Aprueba únicamente minutos adicionales registrados tras la salida");
            Attendance updated = new Attendance(id, workerId, entry.shiftId(), entry.workDate(), entry.entry(),
                    entry.exit(), entry.scheduledEnd(), entry.lateMinutes(), entry.additionalMinutes(), minutes);
            return saveAttendanceChange(actor, AttendanceAction.APROBACION_EXTRA, "Revisión de horas extra", entry, updated);
        });
    }

    @Override
    public Attendance correct(User actor, long id, LocalDateTime entry, LocalDateTime exit, String reason) {
        requireAdministrator(actor);
        if (entry == null || reason == null || reason.isBlank() || reason.strip().length() > 255)
            throw invalid("Indica la hora de entrada y un motivo de hasta 255 caracteres");
        LocalDateTime correctedEntry = entry.truncatedTo(ChronoUnit.MICROS);
        LocalDateTime correctedExit = exit == null ? null : exit.truncatedTo(ChronoUnit.MICROS);
        Long workerId = required(Attendance.class, id).workerId();
        return store.write(() -> {
            store.lockWorker(workerId).orElseThrow(() -> missing("Trabajador inexistente"));
            Attendance previous = required(Attendance.class, id);
            LocalDateTime time = now();
            if (correctedEntry.isBefore(previous.workDate().atStartOfDay())
                    || !correctedEntry.isBefore(previous.scheduledEnd()) || correctedEntry.isAfter(time))
                throw invalid("La entrada debe corresponder a la jornada original y no puede ser futura");
            if (correctedExit != null && (correctedExit.isBefore(correctedEntry) || correctedExit.isAfter(time)))
                throw invalid("La salida debe estar entre la entrada y la hora actual");
            boolean overlaps = store.all(Attendance.class).stream()
                    .filter(item -> item.workerId().equals(workerId) && !item.id().equals(id))
                    .anyMatch(item -> (correctedExit == null || item.entry().isBefore(correctedExit))
                            && (item.exit() == null || correctedEntry.isBefore(item.exit())));
            if (overlaps) throw conflict("La corrección se cruza con otra jornada del trabajador");
            Shift shift = required(Shift.class, previous.shiftId());
            int extra = additionalMinutes(previous.scheduledEnd(), correctedExit);
            int late = correctedEntry.equals(previous.entry()) ? previous.lateMinutes()
                    : lateMinutes(shift, previous.workDate(), correctedEntry);
            Attendance updated = new Attendance(id, workerId, previous.shiftId(), previous.workDate(), correctedEntry,
                    correctedExit, previous.scheduledEnd(), late, extra, Math.min(previous.approvedMinutes(), extra));
            return saveAttendanceChange(actor, AttendanceAction.CORRECCION, reason.strip(), previous, updated);
        });
    }

    private Attendance saveAttendanceChange(User actor, AttendanceAction action, String reason, Attendance previous, Attendance updated) {
        if (previous.equals(updated)) return previous;
        Attendance saved = store.save(updated);
        store.save(new AttendanceAudit(null, saved.id(), actor.id(), actor.username(), action, reason, now(),
                AttendanceValues.from(previous), AttendanceValues.from(saved)));
        return saved;
    }

    @Override
    public List<AttendanceAudit> attendanceAudit(User actor, long id) {
        requireAdministrator(actor);
        required(Attendance.class, id);
        return store.all(AttendanceAudit.class).stream().filter(item -> item.attendanceId().equals(id))
                .sorted(Comparator.comparing(AttendanceAudit::id).reversed()).toList();
    }

    @Override
    public List<Attendance> attendance(User actor, LocalDate from, LocalDate to) {
        validateRange(from, to);
        return store.all(Attendance.class).stream()
                .filter(a -> actor.role() == Role.ADMIN || a.workerId().equals(actor.workerId()))
                .filter(a -> inRange(a.workDate(), from, to)).sorted(Comparator.comparing(Attendance::id).reversed()).toList();
    }

    @Override
    public List<Absence> absences(User actor, LocalDate from, LocalDate to) {
        validateRange(from, to);
        return store.read(() -> attendanceReport(actor, from, to).absences());
    }

    private record AttendanceReport(AttendanceSummary summary, long activeWorkers, List<Absence> absences) {}

    private AttendanceReport attendanceReport(User actor, LocalDate from, LocalDate to) {
        List<Worker> workers = store.all(Worker.class).stream().filter(Worker::active)
                .filter(w -> actor.role() == Role.ADMIN || w.id().equals(actor.workerId())).toList();
        Map<Long, Shift> shifts = byId(store.all(Shift.class), Shift::id);
        List<Attendance> history = attendance(actor, from, to);
        List<Absence> absences = new ArrayList<>();
        LocalDateTime time = now();
        Set<String> visits = history.stream().map(a -> a.workerId() + "/" + a.workDate()).collect(Collectors.toSet());
        long scheduled = 0;
        for (LocalDate date = from; !date.isAfter(to) && !date.isAfter(time.toLocalDate()); date = date.plusDays(1)) {
            for (Worker worker : workers) {
                Shift shift = shifts.get(worker.shiftId());
                if (date.isBefore(worker.startDate()) || shift == null || !scheduled(shift, date)) continue;
                scheduled++;
                if (!visits.contains(worker.id() + "/" + date) && !time.isBefore(shiftEnd(shift, date))) {
                    absences.add(new Absence(worker.id(), worker.firstName() + " " + worker.lastName(), date, shift.name()));
                }
            }
        }
        absences.sort(Comparator.comparing(Absence::workDate).reversed().thenComparing(Absence::worker));
        long atWork = history.stream().filter(a -> a.exit() == null && !a.entry().isAfter(time)).count();
        var summary = new AttendanceSummary(history.size(), atWork, scheduled,
                history.stream().filter(a -> a.lateMinutes() > 0).count(), absences.size(),
                history.stream().mapToLong(Attendance::approvedMinutes).sum());
        return new AttendanceReport(summary, workers.size(), absences);
    }

    private boolean inRange(LocalDate value, LocalDate from, LocalDate to) {
        return !value.isBefore(from) && !value.isAfter(to);
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from.getYear() < 1000 || to.getYear() > 9999 || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 366)
            throw invalid("Selecciona un rango válido de hasta 367 días");
    }

    public record NotificationView(Notification notification, boolean read) {}

    public List<NotificationView> notifications(User actor) {
        return store.read(() -> {
            Set<Long> orders = visibleOrders(actor).stream().map(Order::id).collect(Collectors.toSet());
            Set<Long> read = store.all(NotificationRead.class).stream().filter(n -> n.userId().equals(actor.id()))
                    .map(NotificationRead::notificationId).collect(Collectors.toSet());
            return store.all(Notification.class).stream().filter(n -> n.orderId() == null
                            ? actor.role() == Role.ADMIN || (actor.role() == Role.ALMACEN && !n.type().equals("NUEVA_INCIDENCIA"))
                            : orders.contains(n.orderId()))
                    .sorted(Comparator.comparing(Notification::id).reversed()).limit(35)
                    .map(n -> new NotificationView(n, read.contains(n.id()))).toList();
        });
    }

    public void readNotifications(User actor) {
        store.write(() -> {
            store.lockUser(actor.id()).orElseThrow(() -> missing("Cuenta inexistente"));
            for (NotificationView item : notifications(actor)) {
                if (!item.read()) store.save(new NotificationRead(null, item.notification().id(), actor.id()));
            }
            return true;
        });
    }

    public Dashboard dashboard(User actor, LocalDate from, LocalDate to) {
        validateRange(from, to);
        return store.read(() -> {
            List<Order> orders = visibleOrders(actor);
            List<Order> selected = orders.stream().filter(o -> inRange(o.createdAt().toLocalDate(), from, to)).toList();
            List<ProductView> products = productViews();
            List<ProductView> low = products.stream().filter(ProductView::lowStock)
                    .sorted(Comparator.comparingInt(ProductView::quantity)).toList();
            AttendanceReport attendance = attendanceReport(actor, from, to);
            Map<Long, Long> byCategory = new HashMap<>();
            Map<Long, String> productNames = new HashMap<>();
            for (ProductView product : products) {
                productNames.put(product.id(), product.name());
                if (product.active()) byCategory.merge(product.categoryId(), (long) product.quantity(), Long::sum);
            }
            List<Count> inventory = store.all(Category.class).stream()
                    .map(c -> new Count(c.name(), byCategory.getOrDefault(c.id(), 0L))).toList();
            Map<Status, Long> byStatus = selected.stream().collect(Collectors.groupingBy(Order::status, Collectors.counting()));
            List<Count> statuses = Arrays.stream(Status.values())
                    .map(status -> new Count(status.name(), byStatus.getOrDefault(status, 0L))).toList();
            Map<LocalDate, Long> byDate = selected.stream()
                    .collect(Collectors.groupingBy(o -> o.createdAt().toLocalDate(), Collectors.counting()));
            List<Count> dates = from.datesUntil(to.plusDays(1)).map(d -> new Count(d.toString(), byDate.getOrDefault(d, 0L))).toList();
            Set<Long> selectedIds = selected.stream().filter(o -> o.status() != Status.CANCELADO)
                    .map(Order::id).collect(Collectors.toSet());
            Map<Long, Long> popularity = new HashMap<>();
            store.all(Line.class).stream().filter(l -> selectedIds.contains(l.orderId()))
                    .forEach(l -> popularity.merge(l.productId(), (long) l.quantity(), Long::sum));
            List<Count> popular = popularity.entrySet().stream().sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                    .limit(10).map(e -> new Count(productNames.get(e.getKey()), e.getValue())).toList();
            return new Dashboard(selected.size(), orders.stream().filter(o -> o.status() == Status.PENDIENTE).count(),
                    low.size(), attendance.activeWorkers(), attendance.summary(), selected.stream().limit(5).map(o -> view(o, false)).toList(),
                    low.stream().limit(6).toList(), inventory, statuses, dates, popular);
        });
    }

    private static <T> Map<Long, T> byId(List<T> values, Function<T, Long> key) {
        return values.stream().collect(Collectors.toMap(key, Function.identity()));
    }
}
