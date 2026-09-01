package com.guesswho.persistence;

import com.guesswho.room.RoomStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores online rooms in the result database.
 */
public class JdbcRoomRepository implements RoomRepository {
    private static final String SELECT_SQL = """
            SELECT room.code, room.status, room.game_state, room.version,
                   room.host_last_seen, room.guest_last_seen,
                   room.created_at, room.updated_at, room.expires_at,
                   room.host_account_id, host.username AS host_name,
                   room.guest_account_id, guest.username AS guest_name
            FROM game_rooms room
            JOIN accounts host ON host.id = room.host_account_id
            LEFT JOIN accounts guest ON guest.id = room.guest_account_id
            WHERE room.code = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    /**
     * @param jdbcTemplate JDBC operations for the result database
     */
    public JdbcRoomRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public StoredRoom create(String code, long hostAccountId, Instant expiresAt) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO game_rooms (code, host_account_id, status, expires_at)
                    VALUES (?, ?, ?, ?)
                    """, code, hostAccountId, RoomStatus.WAITING.name(),
                    Timestamp.from(expiresAt));
        }
        catch (DuplicateKeyException taken) {
            //The unique constraint decides, not a read beforehand: two requests
            //can both find a code free and both go on to insert it.
            throw new CodeTakenException(code);
        }
        return findByCode(code).orElseThrow(() -> new IllegalStateException(
                "Room vanished immediately after being created: " + code));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredRoom> findByCode(String code) {
        List<StoredRoom> found = jdbcTemplate.query(SELECT_SQL, rowMapper(), code);
        return found.stream().findFirst();
    }

    @Override
    @Transactional
    public boolean join(String code, long guestAccountId, String gameState, Instant expiresAt) {
        //Conditional on the room still waiting and still empty. Reading first
        //and writing after leaves a gap two people racing on one code both fit
        //through, and the loser would silently take over the winner's game.
        int updated = jdbcTemplate.update("""
                UPDATE game_rooms
                SET guest_account_id = ?, game_state = ?, status = ?,
                    expires_at = ?, updated_at = CURRENT_TIMESTAMP
                WHERE code = ? AND status = ? AND guest_account_id IS NULL
                """,
                guestAccountId, gameState, RoomStatus.IN_PROGRESS.name(),
                Timestamp.from(expiresAt), code, RoomStatus.WAITING.name());
        return updated == 1;
    }

    @Override
    @Transactional
    public boolean updateGame(String code, String gameState, RoomStatus status,
            Instant expiresAt, long expectedVersion) {
        //The version in the WHERE clause is what decides. Reading it first and
        //comparing in Java would leave the same gap it is meant to close.
        int updated = jdbcTemplate.update("""
                UPDATE game_rooms
                SET game_state = ?, status = ?, expires_at = ?,
                    version = version + 1, updated_at = CURRENT_TIMESTAMP
                WHERE code = ? AND version = ?
                """, gameState, status.name(), Timestamp.from(expiresAt), code,
                expectedVersion);
        return updated == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public int openRoomCount(long hostAccountId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM game_rooms
                WHERE host_account_id = ? AND status IN (?, ?)
                """, Integer.class, hostAccountId,
                RoomStatus.WAITING.name(), RoomStatus.IN_PROGRESS.name());
        return count == null ? 0 : count;
    }

    @Override
    @Transactional
    public boolean claimMove(String code, String moveKey) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO room_move_keys (room_code, move_key) VALUES (?, ?)",
                    code, moveKey);
            return true;
        }
        catch (DuplicateKeyException alreadyApplied) {
            //The retry of a move that already happened. Not an error: the
            //client is asking for the same thing twice and getting it once.
            return false;
        }
    }

    @Override
    @Transactional
    public void deleteMoveKeys(String code) {
        jdbcTemplate.update("DELETE FROM room_move_keys WHERE room_code = ?", code);
    }

    @Override
    @Transactional
    public int deleteExpired(Instant now) {
        //The keys go with the room. They have no foreign key to cascade from,
        //because they outlive individual moves and are only ever read by code.
        jdbcTemplate.update("""
                DELETE FROM room_move_keys WHERE room_code IN (
                    SELECT code FROM game_rooms WHERE expires_at <= ?)
                """, Timestamp.from(now));
        return jdbcTemplate.update(
                "DELETE FROM game_rooms WHERE expires_at <= ?", Timestamp.from(now));
    }

    /** Null until that player has been heard from at all. */
    private static Instant instantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    @Override
    @Transactional
    public void markSeenBy(String code, long accountId, Instant seenAt) {
        //One statement rather than a read and then a write. Which side of the
        //room somebody is on is something the row already knows, and asking it
        //first would cost a query to learn what the WHERE clause can decide.
        //
        //Deliberately not touching version or updated_at: being present is not
        //a change to the game, and counting it as one would make every poll
        //look like a move to anything watching for them.
        Timestamp at = Timestamp.from(seenAt);
        jdbcTemplate.update("""
                UPDATE game_rooms
                SET host_last_seen =
                        CASE WHEN host_account_id = ? THEN ? ELSE host_last_seen END,
                    guest_last_seen =
                        CASE WHEN guest_account_id = ? THEN ? ELSE guest_last_seen END
                WHERE code = ? AND (host_account_id = ? OR guest_account_id = ?)
                """, accountId, at, accountId, at, code, accountId, accountId);
    }

    private static RowMapper<StoredRoom> rowMapper() {
        return (resultSet, rowNumber) -> {
            //Checked immediately: wasNull reports on the last column read, so
            //reading anything else first makes it answer about that instead.
            long guestId = resultSet.getLong("guest_account_id");
            Long guestAccountId = resultSet.wasNull() ? null : guestId;
            return new StoredRoom(
                    resultSet.getString("code"),
                    RoomStatus.valueOf(resultSet.getString("status")),
                    resultSet.getLong("host_account_id"),
                    resultSet.getString("host_name"),
                    guestAccountId,
                    resultSet.getString("guest_name"),
                    resultSet.getString("game_state"),
                    resultSet.getLong("version"),
                    instantOrNull(resultSet.getTimestamp("host_last_seen")),
                    instantOrNull(resultSet.getTimestamp("guest_last_seen")),
                    resultSet.getTimestamp("created_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant(),
                    resultSet.getTimestamp("expires_at").toInstant());
        };
    }
}
