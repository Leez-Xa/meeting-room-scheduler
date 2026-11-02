package com.company.meeting.notice;

import java.time.LocalDateTime;

/**
 * 消息中心响应，数据库 readed 映射为 read，返回内容和分页计数，不暴露去重键。
 */
public final class NoticeDtos {
    private NoticeDtos() {}

    /**
     * 消息展示，不返回内部幂等键。
     */
    public record NoticeRow(Long id, Long bookId, String title, String content, Boolean read,
                            LocalDateTime createTime) {}

    /**
     * 本页消息和本人全部未读数。
     */
    public record NoticePage(java.util.List<NoticeRow> records, Long total, Long unread,
                             Integer page, Integer pageSize) {}
}