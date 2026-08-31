package com.guesswho.persistence;

import com.guesswho.account.Account;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores registered players in the result database.
 *
 * <p>Two columns hold the name: one as it was typed, which is what a player
 * sees, and one folded to lower case, which is what uniqueness is enforced on.
 * Without the second, {@code Sam} and {@code sam} are different accounts that
 * look identical on a leaderboard, and one can be used to impersonate the
 * other.</p>
 */
public class JdbcAccountRepository implements AccountRepository {
    private static final String INSERT_SQL = """
            INSERT INTO accounts (username, username_folded, password_hash)
            VALUES (?, ?, ?)
            """;

    private static final String SELECT_SQL = """
            SELECT id, username, password_hash
            FROM accounts
            WHERE username_folded = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    /**
     * Creates a repository over the configured database.
     *
     * @param jdbcTemplate JDBC operations for the result database
     */
    public JdbcAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public Account create(String username, String passwordHash) {
        try {
            jdbcTemplate.update(INSERT_SQL, username, fold(username), passwordHash);
        }
        catch (DuplicateKeyException alreadyTaken) {
            //Checking first and inserting after leaves a gap two requests can
            //both pass through. The unique constraint is what actually decides,
            //so the failure it raises is what gets reported.
            throw new UsernameTakenException(username);
        }
        return findByUsername(username)
                .orElseThrow(() -> new IllegalStateException(
                        "Account vanished immediately after being created: " + username))
                .account();
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<StoredAccount> findByUsername(String username) {
        RowMapper<StoredAccount> rowMapper = (resultSet, rowNumber) -> new StoredAccount(
                new Account(resultSet.getLong("id"), resultSet.getString("username")),
                resultSet.getString("password_hash"));
        List<StoredAccount> found = jdbcTemplate.query(SELECT_SQL, rowMapper, fold(username));
        return found.stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(String username) {
        return findByUsername(username).isPresent();
    }

    /** Lower case in a fixed locale: in a Turkish one, I does not fold to i. */
    private static String fold(String username) {
        return username.toLowerCase(Locale.ROOT);
    }
}
