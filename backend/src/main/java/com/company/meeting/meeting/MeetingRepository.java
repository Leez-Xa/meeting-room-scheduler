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

/**
 * 预约聚合数据访问层，负责主记录、参会人、需求、实际设备、审批与审计。
 * SQL 内限制可见范围，状态更新限定旧状态并返回影响数。
 * 本层不替代服务授权，多表更新与 FOR UPDATE 必须在外层事务内使用。
 */
@Repository
public class MeetingRepository {
    private final JdbcTemplate jdbc;

    public MeetingRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * 读取未删除单场及关联详情，不存在返回 null。
     * 本方法不授权，调用方必须校验数据范围。
     */
    public MeetingRow findById(Long id) {
        return jdbc.query("""
                SELECT b.id, b.user_id, u.real_name applicant_name, b.dept_id, d.dept_name,
                       b.room_id, r.room_name, r.floor_no, b.subject, b.attend_num, b.start_time,
                       b.end_time, b.status, b.audit_user, b.audit_time, b.audit_comment, b.cancel_reason, b.applicant_role,
                       b.series_id, b.series_index, b.series_total
                FROM meeting_book b JOIN sys_user u ON u.id = b.user_id JOIN dept d ON d.id = b.dept_id
                LEFT JOIN meeting_room r ON r.id = b.room_id WHERE b.id = ? AND b.deleted = 0
                """, this::mapRows, id).stream().findFirst().orElse(null);
    }

    /**
     * 锁预约主记录后重读详情，锁持续至外层事务结束。
     * 资源写操作应先锁房间，再锁预约，保持相同锁层级。
     */
    public MeetingRow lockById(Long id) {
        /**
         * 构造列表、分页共用的可见 SQL 与绑定参数。
         * 先按角色和部门、参会关系收紧，再加 mine/pending、时间与状态筛选。
         * pending 额外排除本人及部门管理员不可处理的管理员申请。
         * 动态部分是受控片段，业务值均通过占位符绑定。
         */
        jdbc.query("SELECT id FROM meeting_book WHERE id=? AND deleted=0 FOR UPDATE", (rs, n) -> rs.getLong(1), id);
        /**
         * 读取未删除单场及关联详情，不存在返回 null。
         * 本方法不授权，调用方必须校验数据范围。
         */
        return findById(id);
    }

    /**
     * 记录该房间全部启用固定设备及数量，不只是需求中的类型。
     * 调用方须已持有房间锁；需求另存为类型和数量。
     */
    public void bindDevices(Long bookId, List<com.company.meeting.resource.RoomRepository.DeviceRow> devices, Long operatorId) {
        for (var device : devices) {
            jdbc.update("INSERT INTO meeting_book_device(book_id,device_id,quantity,create_by,update_by) VALUES (?,?,?,?,?)",
                    bookId, device.id(), device.quantity(), operatorId, operatorId);
        }
    }

    /**
     * 构造列表、分页共用的可见 SQL 与绑定参数。
     * 先按角色和部门、参会关系收紧，再加 mine/pending、时间与状态筛选。
     * pending 额外排除本人及部门管理员不可处理的管理员申请。
     * 动态部分是受控片段，业务值均通过占位符绑定。
     */
    private MeetingQuery query(UserPrincipal principal, String scope, LocalDateTime start, LocalDateTime end, MeetingStatus status) {
        StringBuilder sql = new StringBuilder("""
                SELECT b.id, b.user_id, u.real_name applicant_name, b.dept_id, d.dept_name,
                       b.room_id, r.room_name, r.floor_no, b.subject, b.attend_num, b.start_time,
                       b.end_time, b.status, b.audit_user, b.audit_time, b.audit_comment, b.cancel_reason, b.applicant_role,
                       b.series_id, b.series_index, b.series_total
                FROM meeting_book b JOIN sys_user u ON u.id = b.user_id JOIN dept d ON d.id = b.dept_id
                LEFT JOIN meeting_room r ON r.id = b.room_id
                WHERE b.deleted = 0
                """);
        List<Object> args = new ArrayList<>();
        // SQL 先限制权限，total 和当前页都不能包含无权读取的记录。
        if (principal.role().equals("SYSTEM_ADMIN")) {
            // Full visibility.
        } else if (principal.role().equals("DEPT_ADMIN")) {
            sql.append(" AND (b.dept_id = ? OR EXISTS (SELECT 1 FROM meeting_attendee a WHERE a.book_id=b.id AND a.user_id=? AND a.deleted=0))");
            args.add(principal.deptId()); args.add(principal.id());
        } else {
            sql.append(" AND (b.user_id = ? OR EXISTS (SELECT 1 FROM meeting_attendee a WHERE a.book_id = b.id AND a.user_id = ? AND a.deleted=0))");
            args.add(principal.id()); args.add(principal.id());
        }
        // mine 收紧为本人申请或参与，pending 则进一步限制可审批队列。
        if ("mine".equals(scope)) {
            sql.append(" AND (b.user_id=? OR EXISTS (SELECT 1 FROM meeting_attendee a WHERE a.book_id=b.id AND a.user_id=? AND a.deleted=0))");
            args.add(principal.id()); args.add(principal.id());
        }
        if ("pending".equals(scope)) {
            sql.append(" AND b.status='PENDING' AND b.user_id<>?"); args.add(principal.id());
            if (!principal.role().equals("SYSTEM_ADMIN")) { sql.append(" AND b.dept_id=?"); args.add(principal.deptId()); }
            if (principal.role().equals("DEPT_ADMIN")) sql.append(" AND b.applicant_role<>'SYSTEM_ADMIN'");
        }
        // 按区间相交筛选，避免漏掉跨查询边界的会议。
        if (start != null) { sql.append(" AND b.end_time > ?"); args.add(start); }
        if (end != null) { sql.append(" AND b.start_time < ?"); args.add(end); }
        if (status != null) { sql.append(" AND b.status=?"); args.add(status.name()); }
        sql.append(" ORDER BY b.start_time ASC, b.id ASC");
        return new MeetingQuery(sql.toString(),args);
    }

    /**
     * 共用 SQL 与参数，避免列表和分页数据范围漂移。
     */
    private record MeetingQuery(String sql, List<Object> args) {}

    /**
     * 读取完整可见列表供工作台等内部逻辑使用，对外列表优先分页。
     */
    public List<MeetingRow> list(UserPrincipal principal, String scope, LocalDateTime start, LocalDateTime end) {
        var query=query(principal,scope,start,end,null);
        return jdbc.query(query.sql(),this::mapRows,query.args().toArray());
    }

    /**
     * 同一范围 SQL 计算总数并取页，按时间与 ID 稳定排序。
     * 边界由服务层校验，LIMIT 和 OFFSET 仍用参数绑定。
     */
    public com.company.meeting.resource.AdminDtos.Page<MeetingRow> listPage(UserPrincipal principal, String scope,
            LocalDateTime start, LocalDateTime end, int page, int size, MeetingStatus status) {
        var query=query(principal,scope,start,end,status);
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM ("+query.sql()+") visible_meetings",Long.class,query.args().toArray());
        var args=new ArrayList<>(query.args()); args.add(size); args.add((page-1)*size);
        var records=jdbc.query(query.sql()+" LIMIT ? OFFSET ?",this::mapRows,args.toArray());
        return new com.company.meeting.resource.AdminDtos.Page<>(records,total==null?0:total,page,size);
    }

    /**
     * 映射主记录并加载参会人和需求，可空外键使用 getObject 避免 NULL 变 0。
     * 当前逐条加载关联，数据规模扩大时需评估批量加载以减少查询次数。
     */
    private List<MeetingRow> mapRows(java.sql.ResultSet rs) throws java.sql.SQLException {
        List<MeetingRow> rows = new ArrayList<>();
        while (rs.next()) {
            Long id = rs.getLong("id");
            rows.add(new MeetingRow(id, rs.getLong("user_id"), rs.getString("applicant_name"), rs.getLong("dept_id"), rs.getString("dept_name"),
                    rs.getObject("room_id", Long.class), rs.getString("room_name"), rs.getObject("floor_no", Integer.class),
                    rs.getString("subject"), rs.getInt("attend_num"), rs.getObject("start_time", LocalDateTime.class), rs.getObject("end_time", LocalDateTime.class),
                    rs.getString("status"), rs.getObject("audit_user", Long.class), rs.getObject("audit_time", LocalDateTime.class),
                    rs.getString("audit_comment"), rs.getString("cancel_reason"), listAttendees(id), listDevices(id), rs.getString("applicant_role"),
                    rs.getObject("series_id", Long.class), rs.getObject("series_index", Integer.class), rs.getObject("series_total", Integer.class)));
        }
        return rows;
    }

    /**
     * 创建不占资源的 DRAFT，取数据库生成 ID，申请部门来自服务端身份。
     */
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

    /**
     * 写周期组元数据，本身不展开场次，应与场次插入处于同一事务。
     */
    public Long insertSeries(Long userId, Long deptId, Long roomId, String subject, Integer attendNum,
                             LocalDateTime firstStart, LocalDateTime firstEnd, Integer occurrenceCount) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO meeting_series(user_id,dept_id,room_id,subject,attend_num,first_start_time,first_end_time,occurrence_count,create_by,update_by)
                    VALUES (?,?,?,?,?,?,?,?,?,?)
                    """, new String[]{"id"});
            ps.setLong(1, userId); ps.setLong(2, deptId); ps.setLong(3, roomId); ps.setString(4, subject);
            ps.setInt(5, attendNum); ps.setObject(6, firstStart); ps.setObject(7, firstEnd); ps.setInt(8, occurrenceCount);
            ps.setLong(9, userId); ps.setLong(10, userId); return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    /**
     * 直接创建周期中的 PENDING 场次，保存从 1 开始序号及申请时角色。
     * 调用方须先完成全部校验并锁房间，不经中间草稿。
     */
    public Long insertSeriesOccurrence(Long seriesId, int index, int total, Long userId, Long deptId, Long roomId,
                                       String subject, int attendNum, LocalDateTime start, LocalDateTime end, String applicantRole) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO meeting_book(user_id,dept_id,series_id,series_index,series_total,applicant_role,room_id,subject,attend_num,start_time,end_time,status,create_by,update_by)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,'PENDING',?,?)
                    """, new String[]{"id"});
            ps.setLong(1, userId); ps.setLong(2, deptId); ps.setLong(3, seriesId); ps.setInt(4, index); ps.setInt(5, total);
            ps.setString(6, applicantRole); ps.setLong(7, roomId); ps.setString(8, subject); ps.setInt(9, attendNum);
            ps.setObject(10, start); ps.setObject(11, end); ps.setLong(12, userId); ps.setLong(13, userId); return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    /**
     * 按场次和 ID 读取全组未删除记录，包含取消及过期等终态。
     */
    public List<MeetingRow> findSeries(Long seriesId) {
        return jdbc.query("""
                SELECT b.id, b.user_id, u.real_name applicant_name, b.dept_id, d.dept_name,
                       b.room_id, r.room_name, r.floor_no, b.subject, b.attend_num, b.start_time,
                       b.end_time, b.status, b.audit_user, b.audit_time, b.audit_comment, b.cancel_reason, b.applicant_role,
                       b.series_id, b.series_index, b.series_total
                FROM meeting_book b JOIN sys_user u ON u.id=b.user_id JOIN dept d ON d.id=b.dept_id
                LEFT JOIN meeting_room r ON r.id=b.room_id
                WHERE b.series_id=? AND b.deleted=0 ORDER BY b.series_index,b.id
                """, this::mapRows, seriesId);
    }

    /**
     * 按 ID 升序发起组内预约锁定查询，调用前须先锁房间，锁后再重读。
     */
    public void lockSeries(Long seriesId) {
        /**
         * 构造列表、分页共用的可见 SQL 与绑定参数。
         * 先按角色和部门、参会关系收紧，再加 mine/pending、时间与状态筛选。
         * pending 额外排除本人及部门管理员不可处理的管理员申请。
         * 动态部分是受控片段，业务值均通过占位符绑定。
         */
        jdbc.query("SELECT id FROM meeting_book WHERE series_id=? AND deleted=0 ORDER BY id FOR UPDATE",
                (rs, n) -> rs.getLong(1), seriesId);
    }

    /**
     * 仅更新指定用户未删除的 DRAFT，外层须先验证归属并锁定。
     */
    public void updateDraft(Long id, Long userId, String subject, Integer attendNum, LocalDateTime start, LocalDateTime end, Long roomId) {
        jdbc.update("UPDATE meeting_book SET subject=?, attend_num=?, start_time=?, end_time=?, room_id=?, update_by=?, update_time=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND status='DRAFT' AND deleted=0",
                subject, attendNum, start, end, roomId, userId, id, userId);
    }

    /**
     * 设置本人草稿的房间，不改变状态或开始占用资源。
     */
    public void setDraftRoom(Long id, Long roomId, Long userId) {
        jdbc.update("UPDATE meeting_book SET room_id=?, update_by=?, update_time=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND status='DRAFT' AND deleted=0", roomId, userId, id, userId);
    }

    /**
     * 用名单替换有效参会关系：旧行先失效，再恢复已有或新增关系。
     * 不物理删除，利用恢复旧行避免唯一键下重复插入。
     */
    public void replaceAttendees(Long bookId, List<Long> userIds) {
        // 关系唯一键存在，恢复旧行可支持多次草稿编辑，无需物理删行。
        jdbc.update("UPDATE meeting_attendee SET deleted=1, update_time=CURRENT_TIMESTAMP WHERE book_id=?", bookId);
        for (Long userId : userIds) {
            int updated = jdbc.update("UPDATE meeting_attendee SET deleted=0, update_time=CURRENT_TIMESTAMP WHERE book_id=? AND user_id=?", bookId, userId);
            if (updated == 0) jdbc.update("INSERT INTO meeting_attendee(book_id,user_id,create_by,update_by) SELECT ?,?,user_id,user_id FROM meeting_book WHERE id=?", bookId, userId, bookId);
        }
    }

    /**
     * 替换按类型的设备需求，恢复已有类型并更新数量，和实际设备绑定分离。
     */
    public void replaceDevices(Long bookId, Map<String, Integer> devices) {
        jdbc.update("UPDATE meeting_device_requirement SET deleted=1, update_time=CURRENT_TIMESTAMP WHERE book_id=?", bookId);
        devices.forEach((type, quantity) -> {
            int updated = jdbc.update("UPDATE meeting_device_requirement SET quantity=?, deleted=0, update_time=CURRENT_TIMESTAMP WHERE book_id=? AND device_type=?", quantity, bookId, type);
            if (updated == 0) jdbc.update("INSERT INTO meeting_device_requirement(book_id,device_type,quantity,create_by,update_by) SELECT ?,?,?,user_id,user_id FROM meeting_book WHERE id=?", bookId, type, quantity, bookId);
        });
    }

    /**
     * 按 ID 读取有效参会人，用于排期权限等判断。
     */
    public List<Long> listAttendeeIds(Long bookId) {
        return jdbc.query("SELECT user_id FROM meeting_attendee WHERE book_id=? AND deleted=0 ORDER BY user_id", (rs, rowNum) -> rs.getLong(1), bookId);
    }

    /**
     * 关联用户和部门生成参会人摘要，只取有效关系。
     */
    public List<AttendeeRow> listAttendees(Long bookId) {
        return jdbc.query("SELECT a.user_id, u.real_name, u.username, d.dept_name FROM meeting_attendee a JOIN sys_user u ON u.id=a.user_id JOIN dept d ON d.id=u.dept_id WHERE a.book_id=? AND a.deleted=0 ORDER BY a.user_id",
                (rs, rowNum) -> new AttendeeRow(rs.getLong("user_id"), rs.getString("real_name"), rs.getString("username"), rs.getString("dept_name")), bookId);
    }

    /**
     * 读取按类型排序的有效需求，不是实际设备 ID 清单。
     */
    public List<DeviceRequirement> listDevices(Long bookId) {
        return jdbc.query("SELECT device_type, quantity FROM meeting_device_requirement WHERE book_id=? AND deleted=0 ORDER BY device_type",
                (rs, rowNum) -> new DeviceRequirement(rs.getString("device_type"), rs.getInt("quantity")), bookId);
    }

    /**
     * 把有效需求映射为类型到数量，便于逐项匹配。
     */
    public Map<String, Integer> requirementMap(Long bookId) {
        return jdbc.query("SELECT device_type, quantity FROM meeting_device_requirement WHERE book_id=? AND deleted=0", rs -> { Map<String, Integer> result = new java.util.HashMap<>(); while (rs.next()) result.put(rs.getString(1), rs.getInt(2)); return result; }, bookId);
    }

    /**
     * 仅 DRAFT 可更新为 PENDING，并保存房间及申请时角色。
     * 返回影响数供 Service 检查，不能绕过外层资源和权限校验直接调用。
     */
    public int submit(Long id, Long roomId, Long operatorId, String applicantRole) {
        return jdbc.update("UPDATE meeting_book SET room_id=?, applicant_role=?, status='PENDING', update_by=?, update_time=CURRENT_TIMESTAMP WHERE id=? AND status='DRAFT' AND deleted=0", roomId, applicantRole, operatorId, id);
    }

    /**
     * 限定旧状态 PENDING，保存审批人、时间和意见；目标状态由服务层决定。
     */
    public int updateApproval(Long id, String status, Long operatorId, String comment) {
        return jdbc.update("UPDATE meeting_book SET status=?, audit_user=?, audit_time=CURRENT_TIMESTAMP, audit_comment=?, update_by=?, update_time=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING' AND deleted=0", status, operatorId, comment, operatorId, id);
    }

    /**
     * 更新组内 PENDING 并返回条数。
     * 服务层先过期已到开始时刻记录，再校验影响数与未来目标场次数一致。
     */
    public int updateSeriesApproval(Long seriesId, String status, Long operatorId, String comment) {
        return jdbc.update("""
                UPDATE meeting_book SET status=?, audit_user=?, audit_time=CURRENT_TIMESTAMP, audit_comment=?, update_by=?, update_time=CURRENT_TIMESTAMP
                WHERE series_id=? AND status='PENDING' AND deleted=0
                """, status, operatorId, comment, operatorId, seriesId);
    }

    /**
     * 只将占资源的三种状态改 CANCELED，时间和权限在服务层检查。
     * 返回 0 表示已不存在或旧状态不允许取消。
     */
    public int cancel(Long id, Long operatorId, String reason) {
        return jdbc.update("UPDATE meeting_book SET status='CANCELED', cancel_reason=?, update_by=?, update_time=CURRENT_TIMESTAMP WHERE id=? AND status IN ('PENDING','APPROVED','IN_PROGRESS') AND deleted=0", reason, operatorId, id);
    }

    /**
     * 取消组内未来的待审批或已批准场次，不动进行中和终态。
     */
    public int cancelSeries(Long seriesId, Long operatorId, String reason, LocalDateTime now) {
        return jdbc.update("""
                UPDATE meeting_book SET status='CANCELED', cancel_reason=?, update_by=?, update_time=CURRENT_TIMESTAMP
                WHERE series_id=? AND status IN ('PENDING','APPROVED') AND start_time>? AND deleted=0
                """, reason, operatorId, seriesId, now);
    }

    /**
     * 追加人工审批历史，与审批状态在同一事务提交。
     */
    public void addApprovalHistory(Long bookId, Long operatorId, String action, String comment) {
        jdbc.update("INSERT INTO approval_history(book_id,operator_id,action,comment,create_by,update_by) VALUES (?,?,?,?,?,?)", bookId, operatorId, action, comment, operatorId, operatorId);
    }

    /**
     * 追加业务审计，调用方不能在内容中写密码或令牌。
     */
    public void addAudit(Long operatorId, String type, String content) {
        jdbc.update("INSERT INTO audit_log(operator_id,operate_type,content,create_by,update_by) VALUES (?,?,?,?,?)", operatorId, type, content, operatorId, operatorId);
    }

    /**
     * 按最新操作优先返回最多 100 条，权限由服务层校验。
     */
    public List<AuditRow> listAudit() {
        return jdbc.query("SELECT a.id, a.operator_id, u.real_name, a.operate_type, a.content, a.operate_time FROM audit_log a JOIN sys_user u ON u.id=a.operator_id ORDER BY a.operate_time DESC, a.id DESC LIMIT 100",
                (rs, rowNum) -> new AuditRow(rs.getLong("id"), rs.getLong("operator_id"), rs.getString("real_name"), rs.getString("operate_type"), rs.getString("content"), rs.getObject("operate_time", LocalDateTime.class)));
    }

    /**
     * 查询与指定区间重叠的有效占用，不含草稿、终态。
     * 返回内部主题及参会 ID，服务必须脱敏后才能输出排期。
     */
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

    /**
     * 批量过期待审批底层工具，不自动记录审计通知；当前定时主路径使用逐条 transition。
     */
    public int expirePending(LocalDateTime now) { return jdbc.update("UPDATE meeting_book SET status='EXPIRED', update_time=CURRENT_TIMESTAMP WHERE status='PENDING' AND start_time <= ? AND deleted=0", now); }
    /**
     * 批量开始已批准且未结束会议，不自动逐条审计，非当前调度主路径。
     */
    public int startApproved(LocalDateTime now) { return jdbc.update("UPDATE meeting_book SET status='IN_PROGRESS', update_time=CURRENT_TIMESTAMP WHERE status='APPROVED' AND start_time <= ? AND end_time > ? AND deleted=0", now, now); }
    /**
     * 批量结束已批准或进行中会议，不自动逐条审计，非当前调度主路径。
     */
    public int completeRunning(LocalDateTime now) { return jdbc.update("UPDATE meeting_book SET status='COMPLETED', update_time=CURRENT_TIMESTAMP WHERE status IN ('APPROVED','IN_PROGRESS') AND end_time <= ? AND deleted=0", now); }

    /**
     * 按最后更新时间逻辑清理草稿及关联，先关联后主表，返回主记录影响数。
     * 调用方须提供事务保证，不影响非草稿历史记录。
     */
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
    /**
     * 查询需推进的候选；Service 还须加锁重读，不能把候选当最终状态。
     */
    public List<MeetingRow> dueStates(LocalDateTime now) {
        List<Long> ids = jdbc.query("SELECT id FROM meeting_book WHERE deleted=0 AND ((status IN ('PENDING','APPROVED') AND start_time<=?) OR (status='IN_PROGRESS' AND end_time<=?)) ORDER BY room_id,id", (rs, n) -> rs.getLong(1), now, now);
        return ids.stream().map(this::findById).toList();
    }

    /**
     * 查询 (from, to] 开始的已批准会议，已经开始不补发会前提醒，去重交消息键。
     */
    public List<MeetingRow> upcomingApproved(LocalDateTime from, LocalDateTime to) {
        List<Long> ids = jdbc.query("""
                SELECT id FROM meeting_book
                WHERE status='APPROVED' AND deleted=0 AND start_time > ? AND start_time <= ?
                ORDER BY start_time, id
                """, (rs, rowNum) -> rs.getLong(1), from, to);
        return ids.stream().map(this::findById).toList();
    }

    /**
     * 按 ID、未删除及旧状态条件更新，返回是否成功。
     * 仅成功时 Service 写通知和审计，避免重复任务重复副作用。
     */
    public boolean transition(Long id, String previous, String next) {
        return jdbc.update("UPDATE meeting_book SET status=?, update_time=CURRENT_TIMESTAMP WHERE id=? AND status=? AND deleted=0", next, id, previous) == 1;
    }

    /**
     * 预约聚合读模型，含申请时角色；series 字段为空表示普通预约。
     */
    public record MeetingRow(Long id, Long userId, String applicantName, Long deptId, String deptName, Long roomId, String roomName, Integer floorNo,
                             String subject, Integer attendNum, LocalDateTime startTime, LocalDateTime endTime, String status, Long auditUser,
                             LocalDateTime auditTime, String auditComment, String cancelReason, List<AttendeeRow> attendees, List<DeviceRequirement> devices,
                             String applicantRole, Long seriesId, Integer seriesIndex, Integer seriesTotal) {}
    /**
     * 参会人展示摘要，不含密码。
     */
    public record AttendeeRow(Long userId, String realName, String username, String deptName) {}
    /**
     * 类型与需求量，不是实际设备 ID。
     */
    public record DeviceRequirement(String deviceType, Integer quantity) {}
    /**
     * 审计展示模型，操作者姓名来自关联用户。
     */
    public record AuditRow(Long id, Long operatorId, String operatorName, String operateType, String content, LocalDateTime operateTime) {}
    /**
     * 排期内部投影，主题未脱敏，输出前需服务处理。
     */
    public record OccupancyRow(Long id, Long userId, Long deptId, String subject, LocalDateTime startTime, LocalDateTime endTime,
                               String status, Long roomId, String roomName, List<Long> attendeeIds) {}
}
