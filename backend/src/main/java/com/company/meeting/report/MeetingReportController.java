package com.company.meeting.report;

import com.company.meeting.auth.UserPrincipal;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * CSV 文件响应入口，声明 UTF-8 与附件文件名。
 * 服务层检查导出角色和范围，响应不套普通 JSON 外壳。
 */
@RestController
@RequestMapping("/api/v1/reports")
public class MeetingReportController {
    private final MeetingReportService service;

    public MeetingReportController(MeetingReportService service) { this.service = service; }

    /**
     * 返回 CSV 附件，解析筛选条件，服务校验角色和范围。
     */
    @GetMapping(value = "/meetings.csv", produces = "text/csv")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                                         @RequestParam(required = false) Long roomId,
                                         @RequestParam(required = false) String status,
                                         @AuthenticationPrincipal UserPrincipal user) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename("meeting-report.csv", StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(service.export(user, start, end, roomId, status));
    }
}
