package org.jdbscript.liquibase;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.change.core.TagDatabaseChange;
import liquibase.changelog.ChangeSet;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.jdbscript.DBMSType;
import org.jdbscript.MigrationRunner;
import org.jdbscript.errors.JDBScriptException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

/**
 * {@link MigrationRunner} backed by Liquibase. A marker is a changeset or a tag (standing for its
 * {@code tagDatabase} changeset). A changeset is referenced as {@code id}, {@code id::author} or
 * {@code path::id::author}, whichever is unique in the changelog. A marker that matches both a tag
 * and a changeset must be prefixed with {@code tag:} or {@code changeset:}.
 */
public class LiquibaseMigrator implements MigrationRunner {

    private static final String TAG_PREFIX = "tag:";
    private static final String CHANGESET_PREFIX = "changeset:";

    private final String changeLogFile;

    /**
     * @param changeLogFile classpath-relative path to the Liquibase changelog (e.g.
     *                       {@code "db/changelog.yaml"})
     */
    public LiquibaseMigrator(String changeLogFile) {
        this.changeLogFile = changeLogFile;
    }

    @Override
    public void migrateTo(DataSource dataSource, String marker) {
        run(dataSource, liquibase -> updateUpTo(liquibase, marker, true));
    }

    @Override
    public void migrateBefore(DataSource dataSource, String marker) {
        run(dataSource, liquibase -> updateUpTo(liquibase, marker, false));
    }

    // update(count) applies the next count unrun changesets in changelog order.
    private void updateUpTo(Liquibase liquibase, String marker, boolean includingMarker) throws Exception {
        List<ChangeSet> changeSets = liquibase.getDatabaseChangeLog().getChangeSets();
        int index = indexOf(changeSets, marker);
        Set<String> unrun = liquibase.listUnrunChangeSets(new Contexts(), new LabelExpression()).stream()
                .map(ChangeSet::toString)
                .collect(toSet());
        if (!unrun.contains(changeSets.get(index).toString())) {
            throw new JDBScriptException("Liquibase changeset '" + changeSets.get(index) + "' (marker '" + marker
                    + "') is already applied (changelog '" + changeLogFile + "').");
        }
        int count = (int) changeSets.subList(0, includingMarker ? index + 1 : index).stream()
                .filter(changeSet -> unrun.contains(changeSet.toString()))
                .count();
        liquibase.update(count, new Contexts(), new LabelExpression());
    }

    private int indexOf(List<ChangeSet> changeSets, String marker) {
        if (marker.startsWith(TAG_PREFIX)) {
            String tag = marker.substring(TAG_PREFIX.length());
            return requireFound(findTag(changeSets, tag), "tag '" + tag + "'");
        }
        if (marker.startsWith(CHANGESET_PREFIX)) {
            String reference = marker.substring(CHANGESET_PREFIX.length());
            return requireFound(findChangeSet(changeSets, reference), "changeset '" + reference + "'");
        }
        int byTag = findTag(changeSets, marker);
        int byChangeSet = findChangeSet(changeSets, marker);
        if (byTag >= 0 && byChangeSet >= 0) {
            throw new JDBScriptException("Liquibase marker '" + marker + "' matches both a tag and a changeset in changelog '"
                    + changeLogFile + "'. Use '" + TAG_PREFIX + marker + "' or '" + CHANGESET_PREFIX + marker + "' to choose.");
        }
        return requireFound(Math.max(byTag, byChangeSet), "tag or changeset '" + marker + "'");
    }

    private int requireFound(int index, String description) {
        if (index < 0) {
            throw new JDBScriptException("Liquibase " + description + " not found in changelog '" + changeLogFile + "'.");
        }
        return index;
    }

    private int findTag(List<ChangeSet> changeSets, String tag) {
        for (int i = 0; i < changeSets.size(); i++) {
            if (isTaggedWith(changeSets.get(i), tag)) {
                return i;
            }
        }
        return -1;
    }

    private int findChangeSet(List<ChangeSet> changeSets, String reference) {
        List<Integer> matches = new ArrayList<>();
        for (int i = 0; i < changeSets.size(); i++) {
            if (isReferencedBy(changeSets.get(i), reference)) {
                matches.add(i);
            }
        }
        if (matches.size() > 1) {
            throw new JDBScriptException("Liquibase changeset '" + reference + "' is ambiguous in changelog '" + changeLogFile
                    + "': " + matches.stream().map(i -> changeSets.get(i).toString()).collect(joining(", "))
                    + ". Use 'id::author' or 'path::id::author' to choose.");
        }
        return matches.isEmpty() ? -1 : matches.get(0);
    }

    // ChangeSet.toString() is "path::id::author".
    private boolean isReferencedBy(ChangeSet changeSet, String reference) {
        return reference.equals(changeSet.getId())
                || reference.equals(changeSet.getId() + "::" + changeSet.getAuthor())
                || reference.equals(changeSet.toString());
    }

    private boolean isTaggedWith(ChangeSet changeSet, String tag) {
        return changeSet.getChanges().stream()
                .anyMatch(change -> change instanceof TagDatabaseChange tagChange && tag.equals(tagChange.getTag()));
    }

    @Override
    public void migrate(DataSource dataSource) {
        run(dataSource, liquibase -> liquibase.update(new Contexts()));
    }

    @Override
    public void reset(DataSource dataSource) {
        // Liquibase's dropAll() can't generate a DROP FOREIGN KEY statement for SQLite - dropping
        // an FK there requires recreating the whole table, which Liquibase has never implemented
        // (see https://github.com/liquibase/liquibase/issues/2267, closed without a fix). Drop
        // tables directly instead; this also naturally clears DATABASECHANGELOG, same as
        // dropAll() does everywhere else.
        DBMSType dbmsType = detectDbmsType(dataSource);
        if (dbmsType == DBMSType.SQLITE) {
            resetSqlite(dataSource);
        } else if (dbmsType == DBMSType.SPANNER) {
            resetSpanner(dataSource);
        } else {
            run(dataSource, Liquibase::dropAll);
        }
    }

    private DBMSType detectDbmsType(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return DBMSType.getType(connection.getMetaData());
        } catch (SQLException e) {
            throw new JDBScriptException("Failed to detect database type (changelog '" + changeLogFile + "').", e);
        }
    }

    private void resetSqlite(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            // Safe regardless of the caller's own PRAGMA setting - dropping a referenced table
            // would otherwise fail the same way dropAll() does.
            statement.execute("PRAGMA foreign_keys = OFF");
            List<String> tables = new ArrayList<>();
            try (var rs = connection.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
            }
            for (String table : tables) {
                statement.execute("DROP TABLE \"" + table + "\"");
            }
        } catch (SQLException e) {
            throw new JDBScriptException("Failed to reset SQLite database (changelog '" + changeLogFile + "').", e);
        }
    }

    private void resetSpanner(DataSource dataSource) {
        // dropAll() opens a transaction to acquire its changelog lock, then fails issuing DDL
        // inside it - Spanner forbids DDL in an open transaction. Drop tables directly in
        // autocommit mode instead, retrying since Spanner has no SQLite-style pragma to disable FK
        // enforcement.
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            for (int i = 0; i < 5; i++) {
                List<String> tables = new ArrayList<>();
                try (var rs = connection.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
                    while (rs.next()) {
                        tables.add(rs.getString("TABLE_NAME"));
                    }
                }
                if (tables.isEmpty() || (tables.size() <= 2 && tables.stream().allMatch(t -> t.startsWith("DATABASECHANGELOG")))) {
                    break;
                }
                try (Statement statement = connection.createStatement()) {
                    for (String table : tables) {
                        if (table.startsWith("DATABASECHANGELOG")) continue;
                        try {
                            statement.execute("DROP TABLE " + table);
                        } catch (Exception e) {
                            // FK-dependent table not yet droppable this pass - retry next iteration.
                        }
                    }
                }
            }
            try (Statement statement = connection.createStatement()) {
                try { statement.execute("DROP TABLE DATABASECHANGELOGLOCK"); } catch (Exception e) {}
                try { statement.execute("DROP TABLE DATABASECHANGELOG"); } catch (Exception e) {}
            }
        } catch (SQLException e) {
            throw new JDBScriptException("Failed to reset Spanner database (changelog '" + changeLogFile + "').", e);
        }
    }

    @FunctionalInterface
    private interface LiquibaseAction {
        void run(Liquibase liquibase) throws Exception;
    }

    private void run(DataSource dataSource, LiquibaseAction action) {
        try (Connection connection = dataSource.getConnection()) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            // Not just for the JDBC connection (the outer try already closes that) - Liquibase
            // keeps this Database in an internal per-instance executor cache that's only cleared
            // by close(), so skipping it leaks an entry there on every call.
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            ClassLoader loader = contextClassLoader != null ? contextClassLoader : LiquibaseMigrator.class.getClassLoader();
            try (Liquibase liquibase = new Liquibase(changeLogFile, new ClassLoaderResourceAccessor(loader), database)) {
                action.run(liquibase);
            }
        } catch (JDBScriptException e) {
            throw e;
        } catch (Exception e) {
            throw new JDBScriptException("Liquibase migration failed (changelog '" + changeLogFile + "').", e);
        }
    }
}
