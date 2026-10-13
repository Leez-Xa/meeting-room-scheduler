package com.company.meeting.meeting;

import com.company.meeting.auth.UserPrincipal;
import com.company.meeting.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.util.MultiValueMap;

@RestController
@RequestMapping("/api/v1")
public class MeetingController {
    private final MeetingService service;

    public MeetingController(MeetingService service) { this.service = service; }

    @GetMapping("/dashboard")
    public ApiResponse<MeetingDtos.Dashboard> dashboard(@AuthenticationPrincipal UserPrincipal user) { return ApiResponse.ok(service.dashboard(user)); }

    @GetMapping("/meetings")
    public ApiResponse<?> list(@RequestParam(defaultValue = "mine") String scope,
                                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                                                  @RequestParam(defaultValue="1") int page,
                                                                  @RequestParam(defaultValue="20") int pageSize,
                                                                  @RequestParam(required=false) MeetingStatus status,
                                                                  @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(service.listPage(user, scope, start, end, page, pageSize, status));
    }

    @GetMapping("/meetings/{id}")
    public ApiResponse<MeetingRepository.MeetingRow> detail(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal user) { return ApiResponse.ok(service.detail(id, user)); }

    @PostMapping("/meetings/drafts")
    public ApiResponse<MeetingRepository.MeetingRow> saveDraft(@Valid @RequestBody MeetingDtos.DraftRequest request, @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(service.saveDraft(request, user), "草稿已保存");
    }

    @PostMapping("/meetings/submit")
    public ApiResponse<MeetingDtos.SubmissionResult> submit(@Valid @RequestBody MeetingDtos.SubmitRequest request, @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(service.submit(request, user), "预约已提交，等待审批");
    }

    @GetMapping("/meetings/recommendations")
    public ApiResponse<List<MeetingDtos.Recommendation>> recommend(@RequestParam Integer attendNum, @RequestParam Integer floorNo,
                                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                                                    @RequestParam MultiValueMap<String, String> params) {
        Map<String, Integer> devices = new java.util.HashMap<>();
        params.forEach((key, values) -> {
            if (key.startsWith("device_") && !values.isEmpty()) devices.put(key.substring("device_".length()), Integer.valueOf(values.get(0)));
        });
        return ApiResponse.ok(service.recommend(attendNum, floorNo, start, end, devices));
    }

    @PostMapping("/meetings/{id}/approval")
    public ApiResponse<MeetingRepository.MeetingRow> approval(@PathVariable Long id, @Valid @RequestBody MeetingDtos.ApprovalRequest request, @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(service.approve(id, request, user), request.approved() ? "审批通过" : "已拒绝");
    }

    @PostMapping("/meetings/{id}/cancel")
    public ApiResponse<MeetingRepository.MeetingRow> cancel(@PathVariable Long id, @Valid @RequestBody(required = false) MeetingDtos.CancelRequest request, @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(service.cancel(id, request == null ? new MeetingDtos.CancelRequest(null) : request, user), "会议已取消");
    }

    @GetMapping("/schedule")
    public ApiResponse<List<MeetingDtos.ScheduleEvent>> schedule(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                                                  @AuthenticationPrincipal UserPrincipal user) { return ApiResponse.ok(service.schedule(start, end, user)); }

    @GetMapping("/audit")
    public ApiResponse<List<MeetingRepository.AuditRow>> audit(@AuthenticationPrincipal UserPrincipal user) { return ApiResponse.ok(service.audit(user)); }
}
