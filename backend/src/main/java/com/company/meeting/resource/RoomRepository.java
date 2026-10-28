package com.company.meeting.resource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public class RoomRepository {
    private final JdbcTemplate jdbc;

    public RoomRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<RoomRow> listRooms() {
        return jdbc.query("""
                SELECT id, room_name, floor_no, capacity, location, status
                FROM meeting_room WHERE deleted = 0 ORDER BY floor_no, id
                """, (rs, rowNum) -> new RoomRow(rs.getLong("id"), rs.getString("room_name"), rs.getInt("floor_no"),
                rs.getInt("capacity"), rs.getString("location"), rs.getInt("status") == 1,
                listDevices(rs.getLong("id"))));
    }

    public RoomRow findRoom(Long id) {
        return jdbc.query("SELECT id, room_name, floor_no, capacity, location, status FROM meeting_room WHERE id = ? AND deleted = 0", 
                (rs, rowNum) -> new RoomRow(rs.getLong("id"), rs.getString("room_name"), rs.getInt("floor_no"), rs.getInt("capacity"), rs.getString("location"), rs.getInt("status") == 1, listDevices(rs.getLong("id"))), id)
                .stream().findFirst().orElse(null);
    }

    public RoomRow lockRoom(Long id) {
        return jdbc.query("SELECT id, room_name, floor_no, capacity, location, status FROM meeting_room WHERE id = ? AND deleted = 0 FOR UPDATE",
                (rs, rowNum) -> new RoomRow(rs.getLong("id"), rs.getString("room_name"), rs.getInt("floor_no"), rs.getInt("capacity"), rs.getString("location"), rs.getInt("status") == 1, listDevices(rs.getLong("id"))), id)
                .stream().findFirst().orElse(null);
    }

    private List<DeviceRow> listDevices(Long roomId) {
        return jdbc.query("SELECT id, device_name, device_type, quantity, status FROM device WHERE room_id = ? AND deleted = 0 ORDER BY id",
                (rs, rowNum) -> new DeviceRow(rs.getLong("id"), rs.getString("device_name"), rs.getString("device_type"), rs.getInt("quantity"), rs.getInt("status") == 1), roomId);
    }

    public boolean hasRoomConflict(Long roomId, LocalDateTime start, LocalDateTime end, Long exceptId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM meeting_book
                WHERE room_id = ? AND deleted = 0 AND status IN ('PENDING','APPROVED','IN_PROGRESS')
                  AND start_time < ? AND end_time > ? AND (? IS NULL OR id <> ?)
                """, Integer.class, roomId, end, start, exceptId, exceptId);
        return count != null && count > 0;
    }

    public boolean hasRoomConflictExceptSeries(Long roomId, LocalDateTime start, LocalDateTime end, Long seriesId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM meeting_book
                WHERE room_id = ? AND deleted = 0 AND status IN ('PENDING','APPROVED','IN_PROGRESS')
                  AND start_time < ? AND end_time > ? AND (series_id IS NULL OR series_id <> ?)
                """, Integer.class, roomId, end, start, seriesId);
        return count != null && count > 0;
    }

    public boolean hasUserConflict(Long userId, LocalDateTime start, LocalDateTime end, Long exceptId) {
        return !userConflictKeys(userId, start, end, exceptId).isEmpty();
    }

    // Keys stay server-side; they detect changed meetings without exposing other meetings' details.
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

    public Map<String, Integer> deviceInventory(Long roomId) {
        return jdbc.query("SELECT device_type, SUM(quantity) quantity FROM device WHERE room_id = ? AND status = 1 AND deleted = 0 GROUP BY device_type",
                rs -> { Map<String, Integer> map = new java.util.HashMap<>(); while (rs.next()) map.put(rs.getString("device_type"), rs.getInt("quantity")); return map; }, roomId);
    }

    public record RoomRow(Long id, String roomName, Integer floorNo, Integer capacity, String location, Boolean active, List<DeviceRow> devices) {}
    public record DeviceRow(Long id, String deviceName, String deviceType, Integer quantity, Boolean active) {}
}
