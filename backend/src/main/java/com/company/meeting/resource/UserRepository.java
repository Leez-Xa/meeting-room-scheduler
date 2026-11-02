package com.company.meeting.resource;

import com.company.meeting.auth.UserPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 认证身份、员工摘要与审批人查询。
 * 认证数据含密码编码并过滤账号和部门有效性；摘要不含密码。
 * 审批人查询排除本人并遵守系统管理员互审。
 */
@Repository
public class UserRepository {
    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * 查询账号及部门均启用且未删除的身份，不存在返回空 Optional。
     * 含密码编码，仅供认证与会话复查，不得直接响应前端。
     */
    public Optional<UserPrincipal> findPrincipal(String username) {
        List<UserPrincipal> result = jdbc.query("""
                SELECT u.id, u.username, u.password, u.real_name, u.dept_id, d.dept_name, u.role, u.floor_no
                FROM sys_user u JOIN dept d ON d.id = u.dept_id
                WHERE u.username = ? AND u.status = 1 AND u.deleted = 0 AND d.status=1 AND d.deleted=0
                """, (rs, rowNum) -> new UserPrincipal(rs.getLong("id"), rs.getString("username"), rs.getString("password"),
                rs.getString("real_name"), rs.getLong("dept_id"), rs.getString("dept_name"), rs.getString("role"), rs.getInt("floor_no")), username);
        return result.stream().findFirst();
    }

    /**
     * 读取启用未删除用户的无密码摘要，用于参会选择和 ID 校验。
     * 此查询未额外限制部门启用状态，不能等同完整登录资格检查。
     */
    public List<UserSummary> listUsers() {
        return jdbc.query("""
                SELECT u.id, u.real_name, u.username, u.dept_id, d.dept_name, u.role, u.floor_no
                FROM sys_user u JOIN dept d ON d.id = u.dept_id
                WHERE u.status = 1 AND u.deleted = 0 ORDER BY u.id
                """, (rs, rowNum) -> new UserSummary(rs.getLong("id"), rs.getString("real_name"), rs.getString("username"),
                rs.getLong("dept_id"), rs.getString("dept_name"), rs.getString("role"), rs.getInt("floor_no")));
    }

    /**
     * 排除申请人，管理员申请只通知其他系统管理员；其他申请通知系统管理员及本部门管理员。
     */
    public List<Long> listApproverIds(Long deptId, String applicantRole, Long applicantId) {
        if ("SYSTEM_ADMIN".equals(applicantRole)) {
            return jdbc.query("""
                    SELECT id FROM sys_user
                    WHERE role='SYSTEM_ADMIN' AND status=1 AND deleted=0 AND id<>?
                    ORDER BY id
                    """, (rs, rowNum) -> rs.getLong(1), applicantId);
        }
        return jdbc.query("""
                SELECT id FROM sys_user
                WHERE status=1 AND deleted=0 AND id<>?
                  AND (role='SYSTEM_ADMIN' OR (role='DEPT_ADMIN' AND dept_id=?))
                ORDER BY id
                """, (rs, rowNum) -> rs.getLong(1), applicantId, deptId);
    }

    /**
     * 不含密码编码的内部员工摘要。
     */
    public record UserSummary(Long id, String realName, String username, Long deptId, String deptName, String role, Integer floorNo) {}
}
