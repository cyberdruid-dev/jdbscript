package org.jdbscript.impl.conversion;

import java.sql.Time;
import java.time.LocalTime;

public class LocalTimeConverter implements IJDBTypeConverter {
    @Override
    public boolean canConvert(Object value) {
        return value != null && value.getClass() == LocalTime.class;
    }

    @Override
    public Object convert(Object value) {
        return Time.valueOf((LocalTime) value);
    }
}
