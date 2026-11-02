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

/**
 * 预约核心编排，集中处理时间、人员、资源、权限与生命周期。
 *
 * <p>资源占用写链路在 READ_COMMITTED 事务内执行：先锁会议室，再按需锁配置或预约，
 * 锁后重查资源，避免两个请求都基于旧空闲结果写入。待审批占资源，草稿不占。
 * 所有资源写路径必须遵守相同锁约定，不能绕过服务直接写库。
 *
 * <p>固定设备属于房间，提交记录其启用设备绑定；人员冲突仅要求明确确认。
 * 预约、关联、通知与审计在同库事务中保存，业务异常回滚本次写入。
 * 推荐与预检只代表查询时结果，不对之后提交作资源承诺。
 */
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

    /**
     * 构造前端用户摘要，排除内部身份中的密码编码。
     */
    public Map<String, Object> currentUser(UserPrincipal principal) {
        return Map.of("id", principal.id(), "username", principal.username(), "realName", principal.realName(),
                "deptId", principal.deptId(), "deptName", principal.deptName(), "role", principal.role(), "floorNo", principal.floorNo());
    }

    /**
     * 保存或更新本人草稿，不预占房间。
     * 草稿只做基本校验，不限制当前开放周；已有 ID 须锁定并验证归属和 DRAFT 状态。
     * 人数由去重参会名单计算，而非信任客户端声明值。
     * <p>这里允许保存未来开放窗口外的想法，正式提交才执行完整预约规则。
     * 草稿更新、参会关系和设备需求一起提交，防止人数与关联名单不一致。
     *
     * @param request id 为空新建，roomId 可暂不选
     * @param principal 当前申请人
     * @return 保存后的预约详情
     *
     */
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

    /**
     * 只读预检单次人员冲突，已有草稿须属于本人。
     * 不占资源，结果可能在提交前变化，因此提交必须重查。
     * @return 警告和确认指纹，无冲突时指纹为空字符串
     */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public MeetingDtos.ConflictPreview checkConflicts(MeetingDtos.SubmitRequest request, UserPrincipal principal) {
        validateBasic(request.subject(), request.attendNum(), request.startTime(), request.endTime());
        rules.validateBookingWindow(request.startTime(), request.endTime());
        if (request.id() != null) {
            MeetingRepository.MeetingRow draft = meetings.findById(request.id());
            if (draft == null || !draft.userId().equals(principal.id()) || !draft.status().equals("DRAFT")) {
                throw new BizException("INVALID_STATUS", "只有本人草稿可以修改");
            }
        }
        /**
         * 汇总各场人员冲突并计算确认指纹，不返回他人会议详情。
         * <p>排序名单后，将操作者、房间、时间、次数、草稿和实际冲突键一起做 SHA-256。
         * 参数或冲突变化会使指纹变化，避免旧确认对应新冲突。指纹不是认证凭证。
         * @param count 场次数，单次为 1
         * @param exceptId 排除本次草稿
         * @return 提示文本与对应指纹
         */
        return conflictPreview(normalizeAttendees(request.attendeeIds(), principal), request.startTime(), request.endTime(),
                1, request.id(), request.roomId(), principal);
    }

    /**
     * 按周展开人员冲突预检，次数包含首场。
     * 这里检查首场开放周，正式提交还须逐场验证后续日期和资源。
     */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public MeetingDtos.ConflictPreview checkRecurringConflicts(MeetingDtos.RecurringSubmitRequest request, UserPrincipal principal) {
        validateBasic(request.subject(), request.attendNum(), request.startTime(), request.endTime());
        validateOccurrenceCount(request.occurrenceCount());
        rules.validateBookingWindow(request.startTime(), request.endTime());
        /**
         * 汇总各场人员冲突并计算确认指纹，不返回他人会议详情。
         * <p>排序名单后，将操作者、房间、时间、次数、草稿和实际冲突键一起做 SHA-256。
         * 参数或冲突变化会使指纹变化，避免旧确认对应新冲突。指纹不是认证凭证。
         * @param count 场次数，单次为 1
         * @param exceptId 排除本次草稿
         * @return 提示文本与对应指纹
         */
        return conflictPreview(normalizeAttendees(request.attendeeIds(), principal), request.startTime(), request.endTime(),
                request.occurrenceCount(), null, request.roomId(), principal);
    }

    /**
     * 限制周期为 2–12 场，同时限制一次事务展开规模。
     */
    private void validateOccurrenceCount(Integer count) {
        if (count == null || count < 2 || count > 12) throw new BizException("INVALID_OCCURRENCE_COUNT", "周期次数必须为2到12次");
    }

    /**
     * 汇总各场人员冲突并计算确认指纹，不返回他人会议详情。
     * <p>排序名单后，将操作者、房间、时间、次数、草稿和实际冲突键一起做 SHA-256。
     * 参数或冲突变化会使指纹变化，避免旧确认对应新冲突。指纹不是认证凭证。
     * <p>例：用户确认 A 的冲突后，其他人又新增 B 的冲突，重新计算的摘要不再相等，
     * 服务端会返回新的提示而非沿用旧确认。这里只保证确认内容对应当前查询结果，
     * 人员不是独占资源，不会为了人员冲突加排他锁。
     *
     * @param count 场次数，单次为 1
     * @param exceptId 排除本次草稿
     * @return 提示文本与对应指纹
     *
     */
    private MeetingDtos.ConflictPreview conflictPreview(List<Long> attendeeIds, LocalDateTime start, LocalDateTime end,
                                                         int count, Long exceptId, Long roomId, UserPrincipal principal) {
        List<Long> sortedAttendees = attendeeIds.stream().sorted().toList();
        Map<Long, String> names = users.listUsers().stream().collect(Collectors.toMap(UserRepository.UserSummary::id, UserRepository.UserSummary::realName));
        List<String> warnings = new ArrayList<>();
        StringBuilder fingerprint = new StringBuilder().append(principal.id()).append('|').append(exceptId).append('|')
                .append(roomId).append('|').append(start).append('|').append(end).append('|').append(count).append('|').append(sortedAttendees);
        for (int index = 0; index < count; index++) {
            LocalDateTime occurrenceStart = start.plusWeeks(index), occurrenceEnd = end.plusWeeks(index);
            for (Long attendeeId : sortedAttendees) {
                List<String> keys = rooms.userConflictKeys(attendeeId, occurrenceStart, occurrenceEnd, exceptId);
                if (!keys.isEmpty()) {
                    warnings.add(occurrenceStart.toLocalDate() + " " + occurrenceStart.toLocalTime() + "–" + occurrenceEnd.toLocalTime()
                            + "：" + names.getOrDefault(attendeeId, "参会人") + "在该时段已有会议");
                    fingerprint.append('|').append(index).append(':').append(attendeeId).append(':').append(keys);
                }
            }
        }
        try {
            String token = warnings.isEmpty() ? "" : java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(fingerprint.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            return new MeetingDtos.ConflictPreview(warnings, token);
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    /**
     * 有人员冲突时要求提交值匹配最新指纹，否则返回 409 与最新提示。
     * 确认后允许继续，不会因此跳过资源或权限检查。
     */
    private void requireConflictConfirmation(MeetingDtos.ConflictPreview preview, String confirmation) {
        if (!preview.warnings().isEmpty() && !preview.confirmationToken().equals(confirmation)) {
            throw new BizException("ATTENDEE_CONFIRMATION_REQUIRED", "人员安排可能存在冲突，请确认后再提交",
                    org.springframework.http.HttpStatus.CONFLICT, preview);
        }
    }

    /**
     * 在同一事务内提交新申请或本人草稿，进入占资源的待审批状态。
     * <p>规范名单后锁房间，锁定读取规则，再查时间、容量、设备和房间冲突。
     * 人员冲突要求明确确认；检查通过后写主记录与关联，条件更新为 PENDING，
     * 绑定实际设备并生成审计、通知，任一业务异常使本次事务回滚。
     * <p>READ_COMMITTED 下，等待房间锁的请求取得锁后可重新查到前一事务已提交的占用。
     * 防重依赖所有资源写入路径遵守共用锁规则，不能仅依赖事务注解。
     * <p>执行顺序：基本校验 → 人员/需求规范化 → 房间锁 → 锁内规则与资源复查
     * → 最新人员确认 → 草稿与关联保存 → DRAFT 条件提交 → 实际设备/通知/审计。
     * 固定设备不跨房间共享，所以同一房间的有效占用也保护该房间固定设备。
     * 待审批就在冲突查询范围内，不必等待批准后才占用。
     *
     * @param request id 可为空，确认值须对应当前人员冲突
     * @param principal 来自认证上下文的申请人
     * @return 待审批预约和警告
     * @throws BizException 规则、资源或人员确认条件不满足
     *
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MeetingDtos.SubmissionResult submit(MeetingDtos.SubmitRequest request, UserPrincipal principal) {
        validateBasic(request.subject(), request.attendNum(), request.startTime(), request.endTime());
        rules.validateBookingWindow(request.startTime(), request.endTime());
        List<Long> attendeeIds = normalizeAttendees(request.attendeeIds(), principal);
        Map<String, Integer> deviceRequirements = normalizeDevices(request.devices());
        // 防重入口：等待同房间前一事务完成，取得锁后仍必须重查占用。
        RoomRepository.RoomRow room = rooms.lockRoom(request.roomId());
        // 锁内再读规则，协调管理员并发调整开放时段。
        rules.validateLockedBookingWindow(request.startTime(), request.endTime());
        if (request.id() != null) ownDraft(request.id(), principal);
        if (room == null || !room.active()) throw new BizException("ROOM_UNAVAILABLE", "会议室不存在或已停用");
        if (room.capacity() < attendeeIds.size()) throw new BizException("CAPACITY_NOT_ENOUGH", "会议室容量不足");
        Map<String, Integer> inventory = rooms.deviceInventory(room.id());
        for (var entry : deviceRequirements.entrySet()) {
            if (inventory.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                throw new BizException("DEVICE_NOT_ENOUGH", "会议室缺少设备：" + entry.getKey());
            }
        }
        if (rooms.hasRoomConflict(room.id(), request.startTime(), request.endTime(), request.id())) {
            throw new BizException("ROOM_CONFLICT", "会议室在该时间段已被占用");
        }
        MeetingDtos.ConflictPreview preview = conflictPreview(attendeeIds, request.startTime(), request.endTime(), 1, request.id(), room.id(), principal);
        requireConflictConfirmation(preview, request.conflictConfirmation());
        Long draftId = requestDraftForSubmit(request, principal);
        meetings.updateDraft(draftId, principal.id(), request.subject(), attendeeIds.size(), request.startTime(), request.endTime(), room.id());
        meetings.replaceAttendees(draftId, attendeeIds);
        meetings.replaceDevices(draftId, deviceRequirements);
        if (meetings.submit(draftId, room.id(), principal.id(), principal.role()) == 0) throw new BizException("INVALID_STATUS", "只有草稿可以提交");
        // 当前绑定该房间全部启用设备，不只是需求中填写的类型。
        meetings.bindDevices(draftId, room.devices().stream().filter(RoomRepository.DeviceRow::active).toList(), principal.id());
        List<String> warnings = preview.warnings();
        meetings.addAudit(principal.id(), "MEETING_SUBMIT", "提交预约：" + request.subject());
        MeetingRepository.MeetingRow submitted = meetings.findById(draftId);
        notices.notifySubmitted(submitted);
        return new MeetingDtos.SubmissionResult(submitted, warnings);
    }

    /**
     * 原子提交每周重复的全组场次。
     * <p>锁房间，首场检查开放周，后续可跨周但仍校验工作日、开放时段和未来时间。
     * 写入前收集全部资源冲突日期，存在冲突即返回 409；成功后写组与各场关联、通知、审计。
     * 任何后续运行时异常仍整体回滚，不能部分成功。
     * <p>人员冲突不会导致整组必须失败；确认有效后可继续。资源冲突始终阻止整组。
     *
     * @return 周期组 ID、顺序场次和人员警告
     *
     * @param request 首场时间、同一房间及每周重复总次数，次数包含首场
     * @param principal 当前申请人，保存其部门及申请时角色
     * @throws BizException 任一场时间或资源不满足、人数设备不符或人员确认过期
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MeetingDtos.RecurringSubmissionResult submitRecurring(MeetingDtos.RecurringSubmitRequest request, UserPrincipal principal) {
        validateBasic(request.subject(), request.attendNum(), request.startTime(), request.endTime());
        validateOccurrenceCount(request.occurrenceCount());
        List<Long> attendeeIds = normalizeAttendees(request.attendeeIds(), principal);
        Map<String, Integer> deviceRequirements = normalizeDevices(request.devices());
        // 防重入口：等待同房间前一事务完成，取得锁后仍必须重查占用。
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
        // 先汇总所有冲突日期，检查通过前不写入任何周期记录。
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
        MeetingDtos.ConflictPreview preview = conflictPreview(attendeeIds, request.startTime(), request.endTime(), request.occurrenceCount(), null, room.id(), principal);
        requireConflictConfirmation(preview, request.conflictConfirmation());
        Long seriesId = meetings.insertSeries(principal.id(), principal.deptId(), room.id(), request.subject(), attendeeIds.size(),
                request.startTime(), request.endTime(), request.occurrenceCount());
        List<MeetingRepository.MeetingRow> created = new ArrayList<>();
        List<String> warnings = preview.warnings();
        List<RoomRepository.DeviceRow> actualDevices = room.devices().stream().filter(RoomRepository.DeviceRow::active).toList();
        for (int index = 0; index < request.occurrenceCount(); index++) {
            LocalDateTime start = request.startTime().plusWeeks(index);
            LocalDateTime end = request.endTime().plusWeeks(index);
            Long bookId = meetings.insertSeriesOccurrence(seriesId, index + 1, request.occurrenceCount(), principal.id(), principal.deptId(), room.id(),
                    request.subject(), attendeeIds.size(), start, end, principal.role());
            meetings.replaceAttendees(bookId, attendeeIds);
            meetings.replaceDevices(bookId, deviceRequirements);
            meetings.bindDevices(bookId, actualDevices, principal.id());
            MeetingRepository.MeetingRow createdMeeting = meetings.findById(bookId);
            meetings.addAudit(principal.id(), "MEETING_SERIES_SUBMIT", "提交周期预约第" + (index + 1) + "场：" + request.subject());
            notices.notifySubmitted(createdMeeting);
            created.add(createdMeeting);
        }
        return new MeetingDtos.RecurringSubmissionResult(seriesId, created, warnings);
    }

    /**
     * 复用本人草稿，或为直接提交创建事务内的中间草稿。
     * 新建人数暂为 1，调用方随即写实际人数；失败时不能遗留中间草稿。
     */
    private Long requestDraftForSubmit(MeetingDtos.SubmitRequest request, UserPrincipal principal) {
        if (request.id() != null) {
            ownDraft(request.id(), principal);
            return request.id();
        }
        return meetings.insertDraft(principal.id(), principal.deptId(), request.subject(), 1, request.startTime(), request.endTime());
    }

    /**
     * 校验范围后查询可见列表，SQL 继续限制实际数据权限。
     */
    public List<MeetingRepository.MeetingRow> list(UserPrincipal principal, String scope, LocalDateTime start, LocalDateTime end) {
        validateScope(principal,scope);
        return meetings.list(principal, scope, start, end);
    }
    /**
     * 分页读取可见预约，限定页号和条数；总数与结果共用同一范围条件。
     */
    public com.company.meeting.resource.AdminDtos.Page<MeetingRepository.MeetingRow> listPage(UserPrincipal principal, String scope,
            LocalDateTime start, LocalDateTime end, int page, int size, MeetingStatus status) {
        validateScope(principal,scope);
        if(page<1 || page>100000 || size<1 || size>100) throw new BizException("INVALID_PAGE","分页参数不合法");
        return meetings.listPage(principal,scope,start,end,page,size,status);
    }
    /**
     * 只允许 mine、visible、pending，员工不能查看审批队列。
     * 查询意图不能扩大本人的实际数据权限。
     */
    private void validateScope(UserPrincipal actor,String scope) {
        if(!Set.of("mine","visible","pending").contains(scope)) throw new BizException("INVALID_SCOPE","会议范围不合法");
        if(scope.equals("pending") && actor.role().equals("EMPLOYEE")) throw new BizException("FORBIDDEN","普通员工不能查看审批列表",org.springframework.http.HttpStatus.FORBIDDEN);
    }

    /**
     * 读取完整详情并验证申请人、参会人或管理范围；占用可见不等于详情可见。
     */
    public MeetingRepository.MeetingRow detail(Long id, UserPrincipal principal) {
        MeetingRepository.MeetingRow meeting = meetings.findById(id);
        if (meeting == null) throw new BizException("NOT_FOUND", "会议不存在");
        if (!canView(meeting, principal)) throw new BizException("FORBIDDEN", "没有权限查看该会议", org.springframework.http.HttpStatus.FORBIDDEN);
        return meeting;
    }

    /**
     * 审批单次申请，先禁自审并检查角色与部门，再锁房间、锁预约并重读。
     * 周期场次必须整组审批；过开始时间禁止审批，批准前重新检查房间可用性。
     * 结果、审批历史、审计和通知共同提交。
     *
     * <p>批准前排除本条记录自身的占用，否则待审批状态会被误判成与自己冲突。
     *
     * @param id 普通预约 ID，周期场次不能借此逐场审批
     * @param request approved 决定批准或拒绝，comment 为可选意见
     * @param principal 当前审批人，不能与申请人相同
     * @return 审批后的最新详情
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MeetingRepository.MeetingRow approve(Long id, MeetingDtos.ApprovalRequest request, UserPrincipal principal) {
        MeetingRepository.MeetingRow meeting = meetings.findById(id);
        if (meeting == null) throw new BizException("NOT_FOUND", "会议不存在");
        if (meeting.userId().equals(principal.id())) throw new BizException("SELF_APPROVAL", "禁止审批自己的申请", org.springframework.http.HttpStatus.FORBIDDEN);
        if (!canApprove(meeting, principal)) throw new BizException("FORBIDDEN", "没有权限审批该会议", org.springframework.http.HttpStatus.FORBIDDEN);
        RoomRepository.RoomRow room = rooms.lockRoom(meeting.roomId());
        meeting = meetings.lockById(id);
        if (meeting.seriesId() != null) throw new BizException("INVALID_STATUS", "周期预约请使用整组审批");
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

    /**
     * 整组处理剩余未来待审批场次，保留取消与过期记录。
     * 先校验组内权限，再锁房间和组并重读，不依赖第一场仍待审批。
     * 已到开始时间的申请补过期；批准前排除本组后检查资源冲突。
     * 更新数必须匹配目标数，否则异常回滚，防止部分处理。
     *
     * <p>这里排除的是整组占用而非仅当前场，避免组内记录互相误判；
     * 若已批准或拒绝过，则不能把整个周期重新当作待审批组处理。
     *
     * @param seriesId 周期组 ID，不是第一场预约 ID
     * @param request 对剩余合法场次统一采用的批准或拒绝意见
     * @param principal 具备组内每一场审批范围且不是申请人的操作者
     * @return 全组最新状态，包括未被重新处理的取消和过期场次
     */
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
        if (series.stream().anyMatch(meeting -> !Set.of("PENDING", "CANCELED", "EXPIRED").contains(meeting.status()))) {
            throw new BizException("INVALID_STATUS", "周期预约已处理，请刷新列表");
        }
        LocalDateTime now = rules.now();
        // 按剩余可处理场次计算，首场已取消或过期不应阻断其余场次。
        List<MeetingRepository.MeetingRow> pending = series.stream()
                .filter(meeting -> "PENDING".equals(meeting.status()) && meeting.startTime().isAfter(now)).toList();
        if (pending.isEmpty()) throw new BizException("INVALID_STATUS", "没有可审批的未开始场次");
        for (var meeting : series) {
            if ("PENDING".equals(meeting.status()) && !meeting.startTime().isAfter(now)
                    && meetings.transition(meeting.id(), "PENDING", "EXPIRED")) {
                meetings.addAudit(meeting.userId(), "AUTO_EXPIRED", "系统自动流转：" + meeting.subject() + " → EXPIRED");
                notices.notifyExpired(meetings.findById(meeting.id()));
            }
        }
        if (request.approved() && (room == null || !room.active())) throw new BizException("ROOM_UNAVAILABLE", "会议室不存在或已停用");
        if (request.approved()) {
            List<String> conflicts = pending.stream().filter(meeting -> rooms.hasRoomConflictExceptSeries(room.id(), meeting.startTime(), meeting.endTime(), seriesId))
                    .map(meeting -> meeting.startTime().toLocalDate().toString()).toList();
            if (!conflicts.isEmpty()) throw new BizException("RECURRING_CONFLICT", "审批时发现会议室冲突：" + String.join("、", conflicts), org.springframework.http.HttpStatus.CONFLICT, Map.of("conflictDates", conflicts));
        }
        String status = request.approved() ? "APPROVED" : "REJECTED";
        // 目标数与影响数不符就异常回滚，不能默默只处理一部分。
        if (meetings.updateSeriesApproval(seriesId, status, principal.id(), request.comment()) != pending.size()) {
            throw new BizException("INVALID_STATUS", "周期预约状态已变化，请刷新列表");
        }
        for (MeetingRepository.MeetingRow meeting : pending) {
            meetings.addApprovalHistory(meeting.id(), principal.id(), request.approved() ? "APPROVE" : "REJECT", request.comment());
            meetings.addAudit(principal.id(), request.approved() ? "MEETING_SERIES_APPROVE" : "MEETING_SERIES_REJECT", (request.approved() ? "周期预约审批通过：" : "周期预约拒绝：") + meeting.subject());
            notices.notifyApproval(meetings.findById(meeting.id()), request.approved(), request.comment());
        }
        return new MeetingDtos.SeriesActionResult(seriesId, meetings.findSeries(seriesId));
    }

    /**
     * 取消单场，先检查操作者再锁房间和预约并重读时间。
     * 申请人仅能取消未开始会议；系统管理员提供原因时可维护取消未结束会议。
     * 取消他人必须填原因，部门管理员没有取消他人的特殊权力。
     *
     * <p>释放资源依赖状态变为 CANCELED 后不再命中占用查询，不是删除预约记录。
     *
     * @param id 单场预约 ID
     * @param request 取消原因；管理员取消他人或维护取消时必须非空
     * @param principal 申请人或系统管理员
     * @return 取消后的详情，历史记录仍保留
     */
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

    /**
     * 按 ONE 或 ALL 取消周期场次。
     * ONE 要求 ID 属于本组，按单次维护取消边界处理；
     * ALL 仅未来 PENDING/APPROVED，不包括进行中或终态。
     * 加锁重读并核对更新数，仅为本次实际变更的场次生成通知、审计。
     *
     * <p>ALL 即使填写管理员原因，也不会扩展到正在进行中的场次；
     * 如需维护取消进行中会议，应明确选择对应单场并遵守单场维护条件。
     *
     * @param seriesId 周期组 ID
     * @param request ONE 取消指定场，ALL 取消所有尚未开始的可取消场次
     * @param principal 申请人或系统管理员，部门管理员不能取消他人
     * @return 最新全组详情
     */
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
        // 只为本轮真正取消的 ID 写副作用，不重复发送旧取消通知。
        Set<Long> cancelIds = series.stream().filter(meeting -> Set.of("PENDING", "APPROVED").contains(meeting.status())
                && meeting.startTime().isAfter(now)).map(MeetingRepository.MeetingRow::id).collect(Collectors.toSet());
        int changed = meetings.cancelSeries(seriesId, principal.id(), request.reason(), now);
        if (changed == 0) throw new BizException("INVALID_STATUS", "没有可取消的未开始场次");
        if (changed != cancelIds.size()) throw new BizException("INVALID_STATUS", "周期预约状态已变化，请刷新列表");
        List<MeetingRepository.MeetingRow> canceled = meetings.findSeries(seriesId);
        for (MeetingRepository.MeetingRow meeting : canceled) {
            if (cancelIds.contains(meeting.id())) {
                meetings.addAudit(principal.id(), "MEETING_SERIES_CANCEL", "取消周期预约场次：" + meeting.subject());
                notices.notifyCanceled(meeting, request.reason());
            }
        }
        return new MeetingDtos.SeriesActionResult(seriesId, canceled);
    }

    /**
     * 先过滤停用、容量、设备及给定时段冲突，再按权重评分。
     * 总分降序，同分按房间 ID 升序；时间须同时给出或同时省略。
     * 省略时间不校验时段冲突，推荐不构成资源预占。
     *
     * @param attendNum 推荐时的正参会人数，正式提交会从实际名单重算
     * @param floorNo 申请人办公楼层，用于计算绝对楼层差
     * @param start 开始时间，可与 end 一起省略
     * @param end 结束时间，与 start 成对
     * @param requirements 设备类型与数量
     * @return 可行候选的有序列表；没有候选时为空，不自动放宽硬约束
     */
    public List<MeetingDtos.Recommendation> recommend(Integer attendNum, Integer floorNo, LocalDateTime start, LocalDateTime end, Map<String, Integer> requirements) {
        if (attendNum == null || attendNum < 1 || floorNo == null) throw new BizException("INVALID_REQUEST", "参会人数和楼层不合法");
        if ((start == null) != (end == null)) throw new BizException("INVALID_TIME", "请同时提供开始和结束时间");
        var settings = rules.settings();
        if (start != null) rules.validateBookingWindow(start, end, settings);
        Map<String, Integer> needed = normalizeDevices(requirements);
        // 先满足硬条件再评分，不能让高分抵消资源冲突。
        return rooms.listRooms().stream().filter(RoomRepository.RoomRow::active).filter(room -> room.capacity() >= attendNum)
                .filter(room -> devicesMatch(room, needed)).filter(room -> start == null || end == null || !rooms.hasRoomConflict(room.id(), start, end, null))
                .map(room -> score(room, attendNum, floorNo, needed, settings)).sorted(Comparator.comparing(MeetingDtos.Recommendation::totalScore).reversed().thenComparing(MeetingDtos.Recommendation::roomId)).toList();
    }

    /**
     * 生成排期事件，申请人、参会人或管理范围外只返回脱敏占用标题。
     * 最终详情接口仍需独立授权，不能只信任前端 detailVisible。
     */
    public List<MeetingDtos.ScheduleEvent> schedule(LocalDateTime start, LocalDateTime end, UserPrincipal principal) {
        List<MeetingDtos.ScheduleEvent> events = meetings.listOccupancies(start, end).stream().map(row -> {
            boolean visible = row.userId().equals(principal.id()) || row.attendeeIds().contains(principal.id()) || principal.role().equals("SYSTEM_ADMIN") || (principal.role().equals("DEPT_ADMIN") && principal.deptId().equals(row.deptId()));
            // 脱敏在服务端完成，只隐藏前端标题不能保护接口数据。
            return new MeetingDtos.ScheduleEvent(row.id(), row.roomId(), row.roomName(), visible ? row.subject() : "已占用", row.startTime(), row.endTime(), row.status(), visible);
        }).toList();
        return events;
    }

    /**
     * 按个人及管理范围汇总指标，availableRooms 是启用数，不是指定时段空闲数。
     */
    public MeetingDtos.Dashboard dashboard(UserPrincipal principal) {
        long total = rooms.listRooms().size();
        long available = rooms.listRooms().stream().filter(RoomRepository.RoomRow::active).count();
        List<MeetingRepository.MeetingRow> own = meetings.list(principal, "mine", rules.now(), null);
        long pending = own.stream().filter(item -> item.status().equals("PENDING")).count();
        long upcoming = own.stream().filter(item -> Set.of("PENDING", "APPROVED").contains(item.status())).count();
        long approval = (principal.role().equals("SYSTEM_ADMIN") || principal.role().equals("DEPT_ADMIN")) ? meetings.list(principal, "pending", null, null).stream().filter(item -> !item.userId().equals(principal.id())).count() : 0;
        return new MeetingDtos.Dashboard(total, available, pending, upcoming, approval);
    }

    /**
     * 读取未逻辑删除房间及设备，启用与冲突由具体业务筛选。
     */
    public List<RoomRepository.RoomRow> rooms() { return rooms.listRooms(); }
    /**
     * 读取参会选择用的无密码员工摘要。
     */
    public List<UserRepository.UserSummary> users() { return users.listUsers(); }
    /**
     * 返回规则、北京时间及服务端时钟，供前端约束时间选择。
     */
    public Map<String,Object> bookingRules() {
        var settings = rules.settings();
        return Map.of("zone","Asia/Shanghai","serverNow",rules.now(),"openTime",settings.openTime().toString(),
            "closeTime",settings.closeTime().toString(),"workdays",settings.workdays(),
            "weights",Map.of("capacity",settings.capacityWeight(),"device",settings.deviceWeight(),"floor",settings.floorWeight()));
    }
    /**
     * 再次校验系统管理员后查询敏感操作审计。
     */
    public List<MeetingRepository.AuditRow> audit(UserPrincipal principal) {
        if (!principal.role().equals("SYSTEM_ADMIN")) throw new BizException("FORBIDDEN", "只有系统管理员可以查看审计日志", org.springframework.http.HttpStatus.FORBIDDEN);
        return meetings.listAudit();
    }

    @Scheduled(fixedDelayString = "${meeting.scheduler-delay-ms:30000}")
    /**
     * 定时推进状态、提醒及过期草稿逻辑清理，启动补偿复用本入口。
     * <p>默认上一轮结束后延迟 30 秒，配置可改。先按房间 ID 升序加锁，再锁到期记录。
     * 待审批到开始即过期；已批准若跨过结束可直接 COMPLETED，无须重放中间态。
     * 限定旧状态更新成功才写副作用，避免重复任务多次通知或审计。
     *
     * <p>通知去重由稳定业务键与唯一索引保障；草稿按最后更新时间而非创建时间清理。
     * 全部房间加锁易于维持当前事务规则，但会阻塞短时间内的资源提交，
     * 规模增长后应测量锁等待，再评估批次或粒度，不能宣称已经实现分布式调度。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void advanceStates() {
        LocalDateTime now = rules.now();
        // 和资源维护相同，按会议室 ID 固定顺序加锁，再处理预约。
        rooms.listRooms().stream().map(RoomRepository.RoomRow::id).sorted().forEach(rooms::lockRoom);
        for (var candidate : meetings.dueStates(now)) {
            var meeting = meetings.lockById(candidate.id());
            if (meeting == null) continue;
            // 优先判结束，停机跨过全程时 APPROVED 可直接补为 COMPLETED。
            String next = meeting.status().equals("PENDING") ? "EXPIRED" : !meeting.endTime().isAfter(now) ? "COMPLETED" : "IN_PROGRESS";
            if (meetings.transition(meeting.id(), meeting.status(), next)) {
                meetings.addAudit(meeting.userId(), "AUTO_" + next, "系统自动流转：" + meeting.subject() + " → " + next);
                if ("EXPIRED".equals(next)) notices.notifyExpired(meetings.findById(meeting.id()));
            }
        }
        notices.sendUpcomingReminders(now);
        meetings.cleanupStaleDrafts(now.minusDays(rules.settings().draftRetentionDays()));
    }

    /**
     * 为已满足硬条件的房间计算分项与加权总分。
     * 容量分偏好少浪费座位，设备分偏好需求与配置贴合，楼层按距离扣分。
     * 设备并非越多越高分；无需求时设备分为 100。展示保留一位小数，是规则评分而非机器学习。
     *
     * <p>例如 8 人使用 10 座与 20 座房间，容量分分别为 80、40。
     * 同层楼层分为 100，每差一层减 floorPenalty，最低到 0。
     * 权重与楼层扣分来自当前规则，不在本方法固定写死。
     */
    private MeetingDtos.Recommendation score(RoomRepository.RoomRow room, Integer attendNum, Integer floorNo, Map<String, Integer> needed, com.company.meeting.config.RuleSettings settings) {
        // 容量足够时等价于人数/容量×100，倾向节省座位。
        double capacityScore = Math.max(0, 100 - ((room.capacity() - attendNum) * 100.0 / Math.max(1, room.capacity())));
        Map<String,Integer> inventory=rooms.deviceInventory(room.id());
        int required=needed.values().stream().mapToInt(Integer::intValue).sum();
        int available=needed.keySet().stream().mapToInt(type->inventory.getOrDefault(type,0)).sum();
        // 需求满足后看配置贴合度，并非设备越多分越高。
        double deviceScore = required==0 ? 100 : required * 100.0 / Math.max(required,available);
        int distance = Math.abs(room.floorNo() - floorNo);
        double floorScore = Math.max(0, 100 - distance * settings.floorPenalty());
        double total = capacityScore * settings.capacityWeight() + deviceScore * settings.deviceWeight() + floorScore * settings.floorWeight();
        return new MeetingDtos.Recommendation(room.id(), room.roomName(), room.floorNo(), room.capacity(), distance,
                round(capacityScore), round(deviceScore), round(floorScore), round(total), "容量匹配 " + round(capacityScore) + " 分，设备匹配 " + round(deviceScore) + " 分，距离相差 " + distance + " 层");
    }

    /**
     * 按类型合并启用设备数量，逐项满足需求，不能只比较所有设备的总数量。
     */
    private boolean devicesMatch(RoomRepository.RoomRow room, Map<String, Integer> needed) {
        Map<String, Integer> inventory = room.devices().stream().filter(RoomRepository.DeviceRow::active)
                .collect(Collectors.groupingBy(RoomRepository.DeviceRow::deviceType,
                        Collectors.summingInt(RoomRepository.DeviceRow::quantity)));
        return needed.entrySet().stream().allMatch(entry -> inventory.getOrDefault(entry.getKey(), 0) >= entry.getValue());
    }

    /**
     * 自动加入申请人、去空去重，验证 ID 位于员工摘要集合。
     * 容量与保存人数使用本结果长度，不使用客户端声明的 attendNum。
     */
    private List<Long> normalizeAttendees(List<Long> requested, UserPrincipal principal) {
        List<Long> ids = new ArrayList<>(); ids.add(principal.id()); if (requested != null) ids.addAll(requested);
        // 去重名单才是人数来源，前端修改 attendNum 不能绕过容量。
        List<Long> unique = ids.stream().filter(Objects::nonNull).distinct().toList();
        Set<Long> known = users.listUsers().stream().map(UserRepository.UserSummary::id).collect(Collectors.toSet());
        if (unique.stream().anyMatch(id -> !known.contains(id))) throw new BizException("INVALID_ATTENDEE", "参会人必须是内部员工");
        return unique;
    }

    /**
     * 校验支持的类型与 0–100 数量，移除零需求，空输入返回空映射。
     */
    private Map<String, Integer> normalizeDevices(Map<String, Integer> devices) {
        if (devices == null) return Map.of();
        if (devices.entrySet().stream().anyMatch(e -> !Set.of("PROJECTOR", "MIC", "VIDEO", "DISPLAY").contains(e.getKey()) || e.getValue() == null || e.getValue() < 0 || e.getValue() > 100)) {
            throw new BizException("INVALID_DEVICE_REQUIREMENT", "设备类型或数量不合法");
        }
        return devices.entrySet().stream().filter(entry -> entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Integer::max, LinkedHashMap::new));
    }

    /**
     * 检查主题、正人数和时间先后，不代替开放周或资源验证。
     */
    private void validateBasic(String subject, Integer attendNum, LocalDateTime start, LocalDateTime end) {
        if (subject == null || subject.isBlank()) throw new BizException("INVALID_SUBJECT", "请填写会议主题");
        if (attendNum == null || attendNum < 1) throw new BizException("INVALID_ATTENDEE_COUNT", "参会人数必须大于 0");
        if (start == null || end == null || !start.isBefore(end)) throw new BizException("INVALID_TIME", "开始时间必须早于结束时间");
    }

    /**
     * 锁定记录并要求其为当前用户的草稿，拒绝编辑他人或已提交记录。
     */
    private MeetingRepository.MeetingRow ownDraft(Long id, UserPrincipal principal) {
        MeetingRepository.MeetingRow item = meetings.lockById(id);
        if (item == null || !item.userId().equals(principal.id()) || !item.status().equals("DRAFT")) throw new BizException("INVALID_STATUS", "只有本人草稿可以修改");
        return item;
    }

    /**
     * 管理范围、本人申请或参会三条可见路径，与排期脱敏规则一致。
     */
    private boolean canView(MeetingRepository.MeetingRow meeting, UserPrincipal principal) {
        return DepartmentScopePolicy.canAccessDepartment(principal, meeting.deptId())
                || meeting.userId().equals(principal.id()) || meeting.attendees().stream().anyMatch(item -> item.userId().equals(principal.id()));
    }

    /**
     * 系统管理员申请只可由系统管理员处理，再检查部门范围并排除自审。
     * 采用提交时保存的 applicantRole，而非申请人之后可能改变的角色。
     */
    private boolean canApprove(MeetingRepository.MeetingRow meeting, UserPrincipal principal) {
        if (meeting.applicantRole().equals("SYSTEM_ADMIN") && !principal.role().equals("SYSTEM_ADMIN")) return false;
        return DepartmentScopePolicy.canApproveDepartment(principal, meeting.deptId()) && !meeting.userId().equals(principal.id());
    }

    /**
     * 推荐得分统一四舍五入为一位小数。
     */
    private double round(double value) { return Math.round(value * 10) / 10.0; }
}
