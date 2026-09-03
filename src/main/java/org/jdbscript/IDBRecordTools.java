package org.jdbscript;

import org.jdbscript.impl.JDBRecord;

/**
 * Utility tools interface available to record default methods for dynamic value generation,
 * sequences, and template interpolation.
 */
public interface IDBRecordTools {

    /**
     * Associates the current record context with these tools.
     *
     * @param record the current database record
     */
    void setRecord(JDBRecord record);

    /**
     * Generates or increments an integer sequence counter for the given name.
     *
     * @param name       the unique identifier for the sequence counter
     * @param firstValue the initial value if the sequence counter has not yet been initialized
     * @return the next integer sequence value
     */
    int nextIntId(String name, int firstValue);

    /**
     * Generates or increments a long sequence counter for the given name.
     *
     * @param name       the unique identifier for the sequence counter
     * @param firstValue the initial value if the sequence counter has not yet been initialized
     * @return the next long sequence value
     */
    long nextLongId(String name, long firstValue);

    /**
     * Resolves placeholders in the template string using existing column values from the current record.
     * <p>
     * Placeholders should follow the format <code>${columnName}</code>.
     * </p>
     *
     * @param s the template string containing placeholders (e.g. <code>"${username}@example.com"</code>)
     * @return the interpolated string
     */
    String strValue(String s);
}
