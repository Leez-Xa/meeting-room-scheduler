package com.company.meeting.report;

import com.company.meeting.auth.UserPrincipal;
import com.company.meeting.common.BizException;
import com.company.meeting.meeting.MeetingRepository;
import com.company.meeting.meeting.MeetingStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class MeetingReportService {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final MeetingReportRepository reports;
    private final MeetingRepository meetings;

    public MeetingReportService(MeetingReportRepository reports, MeetingRepository meetings) {
        this.reports = reports;
        this.meetings = meetings;
    }

    @Transactional
    public byte[] export(UserPrincipal principal, LocalDate start, LocalDate end, Long roomId, String status) {
        if (!"SYSTEM_ADMIN".equals(principal.role()) && !"DEPT_ADMIN".equals(principal.role())) {
            throw new BizException("FORBIDDEN", "只有管理员可以导出会议报表", org.springframework.http.HttpStatus.FORBIDDEN);
        }
        if (start != null && end != null && start.isAfter(end)) throw new BizException("INVALID_TIME", "报表开始日期不能晚于结束日期");
        if (roomId != null && roomId <= 0) throw new BizException("INVALID_REQUEST", "会议室ID不合法");
        status = status == null || status.isBlank() ? null : status.trim();
        if (status != null) {
            try { MeetingStatus.valueOf(status); }
            catch (IllegalArgumentException ex) { throw new BizException("INVALID_STATUS_FILTER", "报表状态不合法"); }
        }
        if ((start != null && (start.getYear() < 1000 || start.getYear() > 9999))
                || (end != null && (end.getYear() < 1000 || end.getYear() > 9999))) {
            throw new BizException("INVALID_TIME", "报表日期不在支持的范围内");
        }
        StringBuilder csv = new StringBuilder();
        csv.append("会议ID,周期组ID,场次,总场次,会议主题,申请人,部门,会议室,开始时间,结束时间,状态,参会人数,审批意见,取消原因\r\n");
        for (MeetingReportRepository.ReportRow row : reports.list(principal, start, end, roomId, status)) {
            csv.append(line(row.id(), row.seriesId(), row.seriesIndex(), row.seriesTotal(), row.subject(), row.applicantName(),
                    row.deptName(), row.roomName(), row.startTime() == null ? "" : TIME.format(row.startTime()),
                    row.endTime() == null ? "" : TIME.format(row.endTime()), row.status(), row.attendNum(), row.auditComment(), row.cancelReason()));
        }
        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[body.length + 3];
        result[0] = (byte) 0xEF; result[1] = (byte) 0xBB; result[2] = (byte) 0xBF;
        System.arraycopy(body, 0, result, 3, body.length);
        meetings.addAudit(principal.id(), "MEETING_REPORT_EXPORT", "导出会议报表：范围=" + principal.role()
                + "，开始=" + start + "，结束=" + end + "，会议室=" + roomId + "，状态=" + status);
        return result;
    }

    private String line(Object... values) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) result.append(',');
            result.append(escape(values[i]));
        }
        return result.append("\r\n").toString();
    }

    private String escape(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (value instanceof String) {
            String leading = text.stripLeading();
            if ((!leading.isEmpty() && "=+-@".indexOf(leading.charAt(0)) >= 0)
                    || text.startsWith("\t") || text.startsWith("\r") || text.startsWith("\n")) {
                text = "'" + text;
            }
        }
        if (text.contains(",") || text.contains("\r") || text.contains("\n") || text.contains("\"")) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }
}
