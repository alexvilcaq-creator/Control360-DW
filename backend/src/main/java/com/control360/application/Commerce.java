package com.control360.application;

import com.control360.domain.Models.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

import static com.control360.domain.RuleViolation.*;

public final class Commerce implements CommerceUseCases {
    private final Store store;
    private final Clock clock;

    public Commerce(Store store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Override
    public PaymentView payment(User actor, long orderId) {
        requireCashier(actor);
        return store.read(() -> {
            Order order = required(Order.class, orderId);
            requireOrderOwner(actor, order);
            return view(order, store.paymentForOrder(orderId).orElse(null));
        });
    }

    @Override
    public PaymentView recordPayment(User actor, long orderId, PaymentMethod method, BigDecimal received, boolean confirmed) {
        requireCashier(actor);
        if (method == null) throw invalid("Elige un método de pago");
        if (method != PaymentMethod.EFECTIVO && (!confirmed || received != null))
            throw invalid("Confirma el pago manual y deja vacío el importe recibido");
        if (method == PaymentMethod.EFECTIVO && (received == null || received.signum() < 0
                || received.scale() > 2 || received.precision() - received.scale() > 16))
            throw invalid("Indica el efectivo recibido con hasta dos decimales");
        return store.write(() -> {
            Order order = store.lockOrder(orderId).orElseThrow(() -> missing("Pedido inexistente"));
            requireOrderOwner(actor, order);
            if (order.status() == Status.CANCELADO) throw conflict("El pedido está cancelado");
            Payment existing = store.paymentForOrder(orderId).orElse(null);
            if (existing != null) {
                if (existing.status() == PaymentStatus.PAGADO && existing.method() == method
                        && (received == null || received.compareTo(existing.cashReceived()) == 0))
                    return view(order, existing);
                throw conflict("Este pedido ya tiene un pago registrado");
            }
            if (order.status() == Status.ENTREGADO) throw conflict("Este pedido ya fue entregado");
            BigDecimal change = received == null ? null : received.subtract(order.total());
            if (change != null && change.signum() < 0)
                throw invalid("El efectivo recibido debe cubrir el total del pedido");
            Payment saved = store.save(new Payment(null, orderId, actor.id(), method, PaymentStatus.PAGADO,
                    order.total(), received, change, now(), null, null));
            return view(order, saved);
        });
    }

    @Override
    public List<PaymentView> payments(User actor) {
        requireCashier(actor);
        return store.read(() -> store.all(Payment.class).stream()
                .sorted(Comparator.comparing(Payment::id).reversed())
                .map(payment -> new PaymentOrder(payment, required(Order.class, payment.orderId())))
                .filter(item -> actor.role() == Role.ADMIN || actor.id().equals(item.order().sellerId()))
                .map(item -> view(item.order(), item.payment())).toList());
    }

    private record PaymentOrder(Payment payment, Order order) {}

    private PaymentView view(Order order, Payment payment) {
        PaymentStatus status = order.status() == Status.CANCELADO ? PaymentStatus.ANULADO
                : payment == null ? PaymentStatus.PENDIENTE : payment.status();
        return new PaymentView(order.id(), required(Customer.class, order.customerId()).name(), order.total(), status, payment);
    }

    private void requireCashier(User actor) {
        if (actor.role() == Role.ALMACEN) throw forbidden("Tu rol no tiene acceso a pagos");
    }

    private void requireOrderOwner(User actor, Order order) {
        if (actor.role() != Role.ADMIN && !actor.id().equals(order.sellerId()))
            throw forbidden("No tienes acceso a los pagos de ese pedido");
    }

    @Override
    public IncidentView report(User actor, long productId, IncidentType type, String observation, String key) {
        if (actor.role() != Role.ALMACEN) throw forbidden("Solo almacén registra incidencias");
        String text = observation.strip();
        if (!key.matches("[A-Za-z0-9_-]{16,80}")) throw invalid("Clave de registro inválida");
        return store.write(() -> {
            store.lockUser(actor.id()).orElseThrow(() -> missing("Cuenta inexistente"));
            Incident existing = store.incidentByRequest(actor.id(), key).orElse(null);
            if (existing != null) {
                if (!existing.productId().equals(productId) || existing.type() != type || !existing.observation().equals(text))
                    throw conflict("La clave de registro ya se usó para otra incidencia");
                return view(existing);
            }
            Product product = required(Product.class, productId);
            Incident saved = store.save(new Incident(null, actor.id(), productId, key, type, IncidentStatus.PENDIENTE,
                    text, now(), null, null, null));
            store.save(new Notification(null, "NUEVA_INCIDENCIA", "Incidencia de almacén", product.name(), null, now()));
            return view(saved);
        });
    }

    @Override
    public IncidentView review(User actor, long id, IncidentStatus status, String note) {
        if (actor.role() != Role.ADMIN) throw forbidden("Solo un administrador puede revisar incidencias");
        String text = note.strip();
        return store.write(() -> {
            Incident incident = store.lockIncident(id).orElseThrow(() -> missing("Incidencia inexistente"));
            if (incident.status() == status && text.equals(incident.reviewNote())) return view(incident);
            if (!incident.status().allows(status)) throw conflict("No se puede aplicar ese estado a la incidencia");
            Incident saved = store.save(new Incident(id, incident.reporterId(), incident.productId(), incident.requestKey(),
                    incident.type(), status, incident.observation(), incident.createdAt(), actor.id(), text, now()));
            return view(saved);
        });
    }

    @Override
    public List<IncidentView> incidents(User actor) {
        if (actor.role() == Role.VENDEDOR) throw forbidden("Tu rol no tiene acceso a incidencias");
        return store.read(() -> store.all(Incident.class).stream()
                .filter(incident -> actor.role() == Role.ADMIN || incident.reporterId().equals(actor.id()))
                .sorted(Comparator.comparing(Incident::id).reversed()).map(this::view).toList());
    }

    private IncidentView view(Incident incident) {
        Product product = required(Product.class, incident.productId());
        return new IncidentView(incident.id(), product.id(), product.name(), product.sku(),
                required(User.class, incident.reporterId()).username(), incident.type(), incident.status(),
                incident.observation(), incident.createdAt(),
                incident.reviewerId() == null ? null : required(User.class, incident.reviewerId()).username(),
                incident.reviewNote(), incident.reviewedAt());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private <T> T required(Class<T> type, long id) {
        return store.find(type, id).orElseThrow(() -> missing("No se encontró el registro solicitado"));
    }
}
