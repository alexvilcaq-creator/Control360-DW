package com.control360.infrastructure.persistence;

import com.control360.infrastructure.persistence.Tables.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public final class JpaQueries {
    private JpaQueries() {}

    public interface Users extends JpaRepository<UserRow, Long> {
        Optional<UserRow> findByUsername(String username);
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select u from UserRow u where u.id = :id")
        Optional<UserRow> lockById(@Param("id") long id);
    }

    public interface Inventories extends JpaRepository<InventoryRow, Long> {
        Optional<InventoryRow> findByProductId(long productId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select i from InventoryRow i where i.productId = :productId")
        Optional<InventoryRow> lockByProductId(@Param("productId") long productId);
    }

    public interface Orders extends JpaRepository<OrderRow, Long> {
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select o from OrderRow o where o.id = :id")
        Optional<OrderRow> lockById(@Param("id") long id);
    }

    public interface Workers extends JpaRepository<WorkerRow, Long> {
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select w from WorkerRow w where w.id = :id")
        Optional<WorkerRow> lockById(@Param("id") long id);
    }

    public interface Lines extends JpaRepository<LineRow, Long> {
        List<LineRow> findByOrderIdOrderById(long orderId);
    }

    public interface Histories extends JpaRepository<HistoryRow, Long> {
        List<HistoryRow> findByOrderIdOrderById(long orderId);
    }
}
