package org.jdbscript.impl.conversion;

import java.sql.Timestamp;
import java.util.Date;

public class DateConverter implements IJDBTypeConverter{
    @Override
    public boolean canConvert(Object value) {
        return value != null && value.getClass() == Date.class;
    }

    @Override
    public Object convert(Object value) {
        return new Timestamp(((Date) value).getTime());
    }
}
