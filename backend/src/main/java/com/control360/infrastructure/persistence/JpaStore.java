package com.control360.infrastructure.persistence;

import com.control360.application.Store;
import com.control360.domain.Models.*;
import com.control360.domain.RuleViolation;
import com.control360.infrastructure.persistence.Tables.*;
import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Map.entry;

@Repository
@EnableJpaRepositories(considerNestedRepositories = true)
public class JpaStore implements Store {
    private record Mapping<T, R extends Row<T>>(Class<R> type, Supplier<R> create, Function<T, Long> id) {}

    private static final Map<Class<?>, Mapping<?, ?>> MAPPINGS = Map.ofEntries(
            entry(Category.class, new Mapping<>(CategoryRow.class, CategoryRow::new, Category::id)),
            entry(Location.class, new Mapping<>(LocationRow.class, LocationRow::new, Location::id)),
            entry(Customer.class, new Mapping<>(CustomerRow.class, CustomerRow::new, Customer::id)),
            entry(Shift.class, new Mapping<>(ShiftRow.class, ShiftRow::new, Shift::id)),
            entry(Worker.class, new Mapping<>(WorkerRow.class, WorkerRow::new, Worker::id)),
            entry(User.class, new Mapping<>(UserRow.class, UserRow::new, User::id)),
            entry(Product.class, new Mapping<>(ProductRow.class, ProductRow::new, Product::id)),
            entry(Inventory.class, new Mapping<>(InventoryRow.class, InventoryRow::new, Inventory::id)),
            entry(Movement.class, new Mapping<>(MovementRow.class, MovementRow::new, Movement::id)),
            entry(Order.class, new Mapping<>(OrderRow.class, OrderRow::new, Order::id)),
            entry(OrderRequest.class, new Mapping<>(OrderRequestRow.class, OrderRequestRow::new, OrderRequest::id)),
            entry(Line.class, new Mapping<>(LineRow.class, LineRow::new, Line::id)),
            entry(History.class, new Mapping<>(HistoryRow.class, HistoryRow::new, History::id)),
            entry(Attendance.class, new Mapping<>(AttendanceRow.class, AttendanceRow::new, Attendance::id)),
            entry(Notification.class, new Mapping<>(NotificationRow.class, NotificationRow::new, Notification::id)),
            entry(NotificationRead.class, new Mapping<>(NotificationReadRow.class, NotificationReadRow::new, NotificationRead::id))
    );

    private final EntityManager entityManager;
    private final TransactionTemplate writer;
    private final TransactionTemplate reader;
    private final JpaQueries.Users users;
    private final JpaQueries.Inventories inventories;
    private final JpaQueries.Orders orders;
    private final JpaQueries.Workers workers;
    private final JpaQueries.Lines lines;
    private final JpaQueries.Histories histories;

    public JpaStore(EntityManager entityManager, PlatformTransactionManager manager, JpaQueries.Users users,
                    JpaQueries.Inventories inventories, JpaQueries.Orders orders, JpaQueries.Workers workers,
                    JpaQueries.Lines lines, JpaQueries.Histories histories) {
        this.entityManager = entityManager;
        this.users = users;
        this.inventories = inventories;
        this.orders = orders;
        this.workers = workers;
        this.lines = lines;
        this.histories = histories;
        writer = new TransactionTemplate(manager);
        writer.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        reader = new TransactionTemplate(manager);
        reader.setReadOnly(true);
    }

    @Override
    public <T> T write(Supplier<T> action) {
        return writer.execute(status -> action.get());
    }

    @Override
    public <T> T read(Supplier<T> action) {
        return reader.execute(status -> action.get());
    }

    @SuppressWarnings("unchecked")
    private <T> Mapping<T, ? extends Row<T>> mapping(Class<?> type) {
        return (Mapping<T, ? extends Row<T>>) MAPPINGS.get(type);
    }

    @Override
    public <T> T save(T value) {
        Mapping<T, ? extends Row<T>> mapping = mapping(value.getClass());
        Long id = mapping.id().apply(value);
        Row<T> row = id == null ? mapping.create().get() : entityManager.find(mapping.type(), id);
        if (row == null) throw RuleViolation.missing("No se encontró el registro");
        row.copyFrom(value);
        if (id == null) entityManager.persist(row);
        return row.toDomain();
    }

    @Override
    public <T> List<T> all(Class<T> type) {
        Mapping<T, ? extends Row<T>> mapping = mapping(type);
        String entity = mapping.type().getSimpleName();
        return entityManager.createQuery("select r from " + entity + " r order by r.id", mapping.type())
                .getResultList().stream().map(Row::toDomain).toList();
    }

    @Override
    public <T> Optional<T> find(Class<T> type, long id) {
        Mapping<T, ? extends Row<T>> mapping = mapping(type);
        return Optional.ofNullable(entityManager.find(mapping.type(), id)).map(Row::toDomain);
    }

    @Override
    public Optional<User> user(String username) {
        return users.findByUsername(username).map(Row::toDomain);
    }

    @Override
    public Optional<User> lockUser(long id) {
        return users.lockById(id).map(Row::toDomain);
    }

    @Override
    public Optional<Inventory> inventory(long productId, boolean lock) {
        return (lock ? inventories.lockByProductId(productId) : inventories.findByProductId(productId))
                .map(Row::toDomain);
    }

    @Override
    public Optional<Order> lockOrder(long id) {
        return orders.lockById(id).map(Row::toDomain);
    }

    @Override
    public OrderRequest reserveOrderRequest(long userId, String key, String fingerprint) {
        entityManager.createNativeQuery("""
                INSERT INTO order_requests (user_id, request_key, fingerprint)
                VALUES (:userId, :requestKey, :fingerprint)
                ON DUPLICATE KEY UPDATE id = id
                """).setParameter("userId", userId).setParameter("requestKey", key)
                .setParameter("fingerprint", fingerprint).executeUpdate();
        return entityManager.createQuery("""
                SELECT r FROM OrderRequestRow r WHERE r.userId = :userId AND r.requestKey = :requestKey
                """, OrderRequestRow.class).setParameter("userId", userId).setParameter("requestKey", key)
                .getSingleResult().toDomain();
    }

    @Override
    public Optional<Worker> lockWorker(long id) {
        return workers.lockById(id).map(Row::toDomain);
    }

    @Override
    public List<Line> orderLines(long orderId) {
        return lines.findByOrderIdOrderById(orderId).stream().map(Row::toDomain).toList();
    }

    @Override
    public List<History> orderHistory(long orderId) {
        return histories.findByOrderIdOrderById(orderId).stream().map(Row::toDomain).toList();
    }
}
