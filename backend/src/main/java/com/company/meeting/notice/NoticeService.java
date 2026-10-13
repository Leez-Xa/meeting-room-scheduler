package com.company.meeting.notice;

import com.company.meeting.auth.UserPrincipal;
import com.company.meeting.meeting.MeetingRepository;
import com.company.meeting.resource.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class NoticeService {
    private final NoticeRepository notices;
    private final MeetingRepository meetings;
    private final UserRepository users;

    public NoticeService(NoticeRepository notices, MeetingRepository meetings, UserRepository users) {
        this.notices = notices; this.meetings = meetings; this.users = users;
    }

    public NoticeDtos.NoticePage list(UserPrincipal user, boolean unreadOnly, int page, int pageSize) {
        if (page < 1 || page > 100000 || pageSize < 1 || pageSize > 100) {
            throw new com.company.meeting.common.BizException("INVALID_PAGE", "分页参数不合法");
        }
        return notices.list(user.id(), unreadOnly, page, pageSize);
    }

    public long unreadCount(UserPrincipal user) { return notices.unreadCount(user.id()); }

    public void markRead(Long id, UserPrincipal user) {
        if (notices.markRead(id, user.id()) == 0) {
            throw new com.company.meeting.common.BizException("NOT_FOUND", "消息不存在");
        }
    }

    public void markAllRead(UserPrincipal user) { notices.markAllRead(user.id()); }

    public void notifySubmitted(MeetingRepository.MeetingRow meeting) {
        String content = "“" + meeting.subject() + "”提交了" + meeting.startTime() + "的预约，请及时处理。";
        for (Long userId : users.listApproverIds(meeting.deptId(), meeting.applicantRole(), meeting.userId())) {
            notices.insertIgnore(userId, meeting.id(), "新的会议预约待审批", content, "SUBMIT:" + meeting.id() + ":" + userId);
        }
    }

    public void notifyApproval(MeetingRepository.MeetingRow meeting, boolean approved, String comment) {
        String title = approved ? "会议预约已通过" : "会议预约未通过";
        String content = "“" + meeting.subject() + "”的预约" + (approved ? "已审批通过" : "已被拒绝")
                + (comment == null || comment.isBlank() ? "。" : "，审批意见：" + comment);
        notifyParticipants(meeting, "APPROVAL:" + meeting.id() + ":" + (approved ? "APPROVED" : "REJECTED"), title, content);
    }

    public void notifyCanceled(MeetingRepository.MeetingRow meeting, String reason) {
        String content = "“" + meeting.subject() + "”已取消" + (reason == null || reason.isBlank() ? "。" : "，原因：" + reason);
        notifyParticipants(meeting, "CANCEL:" + meeting.id(), "会议预约已取消", content);
    }

    public void notifyExpired(MeetingRepository.MeetingRow meeting) {
        notifyParticipants(meeting, "EXPIRED:" + meeting.id(), "会议预约已过期",
                "“" + meeting.subject() + "”未在开始前完成审批，预约已过期。");
    }

    public void sendUpcomingReminders(LocalDateTime now) {
        for (MeetingRepository.MeetingRow meeting : meetings.upcomingApproved(now, now.plusMinutes(15))) {
            notifyParticipants(meeting, "REMINDER_15M:" + meeting.id(), "会议即将开始",
                    "“" + meeting.subject() + "”将在15分钟内开始，地点：" + meeting.roomName() + "。");
        }
    }

    private void notifyParticipants(MeetingRepository.MeetingRow meeting, String keyPrefix, String title, String content) {
        Set<Long> participantIds = new LinkedHashSet<>();
        participantIds.add(meeting.userId());
        meeting.attendees().forEach(attendee -> participantIds.add(attendee.userId()));
        for (Long userId : participantIds) {
            notices.insertIgnore(userId, meeting.id(), title, content, keyPrefix + ":" + userId);
        }
    }
}