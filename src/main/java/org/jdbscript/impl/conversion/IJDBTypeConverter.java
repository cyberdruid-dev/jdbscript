package org.jdbscript.impl.conversion;

/**
 * Strategy interface for custom data type converters used during record value preparation before SQL execution.
 */
public interface IJDBTypeConverter {

    /**
     * Determines whether this converter can convert the given source value.
     *
     * @param value the source value to inspect
     * @return {@code true} if this converter can handle the value, {@code false} otherwise
     */
    boolean canConvert(Object value);

    /**
     * Converts the given source value into a JDBC-compatible representation.
     *
     * @param value the source value to convert
     * @return the converted JDBC-compatible value
     */
    Object convert(Object value);

}
