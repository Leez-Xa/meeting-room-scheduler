package com.company.meeting.meeting;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 预约请求和响应 record 集合，明确接口与业务实体边界。
 * 输入不开放任意 status；注解验证结构，服务端仍须重算人数并校验资源与范围。
 */
public final class MeetingDtos {
    private MeetingDtos() {}

    /**
     * 草稿：id 空新建，roomId 可空；人数只做正数结构校验，保存取去重名单长度。
     * attendeeIds 不必含申请人，服务自动加入；devices 为类型到数量。
     */
    public record DraftRequest(Long id, @NotBlank @Size(max=200) String subject, @NotNull @Min(1) Integer attendNum,
                               @NotNull LocalDateTime startTime, @NotNull LocalDateTime endTime,
                               Long roomId, List<Long> attendeeIds, Map<String, Integer> devices) {}

    /**
     * 单次提交，roomId 必填，id 可指本人草稿；conflictConfirmation 关联最新人员冲突。
     */
    public record SubmitRequest(Long id, @NotBlank @Size(max=200) String subject, @NotNull @Min(1) Integer attendNum,
                                 @NotNull LocalDateTime startTime, @NotNull LocalDateTime endTime,
                                 @NotNull Long roomId, List<Long> attendeeIds, Map<String, Integer> devices, String conflictConfirmation) {
        public SubmitRequest(Long id, String subject, Integer attendNum, LocalDateTime startTime, LocalDateTime endTime,
                             Long roomId, List<Long> attendeeIds, Map<String, Integer> devices) {
            this(id, subject, attendNum, startTime, endTime, roomId, attendeeIds, devices, null);
        }
    }

    /**
     * 每周重复，时间为首场，occurrenceCount 含首场且为 2–12，不支持任意周期表达式。
     */
    public record RecurringSubmitRequest(@NotBlank @Size(max=200) String subject, @NotNull @Min(1) Integer attendNum,
                                         @NotNull LocalDateTime startTime, @NotNull LocalDateTime endTime,
                                         @NotNull Long roomId, @NotNull @Min(2) @Max(12) Integer occurrenceCount,
                                         List<Long> attendeeIds, Map<String, Integer> devices, String conflictConfirmation) {
        public RecurringSubmitRequest(String subject, Integer attendNum, LocalDateTime startTime, LocalDateTime endTime,
                                      Long roomId, Integer occurrenceCount, List<Long> attendeeIds, Map<String, Integer> devices) {
            this(subject, attendNum, startTime, endTime, roomId, occurrenceCount, attendeeIds, devices, null);
        }
    }

    /**
     * 人员警告和确认指纹，无冲突时提示列表为空、指纹为空字符串。
     */
    public record ConflictPreview(List<String> warnings, String confirmationToken) {}
    /**
     * 明确批准或拒绝及意见，不接受任意目标状态。
     */
    public record ApprovalRequest(@NotNull Boolean approved, @Size(max=500) String comment) {}
    /**
     * 可选取消原因，是否必须由具体权限和操作场景决定。
     */
    public record CancelRequest(@Size(max=500) String reason) {}
    /**
     * 单次提交成功的待审批预约和已确认人员警告。
     */
    public record SubmissionResult(MeetingRepository.MeetingRow meeting, List<String> warnings) {}
    /**
     * 周期组 ID 与实际场次，各场独立保留业务状态。
     */
    public record RecurringSubmissionResult(Long seriesId, List<MeetingRepository.MeetingRow> meetings, List<String> warnings) {}
    /**
     * scope 为 ONE/ALL；ONE 时 meetingId 必须属于本组。
     */
    public record SeriesCancelRequest(@NotBlank String scope, Long meetingId, @Size(max=500) String reason) {}
    /**
     * 组操作后的最新场次列表，含保留的已取消和过期记录。
     */
    public record SeriesActionResult(Long seriesId, List<MeetingRepository.MeetingRow> meetings) {}
    /**
     * 推荐分项、总分与理由，distance 表示绝对楼层差。
     */
    public record Recommendation(Long roomId, String roomName, Integer floorNo, Integer capacity, Integer distance,
                                 Double capacityScore, Double deviceScore, Double floorScore, Double totalScore, String reason) {}
    /**
     * 排期事件，detailVisible=false 时 title 已在服务端脱敏。
     */
    public record ScheduleEvent(Long id, Long roomId, String roomName, String title, LocalDateTime startTime,
                                LocalDateTime endTime, String status, Boolean detailVisible) {}
    /**
     * 工作台指标，availableRooms 是启用数，myUpcoming 包含个人未结束且仍待审批或批准的记录。
     */
    public record Dashboard(Long totalRooms, Long availableRooms, Long myPending, Long myUpcoming, Long pendingApproval) {}
}
