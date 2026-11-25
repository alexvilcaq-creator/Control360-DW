package com.control360.infrastructure.web;

import com.control360.domain.Models.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

final class Dtos {
    private Dtos() {}

    record CategoryBody(@NotBlank @Size(max = 100) String name) {
        Category toDomain(Long id) { return new Category(id, name.strip()); }
    }
    record LocationBody(@NotBlank @Size(max = 20) String zone, @NotBlank @Size(max = 20) String aisle,
                        @NotBlank @Size(max = 20) String shelf) {
        Location toDomain(Long id) { return new Location(id, zone.strip(), aisle.strip(), shelf.strip()); }
    }
    record CustomerBody(@NotBlank @Size(max = 150) String name, @NotBlank @Size(max = 30) String document,
                        @Email @Size(max = 180) String email) {
        Customer toDomain() { return new Customer(null, name.strip(), document.strip(), optional(email)); }
    }
    record ProductBody(@NotBlank @Size(max = 60) String sku, @NotBlank @Size(max = 150) String name,
                       @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
                       @NotNull @PositiveOrZero Integer minStock, @NotNull Boolean active,
                       @NotNull @Positive Long categoryId, @NotNull @Positive Long locationId,
                       @PositiveOrZero Integer initialStock) {
        Product toDomain(Long id) { return new Product(id, sku.strip(), name.strip(), price, minStock, active, categoryId, locationId); }
        int openingBalance() { return initialStock == null ? 0 : initialStock; }
    }
    record Adjustment(@NotNull Integer delta, @NotBlank @Size(max = 250) String note) {}
    record WorkerBody(@NotBlank @Size(max = 100) String firstName, @NotBlank @Size(max = 100) String lastName,
                      @NotBlank @Size(max = 30) String document, @NotBlank @Email @Size(max = 180) String email,
                      @NotBlank @Size(max = 100) String position, @NotNull Boolean active, @Positive Long shiftId) {
        Worker toDomain(Long id) {
            return new Worker(id, firstName.strip(), lastName.strip(), document.strip(), email.strip(), position.strip(), active, shiftId, null);
        }
    }
    record ShiftBody(@NotBlank @Size(max = 100) String name, @NotNull LocalTime startsAt, @NotNull LocalTime endsAt,
                     @NotNull @Min(0) @Max(180) Integer graceMinutes, @NotBlank @Pattern(regexp = "[1-7](,[1-7]){0,6}") String days) {
        Shift toDomain(Long id) { return new Shift(id, name.strip(), startsAt, endsAt, graceMinutes, days); }
    }
    record UserBody(@NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{3,80}") String username,
                    @Size(max = 72) String password, @NotNull Role role, @NotNull Boolean active, @Positive Long workerId) {}
    record OrderBody(@NotNull @Positive Long customerId, @NotEmpty @Size(max = 100) List<@NotNull @Valid Item> items) {}
    record Item(@NotNull @Positive Long productId, @NotNull @Min(1) @Max(1000000) Integer quantity) {}
    record StateBody(@NotNull Status status, @Positive Long workerId) {}
    record Assignee(@Positive Long workerId) {}
    record Approval(@NotNull @PositiveOrZero Integer minutes) {}

    private static String optional(String value) { return value == null || value.isBlank() ? null : value.strip(); }
}
