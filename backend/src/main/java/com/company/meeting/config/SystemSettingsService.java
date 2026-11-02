package com.company.meeting.config;

import com.company.meeting.auth.UserPrincipal;
import com.company.meeting.common.BizException;
import com.company.meeting.meeting.MeetingRepository;
import com.company.meeting.resource.RoomRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 运行期规则的持久化与并发协调。
 * 先按房间 ID 升序加锁，再锁配置行，与预约保持相同层级；
 * revision 检测过期编辑，开放规则变更不能使现有有效预约违规。
 * 旧库缺表只兼容读取，写操作须先迁移。
 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class SystemSettingsService {
    /**
     * 规则、版本及旧库迁移标记，revision 用于并发编辑检查。
     */
    public record SettingsView(Long revision, RuleSettings rules, LocalDateTime updatedAt, Long updatedBy, boolean migrationRequired) {}
    /**
     * 必须带读取时版本和完整规则的保存输入。
     */
    public record SettingsUpdate(Long revision, RuleSettings rules) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RuleProperties defaults;
    private final RoomRepository rooms;
    private final MeetingRepository meetings;
    private final Clock clock;

    public SystemSettingsService(JdbcTemplate jdbc, ObjectMapper mapper, RuleProperties defaults,
                                 RoomRepository rooms, MeetingRepository meetings, Clock clock) {
        this.jdbc = jdbc; this.mapper = mapper; this.defaults = defaults;
        this.rooms = rooms; this.meetings = meetings; this.clock = clock;
    }

    /**
     * 读单例配置行，无 JSON 用默认值；仅明确缺表时兼容，其他 SQL 错误抛出。
     */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public SettingsView current() {
        try {
            return jdbc.queryForObject("SELECT revision, settings_json, update_time, update_by FROM system_config WHERE id=1 AND deleted=0", (rs, n) -> {
                String json = rs.getString("settings_json");
                RuleSettings rules = json == null ? RuleSettings.from(defaults) : decode(json);
                return new SettingsView(rs.getLong("revision"), rules,
                        rs.getObject("update_time", LocalDateTime.class), rs.getObject("update_by", Long.class), false);
            });
        } catch (org.springframework.jdbc.BadSqlGrammarException ex) {
            if (!missingConfigTable(ex)) throw ex;
            return new SettingsView(0L, RuleSettings.from(defaults), null, null, true);
        }
    }

    /**
     * 锁房间再锁参数，检查旧 revision，拒绝使有效预约违规的新开放规则。
     * 成功递增版本，并审计前后参数。
     */
    public SettingsView update(SettingsUpdate request, UserPrincipal actor) {
        if (!actor.role().equals("SYSTEM_ADMIN")) throw new BizException("FORBIDDEN", "只有系统管理员可以修改参数", HttpStatus.FORBIDDEN);
        if (request.revision() == null || request.revision() < 0 || request.rules() == null) {
            throw new BizException("INVALID_CONFIG", "请提供完整参数及版本号");
        }
        // Same lock order as submission, approval, cancellation and resource maintenance.
        rooms.listRooms().stream().map(RoomRepository.RoomRow::id).sorted().forEach(rooms::lockRoom);
        // 此处在房间锁之后锁参数，与提交遵循相同顺序。
        lockConfig();
        SettingsView previous = current();
        // 行锁协调当前事务，revision 额外检测旧页面过期编辑。
        if (!previous.revision().equals(request.revision())) {
            throw new BizException("CONFIG_CHANGED", "参数已被其他管理员修改，请重新加载后再保存", HttpStatus.CONFLICT);
        }
        RuleSettings next = request.rules();
        if (!previous.rules().workdays().equals(next.workdays()) || !previous.rules().openTime().equals(next.openTime())
                || !previous.rules().closeTime().equals(next.closeTime())) {
            LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Shanghai")));
            long affected = jdbc.query("SELECT start_time,end_time FROM meeting_book WHERE deleted=0 AND status IN ('PENDING','APPROVED','IN_PROGRESS') AND end_time > ?",
                    (rs, n) -> {
                        var start = rs.getObject("start_time", LocalDateTime.class);
                        var end = rs.getObject("end_time", LocalDateTime.class);
                        return !next.workdays().contains(start.getDayOfWeek().getValue())
                                || start.toLocalTime().isBefore(next.openTime()) || end.toLocalTime().isAfter(next.closeTime());
                    }, now).stream().filter(Boolean::booleanValue).count();
            if (affected > 0) throw new BizException("CONFIG_IN_USE", "新开放规则影响" + affected + "个有效预约，请先处理预约", HttpStatus.CONFLICT);
        }
        /**
         * 锁房间再锁参数，检查旧 revision，拒绝使有效预约违规的新开放规则。
         * 成功递增版本，并审计前后参数。
         */
        jdbc.update("UPDATE system_config SET settings_json=?,revision=revision+1,update_by=?,update_time=CURRENT_TIMESTAMP WHERE id=1", encode(next), actor.id());
        meetings.addAudit(actor.id(), "SYSTEM_CONFIG_UPDATE", "系统参数版本 " + previous.revision() + " → " + (previous.revision() + 1)
                + "；原参数：" + encode(previous.rules()) + "；新参数：" + encode(next));
        /**
         * 读单例配置行，无 JSON 用默认值；仅明确缺表时兼容，其他 SQL 错误抛出。
         */
        return current();
    }

    /**
     * 提交中锁定规则，旧库缺表时仅读取回退默认值。
     */
    public RuleSettings lockForSubmission() {
        try { lockConfig(); }
        catch (BizException ex) {
            if (!ex.code().equals("CONFIG_UPGRADE_REQUIRED")) throw ex;
            return RuleSettings.from(defaults);
        }
        /**
         * 读单例配置行，无 JSON 用默认值；仅明确缺表时兼容，其他 SQL 错误抛出。
         */
        return current().rules();
    }

    /**
     * 锁 id=1 参数行，缺表返回 503 升级提示，不自动迁移。
     */
    private void lockConfig() {
        try { jdbc.queryForObject("SELECT id FROM system_config WHERE id=1 AND deleted=0 FOR UPDATE", Long.class); }
        catch (org.springframework.jdbc.BadSqlGrammarException ex) {
            if (!missingConfigTable(ex)) throw ex;
            throw new BizException("CONFIG_UPGRADE_REQUIRED", "数据库尚未升级，请先执行系统参数升级脚本", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 只识别表不存在 SQLState，不吞其他数据库错误。
     */
    private boolean missingConfigTable(org.springframework.jdbc.BadSqlGrammarException ex) {
        return "42S02".equals(ex.getSQLException().getSQLState());
    }

    /**
     * 读取 JSON 并重新验证，损坏参数作为内部错误抛出。
     */
    private RuleSettings decode(String json) {
        try { return mapper.readValue(json, RuleSettings.class); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("数据库中的系统参数不合法", ex); }
    }
    /**
     * 序列化完整规则，失败终止事务，避免保存不完整参数。
     */
    private String encode(RuleSettings rules) {
        try { return mapper.writeValueAsString(rules); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("系统参数序列化失败", ex); }
    }
}
