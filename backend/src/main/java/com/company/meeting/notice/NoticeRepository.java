package com.company.meeting.notice;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 站内消息持久化及去重，读写按接收人限制。
 * 先查键只减少重复尝试，最终以 notice_key 数据库唯一约束防止并发重复写入。
 */
@Repository
public class NoticeRepository {
    private final JdbcTemplate jdbc;

    public NoticeRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * 按接收人分页，unread 统计本人全部未读而不局限本页。
     */
    public NoticeDtos.NoticePage list(Long userId, boolean unreadOnly, int page, int pageSize) {
        String filter = unreadOnly ? " AND readed=0" : "";
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM message_notice WHERE user_id=? AND deleted=0" + filter,
                Long.class, userId);
        long unread = unreadCount(userId);
        int offset = (page - 1) * pageSize;
        List<NoticeDtos.NoticeRow> records = jdbc.query("""
                SELECT id, book_id, title, content, readed, create_time
                FROM message_notice
                WHERE user_id=? AND deleted=0
                """ + filter + " ORDER BY create_time DESC, id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new NoticeDtos.NoticeRow(rs.getLong("id"),
                        (Long) rs.getObject("book_id"), rs.getString("title"), rs.getString("content"),
                        rs.getBoolean("readed"), rs.getObject("create_time", LocalDateTime.class)),
                userId, pageSize, offset);
        return new NoticeDtos.NoticePage(records, total, unread, page, pageSize);
    }

    /**
     * 统计接收人未删除未读记录。
     */
    public long unreadCount(Long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM message_notice WHERE user_id=? AND deleted=0 AND readed=0",
                Long.class, userId);
    }

    /**
     * SQL 同时限定消息 ID 与接收人，防止越权。
     */
    public int markRead(Long id, Long userId) {
        return jdbc.update("UPDATE message_notice SET readed=1, update_time=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND deleted=0", id, userId);
    }

    /**
     * 批量本人有效未读，重复调用不影响已读。
     */
    public int markAllRead(Long userId) {
        return jdbc.update("UPDATE message_notice SET readed=1, update_time=CURRENT_TIMESTAMP WHERE user_id=? AND deleted=0 AND readed=0", userId);
    }

    /**
     * 业务键幂等插入：先查只是优化，最终数据库唯一键防并发。
     * 只忽略重复键异常，其他失败仍抛出回滚。
     */
    public void insertIgnore(Long userId, Long bookId, String title, String content, String noticeKey) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM message_notice WHERE notice_key=?", Integer.class, noticeKey) > 0) return;
        try {
            jdbc.update("""
                    INSERT INTO message_notice(user_id, book_id, title, content, readed, notice_key, create_by, update_by)
                    VALUES (?,?,?,?,0,?,?,?)
                    """, userId, bookId, title, content, noticeKey, userId, userId);
        } catch (DuplicateKeyException ignored) {
            // Unique notice_key keeps retries and concurrent scheduler runs idempotent.
        }
    }
}