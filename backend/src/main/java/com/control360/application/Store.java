package com.control360.application;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import com.control360.domain.Models.*;

/** Puerto de salida. Los bloqueos se conservan hasta que termina write. */
public interface Store {
    <T> T write(Supplier<T> action);
    <T> T read(Supplier<T> action);
    <T> T save(T value);
    <T> List<T> all(Class<T> type);
    <T> Optional<T> find(Class<T> type, long id);
    Optional<User> user(String username);
    Optional<User> lockUser(long id);
    Optional<Inventory> inventory(long productId, boolean lock);
    Optional<Order> lockOrder(long id);
    OrderRequest reserveOrderRequest(long userId, String key, String fingerprint);
    Optional<Worker> lockWorker(long id);
    List<Line> orderLines(long orderId);
    List<History> orderHistory(long orderId);
}
