package org.jdbscript.impl.conversion;

public class EnumToStringConverter implements IJDBTypeConverter {
    @Override
    public boolean canConvert(Object value) {
        return value instanceof Enum;
    }

    @Override
    public Object convert(Object value) {
        return value == null ? null : value.toString();
    }
}
