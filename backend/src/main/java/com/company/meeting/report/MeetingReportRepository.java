package com.company.meeting.report;

import com.company.meeting.auth.UserPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class MeetingReportRepository {
    private final JdbcTemplate jdbc;

    public MeetingReportRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<ReportRow> list(UserPrincipal principal, LocalDate start, LocalDate end, Long roomId, String status) {
        StringBuilder sql = new StringBuilder("""
                SELECT b.id,b.series_id,b.series_index,b.series_total,b.subject,u.real_name applicant_name,d.dept_name,
                       r.room_name,b.start_time,b.end_time,b.status,b.attend_num,b.audit_comment,b.cancel_reason
                FROM meeting_book b JOIN sys_user u ON u.id=b.user_id JOIN dept d ON d.id=b.dept_id
                LEFT JOIN meeting_room r ON r.id=b.room_id
                WHERE b.deleted=0
                """);
        List<Object> args = new ArrayList<>();
        if ("DEPT_ADMIN".equals(principal.role())) { sql.append(" AND b.dept_id=?"); args.add(principal.deptId()); }
        if (start != null) { sql.append(" AND b.end_time>=?"); args.add(start.atStartOfDay()); }
        if (end != null) { sql.append(" AND b.start_time<?"); args.add(end.plusDays(1).atStartOfDay()); }
        if (roomId != null) { sql.append(" AND b.room_id=?"); args.add(roomId); }
        if (status != null && !status.isBlank()) { sql.append(" AND b.status=?"); args.add(status); }
        sql.append(" ORDER BY b.start_time,b.id");
        return jdbc.query(sql.toString(), (rs, rowNum) -> new ReportRow(
                rs.getLong("id"), rs.getObject("series_id", Long.class), rs.getObject("series_index", Integer.class),
                rs.getObject("series_total", Integer.class), rs.getString("subject"), rs.getString("applicant_name"),
                rs.getString("dept_name"), rs.getString("room_name"), rs.getObject("start_time", LocalDateTime.class),
                rs.getObject("end_time", LocalDateTime.class), rs.getString("status"), rs.getInt("attend_num"),
                rs.getString("audit_comment"), rs.getString("cancel_reason")), args.toArray());
    }

    public record ReportRow(Long id, Long seriesId, Integer seriesIndex, Integer seriesTotal, String subject,
                            String applicantName, String deptName, String roomName, LocalDateTime startTime,
                            LocalDateTime endTime, String status, Integer attendNum, String auditComment, String cancelReason) {}
}
