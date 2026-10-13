package com.company.meeting.notice;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class NoticeRepository {
    private final JdbcTemplate jdbc;

    public NoticeRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

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

    public long unreadCount(Long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM message_notice WHERE user_id=? AND deleted=0 AND readed=0",
                Long.class, userId);
    }

    public int markRead(Long id, Long userId) {
        return jdbc.update("UPDATE message_notice SET readed=1, update_time=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND deleted=0", id, userId);
    }

    public int markAllRead(Long userId) {
        return jdbc.update("UPDATE message_notice SET readed=1, update_time=CURRENT_TIMESTAMP WHERE user_id=? AND deleted=0 AND readed=0", userId);
    }

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