package org.jdbscript.impl.conversion;

import org.jdbscript.impl.JDBRecord;
import org.jdbscript.impl.JDBScript;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;

public class JDBTypeConverter {

    private final List<IJDBTypeConverter> converters = new ArrayList<>();
    {
        converters.add(new EnumToStringConverter());
        converters.add(new DateConverter());
        converters.add(new InstantConverter());
    }

    public void addConverter(IJDBTypeConverter converter) {
        converters.add(converter);
    }

    public void convertTypes(JDBScript script) {
        script.getRecords().forEach(this::convertTypes);
    }

    private void convertTypes(JDBRecord jDbRecord) {
        jDbRecord.getColumns().entrySet().forEach(this::convertTypes);
    }

    private void convertTypes(Entry<String, Object> entry) {
        Object value = entry.getValue();
        for(var converter:converters) {
            if(converter.canConvert(value)) {
                value = converter.convert(value);
                entry.setValue(value);
                break;
            }
        }
    }

    public void setConverters(Collection<IJDBTypeConverter> converters) {
        this.converters.clear();
        this.converters.addAll(converters);
    }
}
