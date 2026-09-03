package org.jdbscript.impl.conversion;

import java.time.LocalDate;

public class LocalDateConverter implements IJDBTypeConverter {
    @Override
    public boolean canConvert(Object value) {
        return value != null && value.getClass() == LocalDate.class;
    }

    @Override
    public Object convert(Object value) {
        return java.sql.Date.valueOf((LocalDate) value);
    }
}
