package com.company.meeting.resource;
import com.company.meeting.auth.UserPrincipal;
import com.company.meeting.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class AdminController {
    private final AdminService service;
    public AdminController(AdminService service) { this.service=service; }
    @GetMapping("/{kind}") public ApiResponse<?> list(@PathVariable String kind, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize) { return ApiResponse.ok(service.list(kind,page,pageSize)); }
    @PostMapping("/users") public ApiResponse<?> createUser(@Valid @RequestBody AdminDtos.AccountRequest req,@AuthenticationPrincipal UserPrincipal actor) { return ApiResponse.ok(service.saveAccount(null,req,actor)); }
    @PutMapping("/users/{id}") public ApiResponse<?> updateUser(@PathVariable Long id,@Valid @RequestBody AdminDtos.AccountRequest req,@AuthenticationPrincipal UserPrincipal actor) { return ApiResponse.ok(service.saveAccount(id,req,actor)); }
    @PostMapping("/users/{id}/reset-password") public ApiResponse<?> reset(@PathVariable Long id,@Valid @RequestBody AdminDtos.ResetPasswordRequest req,@AuthenticationPrincipal UserPrincipal actor) { service.resetPassword(id,req.password(),actor); return ApiResponse.ok(null); }
    @PostMapping("/departments") public ApiResponse<?> createDept(@Valid @RequestBody AdminDtos.DepartmentRequest req,@AuthenticationPrincipal UserPrincipal actor) { return ApiResponse.ok(service.saveDepartment(null,req,actor)); }
    @PutMapping("/departments/{id}") public ApiResponse<?> updateDept(@PathVariable Long id,@Valid @RequestBody AdminDtos.DepartmentRequest req,@AuthenticationPrincipal UserPrincipal actor) { return ApiResponse.ok(service.saveDepartment(id,req,actor)); }
    @PostMapping("/rooms") public ApiResponse<?> createRoom(@Valid @RequestBody AdminDtos.RoomRequest req,@AuthenticationPrincipal UserPrincipal actor) { return ApiResponse.ok(service.saveRoom(null,req,actor)); }
    @PutMapping("/rooms/{id}") public ApiResponse<?> updateRoom(@PathVariable Long id,@Valid @RequestBody AdminDtos.RoomRequest req,@AuthenticationPrincipal UserPrincipal actor) { return ApiResponse.ok(service.saveRoom(id,req,actor)); }
    @PostMapping("/devices") public ApiResponse<?> createDevice(@Valid @RequestBody AdminDtos.DeviceRequest req,@AuthenticationPrincipal UserPrincipal actor) { return ApiResponse.ok(service.saveDevice(null,req,actor)); }
    @PutMapping("/devices/{id}") public ApiResponse<?> updateDevice(@PathVariable Long id,@Valid @RequestBody AdminDtos.DeviceRequest req,@AuthenticationPrincipal UserPrincipal actor) { return ApiResponse.ok(service.saveDevice(id,req,actor)); }
}
