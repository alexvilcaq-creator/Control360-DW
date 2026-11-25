package com.control360.application;

import com.control360.domain.Models.*;
import java.util.List;

/** Puerto de entrada para registrar y preparar pedidos. */
public interface OrderUseCases {
    OrderView createOrder(User actor, long customerId, List<LineRequest> lines, String requestKey);
    OrderView transition(User actor, long orderId, Status next, Long assignedWorkerId);
    List<OrderView> orders(User actor);
    OrderView order(User actor, long orderId);
}
