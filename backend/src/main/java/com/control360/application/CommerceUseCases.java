package com.control360.application;

import com.control360.domain.Models.*;
import java.math.BigDecimal;
import java.util.List;

public interface CommerceUseCases {
    PaymentView payment(User actor, long orderId);
    PaymentView recordPayment(User actor, long orderId, PaymentMethod method, BigDecimal received, boolean confirmed);
    List<PaymentView> payments(User actor);
    IncidentView report(User actor, long productId, IncidentType type, String observation, String key);
    IncidentView review(User actor, long incidentId, IncidentStatus status, String note);
    List<IncidentView> incidents(User actor);
}
