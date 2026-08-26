package org.jdbscript.impl.conversion;

import java.sql.Timestamp;
import java.time.Instant;

public class InstantConverter implements IJDBTypeConverter{
    @Override
    public boolean canConvert(Object value) {
        return value != null && value.getClass() == Instant.class;
    }

    @Override
    public Object convert(Object value) {
        return new Timestamp(((Instant) value).toEpochMilli());
    }
}
