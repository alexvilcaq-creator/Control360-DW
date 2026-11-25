package com.control360.infrastructure.web;

import com.control360.application.AttendanceUseCases;
import com.control360.application.Store;
import com.control360.domain.Models.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import static com.control360.infrastructure.web.Dtos.*;

@RestController
@RequestMapping("/api/asistencias")
public class AttendanceController {
    private final AttendanceUseCases operations;
    private final Store store;

    public AttendanceController(AttendanceUseCases operations, Store store) {
        this.operations = operations;
        this.store = store;
    }

    @GetMapping
    List<Attendance> attendance(Principal principal, @RequestParam(required = false) LocalDate from,
                                @RequestParam(required = false) LocalDate to) {
        LocalDate end = to != null ? to : operations.today();
        return operations.attendance(actor(principal), from != null ? from : end.minusDays(30), end);
    }

    @GetMapping("/faltas")
    List<Absence> absences(Principal principal, @RequestParam(required = false) LocalDate from,
                           @RequestParam(required = false) LocalDate to) {
        LocalDate end = to != null ? to : operations.today();
        return operations.absences(actor(principal), from != null ? from : end.minusDays(30), end);
    }

    @PostMapping({"", "/entradas"})
    @ResponseStatus(HttpStatus.CREATED)
    Attendance entry(Principal principal, @Valid @RequestBody(required = false) Assignee body) {
        return operations.enter(actor(principal), body == null ? null : body.workerId());
    }

    @PostMapping("/salidas")
    Attendance exit(Principal principal, @Valid @RequestBody(required = false) Assignee body) {
        return operations.leave(actor(principal), body == null ? null : body.workerId());
    }

    @GetMapping("/estado")
    AttendanceState state(Principal principal) { return operations.attendanceState(actor(principal)); }

    @GetMapping("/{id}/auditoria")
    List<AttendanceAudit> audit(Principal principal, @PathVariable long id) {
        return operations.attendanceAudit(actor(principal), id);
    }

    @PutMapping("/{id}/corregir")
    Attendance correct(Principal principal, @PathVariable long id, @Valid @RequestBody AttendanceCorrection body) {
        return operations.correct(actor(principal), id, body.entry(), body.exit(), body.reason());
    }

    @PutMapping("/{id}/aprobar")
    Attendance approve(Principal principal, @PathVariable long id, @Valid @RequestBody Approval body) {
        return operations.approve(actor(principal), id, body.minutes());
    }

    private User actor(Principal principal) { return store.user(principal.getName()).orElseThrow(); }
}
