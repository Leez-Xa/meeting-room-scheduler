package com.company.meeting.meeting;

import com.company.meeting.auth.UserPrincipal;
import com.company.meeting.auth.DepartmentScopePolicy;
import com.company.meeting.common.BizException;
import com.company.meeting.common.BusinessRules;
import com.company.meeting.resource.RoomRepository;
import com.company.meeting.resource.UserRepository;
import com.company.meeting.notice.NoticeService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class MeetingService {
    private final MeetingRepository meetings;
    private final RoomRepository rooms;
    private final UserRepository users;
    private final BusinessRules rules;
    private final NoticeService notices;

    public MeetingService(MeetingRepository meetings, RoomRepository rooms, UserRepository users, BusinessRules rules, NoticeService notices) {
        this.meetings = meetings; this.rooms = rooms; this.users = users;
        this.rules = rules; this.notices = notices;
    }

    public Map<String, Object> currentUser(UserPrincipal principal) {
        return Map.of("id", principal.id(), "username", principal.username(), "realName", principal.realName(),
                "deptId", principal.deptId(), "deptName", principal.deptName(), "role", principal.role(), "floorNo", principal.floorNo());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MeetingRepository.MeetingRow saveDraft(MeetingDtos.DraftRequest request, UserPrincipal principal) {
        validateBasic(request.subject(), request.attendNum(), request.startTime(), request.endTime());
        List<Long> attendeeIds = normalizeAttendees(request.attendeeIds(), principal);
        Long id = request.id();
        if (id == null) {
            id = meetings.insertDraft(principal.id(), principal.deptId(), request.subject(), attendeeIds.size(), request.startTime(), request.endTime());
        } else {
            ownDraft(id, principal);
        }
        if (request.roomId() != null && rooms.findRoom(request.roomId()) == null) throw new BizException("ROOM_UNAVAILABLE", "会议室不存在");
        meetings.updateDraft(id, principal.id(), request.subject(), attendeeIds.size(), request.startTime(), request.endTime(), request.roomId());
        meetings.replaceAttendees(id, attendeeIds);
        meetings.replaceDevices(id, normalizeDevices(request.devices()));
        return meetings.findById(id);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MeetingDtos.SubmissionResult submit(MeetingDtos.SubmitRequest request, UserPrincipal principal) {
        validateBasic(request.subject(), request.attendNum(), request.startTime(), request.endTime());
        rules.validateBookingWindow(request.startTime(), request.endTime());
        List<Long> attendeeIds = normalizeAttendees(request.attendeeIds(), principal);
        Map<String, Integer> deviceRequirements = normalizeDevices(request.devices());
        RoomRepository.RoomRow room = rooms.lockRoom(request.roomId());
        rules.validateLockedBookingWindow(request.startTime(), request.endTime());
        Long draftId = requestDraftForSubmit(request, principal);
        if (room == null || !room.active()) throw new BizException("ROOM_UNAVAILABLE", "会议室不存在或已停用");
        if (room.capacity() < attendeeIds.size()) throw new BizException("CAPACITY_NOT_ENOUGH", "会议室容量不足");
        Map<String, Integer> inventory = rooms.deviceInventory(room.id());
        for (var entry : deviceRequirements.entrySet()) {
            if (inventory.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                throw new BizException("DEVICE_NOT_ENOUGH", "会议室缺少设备：" + entry.getKey());
            }
        }
        if (rooms.hasRoomConflict(room.id(), request.startTime(), request.endTime(), draftId)) {
            throw new BizException("ROOM_CONFLICT", "会议室在该时间段已被占用");
        }
        meetings.updateDraft(draftId, principal.id(), request.subject(), attendeeIds.size(), request.startTime(), request.endTime(), room.id());
        meetings.replaceAttendees(draftId, attendeeIds);
        meetings.replaceDevices(draftId, deviceRequirements);
        if (meetings.submit(draftId, room.id(), principal.id(), principal.role()) == 0) throw new BizException("INVALID_STATUS", "只有草稿可以提交");
        meetings.bindDevices(draftId, room.devices().stream().filter(RoomRepository.DeviceRow::active).toList(), principal.id());
        List<String> warnings = attendeeIds.stream()
                .filter(id -> rooms.hasUserConflict(id, request.startTime(), request.endTime(), draftId))
                .map(id -> users.listUsers().stream().filter(user -> user.id().equals(id)).findFirst().map(UserRepository.UserSummary::realName).orElse("参会人") + "在该时段已有会议")
                .toList();
        meetings.addAudit(principal.id(), "MEETING_SUBMIT", "提交预约：" + request.subject());
        MeetingRepository.MeetingRow submitted = meetings.findById(draftId);
        notices.notifySubmitted(submitted);
        return new MeetingDtos.SubmissionResult(submitted, warnings);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MeetingDtos.RecurringSubmissionResult submitRecurring(MeetingDtos.RecurringSubmitRequest request, UserPrincipal principal) {
        validateBasic(request.subject(), request.attendNum(), request.startTime(), request.endTime());
        if (request.occurrenceCount() == null || request.occurrenceCount() < 2 || request.occurrenceCount() > 12) {
            throw new BizException("INVALID_OCCURRENCE_COUNT", "周期次数必须为2到12次");
        }
        List<Long> attendeeIds = normalizeAttendees(request.attendeeIds(), principal);
        Map<String, Integer> deviceRequirements = normalizeDevices(request.devices());
        RoomRepository.RoomRow room = rooms.lockRoom(request.roomId());
        if (room == null || !room.active()) throw new BizException("ROOM_UNAVAILABLE", "会议室不存在或已停用");
        rules.validateLockedRecurringFirstWindow(request.startTime(), request.endTime());
        for (int index = 1; index < request.occurrenceCount(); index++) {
            rules.validateLockedRecurringOccurrence(request.startTime().plusWeeks(index), request.endTime().plusWeeks(index));
        }
        if (room.capacity() < attendeeIds.size()) throw new BizException("CAPACITY_NOT_ENOUGH", "会议室容量不足");
        Map<String, Integer> inventory = rooms.deviceInventory(room.id());
        for (var entry : deviceRequirements.entrySet()) {
            if (inventory.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                throw new BizException("DEVICE_NOT_ENOUGH", "会议室缺少设备：" + entry.getKey());
            }
        }
        List<String> conflicts = new ArrayList<>();
        for (int index = 0; index < request.occurrenceCount(); index++) {
            LocalDateTime start = request.startTime().plusWeeks(index);
            LocalDateTime end = request.endTime().plusWeeks(index);
            if (rooms.hasRoomConflict(room.id(), start, end, null)) conflicts.add(start.toLocalDate().toString());
        }
        if (!conflicts.isEmpty()) {
            throw new BizException("RECURRING_CONFLICT", "周期预约存在会议室冲突：" + String.join("、", conflicts),
                    org.springframework.http.HttpStatus.CONFLICT, Map.of("conflictDates", conflicts));
        }
        Long seriesId = meetings.insertSeries(principal.id(), principal.deptId(), room.id(), request.subject(), attendeeIds.size(),
                request.startTime(), request.endTime(), request.occurrenceCount());
        List<MeetingRepository.MeetingRow> created = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<RoomRepository.DeviceRow> actualDevices = room.devices().stream().filter(RoomRepository.DeviceRow::active).toList();
        for (int index = 0; index < request.occurrenceCount(); index++) {
            LocalDateTime start = request.startTime().plusWeeks(index);
            LocalDateTime end = request.endTime().plusWeeks(index);
            Long bookId = meetings.insertSeriesOccurrence(seriesId, index + 1, request.occurrenceCount(), principal.id(), principal.deptId(), room.id(),
                    request.subject(), attendeeIds.size(), start, end, principal.role());
            meetings.replaceAttendees(bookId, attendeeIds);
            meetings.replaceDevices(bookId, deviceRequirements);
            meetings.bindDevices(bookId, actualDevices, principal.id());
            for (Long attendeeId : attendeeIds) {
                if (rooms.hasUserConflict(attendeeId, start, end, bookId)) {
                    String name = users.listUsers().stream().filter(user -> user.id().equals(attendeeId)).findFirst()
                            .map(UserRepository.UserSummary::realName).orElse("参会人");
                    warnings.add(start.toLocalDate() + "：" + name + "在该时段已有会议");
                }
            }
            MeetingRepository.MeetingRow createdMeeting = meetings.findById(bookId);
            meetings.addAudit(principal.id(), "MEETING_SERIES_SUBMIT", "提交周期预约第" + (index + 1) + "场：" + request.subject());
            notices.notifySubmitted(createdMeeting);
            created.add(createdMeeting);
        }
        return new MeetingDtos.RecurringSubmissionResult(seriesId, created, warnings);
    }

    private Long requestDraftForSubmit(MeetingDtos.SubmitRequest request, UserPrincipal principal) {
        if (request.id() != null) {
            ownDraft(request.id(), principal);
            return request.id();
        }
        return meetings.insertDraft(principal.id(), principal.deptId(), request.subject(), 1, request.startTime(), request.endTime());
    }

    public List<MeetingRepository.MeetingRow> list(UserPrincipal principal, String scope, LocalDateTime start, LocalDateTime end) {
        validateScope(principal,scope);
        return meetings.list(principal, scope, start, end);
    }
    public com.company.meeting.resource.AdminDtos.Page<MeetingRepository.MeetingRow> listPage(UserPrincipal principal, String scope,
            LocalDateTime start, LocalDateTime end, int page, int size, MeetingStatus status) {
        validateScope(principal,scope);
        if(page<1 || page>100000 || size<1 || size>100) throw new BizException("INVALID_PAGE","分页参数不合法");
        return meetings.listPage(principal,scope,start,end,page,size,status);
    }
    private void validateScope(UserPrincipal actor,String scope) {
        if(!Set.of("mine","visible","pending").contains(scope)) throw new BizException("INVALID_SCOPE","会议范围不合法");
        if(scope.equals("pending") && actor.role().equals("EMPLOYEE")) throw new BizException("FORBIDDEN","普通员工不能查看审批列表",org.springframework.http.HttpStatus.FORBIDDEN);
    }

    public MeetingRepository.MeetingRow detail(Long id, UserPrincipal principal) {
        MeetingRepository.MeetingRow meeting = meetings.findById(id);
        if (meeting == null) throw new BizException("NOT_FOUND", "会议不存在");
        if (!canView(meeting, principal)) throw new BizException("FORBIDDEN", "没有权限查看该会议", org.springframework.http.HttpStatus.FORBIDDEN);
        return meeting;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MeetingRepository.MeetingRow approve(Long id, MeetingDtos.ApprovalRequest request, UserPrincipal principal) {
        MeetingRepository.MeetingRow meeting = meetings.findById(id);
        if (meeting == null) throw new BizException("NOT_FOUND", "会议不存在");
        if (meeting.userId().equals(principal.id())) throw new BizException("SELF_APPROVAL", "禁止审批自己的申请", org.springframework.http.HttpStatus.FORBIDDEN);
        if (!canApprove(meeting, principal)) throw new BizException("FORBIDDEN", "没有权限审批该会议", org.springframework.http.HttpStatus.FORBIDDEN);
        RoomRepository.RoomRow room = rooms.lockRoom(meeting.roomId());
        meeting = meetings.lockById(id);
        if (!"PENDING".equals(meeting.status())) throw new BizException("INVALID_STATUS", "只有待审批会议可以审批");
        if (!meeting.startTime().isAfter(rules.now())) throw new BizException("ALREADY_STARTED", "申请已到开始时间，不能审批");
        if (request.approved()) {
            if (room == null || !room.active() || rooms.hasRoomConflict(room.id(), meeting.startTime(), meeting.endTime(), meeting.id())) {
                throw new BizException("ROOM_CONFLICT", "审批失败：会议室已被占用或不可用");
            }
            if (meetings.updateApproval(id, "APPROVED", principal.id(), request.comment()) == 0) throw new BizException("INVALID_STATUS", "会议状态已变化");
            meetings.addApprovalHistory(id, principal.id(), "APPROVE", request.comment());
            meetings.addAudit(principal.id(), "MEETING_APPROVE", "审批通过：" + meeting.subject());
            notices.notifyApproval(meetings.findById(id), true, request.comment());
        } else {
            if (meetings.updateApproval(id, "REJECTED", principal.id(), request.comment()) == 0) throw new BizException("INVALID_STATUS", "会议状态已变化");
            meetings.addApprovalHistory(id, principal.id(), "REJECT", request.comment());
            meetings.addAudit(principal.id(), "MEETING_REJECT", "审批拒绝：" + meeting.subject());
            notices.notifyApproval(meetings.findById(id), false, request.comment());
        }
        return meetings.findById(id);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MeetingDtos.SeriesActionResult approveSeries(Long seriesId, MeetingDtos.ApprovalRequest request, UserPrincipal principal) {
        List<MeetingRepository.MeetingRow> series = meetings.findSeries(seriesId);
        if (series.isEmpty()) throw new BizException("NOT_FOUND", "周期预约不存在");
        for (MeetingRepository.MeetingRow meeting : series) {
            if (meeting.userId().equals(principal.id())) throw new BizException("SELF_APPROVAL", "禁止审批自己的申请", org.springframework.http.HttpStatus.FORBIDDEN);
            if (!canApprove(meeting, principal)) throw new BizException("FORBIDDEN", "没有权限审批该周期预约", org.springframework.http.HttpStatus.FORBIDDEN);
        }
        RoomRepository.RoomRow room = rooms.lockRoom(series.get(0).roomId());
        meetings.lockSeries(seriesId);
        series = meetings.findSeries(seriesId);
        if (series.stream().anyMatch(meeting -> !"PENDING".equals(meeting.status()))) throw new BizException("INVALID_STATUS", "周期预约中存在不可审批的场次");
        if (series.stream().anyMatch(meeting -> !meeting.startTime().isAfter(rules.now()))) throw new BizException("ALREADY_STARTED", "周期预约中存在已开始场次");
        if (request.approved() && (room == null || !room.active())) throw new BizException("ROOM_UNAVAILABLE", "会议室不存在或已停用");
        if (request.approved()) {
            List<String> conflicts = series.stream().filter(meeting -> rooms.hasRoomConflictExceptSeries(room.id(), meeting.startTime(), meeting.endTime(), seriesId))
                    .map(meeting -> meeting.startTime().toLocalDate().toString()).toList();
            if (!conflicts.isEmpty()) throw new BizException("RECURRING_CONFLICT", "审批时发现会议室冲突：" + String.join("、", conflicts), org.springframework.http.HttpStatus.CONFLICT, Map.of("conflictDates", conflicts));
        }
        String status = request.approved() ? "APPROVED" : "REJECTED";
        meetings.updateSeriesApproval(seriesId, status, principal.id(), request.comment());
        for (MeetingRepository.MeetingRow meeting : series) {
            meetings.addApprovalHistory(meeting.id(), principal.id(), request.approved() ? "APPROVE" : "REJECT", request.comment());
            meetings.addAudit(principal.id(), request.approved() ? "MEETING_SERIES_APPROVE" : "MEETING_SERIES_REJECT", (request.approved() ? "周期预约审批通过：" : "周期预约拒绝：") + meeting.subject());
            notices.notifyApproval(meetings.findById(meeting.id()), request.approved(), request.comment());
        }
        return new MeetingDtos.SeriesActionResult(seriesId, meetings.findSeries(seriesId));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MeetingRepository.MeetingRow cancel(Long id, MeetingDtos.CancelRequest request, UserPrincipal principal) {
        MeetingRepository.MeetingRow meeting = meetings.findById(id);
        if (meeting == null) throw new BizException("NOT_FOUND", "会议不存在");
        boolean owner = meeting.userId().equals(principal.id());
        boolean admin = principal.role().equals("SYSTEM_ADMIN");
        if (!owner && !admin) throw new BizException("FORBIDDEN", "没有权限取消该会议", org.springframework.http.HttpStatus.FORBIDDEN);
        boolean maintenance = admin && request.reason() != null && !request.reason().isBlank();
        if (!meeting.startTime().isAfter(rules.now()) && !maintenance) throw new BizException("ALREADY_STARTED", "会议开始后申请人不能取消");
        if (admin && !owner && (request.reason() == null || request.reason().isBlank())) throw new BizException("REASON_REQUIRED", "管理员取消会议必须填写原因");
        rooms.lockRoom(meeting.roomId());
        meeting = meetings.lockById(id);
        if (!meeting.startTime().isAfter(rules.now()) && !maintenance) throw new BizException("ALREADY_STARTED", "会议开始后申请人不能取消");
        if (!meeting.endTime().isAfter(rules.now())) throw new BizException("ALREADY_ENDED", "会议已结束，不能取消");
        if (meetings.cancel(id, principal.id(), request.reason()) == 0) throw new BizException("INVALID_STATUS", "该状态不能取消");
        meetings.addAudit(principal.id(), "MEETING_CANCEL", "取消会议：" + meeting.subject());
        MeetingRepository.MeetingRow canceled = meetings.findById(id);
        notices.notifyCanceled(canceled, request.reason());
        return canceled;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MeetingDtos.SeriesActionResult cancelSeries(Long seriesId, MeetingDtos.SeriesCancelRequest request, UserPrincipal principal) {
        if (!Set.of("ONE", "ALL").contains(request.scope())) throw new BizException("INVALID_SCOPE", "周期取消范围必须为ONE或ALL");
        List<MeetingRepository.MeetingRow> series = meetings.findSeries(seriesId);
        if (series.isEmpty()) throw new BizException("NOT_FOUND", "周期预约不存在");
        MeetingRepository.MeetingRow first = series.get(0);
        boolean owner = first.userId().equals(principal.id());
        boolean admin = principal.role().equals("SYSTEM_ADMIN");
        if (!owner && !admin) throw new BizException("FORBIDDEN", "没有权限取消该周期预约", org.springframework.http.HttpStatus.FORBIDDEN);
        if (admin && !owner && (request.reason() == null || request.reason().isBlank())) throw new BizException("REASON_REQUIRED", "管理员取消会议必须填写原因");
        Long roomId = first.roomId();
        rooms.lockRoom(roomId);
        meetings.lockSeries(seriesId);
        series = meetings.findSeries(seriesId);
        LocalDateTime now = rules.now();
        if ("ONE".equals(request.scope())) {
            if (request.meetingId() == null) throw new BizException("INVALID_REQUEST", "单次取消必须提供场次ID");
            MeetingRepository.MeetingRow target = series.stream().filter(item -> item.id().equals(request.meetingId())).findFirst().orElse(null);
            if (target == null) throw new BizException("NOT_FOUND", "周期场次不存在");
            boolean maintenance = admin && request.reason() != null && !request.reason().isBlank();
            if (!target.startTime().isAfter(now) && !maintenance) throw new BizException("ALREADY_STARTED", "会议开始后申请人不能取消");
            if (!target.endTime().isAfter(now)) throw new BizException("ALREADY_ENDED", "会议已结束，不能取消");
            if (meetings.cancel(target.id(), principal.id(), request.reason()) == 0) throw new BizException("INVALID_STATUS", "该场次不能取消");
            MeetingRepository.MeetingRow canceled = meetings.findById(target.id());
            meetings.addAudit(principal.id(), "MEETING_SERIES_CANCEL_ONE", "取消周期预约场次：" + canceled.subject());
            notices.notifyCanceled(canceled, request.reason());
            return new MeetingDtos.SeriesActionResult(seriesId, meetings.findSeries(seriesId));
        }
        int changed = meetings.cancelSeries(seriesId, principal.id(), request.reason(), now);
        if (changed == 0) throw new BizException("INVALID_STATUS", "没有可取消的未开始场次");
        List<MeetingRepository.MeetingRow> canceled = meetings.findSeries(seriesId);
        for (MeetingRepository.MeetingRow meeting : canceled) {
            if ("CANCELED".equals(meeting.status())) {
                meetings.addAudit(principal.id(), "MEETING_SERIES_CANCEL", "取消周期预约场次：" + meeting.subject());
                notices.notifyCanceled(meeting, request.reason());
            }
        }
        return new MeetingDtos.SeriesActionResult(seriesId, canceled);
    }

    public List<MeetingDtos.Recommendation> recommend(Integer attendNum, Integer floorNo, LocalDateTime start, LocalDateTime end, Map<String, Integer> requirements) {
        if (attendNum == null || attendNum < 1 || floorNo == null) throw new BizException("INVALID_REQUEST", "参会人数和楼层不合法");
        if ((start == null) != (end == null)) throw new BizException("INVALID_TIME", "请同时提供开始和结束时间");
        var settings = rules.settings();
        if (start != null) rules.validateBookingWindow(start, end, settings);
        Map<String, Integer> needed = normalizeDevices(requirements);
        return rooms.listRooms().stream().filter(RoomRepository.RoomRow::active).filter(room -> room.capacity() >= attendNum)
                .filter(room -> devicesMatch(room, needed)).filter(room -> start == null || end == null || !rooms.hasRoomConflict(room.id(), start, end, null))
                .map(room -> score(room, attendNum, floorNo, needed, settings)).sorted(Comparator.comparing(MeetingDtos.Recommendation::totalScore).reversed().thenComparing(MeetingDtos.Recommendation::roomId)).toList();
    }

    public List<MeetingDtos.ScheduleEvent> schedule(LocalDateTime start, LocalDateTime end, UserPrincipal principal) {
        List<MeetingDtos.ScheduleEvent> events = meetings.listOccupancies(start, end).stream().map(row -> {
            boolean visible = row.userId().equals(principal.id()) || row.attendeeIds().contains(principal.id()) || principal.role().equals("SYSTEM_ADMIN") || (principal.role().equals("DEPT_ADMIN") && principal.deptId().equals(row.deptId()));
            return new MeetingDtos.ScheduleEvent(row.id(), row.roomId(), row.roomName(), visible ? row.subject() : "已占用", row.startTime(), row.endTime(), row.status(), visible);
        }).toList();
        return events;
    }

    public MeetingDtos.Dashboard dashboard(UserPrincipal principal) {
        long total = rooms.listRooms().size();
        long available = rooms.listRooms().stream().filter(RoomRepository.RoomRow::active).count();
        List<MeetingRepository.MeetingRow> own = meetings.list(principal, "mine", rules.now(), null);
        long pending = own.stream().filter(item -> item.status().equals("PENDING")).count();
        long upcoming = own.stream().filter(item -> Set.of("PENDING", "APPROVED").contains(item.status())).count();
        long approval = (principal.role().equals("SYSTEM_ADMIN") || principal.role().equals("DEPT_ADMIN")) ? meetings.list(principal, "pending", null, null).stream().filter(item -> !item.userId().equals(principal.id())).count() : 0;
        return new MeetingDtos.Dashboard(total, available, pending, upcoming, approval);
    }

    public List<RoomRepository.RoomRow> rooms() { return rooms.listRooms(); }
    public List<UserRepository.UserSummary> users() { return users.listUsers(); }
    public Map<String,Object> bookingRules() {
        var settings = rules.settings();
        return Map.of("zone","Asia/Shanghai","serverNow",rules.now(),"openTime",settings.openTime().toString(),
            "closeTime",settings.closeTime().toString(),"workdays",settings.workdays(),
            "weights",Map.of("capacity",settings.capacityWeight(),"device",settings.deviceWeight(),"floor",settings.floorWeight()));
    }
    public List<MeetingRepository.AuditRow> audit(UserPrincipal principal) {
        if (!principal.role().equals("SYSTEM_ADMIN")) throw new BizException("FORBIDDEN", "只有系统管理员可以查看审计日志", org.springframework.http.HttpStatus.FORBIDDEN);
        return meetings.listAudit();
    }

    @Scheduled(fixedDelayString = "${meeting.scheduler-delay-ms:30000}")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void advanceStates() {
        LocalDateTime now = rules.now();
        // 和资源维护相同，按会议室 ID 固定顺序加锁，再处理预约。
        rooms.listRooms().stream().map(RoomRepository.RoomRow::id).sorted().forEach(rooms::lockRoom);
        for (var candidate : meetings.dueStates(now)) {
            var meeting = meetings.lockById(candidate.id());
            if (meeting == null) continue;
            String next = meeting.status().equals("PENDING") ? "EXPIRED" : !meeting.endTime().isAfter(now) ? "COMPLETED" : "IN_PROGRESS";
            if (meetings.transition(meeting.id(), meeting.status(), next)) {
                meetings.addAudit(meeting.userId(), "AUTO_" + next, "系统自动流转：" + meeting.subject() + " → " + next);
                if ("EXPIRED".equals(next)) notices.notifyExpired(meetings.findById(meeting.id()));
            }
        }
        notices.sendUpcomingReminders(now);
        meetings.cleanupStaleDrafts(now.minusDays(rules.settings().draftRetentionDays()));
    }

    private MeetingDtos.Recommendation score(RoomRepository.RoomRow room, Integer attendNum, Integer floorNo, Map<String, Integer> needed, com.company.meeting.config.RuleSettings settings) {
        double capacityScore = Math.max(0, 100 - ((room.capacity() - attendNum) * 100.0 / Math.max(1, room.capacity())));
        Map<String,Integer> inventory=rooms.deviceInventory(room.id());
        int required=needed.values().stream().mapToInt(Integer::intValue).sum();
        int available=needed.keySet().stream().mapToInt(type->inventory.getOrDefault(type,0)).sum();
        double deviceScore = required==0 ? 100 : required * 100.0 / Math.max(required,available);
        int distance = Math.abs(room.floorNo() - floorNo);
        double floorScore = Math.max(0, 100 - distance * settings.floorPenalty());
        double total = capacityScore * settings.capacityWeight() + deviceScore * settings.deviceWeight() + floorScore * settings.floorWeight();
        return new MeetingDtos.Recommendation(room.id(), room.roomName(), room.floorNo(), room.capacity(), distance,
                round(capacityScore), round(deviceScore), round(floorScore), round(total), "容量匹配 " + round(capacityScore) + " 分，设备匹配 " + round(deviceScore) + " 分，距离相差 " + distance + " 层");
    }

    private boolean devicesMatch(RoomRepository.RoomRow room, Map<String, Integer> needed) {
        Map<String, Integer> inventory = room.devices().stream().filter(RoomRepository.DeviceRow::active)
                .collect(Collectors.groupingBy(RoomRepository.DeviceRow::deviceType,
                        Collectors.summingInt(RoomRepository.DeviceRow::quantity)));
        return needed.entrySet().stream().allMatch(entry -> inventory.getOrDefault(entry.getKey(), 0) >= entry.getValue());
    }

    private List<Long> normalizeAttendees(List<Long> requested, UserPrincipal principal) {
        List<Long> ids = new ArrayList<>(); ids.add(principal.id()); if (requested != null) ids.addAll(requested);
        List<Long> unique = ids.stream().filter(Objects::nonNull).distinct().toList();
        Set<Long> known = users.listUsers().stream().map(UserRepository.UserSummary::id).collect(Collectors.toSet());
        if (unique.stream().anyMatch(id -> !known.contains(id))) throw new BizException("INVALID_ATTENDEE", "参会人必须是内部员工");
        return unique;
    }

    private Map<String, Integer> normalizeDevices(Map<String, Integer> devices) {
        if (devices == null) return Map.of();
        if (devices.entrySet().stream().anyMatch(e -> !Set.of("PROJECTOR", "MIC", "VIDEO", "DISPLAY").contains(e.getKey()) || e.getValue() == null || e.getValue() < 0 || e.getValue() > 100)) {
            throw new BizException("INVALID_DEVICE_REQUIREMENT", "设备类型或数量不合法");
        }
        return devices.entrySet().stream().filter(entry -> entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Integer::max, LinkedHashMap::new));
    }

    private void validateBasic(String subject, Integer attendNum, LocalDateTime start, LocalDateTime end) {
        if (subject == null || subject.isBlank()) throw new BizException("INVALID_SUBJECT", "请填写会议主题");
        if (attendNum == null || attendNum < 1) throw new BizException("INVALID_ATTENDEE_COUNT", "参会人数必须大于 0");
        if (start == null || end == null || !start.isBefore(end)) throw new BizException("INVALID_TIME", "开始时间必须早于结束时间");
    }

    private MeetingRepository.MeetingRow ownDraft(Long id, UserPrincipal principal) {
        MeetingRepository.MeetingRow item = meetings.lockById(id);
        if (item == null || !item.userId().equals(principal.id()) || !item.status().equals("DRAFT")) throw new BizException("INVALID_STATUS", "只有本人草稿可以修改");
        return item;
    }

    private boolean canView(MeetingRepository.MeetingRow meeting, UserPrincipal principal) {
        return DepartmentScopePolicy.canAccessDepartment(principal, meeting.deptId())
                || meeting.userId().equals(principal.id()) || meeting.attendees().stream().anyMatch(item -> item.userId().equals(principal.id()));
    }

    private boolean canApprove(MeetingRepository.MeetingRow meeting, UserPrincipal principal) {
        if (meeting.applicantRole().equals("SYSTEM_ADMIN") && !principal.role().equals("SYSTEM_ADMIN")) return false;
        return DepartmentScopePolicy.canApproveDepartment(principal, meeting.deptId()) && !meeting.userId().equals(principal.id());
    }

    private double round(double value) { return Math.round(value * 10) / 10.0; }
}
