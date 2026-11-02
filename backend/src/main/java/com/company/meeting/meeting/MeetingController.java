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

/**
 * 会议业务 HTTP 适配层：解析 DTO、时间和范围，从认证上下文取得操作者。
 * 提交、审批、取消使用专门业务操作接口，不允许直接指定任意状态。
 * 注解验证字段结构，服务层承担授权、资源检查和事务。
 */
@RestController
@RequestMapping("/api/v1")
public class MeetingController {
    private final MeetingService service;

    public MeetingController(MeetingService service) { this.service = service; }

    /**
     * 当前登录人的工作台，不接受客户端指定其他用户身份。
     */
    @GetMapping("/dashboard")
    public ApiResponse<MeetingDtos.Dashboard> dashboard(@AuthenticationPrincipal UserPrincipal user) { return ApiResponse.ok(service.dashboard(user)); }

    /**
     * 分页查询 mine、visible 或 pending，实际权限由服务及 SQL 再约束。
     */
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
    /**
     * 按 ID 查详情，Service 独立授权，知道 ID 不代表有权读取。
     */
    public ApiResponse<MeetingRepository.MeetingRow> detail(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal user) { return ApiResponse.ok(service.detail(id, user)); }

    /**
     * 保存或更新草稿，不占资源。
     */
    @PostMapping("/meetings/drafts")
    public ApiResponse<MeetingRepository.MeetingRow> saveDraft(@Valid @RequestBody MeetingDtos.DraftRequest request, @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(service.saveDraft(request, user), "草稿已保存");
    }

    /**
     * 单次提交前人员冲突提示，不落库也不预占。
     */
    @PostMapping("/meetings/check-conflicts")
    public ApiResponse<MeetingDtos.ConflictPreview> checkConflicts(@Valid @RequestBody MeetingDtos.SubmitRequest request,
                                                                  @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(service.checkConflicts(request, user));
    }

    /**
     * 按周展开人员冲突预检，返回提示与确认指纹。
     */
    @PostMapping("/meetings/series/check-conflicts")
    public ApiResponse<MeetingDtos.ConflictPreview> checkRecurringConflicts(@Valid @RequestBody MeetingDtos.RecurringSubmitRequest request,
                                                                           @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(service.checkRecurringConflicts(request, user));
    }

    /**
     * 单次正式提交，事务内重查资源及人员确认，成功待审批。
     */
    @PostMapping("/meetings/submit")
    public ApiResponse<MeetingDtos.SubmissionResult> submit(@Valid @RequestBody MeetingDtos.SubmitRequest request, @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(service.submit(request, user), "预约已提交，等待审批");
    }

    /**
     * 提交全组周期，任一资源冲突整体失败。
     */
    @PostMapping("/meetings/series/submit")
    public ApiResponse<MeetingDtos.RecurringSubmissionResult> submitRecurring(@Valid @RequestBody MeetingDtos.RecurringSubmitRequest request,
                                                                                @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(service.submitRecurring(request, user), "周期预约已提交，等待审批");
    }

    /**
     * 把 device_类型 查询参数转换为需求，调用过滤和评分，不预占。
     */
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
    /**
     * 单次审批，周期场次不能经此接口绕过整组规则。
     */
    public ApiResponse<MeetingRepository.MeetingRow> approval(@PathVariable Long id, @Valid @RequestBody MeetingDtos.ApprovalRequest request, @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(service.approve(id, request, user), request.approved() ? "审批通过" : "已拒绝");
    }

    @PostMapping("/meetings/series/{seriesId}/approval")
    /**
     * 审批周期剩余合法场次，不依赖第一场状态。
     */
    public ApiResponse<MeetingDtos.SeriesActionResult> approveSeries(@PathVariable Long seriesId,
                                                                       @Valid @RequestBody MeetingDtos.ApprovalRequest request,
                                                                       @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(service.approveSeries(seriesId, request, user), request.approved() ? "周期预约审批通过" : "周期预约已拒绝");
    }

    @PostMapping("/meetings/{id}/cancel")
    /**
     * 无请求体时构造空原因，服务层决定是否必须填写。
     */
    public ApiResponse<MeetingRepository.MeetingRow> cancel(@PathVariable Long id, @Valid @RequestBody(required = false) MeetingDtos.CancelRequest request, @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(service.cancel(id, request == null ? new MeetingDtos.CancelRequest(null) : request, user), "会议已取消");
    }

    @PostMapping("/meetings/series/{seriesId}/cancel")
    /**
     * ONE 或 ALL 取消，组归属、时间和权限由服务层校验。
     */
    public ApiResponse<MeetingDtos.SeriesActionResult> cancelSeries(@PathVariable Long seriesId,
                                                                     @Valid @RequestBody MeetingDtos.SeriesCancelRequest request,
                                                                     @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(service.cancelSeries(seriesId, request, user), "周期预约已处理");
    }

    /**
     * 读取排期，服务端脱敏无权查看的主题。
     */
    @GetMapping("/schedule")
    public ApiResponse<List<MeetingDtos.ScheduleEvent>> schedule(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
                                                                  @AuthenticationPrincipal UserPrincipal user) { return ApiResponse.ok(service.schedule(start, end, user)); }

    /**
     * 读取审计，服务层再次限制系统管理员。
     */
    @GetMapping("/audit")
    public ApiResponse<List<MeetingRepository.AuditRow>> audit(@AuthenticationPrincipal UserPrincipal user) { return ApiResponse.ok(service.audit(user)); }
}
