package org.jdbscript;

import org.jdbscript.impl.JDbRecord;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default implementation of {@link IDbRecordTools} providing in-memory sequence generation
 * and regex-based string template replacement for record defaults.
 */
public class RecordTools implements IDbRecordTools{
    private final static Pattern TEMPLATE_PATTERN = Pattern.compile("\\$\\{(\\w+)\\}");

    private final Map<String, AtomicLong> counters = new HashMap<>();
    private JDbRecord record;

    /**
     * Creates a new instance of {@code RecordTools}.
     */
    public RecordTools() {
    }

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
        return counters.computeIfAbsent(name, k -> new AtomicLong(firstValue)).getAndIncrement();
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
