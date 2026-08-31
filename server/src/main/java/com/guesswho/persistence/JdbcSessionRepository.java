package com.guesswho.persistence;

import com.guesswho.account.Account;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores logged-in sessions in the result database.
 */
public class JdbcSessionRepository implements SessionRepository {
    private static final String INSERT_SQL = """
            INSERT INTO account_sessions (account_id, token_hash, expires_at)
            VALUES (?, ?, ?)
            """;

    private static final String SELECT_SQL = """
            SELECT account.id, account.username
            FROM account_sessions session
            JOIN accounts account ON account.id = session.account_id
            WHERE session.token_hash = ? AND session.expires_at > ?
            """;

    private final JdbcTemplate jdbcTemplate;

    /**
     * @param jdbcTemplate JDBC operations for the result database
     */
    public JdbcSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void create(long accountId, String tokenHash, Instant expiresAt) {
        jdbcTemplate.update(INSERT_SQL, accountId, tokenHash, Timestamp.from(expiresAt));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Account> findAccount(String tokenHash, Instant now) {
        RowMapper<Account> rowMapper = (resultSet, rowNumber) ->
                new Account(resultSet.getLong("id"), resultSet.getString("username"));
        //Expiry is part of the query rather than a check afterwards, so an
        //expired session cannot be used by any path that forgets to look.
        List<Account> found =
                jdbcTemplate.query(SELECT_SQL, rowMapper, tokenHash, Timestamp.from(now));
        return found.stream().findFirst();
    }

    @Override
    @Transactional
    public void delete(String tokenHash) {
        jdbcTemplate.update("DELETE FROM account_sessions WHERE token_hash = ?", tokenHash);
    }

    @Override
    @Transactional
    public int deleteAllFor(long accountId) {
        return jdbcTemplate.update(
                "DELETE FROM account_sessions WHERE account_id = ?", accountId);
    }

    @Override
    @Transactional
    public int deleteExpired(Instant now) {
        return jdbcTemplate.update(
                "DELETE FROM account_sessions WHERE expires_at <= ?", Timestamp.from(now));
    }
}
