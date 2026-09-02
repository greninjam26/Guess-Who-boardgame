package com.guesswho.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Every migration, against the database the server will actually be deployed on.
 *
 * <p>The rest of the suite runs on H2, which accepts things PostgreSQL does not.
 * That is comfortable and misleading: a migration only has to be portable on the
 * day it is deployed, and finding out then means finding out with a database
 * nobody can afford to recreate.</p>
 *
 * <p>Gated on {@code POSTGRES_TEST_URL} so a developer without PostgreSQL is not
 * blocked by a test they cannot run. CI always sets it, so the gate never
 * silently skips the check where it counts — a gated test that is skipped
 * everywhere is a test that does not exist.</p>
 */
@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_URL", matches = ".+")
class PostgresMigrationTest {
    @Test
    void everyMigrationRunsOnAnEmptyPostgresDatabase() {
        DataSource dataSource = postgres();

        MigrateResult result = Flyway.configure().dataSource(dataSource).load().migrate();

        assertTrue(result.success, "PostgreSQL refused a migration H2 had accepted");
        assertEquals(0, Flyway.configure().dataSource(dataSource).load().info().pending().length,
                "A migration was left pending");
    }

    @Test
    void theRoomsGameStateIsAPortableTextColumn() {
        //The one column that is not portable. H2 accepts CLOB; PostgreSQL has no
        //such type, so the whole rooms table — and therefore online play — fails
        //to build on the database this is being deployed to.
        DataSource dataSource = postgres();
        Flyway.configure().dataSource(dataSource).load().migrate();

        assertEquals("text", columnType(dataSource, "game_rooms", "game_state"));
    }

    private static DataSource postgres() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(System.getenv("POSTGRES_TEST_URL"));
        dataSource.setUser(System.getenv("POSTGRES_TEST_USER"));
        dataSource.setPassword(System.getenv("POSTGRES_TEST_PASSWORD"));
        return dataSource;
    }

    private static String columnType(DataSource dataSource, String table, String column) {
        return new JdbcTemplate(dataSource).queryForObject("""
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND column_name = ?
                """, String.class, table, column);
    }
}
