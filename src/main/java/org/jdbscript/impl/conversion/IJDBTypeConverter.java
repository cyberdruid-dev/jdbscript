package org.jdbscript.impl.conversion;

public interface IJDBTypeConverter {

    boolean canConvert(Object value);

    Object convert(Object value);

}
