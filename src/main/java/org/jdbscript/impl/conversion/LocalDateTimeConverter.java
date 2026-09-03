package org.jdbscript.impl.conversion;

import java.sql.Timestamp;
import java.time.LocalDateTime;

public class LocalDateTimeConverter implements IJDBTypeConverter {
    @Override
    public boolean canConvert(Object value) {
        return value != null && value.getClass() == LocalDateTime.class;
    }

    @Override
    public Object convert(Object value) {
        return Timestamp.valueOf((LocalDateTime) value);
    }
}
