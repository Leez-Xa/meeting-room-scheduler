package com.company.meeting.meeting;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class MeetingDtos {
    private MeetingDtos() {}

    public record DraftRequest(Long id, @NotBlank @Size(max=200) String subject, @NotNull @Min(1) Integer attendNum,
                               @NotNull LocalDateTime startTime, @NotNull LocalDateTime endTime,
                               Long roomId, List<Long> attendeeIds, Map<String, Integer> devices) {}

    public record SubmitRequest(Long id, @NotBlank @Size(max=200) String subject, @NotNull @Min(1) Integer attendNum,
                                 @NotNull LocalDateTime startTime, @NotNull LocalDateTime endTime,
                                 @NotNull Long roomId, List<Long> attendeeIds, Map<String, Integer> devices) {}

    public record RecurringSubmitRequest(@NotBlank @Size(max=200) String subject, @NotNull @Min(1) Integer attendNum,
                                         @NotNull LocalDateTime startTime, @NotNull LocalDateTime endTime,
                                         @NotNull Long roomId, @NotNull @Min(2) @Max(12) Integer occurrenceCount,
                                         List<Long> attendeeIds, Map<String, Integer> devices) {}

    public record ApprovalRequest(@NotNull Boolean approved, @Size(max=500) String comment) {}
    public record CancelRequest(@Size(max=500) String reason) {}
    public record SubmissionResult(MeetingRepository.MeetingRow meeting, List<String> warnings) {}
    public record RecurringSubmissionResult(Long seriesId, List<MeetingRepository.MeetingRow> meetings, List<String> warnings) {}
    public record SeriesCancelRequest(@NotBlank String scope, Long meetingId, @Size(max=500) String reason) {}
    public record SeriesActionResult(Long seriesId, List<MeetingRepository.MeetingRow> meetings) {}
    public record Recommendation(Long roomId, String roomName, Integer floorNo, Integer capacity, Integer distance,
                                 Double capacityScore, Double deviceScore, Double floorScore, Double totalScore, String reason) {}
    public record ScheduleEvent(Long id, Long roomId, String roomName, String title, LocalDateTime startTime,
                                LocalDateTime endTime, String status, Boolean detailVisible) {}
    public record Dashboard(Long totalRooms, Long availableRooms, Long myPending, Long myUpcoming, Long pendingApproval) {}
}
