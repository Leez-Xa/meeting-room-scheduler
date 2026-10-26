package com.company.meeting.report;

import com.company.meeting.auth.UserPrincipal;
import com.company.meeting.common.BizException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class MeetingReportService {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final MeetingReportRepository reports;

    public MeetingReportService(MeetingReportRepository reports) { this.reports = reports; }

    public byte[] export(UserPrincipal principal, LocalDate start, LocalDate end, Long roomId, String status) {
        if (!"SYSTEM_ADMIN".equals(principal.role()) && !"DEPT_ADMIN".equals(principal.role())) {
            throw new BizException("FORBIDDEN", "只有管理员可以导出会议报表", org.springframework.http.HttpStatus.FORBIDDEN);
        }
        if (start != null && end != null && start.isAfter(end)) throw new BizException("INVALID_TIME", "报表开始日期不能晚于结束日期");
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
        if (text.contains(",") || text.contains("\r") || text.contains("\n") || text.contains("\"")) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }
}
