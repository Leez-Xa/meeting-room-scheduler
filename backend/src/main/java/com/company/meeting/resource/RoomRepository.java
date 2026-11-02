package com.company.meeting.resource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 房间、固定设备及资源和人员冲突 SQL。
 * 房间行是提交与维护共用锁入口，需外层事务；
 * 仅未删除的待审批、成功和进行中记录占资源，时间按半开区间判断。
 */
@Repository
public class RoomRepository {
    private final JdbcTemplate jdbc;

    public RoomRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * 按楼层和 ID 读取未删除房间及设备，包含停用房间。
     */
    public List<RoomRow> listRooms() {
        return jdbc.query("""
                SELECT id, room_name, floor_no, capacity, location, status
                FROM meeting_room WHERE deleted = 0 ORDER BY floor_no, id
                """, (rs, rowNum) -> new RoomRow(rs.getLong("id"), rs.getString("room_name"), rs.getInt("floor_no"),
                rs.getInt("capacity"), rs.getString("location"), rs.getInt("status") == 1,
                listDevices(rs.getLong("id"))));
    }

    /**
     * 读取未删除房间，存在不代表启用或时段空闲。
     */
    public RoomRow findRoom(Long id) {
        return jdbc.query("SELECT id, room_name, floor_no, capacity, location, status FROM meeting_room WHERE id = ? AND deleted = 0", 
                (rs, rowNum) -> new RoomRow(rs.getLong("id"), rs.getString("room_name"), rs.getInt("floor_no"), rs.getInt("capacity"), rs.getString("location"), rs.getInt("status") == 1, listDevices(rs.getLong("id"))), id)
                .stream().findFirst().orElse(null);
    }

    /**
     * 用 SELECT FOR UPDATE 锁定稳定存在的房间记录。
     * 必须有外层事务才能把锁持有到冲突检查和写入结束；这是房间行锁，并非逐台设备锁。
     */
    public RoomRow lockRoom(Long id) {
        return jdbc.query("SELECT id, room_name, floor_no, capacity, location, status FROM meeting_room WHERE id = ? AND deleted = 0 FOR UPDATE",
                (rs, rowNum) -> new RoomRow(rs.getLong("id"), rs.getString("room_name"), rs.getInt("floor_no"), rs.getInt("capacity"), rs.getString("location"), rs.getInt("status") == 1, listDevices(rs.getLong("id"))), id)
                .stream().findFirst().orElse(null);
    }

    /**
     * 读取未删除固定设备，启用筛选由库存或调用者执行。
     */
    private List<DeviceRow> listDevices(Long roomId) {
        return jdbc.query("SELECT id, device_name, device_type, quantity, status FROM device WHERE room_id = ? AND deleted = 0 ORDER BY id",
                (rs, rowNum) -> new DeviceRow(rs.getLong("id"), rs.getString("device_name"), rs.getString("device_type"), rs.getInt("quantity"), rs.getInt("status") == 1), roomId);
    }

    /**
     * 半开区间重叠：旧开始小于新结束，旧结束大于新开始。
     * 相邻端点相等不冲突；exceptId 排除本次记录。查询本身不加房间锁，提交须先加锁。
     */
    public boolean hasRoomConflict(Long roomId, LocalDateTime start, LocalDateTime end, Long exceptId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM meeting_book
                WHERE room_id = ? AND deleted = 0 AND status IN ('PENDING','APPROVED','IN_PROGRESS')
                  AND start_time < ? AND end_time > ? AND (? IS NULL OR id <> ?)
                """, Integer.class, roomId, end, start, exceptId, exceptId);
        return count != null && count > 0;
    }

    /**
     * 周期审批排除本组，保留 series_id IS NULL，避免 SQL NULL 比较漏掉单次预约。
     */
    public boolean hasRoomConflictExceptSeries(Long roomId, LocalDateTime start, LocalDateTime end, Long seriesId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM meeting_book
                WHERE room_id = ? AND deleted = 0 AND status IN ('PENDING','APPROVED','IN_PROGRESS')
                  AND start_time < ? AND end_time > ? AND (series_id IS NULL OR series_id <> ?)
                """, Integer.class, roomId, end, start, seriesId);
        return count != null && count > 0;
    }

    /**
     * 检查作为申请人或参会人的重叠，只用于人员警告。
     */
    public boolean hasUserConflict(Long userId, LocalDateTime start, LocalDateTime end, Long exceptId) {
        return !userConflictKeys(userId, start, end, exceptId).isEmpty();
    }

    // Keys stay server-side; they detect changed meetings without exposing other meetings' details.
    /**
     * 按预约 ID 排序的内部冲突键，含 ID 与起止时间。
     * 指纹可识别新增、消失或改时，不向客户端暴露他人会议详情。
     */
    public List<String> userConflictKeys(Long userId, LocalDateTime start, LocalDateTime end, Long exceptId) {
        return jdbc.query("""
                SELECT b.id, b.start_time, b.end_time FROM meeting_book b
                WHERE b.deleted = 0 AND b.status IN ('PENDING','APPROVED','IN_PROGRESS')
                  AND b.start_time < ? AND b.end_time > ? AND (? IS NULL OR b.id <> ?)
                  AND (b.user_id = ? OR EXISTS (SELECT 1 FROM meeting_attendee a WHERE a.book_id = b.id AND a.user_id = ? AND a.deleted=0))
                ORDER BY b.id
                """, (rs, row) -> rs.getLong("id") + "@" + rs.getTimestamp("start_time").toLocalDateTime()
                        + "/" + rs.getTimestamp("end_time").toLocalDateTime(), end, start, exceptId, exceptId, userId, userId);
    }

    /**
     * 按类型累加房间启用且未删除的设备数量，用于需求硬条件校验。
     */
    public Map<String, Integer> deviceInventory(Long roomId) {
        return jdbc.query("SELECT device_type, SUM(quantity) quantity FROM device WHERE room_id = ? AND status = 1 AND deleted = 0 GROUP BY device_type",
                rs -> { Map<String, Integer> map = new java.util.HashMap<>(); while (rs.next()) map.put(rs.getString("device_type"), rs.getInt("quantity")); return map; }, roomId);
    }

    /**
     * 房间设备摘要，active 为启用而非时段空闲。
     */
    public record RoomRow(Long id, String roomName, Integer floorNo, Integer capacity, String location, Boolean active, List<DeviceRow> devices) {}
    /**
     * 固定设备记录，quantity 可表示同类多件。
     */
    public record DeviceRow(Long id, String deviceName, String deviceType, Integer quantity, Boolean active) {}
}
