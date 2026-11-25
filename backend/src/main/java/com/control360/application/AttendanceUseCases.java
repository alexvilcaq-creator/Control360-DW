package com.control360.application;

import com.control360.domain.Models.*;
import java.time.LocalDate;
import java.util.List;

public interface AttendanceUseCases {
    LocalDate today();
    List<Attendance> attendance(User actor, LocalDate from, LocalDate to);
    List<Absence> absences(User actor, LocalDate from, LocalDate to);
    Attendance enter(User actor, Long workerId);
    Attendance leave(User actor, Long workerId);
    Attendance approve(long id, int minutes);
}
