package com.company.meeting.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name="meeting.demo-data", havingValue="true")
public class DemoDataInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public DemoDataInitializer(JdbcTemplate jdbc, PasswordEncoder encoder) { this.jdbc = jdbc; this.encoder = encoder; }

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
        jdbc.update("INSERT INTO meeting_room(room_name,floor_no,capacity,location) VALUES ('晨曦厅',1,8,'A座 1层东侧'),('远山厅',2,20,'A座 2层中庭'),('星河厅',3,12,'A座 3层西侧'),('协作间',4,6,'A座 4层南侧')");
        Long r1 = jdbc.queryForObject("SELECT id FROM meeting_room WHERE room_name='晨曦厅'", Long.class);
        Long r2 = jdbc.queryForObject("SELECT id FROM meeting_room WHERE room_name='远山厅'", Long.class);
        Long r3 = jdbc.queryForObject("SELECT id FROM meeting_room WHERE room_name='星河厅'", Long.class);
        Long r4 = jdbc.queryForObject("SELECT id FROM meeting_room WHERE room_name='协作间'", Long.class);
        addDevice(r1, "投影仪", "PROJECTOR", 1); addDevice(r1, "麦克风", "MIC", 2);
        addDevice(r2, "投影仪", "PROJECTOR", 1); addDevice(r2, "麦克风", "MIC", 4); addDevice(r2, "视频会议", "VIDEO", 1);
        addDevice(r3, "投影仪", "PROJECTOR", 1); addDevice(r3, "麦克风", "MIC", 4);
        addDevice(r4, "显示器", "DISPLAY", 1); addDevice(r4, "麦克风", "MIC", 1);
        Long creator = jdbc.queryForObject("SELECT id FROM sys_user WHERE username='admin'", Long.class);
        jdbc.update("UPDATE dept SET create_by=?,update_by=? WHERE create_by IS NULL", creator, creator);
        jdbc.update("UPDATE sys_user SET create_by=?,update_by=? WHERE create_by IS NULL", creator, creator);
        jdbc.update("UPDATE meeting_room SET create_by=?,update_by=? WHERE create_by IS NULL", creator, creator);
        jdbc.update("UPDATE device SET create_by=?,update_by=? WHERE create_by IS NULL", creator, creator);
    }

    private void insertUser(String username, String name, String password, Long deptId, String role, int floor) {
        jdbc.update("INSERT INTO sys_user(username,real_name,password,dept_id,role,floor_no) VALUES (?,?,?,?,?,?)", username, name, password, deptId, role, floor);
    }
    private void addDevice(Long roomId, String name, String type, int quantity) { jdbc.update("INSERT INTO device(room_id,device_name,device_type,quantity) VALUES (?,?,?,?)", roomId, name, type, quantity); }
}
