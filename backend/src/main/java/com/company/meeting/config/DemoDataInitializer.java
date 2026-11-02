package com.company.meeting.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

/**
 * 显式启用演示数据时，仅初始化全新空库。
 * 已有用户时跳过；只有其他基础数据时拒绝继续，防止覆盖现场。
 * 内置账号和资源仅供演示，不是生产配置。
 */
@Component
@ConditionalOnProperty(name="meeting.demo-data", havingValue="true")
public class DemoDataInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public DemoDataInitializer(JdbcTemplate jdbc, PasswordEncoder encoder) { this.jdbc = jdbc; this.encoder = encoder; }

    /**
     * 空库内创建演示组织、账号与资源，已有数据不重置也不补种。
     * 整个初始化在事务内，失败时不留下半套数据。
     */
    @Override
    @Transactional
    public void run(String... args) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class);
        if (count != null && count > 0) return;
        int existingResources = jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM dept) + (SELECT COUNT(*) FROM meeting_room) + (SELECT COUNT(*) FROM device)", Integer.class);
        if (existingResources > 0) throw new IllegalStateException("演示初始化仅支持全新库；已有基础数据需由管理员维护，禁止自动覆盖");
        jdbc.update("INSERT INTO dept(dept_name,sort_no) VALUES ('技术研发部',1),('运营管理部',2)");
        Long tech = jdbc.queryForObject("SELECT id FROM dept WHERE dept_name='技术研发部'", Long.class);
        Long ops = jdbc.queryForObject("SELECT id FROM dept WHERE dept_name='运营管理部'", Long.class);
        String password = encoder.encode("Admin@123");
        insertUser("admin", "系统管理员", password, tech, "SYSTEM_ADMIN", 3);
        insertUser("admin2", "系统管理员二", password, ops, "SYSTEM_ADMIN", 2);
        insertUser("dept-admin", "研发部门管理员", password, tech, "DEPT_ADMIN", 4);
        insertUser("employee", "普通员工", password, tech, "EMPLOYEE", 5);
        insertUser("ops-employee", "运营员工", password, ops, "EMPLOYEE", 2);
        insertUser("ops-admin", "运营部门管理员", password, ops, "DEPT_ADMIN", 2);
        // Public fictional demo accounts; existing databases are never re-seeded.
        insertUser("employee2", "研发员工二", password, tech, "EMPLOYEE", 2);
        insertUser("employee3", "研发员工三", password, tech, "EMPLOYEE", 3);
        insertUser("employee4", "研发员工四", password, tech, "EMPLOYEE", 4);
        insertUser("employee5", "研发员工五", password, tech, "EMPLOYEE", 5);
        insertUser("ops-employee2", "运营员工二", password, ops, "EMPLOYEE", 2);
        insertUser("ops-employee3", "运营员工三", password, ops, "EMPLOYEE", 3);
        jdbc.update("INSERT INTO meeting_room(room_name,floor_no,capacity,location) VALUES ('晨曦厅',1,8,'A座 1层东侧'),('远山厅',2,20,'A座 2层中庭'),('星河厅',3,12,'A座 3层西侧'),('协作间',4,6,'A座 4层南侧')");
        Long r1 = jdbc.queryForObject("SELECT id FROM meeting_room WHERE room_name='晨曦厅'", Long.class);
        Long r2 = jdbc.queryForObject("SELECT id FROM meeting_room WHERE room_name='远山厅'", Long.class);
        Long r3 = jdbc.queryForObject("SELECT id FROM meeting_room WHERE room_name='星河厅'", Long.class);
        Long r4 = jdbc.queryForObject("SELECT id FROM meeting_room WHERE room_name='协作间'", Long.class);
        addDevice(r1, "投影仪", "PROJECTOR", 1); addDevice(r1, "麦克风", "MIC", 2);
        addDevice(r2, "投影仪", "PROJECTOR", 1); addDevice(r2, "麦克风", "MIC", 4); addDevice(r2, "视频会议", "VIDEO", 1);
        addDevice(r3, "投影仪", "PROJECTOR", 1); addDevice(r3, "麦克风", "MIC", 4);
        addDevice(r4, "显示器", "DISPLAY", 1); addDevice(r4, "麦克风", "MIC", 1);
        jdbc.update("INSERT INTO meeting_room(room_name,floor_no,capacity,location) VALUES ('云杉厅',2,8,'A座 2层东侧'),('听雨厅',3,10,'A座 3层南侧'),('映月厅',4,16,'A座 4层西侧'),('多功能厅',5,30,'A座 5层中庭')");
        Long r5 = jdbc.queryForObject("SELECT id FROM meeting_room WHERE room_name='云杉厅'", Long.class);
        Long r6 = jdbc.queryForObject("SELECT id FROM meeting_room WHERE room_name='听雨厅'", Long.class);
        Long r7 = jdbc.queryForObject("SELECT id FROM meeting_room WHERE room_name='映月厅'", Long.class);
        Long r8 = jdbc.queryForObject("SELECT id FROM meeting_room WHERE room_name='多功能厅'", Long.class);
        addDevice(r5, "显示器", "DISPLAY", 1); addDevice(r5, "麦克风", "MIC", 2);
        addDevice(r6, "视频会议", "VIDEO", 1); addDevice(r6, "显示器", "DISPLAY", 1);
        addDevice(r7, "投影仪", "PROJECTOR", 1); addDevice(r7, "麦克风", "MIC", 4);
        addDevice(r8, "投影仪", "PROJECTOR", 2); addDevice(r8, "麦克风", "MIC", 6); addDevice(r8, "视频会议", "VIDEO", 1);
        Long creator = jdbc.queryForObject("SELECT id FROM sys_user WHERE username='admin'", Long.class);
        jdbc.update("UPDATE dept SET create_by=?,update_by=? WHERE create_by IS NULL", creator, creator);
        jdbc.update("UPDATE sys_user SET create_by=?,update_by=? WHERE create_by IS NULL", creator, creator);
        jdbc.update("UPDATE meeting_room SET create_by=?,update_by=? WHERE create_by IS NULL", creator, creator);
        jdbc.update("UPDATE device SET create_by=?,update_by=? WHERE create_by IS NULL", creator, creator);
    }

    /**
     * 写演示用户，password 参数已编码。
     */
    private void insertUser(String username, String name, String password, Long deptId, String role, int floor) {
        jdbc.update("INSERT INTO sys_user(username,real_name,password,dept_id,role,floor_no) VALUES (?,?,?,?,?,?)", username, name, password, deptId, role, floor);
    }
    /**
     * 增加房间固定设备，quantity 可表达多件同类型。
     */
    private void addDevice(Long roomId, String name, String type, int quantity) { jdbc.update("INSERT INTO device(room_id,device_name,device_type,quantity) VALUES (?,?,?,?)", roomId, name, type, quantity); }
}
