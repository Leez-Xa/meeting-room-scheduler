package com.company.meeting.meeting;

import com.company.meeting.auth.UserPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class MeetingRepository {
    private final JdbcTemplate jdbc;

    public MeetingRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public MeetingRow findById(Long id) {
        return jdbc.query("""
                SELECT b.id, b.user_id, u.real_name applicant_name, b.dept_id, d.dept_name,
                       b.room_id, r.room_name, r.floor_no, b.subject, b.attend_num, b.start_time,
                       b.end_time, b.status, b.audit_user, b.audit_time, b.audit_comment, b.cancel_reason, b.applicant_role
                FROM meeting_book b JOIN sys_user u ON u.id = b.user_id JOIN dept d ON d.id = b.dept_id
                LEFT JOIN meeting_room r ON r.id = b.room_id WHERE b.id = ? AND b.deleted = 0
                """, this::mapRows, id).stream().findFirst().orElse(null);
    }

    public MeetingRow lockById(Long id) {
        jdbc.query("SELECT id FROM meeting_book WHERE id=? AND deleted=0 FOR UPDATE", (rs, n) -> rs.getLong(1), id);
        return findById(id);
    }

    public void bindDevices(Long bookId, List<com.company.meeting.resource.RoomRepository.DeviceRow> devices, Long operatorId) {
        for (var device : devices) {
            jdbc.update("INSERT INTO meeting_book_device(book_id,device_id,quantity,create_by,update_by) VALUES (?,?,?,?,?)",
                    bookId, device.id(), device.quantity(), operatorId, operatorId);
        }
    }

    private MeetingQuery query(UserPrincipal principal, String scope, LocalDateTime start, LocalDateTime end, MeetingStatus status) {
        StringBuilder sql = new StringBuilder("""
                SELECT b.id, b.user_id, u.real_name applicant_name, b.dept_id, d.dept_name,
                       b.room_id, r.room_name, r.floor_no, b.subject, b.attend_num, b.start_time,
                       b.end_time, b.status, b.audit_user, b.audit_time, b.audit_comment, b.cancel_reason, b.applicant_role
                FROM meeting_book b JOIN sys_user u ON u.id = b.user_id JOIN dept d ON d.id = b.dept_id
                LEFT JOIN meeting_room r ON r.id = b.room_id
                WHERE b.deleted = 0
                """);
        List<Object> args = new ArrayList<>();
        if (principal.role().equals("SYSTEM_ADMIN")) {
            // Full visibility.
        } else if (principal.role().equals("DEPT_ADMIN")) {
            sql.append(" AND (b.dept_id = ? OR EXISTS (SELECT 1 FROM meeting_attendee a WHERE a.book_id=b.id AND a.user_id=? AND a.deleted=0))");
            args.add(principal.deptId()); args.add(principal.id());
        } else {
            sql.append(" AND (b.user_id = ? OR EXISTS (SELECT 1 FROM meeting_attendee a WHERE a.book_id = b.id AND a.user_id = ? AND a.deleted=0))");
            args.add(principal.id()); args.add(principal.id());
        }
        if ("mine".equals(scope)) {
            sql.append(" AND (b.user_id=? OR EXISTS (SELECT 1 FROM meeting_attendee a WHERE a.book_id=b.id AND a.user_id=? AND a.deleted=0))");
            args.add(principal.id()); args.add(principal.id());
        }
        if ("pending".equals(scope)) {
            sql.append(" AND b.status='PENDING' AND b.user_id<>?"); args.add(principal.id());
            if (!principal.role().equals("SYSTEM_ADMIN")) { sql.append(" AND b.dept_id=?"); args.add(principal.deptId()); }
            if (principal.role().equals("DEPT_ADMIN")) sql.append(" AND b.applicant_role<>'SYSTEM_ADMIN'");
        }
        if (start != null) { sql.append(" AND b.end_time > ?"); args.add(start); }
        if (end != null) { sql.append(" AND b.start_time < ?"); args.add(end); }
        if (status != null) { sql.append(" AND b.status=?"); args.add(status.name()); }
        sql.append(" ORDER BY b.start_time ASC, b.id ASC");
        return new MeetingQuery(sql.toString(),args);
    }

    private record MeetingQuery(String sql, List<Object> args) {}

    public List<MeetingRow> list(UserPrincipal principal, String scope, LocalDateTime start, LocalDateTime end) {
        var query=query(principal,scope,start,end,null);
        return jdbc.query(query.sql(),this::mapRows,query.args().toArray());
    }

    public com.company.meeting.resource.AdminDtos.Page<MeetingRow> listPage(UserPrincipal principal, String scope,
            LocalDateTime start, LocalDateTime end, int page, int size, MeetingStatus status) {
        var query=query(principal,scope,start,end,status);
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM ("+query.sql()+") visible_meetings",Long.class,query.args().toArray());
        var args=new ArrayList<>(query.args()); args.add(size); args.add((page-1)*size);
        var records=jdbc.query(query.sql()+" LIMIT ? OFFSET ?",this::mapRows,args.toArray());
        return new com.company.meeting.resource.AdminDtos.Page<>(records,total==null?0:total,page,size);
    }

    private List<MeetingRow> mapRows(java.sql.ResultSet rs) throws java.sql.SQLException {
        List<MeetingRow> rows = new ArrayList<>();
        while (rs.next()) {
            Long id = rs.getLong("id");
            rows.add(new MeetingRow(id, rs.getLong("user_id"), rs.getString("applicant_name"), rs.getLong("dept_id"), rs.getString("dept_name"),
                    rs.getObject("room_id", Long.class), rs.getString("room_name"), rs.getObject("floor_no", Integer.class),
                    rs.getString("subject"), rs.getInt("attend_num"), rs.getObject("start_time", LocalDateTime.class), rs.getObject("end_time", LocalDateTime.class),
                    rs.getString("status"), rs.getObject("audit_user", Long.class), rs.getObject("audit_time", LocalDateTime.class),
                    rs.getString("audit_comment"), rs.getString("cancel_reason"), listAttendees(id), listDevices(id), rs.getString("applicant_role")));
        }
        return rows;
    }

    public Long insertDraft(Long userId, Long deptId, String subject, Integer attendNum, LocalDateTime start, LocalDateTime end) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO meeting_book(user_id, dept_id, subject, attend_num, start_time, end_time, status, create_by, update_by)
                    VALUES (?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?)
                    """, new String[]{"id"});
            ps.setLong(1, userId); ps.setLong(2, deptId); ps.setString(3, subject); ps.setInt(4, attendNum);
            ps.setObject(5, start); ps.setObject(6, end); ps.setLong(7, userId); ps.setLong(8, userId); return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    public void updateDraft(Long id, Long userId, String subject, Integer attendNum, LocalDateTime start, LocalDateTime end, Long roomId) {
        jdbc.update("UPDATE meeting_book SET subject=?, attend_num=?, start_time=?, end_time=?, room_id=?, update_by=?, update_time=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND status='DRAFT' AND deleted=0",
                subject, attendNum, start, end, roomId, userId, id, userId);
    }

    public void setDraftRoom(Long id, Long roomId, Long userId) {
        jdbc.update("UPDATE meeting_book SET room_id=?, update_by=?, update_time=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND status='DRAFT' AND deleted=0", roomId, userId, id, userId);
    }

    public void replaceAttendees(Long bookId, List<Long> userIds) {
        jdbc.update("UPDATE meeting_attendee SET deleted=1, update_time=CURRENT_TIMESTAMP WHERE book_id=?", bookId);
        for (Long userId : userIds) {
            int updated = jdbc.update("UPDATE meeting_attendee SET deleted=0, update_time=CURRENT_TIMESTAMP WHERE book_id=? AND user_id=?", bookId, userId);
            if (updated == 0) jdbc.update("INSERT INTO meeting_attendee(book_id,user_id,create_by,update_by) SELECT ?,?,user_id,user_id FROM meeting_book WHERE id=?", bookId, userId, bookId);
        }
    }

    public void replaceDevices(Long bookId, Map<String, Integer> devices) {
        jdbc.update("UPDATE meeting_device_requirement SET deleted=1, update_time=CURRENT_TIMESTAMP WHERE book_id=?", bookId);
        devices.forEach((type, quantity) -> {
            int updated = jdbc.update("UPDATE meeting_device_requirement SET quantity=?, deleted=0, update_time=CURRENT_TIMESTAMP WHERE book_id=? AND device_type=?", quantity, bookId, type);
            if (updated == 0) jdbc.update("INSERT INTO meeting_device_requirement(book_id,device_type,quantity,create_by,update_by) SELECT ?,?,?,user_id,user_id FROM meeting_book WHERE id=?", bookId, type, quantity, bookId);
        });
    }

    public List<Long> listAttendeeIds(Long bookId) {
        return jdbc.query("SELECT user_id FROM meeting_attendee WHERE book_id=? AND deleted=0 ORDER BY user_id", (rs, rowNum) -> rs.getLong(1), bookId);
    }

    public List<AttendeeRow> listAttendees(Long bookId) {
        return jdbc.query("SELECT a.user_id, u.real_name, u.username, d.dept_name FROM meeting_attendee a JOIN sys_user u ON u.id=a.user_id JOIN dept d ON d.id=u.dept_id WHERE a.book_id=? AND a.deleted=0 ORDER BY a.user_id",
                (rs, rowNum) -> new AttendeeRow(rs.getLong("user_id"), rs.getString("real_name"), rs.getString("username"), rs.getString("dept_name")), bookId);
    }

    public List<DeviceRequirement> listDevices(Long bookId) {
        return jdbc.query("SELECT device_type, quantity FROM meeting_device_requirement WHERE book_id=? AND deleted=0 ORDER BY device_type",
                (rs, rowNum) -> new DeviceRequirement(rs.getString("device_type"), rs.getInt("quantity")), bookId);
    }

    public Map<String, Integer> requirementMap(Long bookId) {
        return jdbc.query("SELECT device_type, quantity FROM meeting_device_requirement WHERE book_id=? AND deleted=0", rs -> { Map<String, Integer> result = new java.util.HashMap<>(); while (rs.next()) result.put(rs.getString(1), rs.getInt(2)); return result; }, bookId);
    }

    public int submit(Long id, Long roomId, Long operatorId, String applicantRole) {
        return jdbc.update("UPDATE meeting_book SET room_id=?, applicant_role=?, status='PENDING', update_by=?, update_time=CURRENT_TIMESTAMP WHERE id=? AND status='DRAFT' AND deleted=0", roomId, applicantRole, operatorId, id);
    }

    public int updateApproval(Long id, String status, Long operatorId, String comment) {
        return jdbc.update("UPDATE meeting_book SET status=?, audit_user=?, audit_time=CURRENT_TIMESTAMP, audit_comment=?, update_by=?, update_time=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING' AND deleted=0", status, operatorId, comment, operatorId, id);
    }

    public int cancel(Long id, Long operatorId, String reason) {
        return jdbc.update("UPDATE meeting_book SET status='CANCELED', cancel_reason=?, update_by=?, update_time=CURRENT_TIMESTAMP WHERE id=? AND status IN ('PENDING','APPROVED','IN_PROGRESS') AND deleted=0", reason, operatorId, id);
    }

    public void addApprovalHistory(Long bookId, Long operatorId, String action, String comment) {
        jdbc.update("INSERT INTO approval_history(book_id,operator_id,action,comment,create_by,update_by) VALUES (?,?,?,?,?,?)", bookId, operatorId, action, comment, operatorId, operatorId);
    }

    public void addAudit(Long operatorId, String type, String content) {
        jdbc.update("INSERT INTO audit_log(operator_id,operate_type,content,create_by,update_by) VALUES (?,?,?,?,?)", operatorId, type, content, operatorId, operatorId);
    }

    public List<AuditRow> listAudit() {
        return jdbc.query("SELECT a.id, a.operator_id, u.real_name, a.operate_type, a.content, a.operate_time FROM audit_log a JOIN sys_user u ON u.id=a.operator_id ORDER BY a.operate_time DESC, a.id DESC LIMIT 100",
                (rs, rowNum) -> new AuditRow(rs.getLong("id"), rs.getLong("operator_id"), rs.getString("real_name"), rs.getString("operate_type"), rs.getString("content"), rs.getObject("operate_time", LocalDateTime.class)));
    }

    public List<OccupancyRow> listOccupancies(LocalDateTime start, LocalDateTime end) {
        return jdbc.query("""
                SELECT b.id, b.user_id, b.dept_id, b.subject, b.start_time, b.end_time, b.status,
                       b.room_id, r.room_name
                FROM meeting_book b JOIN meeting_room r ON r.id = b.room_id
                WHERE b.deleted = 0 AND b.status IN ('PENDING','APPROVED','IN_PROGRESS')
                  AND b.end_time > ? AND b.start_time < ?
                ORDER BY r.floor_no, r.id, b.start_time
                """, (rs, rowNum) -> {
                    Long id = rs.getLong("id");
                    return new OccupancyRow(id, rs.getLong("user_id"), rs.getLong("dept_id"), rs.getString("subject"),
                            rs.getObject("start_time", LocalDateTime.class), rs.getObject("end_time", LocalDateTime.class),
                            rs.getString("status"), rs.getLong("room_id"), rs.getString("room_name"), listAttendeeIds(id));
                }, start, end);
    }

    public int expirePending(LocalDateTime now) { return jdbc.update("UPDATE meeting_book SET status='EXPIRED', update_time=CURRENT_TIMESTAMP WHERE status='PENDING' AND start_time <= ? AND deleted=0", now); }
    public int startApproved(LocalDateTime now) { return jdbc.update("UPDATE meeting_book SET status='IN_PROGRESS', update_time=CURRENT_TIMESTAMP WHERE status='APPROVED' AND start_time <= ? AND end_time > ? AND deleted=0", now, now); }
    public int completeRunning(LocalDateTime now) { return jdbc.update("UPDATE meeting_book SET status='COMPLETED', update_time=CURRENT_TIMESTAMP WHERE status IN ('APPROVED','IN_PROGRESS') AND end_time <= ? AND deleted=0", now); }

    public int cleanupStaleDrafts(LocalDateTime cutoff) {
        List<Long> ids = jdbc.query("SELECT id FROM meeting_book WHERE status='DRAFT' AND deleted=0 AND update_time < ? ORDER BY id",
                (rs, rowNum) -> rs.getLong(1), cutoff);
        for (Long id : ids) {
            jdbc.update("UPDATE meeting_attendee SET deleted=1, update_time=CURRENT_TIMESTAMP WHERE book_id=? AND deleted=0", id);
            jdbc.update("UPDATE meeting_device_requirement SET deleted=1, update_time=CURRENT_TIMESTAMP WHERE book_id=? AND deleted=0", id);
            jdbc.update("UPDATE meeting_book_device SET deleted=1, update_time=CURRENT_TIMESTAMP WHERE book_id=? AND deleted=0", id);
        }
        if (ids.isEmpty()) return 0;
        return jdbc.update("UPDATE meeting_book SET deleted=1, update_time=CURRENT_TIMESTAMP WHERE status='DRAFT' AND deleted=0 AND update_time < ?", cutoff);
    }
    public List<MeetingRow> dueStates(LocalDateTime now) {
        List<Long> ids = jdbc.query("SELECT id FROM meeting_book WHERE deleted=0 AND ((status IN ('PENDING','APPROVED') AND start_time<=?) OR (status='IN_PROGRESS' AND end_time<=?)) ORDER BY room_id,id", (rs, n) -> rs.getLong(1), now, now);
        return ids.stream().map(this::findById).toList();
    }

    public List<MeetingRow> upcomingApproved(LocalDateTime from, LocalDateTime to) {
        List<Long> ids = jdbc.query("""
                SELECT id FROM meeting_book
                WHERE status='APPROVED' AND deleted=0 AND start_time > ? AND start_time <= ?
                ORDER BY start_time, id
                """, (rs, rowNum) -> rs.getLong(1), from, to);
        return ids.stream().map(this::findById).toList();
    }

    public boolean transition(Long id, String previous, String next) {
        return jdbc.update("UPDATE meeting_book SET status=?, update_time=CURRENT_TIMESTAMP WHERE id=? AND status=? AND deleted=0", next, id, previous) == 1;
    }

    public record MeetingRow(Long id, Long userId, String applicantName, Long deptId, String deptName, Long roomId, String roomName, Integer floorNo,
                             String subject, Integer attendNum, LocalDateTime startTime, LocalDateTime endTime, String status, Long auditUser,
                             LocalDateTime auditTime, String auditComment, String cancelReason, List<AttendeeRow> attendees, List<DeviceRequirement> devices, String applicantRole) {}
    public record AttendeeRow(Long userId, String realName, String username, String deptName) {}
    public record DeviceRequirement(String deviceType, Integer quantity) {}
    public record AuditRow(Long id, Long operatorId, String operatorName, String operateType, String content, LocalDateTime operateTime) {}
    public record OccupancyRow(Long id, Long userId, Long deptId, String subject, LocalDateTime startTime, LocalDateTime endTime,
                               String status, Long roomId, String roomName, List<Long> attendeeIds) {}
}
