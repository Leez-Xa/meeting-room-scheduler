package com.company.meeting.resource;

import com.company.meeting.auth.UserPrincipal;
import com.company.meeting.common.BizException;
import com.company.meeting.common.BusinessRules;
import com.company.meeting.meeting.MeetingRepository;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class AdminService {
    private final AccountMapper accounts;
    private final DepartmentMapper departments;
    private final RoomMapper roomMapper;
    private final DeviceMapper devices;
    private final RoomRepository rooms;
    private final MeetingRepository audit;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final BusinessRules rules;
    public AdminService(AccountMapper accounts, DepartmentMapper departments, RoomMapper roomMapper, DeviceMapper devices,
                        RoomRepository rooms, MeetingRepository audit, JdbcTemplate jdbc, PasswordEncoder encoder, BusinessRules rules) {
        this.accounts=accounts; this.departments=departments; this.roomMapper=roomMapper; this.devices=devices;
        this.rooms=rooms; this.audit=audit; this.jdbc=jdbc; this.encoder=encoder; this.rules=rules;
    }
    public Object list(String kind, int page, int size) {
        return switch(kind) {
            case "users" -> page(accounts, page, size);
            case "departments" -> page(departments, page, size);
            case "rooms" -> page(roomMapper, page, size);
            case "devices" -> page(devices, page, size);
            default -> throw new BizException("NOT_FOUND", "资源不存在");
        };
    }
    private <T> AdminDtos.Page<T> page(BaseMapper<T> mapper, int page, int size) {
        if (page<1 || page>100000 || size<1 || size>100) throw new BizException("INVALID_PAGE", "分页参数不合法");
        long total=mapper.selectCount(new QueryWrapper<>());
        var records=mapper.selectList(new QueryWrapper<T>().orderByAsc("id").last("LIMIT " + size + " OFFSET " + ((page-1)*size)));
        return new AdminDtos.Page<>(records, total, page, size);
    }
    private void stamp(AdminEntities.Base entity, Long id, UserPrincipal actor) {
        entity.id=id; entity.updateBy=actor.id(); entity.updateTime=rules.now();
        if (id == null) { entity.createBy=actor.id(); entity.createTime=rules.now(); entity.deleted=0; }
    }
    private <T> void save(BaseMapper<T> mapper, T entity, Long id) {
        int count=id==null ? mapper.insert(entity) : mapper.updateById(entity);
        if(count!=1) throw new BizException("NOT_FOUND", "资源不存在");
    }
    private void lockOrganisation() {
        jdbc.query("SELECT id FROM dept ORDER BY id FOR UPDATE", (rs,n)->rs.getLong(1));
    }
    public AdminEntities.Account saveAccount(Long id, AdminDtos.AccountRequest req, UserPrincipal actor) {
        lockOrganisation();
        var department=departments.selectById(req.deptId());
        if(department==null || department.status!=1) throw new BizException("INVALID_DEPARTMENT", "请选择启用的部门");
        var previous=id==null?null:accounts.selectById(id);
        if(id!=null && previous==null) throw new BizException("NOT_FOUND", "账号不存在");
        if(Objects.equals(id,actor.id()) && (req.status()==0 || !req.role().equals("SYSTEM_ADMIN"))) throw new BizException("SELF_DISABLE", "不能停用或降级当前管理员账号");
        if(previous!=null && previous.role.equals("SYSTEM_ADMIN") && previous.status==1 && (req.status()==0 || !req.role().equals("SYSTEM_ADMIN"))) {
            long enabled=accounts.selectCount(new QueryWrapper<AdminEntities.Account>().eq("role","SYSTEM_ADMIN").eq("status",1));
            if(enabled<=2) throw new BizException("ADMIN_REQUIRED", "至少保留两名启用的系统管理员以完成互审");
        }
        var same=accounts.selectList(new QueryWrapper<AdminEntities.Account>().eq("username",req.username()));
        if(same.stream().anyMatch(u->!Objects.equals(u.id,id))) throw new BizException("USERNAME_EXISTS", "账号已存在");
        var entity=new AdminEntities.Account(); stamp(entity,id,actor);
        entity.username=req.username(); entity.realName=req.realName(); entity.deptId=req.deptId(); entity.role=req.role(); entity.floorNo=req.floorNo(); entity.status=req.status();
        if(id==null) entity.password=encode(req.password());
        else if(req.password()!=null && !req.password().isBlank()) throw new BizException("USE_RESET_INTERFACE", "请通过独立重置密码操作修改密码");
        save(accounts,entity,id); audit.addAudit(actor.id(),id==null?"USER_CREATE":"USER_UPDATE","维护账号："+req.username());
        return entity;
    }
    public AdminEntities.Department saveDepartment(Long id, AdminDtos.DepartmentRequest req, UserPrincipal actor) {
        lockOrganisation();
        if(id!=null && departments.selectById(id)==null) throw new BizException("NOT_FOUND", "部门不存在");
        if(req.parentId()!=null) {
            var parent=departments.selectById(req.parentId());
            if(parent==null || parent.status!=1) throw new BizException("INVALID_PARENT", "上级部门不存在或未启用");
            for(var cursor=parent; cursor!=null; cursor=cursor.parentId==null?null:departments.selectById(cursor.parentId)) {
                if(Objects.equals(cursor.id,id)) throw new BizException("DEPARTMENT_CYCLE", "部门层级不能成环");
            }
        }
        if(id!=null && req.status()==0 && (accounts.selectCount(new QueryWrapper<AdminEntities.Account>().eq("dept_id",id).eq("status",1))>0 || departments.selectCount(new QueryWrapper<AdminEntities.Department>().eq("parent_id",id).eq("status",1))>0)) {
            throw new BizException("DEPARTMENT_IN_USE", "请先处理部门内启用账号及下级部门");
        }
        var entity=new AdminEntities.Department(); stamp(entity,id,actor); entity.deptName=req.deptName(); entity.parentId=req.parentId(); entity.sortNo=req.sortNo(); entity.status=req.status();
        save(departments,entity,id); audit.addAudit(actor.id(),"DEPARTMENT_SAVE","维护部门："+req.deptName()); return entity;
    }
    private void lockRoom(Long id) { if(rooms.lockRoom(id)==null) throw new BizException("NOT_FOUND", "会议室不存在"); }
    private void ensureFree(Long id) {
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM meeting_book WHERE room_id=? AND deleted=0 AND status IN ('PENDING','APPROVED','IN_PROGRESS')",Integer.class,id);
        if(count!=null && count>0) throw new BizException("RESOURCE_IN_USE", "会议室有待审批、已批准或进行中的预约，请先处理预约");
    }
    public AdminEntities.Room saveRoom(Long id, AdminDtos.RoomRequest req, UserPrincipal actor) {
        if(id!=null) { lockRoom(id); ensureFree(id); }
        var entity=new AdminEntities.Room(); stamp(entity,id,actor); entity.roomName=req.roomName(); entity.floorNo=req.floorNo(); entity.capacity=req.capacity(); entity.location=req.location(); entity.status=req.status();
        save(roomMapper,entity,id); audit.addAudit(actor.id(),"ROOM_SAVE","维护会议室："+req.roomName()); return entity;
    }
    public AdminEntities.Device saveDevice(Long id, AdminDtos.DeviceRequest req, UserPrincipal actor) {
        var previous=id==null?null:devices.selectById(id);
        if(id!=null && previous==null) throw new BizException("NOT_FOUND", "设备不存在");
        java.util.stream.Stream.of(req.roomId(),previous==null?req.roomId():previous.roomId).distinct().sorted().forEach(this::lockRoom);
        ensureFree(req.roomId()); if(previous!=null) ensureFree(previous.roomId);
        if(previous!=null) {
            // 加锁后的当前归属可能已被另一名管理员改变，拒绝基于旧归属继续写入。
            var current=devices.selectById(id);
            if(!Objects.equals(current.roomId,previous.roomId)) throw new BizException("RESOURCE_CHANGED", "设备归属已变化，请刷新后重试");
        }
        var entity=new AdminEntities.Device(); stamp(entity,id,actor); entity.roomId=req.roomId(); entity.deviceName=req.deviceName(); entity.deviceType=req.deviceType(); entity.quantity=req.quantity(); entity.status=req.status();
        save(devices,entity,id); audit.addAudit(actor.id(),"DEVICE_SAVE","维护设备："+req.deviceName()); return entity;
    }
    public void resetPassword(Long id, String password, UserPrincipal actor) {
        lockOrganisation(); var account=accounts.selectById(id);
        if(account==null) throw new BizException("NOT_FOUND", "账号不存在");
        var entity=new AdminEntities.Account(); stamp(entity,id,actor); entity.password=encode(password); accounts.updateById(entity);
        audit.addAudit(actor.id(),"PASSWORD_RESET","重置账号密码，账号ID："+id);
    }
    public void changePassword(AdminDtos.PasswordRequest req, UserPrincipal actor) {
        lockOrganisation(); var account=accounts.selectById(actor.id());
        if(account==null || !encoder.matches(req.currentPassword(),account.password)) throw new BizException("WRONG_PASSWORD", "当前密码不正确");
        var entity=new AdminEntities.Account(); stamp(entity,actor.id(),actor); entity.password=encode(req.newPassword()); accounts.updateById(entity);
        audit.addAudit(actor.id(),"PASSWORD_CHANGE","用户修改本人密码");
    }
    private String encode(String password) {
        if(password==null || password.length()<8 || password.getBytes(StandardCharsets.UTF_8).length>72) throw new BizException("INVALID_PASSWORD", "密码至少8字符，UTF-8编码不超过72字节");
        return encoder.encode(password);
    }
}
