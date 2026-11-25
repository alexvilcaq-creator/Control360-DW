package com.control360.application;

import com.control360.domain.Models.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface AttendanceUseCases {
    LocalDate today();
    List<Attendance> attendance(User actor, LocalDate from, LocalDate to);
    List<Absence> absences(User actor, LocalDate from, LocalDate to);
    Attendance enter(User actor, Long workerId);
    Attendance leave(User actor, Long workerId);
    AttendanceState attendanceState(User actor);
    Attendance approve(User actor, long id, int minutes);
    Attendance correct(User actor, long id, LocalDateTime entry, LocalDateTime exit, String reason);
    List<AttendanceAudit> attendanceAudit(User actor, long id);
}
