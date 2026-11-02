package com.company.meeting.resource;
import com.company.meeting.auth.UserPrincipal;
import com.company.meeting.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 系统管理员维护账号、部门、会议室及设备的接口。
 * 类级授权限制角色，DTO 校验结构，服务检查组织约束及预约资源保护。
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class AdminController {
    private final AdminService service;
    public AdminController(AdminService service) { this.service=service; }
    /**
     * 允许资源类别分页查询。
     */
    @GetMapping("/{kind}") public ApiResponse<?> list(@PathVariable String kind, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize) { return ApiResponse.ok(service.list(kind,page,pageSize)); }
    /**
     * 新增账号，校验部门、唯一性及密码。
     */
    @PostMapping("/users") public ApiResponse<?> createUser(@Valid @RequestBody AdminDtos.AccountRequest req,@AuthenticationPrincipal UserPrincipal actor) { return ApiResponse.ok(service.saveAccount(null,req,actor)); }
    /**
     * 维护资料角色，不在此改密。
     */
    @PutMapping("/users/{id}") public ApiResponse<?> updateUser(@PathVariable Long id,@Valid @RequestBody AdminDtos.AccountRequest req,@AuthenticationPrincipal UserPrincipal actor) { return ApiResponse.ok(service.saveAccount(id,req,actor)); }
    /**
     * 专门重置密码并审计，旧会话后续失效。
     */
    @PostMapping("/users/{id}/reset-password") public ApiResponse<?> reset(@PathVariable Long id,@Valid @RequestBody AdminDtos.ResetPasswordRequest req,@AuthenticationPrincipal UserPrincipal actor) { service.resetPassword(id,req.password(),actor); return ApiResponse.ok(null); }
    /**
     * 新增并检查父部门。
     */
    @PostMapping("/departments") public ApiResponse<?> createDept(@Valid @RequestBody AdminDtos.DepartmentRequest req,@AuthenticationPrincipal UserPrincipal actor) { return ApiResponse.ok(service.saveDepartment(null,req,actor)); }
    /**
     * 修改并检查父链及停用保护。
     */
    @PutMapping("/departments/{id}") public ApiResponse<?> updateDept(@PathVariable Long id,@Valid @RequestBody AdminDtos.DepartmentRequest req,@AuthenticationPrincipal UserPrincipal actor) { return ApiResponse.ok(service.saveDepartment(id,req,actor)); }
    /**
     * 新增已校验的房间资料。
     */
    @PostMapping("/rooms") public ApiResponse<?> createRoom(@Valid @RequestBody AdminDtos.RoomRequest req,@AuthenticationPrincipal UserPrincipal actor) { return ApiResponse.ok(service.saveRoom(null,req,actor)); }
    /**
     * 维护已有房间，拒绝影响有效预约。
     */
    @PutMapping("/rooms/{id}") public ApiResponse<?> updateRoom(@PathVariable Long id,@Valid @RequestBody AdminDtos.RoomRequest req,@AuthenticationPrincipal UserPrincipal actor) { return ApiResponse.ok(service.saveRoom(id,req,actor)); }
    /**
     * 新增固定设备，协调房间锁。
     */
    @PostMapping("/devices") public ApiResponse<?> createDevice(@Valid @RequestBody AdminDtos.DeviceRequest req,@AuthenticationPrincipal UserPrincipal actor) { return ApiResponse.ok(service.saveDevice(null,req,actor)); }
    /**
     * 维护设备，锁新旧房间后再校验归属。
     */
    @PutMapping("/devices/{id}") public ApiResponse<?> updateDevice(@PathVariable Long id,@Valid @RequestBody AdminDtos.DeviceRequest req,@AuthenticationPrincipal UserPrincipal actor) { return ApiResponse.ok(service.saveDevice(id,req,actor)); }
}
