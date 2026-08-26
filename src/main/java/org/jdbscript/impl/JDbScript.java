package org.jdbscript.impl;

import java.util.ArrayList;
import java.util.List;

public class JDbScript {

    private final List<JDbRecord> records = new ArrayList<>();

    public List<JDbRecord> getRecords() {
        return records;
    }

    public void addRecord(JDbRecord record){
        records.add(record);
    }

    public void append(JDbScript dbScript) {
        records.addAll(dbScript.records);
    }

    @Override
    public String toString() {
        String result = "JDbScript[\n";
        for(var item: records) {
            result += item.toString() + "\n";
        }
        result +="]";
        return result;
    }
}
