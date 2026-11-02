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

/**
 * 将会议事件转换为站内消息。
 * 提交发合法审批人，其他结果与提醒发去重后的申请人和参会人。
 * 通知保存在同一数据库，不调用外部短信、邮件或消息队列。
 */
@Service
@Transactional
public class NoticeService {
    private final NoticeRepository notices;
    private final MeetingRepository meetings;
    private final UserRepository users;
    private final com.company.meeting.config.SystemSettingsService settings;

    public NoticeService(NoticeRepository notices, MeetingRepository meetings, UserRepository users, com.company.meeting.config.SystemSettingsService settings) {
        this.notices = notices; this.meetings = meetings; this.users = users;
        this.settings = settings;
    }

    /**
     * 校验页边界，查询本人收件箱。
     */
    public NoticeDtos.NoticePage list(UserPrincipal user, boolean unreadOnly, int page, int pageSize) {
        if (page < 1 || page > 100000 || pageSize < 1 || pageSize > 100) {
            throw new com.company.meeting.common.BizException("INVALID_PAGE", "分页参数不合法");
        }
        return notices.list(user.id(), unreadOnly, page, pageSize);
    }

    /**
     * 只取本人未读，不接收客户端接收人。
     */
    public long unreadCount(UserPrincipal user) { return notices.unreadCount(user.id()); }

    /**
     * 标记本人消息，影响 0 统一不存在，避免泄露他人消息存在性。
     */
    public void markRead(Long id, UserPrincipal user) {
        if (notices.markRead(id, user.id()) == 0) {
            throw new com.company.meeting.common.BizException("NOT_FOUND", "消息不存在");
        }
    }

    /**
     * 批量本人已读，其他收件人不受影响。
     */
    public void markAllRead(UserPrincipal user) { notices.markAllRead(user.id()); }

    /**
     * 发送给合法且非本人的审批人。
     */
    public void notifySubmitted(MeetingRepository.MeetingRow meeting) {
        String content = "“" + meeting.subject() + "”提交了" + meeting.startTime() + "的预约，请及时处理。";
        for (Long userId : users.listApproverIds(meeting.deptId(), meeting.applicantRole(), meeting.userId())) {
            notices.insertIgnore(userId, meeting.id(), "新的会议预约待审批", content, "SUBMIT:" + meeting.id() + ":" + userId);
        }
    }

    /**
     * 广播审批结果，键区分批准和拒绝。
     */
    public void notifyApproval(MeetingRepository.MeetingRow meeting, boolean approved, String comment) {
        String title = approved ? "会议预约已通过" : "会议预约未通过";
        String content = "“" + meeting.subject() + "”的预约" + (approved ? "已审批通过" : "已被拒绝")
                + (comment == null || comment.isBlank() ? "。" : "，审批意见：" + comment);
        notifyParticipants(meeting, "APPROVAL:" + meeting.id() + ":" + (approved ? "APPROVED" : "REJECTED"), title, content);
    }

    /**
     * 广播取消及原因，稳定键防重复。
     */
    public void notifyCanceled(MeetingRepository.MeetingRow meeting, String reason) {
        String content = "“" + meeting.subject() + "”已取消" + (reason == null || reason.isBlank() ? "。" : "，原因：" + reason);
        notifyParticipants(meeting, "CANCEL:" + meeting.id(), "会议预约已取消", content);
    }

    /**
     * 告知申请人与参会人未及时审批，资源已释放。
     */
    public void notifyExpired(MeetingRepository.MeetingRow meeting) {
        notifyParticipants(meeting, "EXPIRED:" + meeting.id(), "会议预约已过期",
                "“" + meeting.subject() + "”未在开始前完成审批，预约已过期。");
    }

    /**
     * 扫描 (now, 提前量截止] 的已批准会议，逐人去重。
     * 保留历史 REMINDER_15M 键，即使提前分钟变化也不重复发同场提醒。
     */
    public void sendUpcomingReminders(LocalDateTime now) {
        int minutes = settings.current().rules().reminderMinutes();
        for (MeetingRepository.MeetingRow meeting : meetings.upcomingApproved(now, now.plusMinutes(minutes))) {
            // Keep the legacy key stable so changing the lead time cannot resend old reminders.
            notifyParticipants(meeting, "REMINDER_15M:" + meeting.id(), "会议即将开始",
                    "“" + meeting.subject() + "”将在" + minutes + "分钟内开始，地点：" + meeting.roomName() + "。");
        }
    }

    /**
     * 有序集合合并申请人和参会人，以事件和收件人生成唯一键。
     */
    private void notifyParticipants(MeetingRepository.MeetingRow meeting, String keyPrefix, String title, String content) {
        Set<Long> participantIds = new LinkedHashSet<>();
        participantIds.add(meeting.userId());
        meeting.attendees().forEach(attendee -> participantIds.add(attendee.userId()));
        for (Long userId : participantIds) {
            notices.insertIgnore(userId, meeting.id(), title, content, keyPrefix + ":" + userId);
        }
    }
}
