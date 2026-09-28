package org.jdbscript.examples.scripting;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jdbscript.IJDBEngine;
import org.jdbscript.JDBEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A resetDB/insertDB script isn't a dataset format — it's the lambda's whole method body,
 * running as plain Java. Loops, helper methods, whatever generates the data you need.
 * <p>
 * The one rule worth internalizing: seed your randomness. {@code new Random(42)} makes "random"
 * data fully reproducible; {@code new Random()} (or {@code Math.random()}) makes your test flaky
 * the day it happens to generate an edge case you didn't expect.
 */
class BulkScriptingTest {

    private static HikariDataSource dataSource;
    private static IJDBEngine<IAppSchema> engine;
    private static LeaderboardService leaderboard;

    @BeforeAll
    static void createSchema() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:scripting;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("sa");
        dataSource = new HikariDataSource(config);

        try (Connection cnn = dataSource.getConnection(); Statement stmt = cnn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE players (
                        id INT PRIMARY KEY,
                        username VARCHAR(100),
                        score INT
                    )
                    """);
        }

        engine = JDBEngine.builder(IAppSchema.class)
                .dataSource(dataSource)
                .build();
        leaderboard = new LeaderboardService(dataSource);
    }

    @AfterAll
    static void closeDataSource() {
        dataSource.close();
    }

    @Test
    void seeding_a_hundred_players_is_just_a_for_loop() {
        engine.resetDB(db -> {
            Random random = new Random(42);
            for (int i = 1; i <= 100; i++) {
                db.players().id(i).username(FunNames.next(random) + "_" + i).score(random.nextInt(1000));
            }
            // A known #1, so the assertion below doesn't need to reimplement the RNG to know
            // who's supposed to win.
            db.players().id(999).username("undisputed_champion").score(1_000_000);
        });

        assertEquals(101, leaderboard.countPlayers());
        assertEquals(List.of("undisputed_champion"), leaderboard.topUsernames(1));
    }

    @Test
    void a_seeded_random_makes_the_leaderboard_reproducible() {
        engine.resetDB(BulkScriptingTest::seedOneHundredPlayers);
        List<String> firstRun = leaderboard.topUsernames(5);

        engine.resetDB(BulkScriptingTest::seedOneHundredPlayers);
        List<String> secondRun = leaderboard.topUsernames(5);

        // Same seed, same 100 rows, same top 5 — every time, on every machine.
        assertEquals(firstRun, secondRun);
    }

    private static void seedOneHundredPlayers(IAppSchema db) {
        Random random = new Random(42);
        for (int i = 1; i <= 100; i++) {
            db.players().id(i).username(FunNames.next(random) + "_" + i).score(random.nextInt(1000));
        }
    }
}
