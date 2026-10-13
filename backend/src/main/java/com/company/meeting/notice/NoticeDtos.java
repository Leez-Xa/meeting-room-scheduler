package com.company.meeting.notice;

import java.time.LocalDateTime;

public final class NoticeDtos {
    private NoticeDtos() {}

    public record NoticeRow(Long id, Long bookId, String title, String content, Boolean read,
                            LocalDateTime createTime) {}

    public record NoticePage(java.util.List<NoticeRow> records, Long total, Long unread,
                             Integer page, Integer pageSize) {}
}