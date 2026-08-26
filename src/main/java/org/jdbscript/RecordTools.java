package org.jdbscript;

import org.jdbscript.impl.JDbRecord;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RecordTools implements IDbRecordTools{
    private final static Pattern TEMPLATE_PATTERN = Pattern.compile("\\$\\{(\\w+)\\}");

    private final Map<String, AtomicLong> counters = new HashMap<>();
    private JDbRecord record;

    @Override
    public void setRecord(JDbRecord record) {
        this.record = record;

    }

    @Override
    public int nextIntId(String name, int firstValue) {
        return (int)nextLongId(name, firstValue);
    }

    @Override
    public long nextLongId(String name, long firstValue) {
        if(!counters.containsKey(name)) {
            counters.put(name, new AtomicLong(firstValue));
        }
        return counters.get(name).getAndIncrement();
    }

    @Override
    public String strValue(String template) {
        String result = template;
        Matcher m = TEMPLATE_PATTERN.matcher(result);
        return m.replaceAll((match)->{
            String key = m.group(1);
            return record.getColumns().get(key)+"";
        });
    }

}
