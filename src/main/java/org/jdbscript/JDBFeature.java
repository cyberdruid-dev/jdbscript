package org.jdbscript;

/**
 * Opt-in switches for narrow, DBMS-specific behaviors that don't warrant a dedicated
 * {@code Builder} method of their own (see {@link CacheStrategy}/{@link ValidationStrategy} for
 * settings that do). Constants are grouped by the concern they answer; within the same group only
 * one can be active at a time - {@link JDBEngine.Builder#feature(JDBFeature)} enforces that a
 * later call for a group replaces an earlier one rather than adding to it.
 */
public enum JDBFeature {
    /**
     * DB2 only. {@code afterInsert()} resets sequences to a safe value so a later
     * auto-generated ID doesn't collide with a manually-inserted one - but DB2 refuses to
     * {@code ALTER SEQUENCE} a sequence that's implicitly owned by an identity column
     * (SQLCODE -20142). This constant leaves such sequences untouched: an identity column keeps
     * whatever counter it already has, so a manually-inserted ID could still collide with a later
     * auto-generated one.
     */
    DB2_ID_OWNED_SEQUENCE_NOT_MODIFIED(Group.DB2_ID_OWNED_SEQUENCE),
    /**
     * DB2 only. Resets an identity column's counter via
     * {@code ALTER TABLE ... ALTER COLUMN ... RESTART WITH}, the one statement DB2 actually
     * allows for it, closing the same collision gap {@code ALTER SEQUENCE} closes for a regular
     * sequence.
     */
    DB2_ID_OWNED_SEQUENCE_RESTART_WITH(Group.DB2_ID_OWNED_SEQUENCE),
    /**
     * DB2 only, and the default: fails loudly instead of silently leaving an identity column's
     * counter unreset. Forces an explicit choice between
     * {@link #DB2_ID_OWNED_SEQUENCE_NOT_MODIFIED} and {@link #DB2_ID_OWNED_SEQUENCE_RESTART_WITH}
     * rather than shipping a silent collision risk.
     */
    DB2_ID_OWNED_SEQUENCE_ERROR(Group.DB2_ID_OWNED_SEQUENCE);

    /** Identifies a set of mutually-exclusive {@link JDBFeature} constants. */
    public enum Group {
        DB2_ID_OWNED_SEQUENCE
    }

    private final Group group;

    JDBFeature(Group group) {
        this.group = group;
    }

    /**
     * The group of mutually-exclusive alternatives this feature belongs to - within a group,
     * only one constant can be active at a time (see {@link JDBEngine.Builder#feature(JDBFeature)}).
     *
     * @return this feature's group
     */
    public Group getGroup() {
        return group;
    }
}
