package org.jdbscript.impl;

import java.util.ArrayList;
import java.util.List;

public class JDBScript {

    private final List<JDBRecord> records = new ArrayList<>();

    public List<JDBRecord> getRecords() {
        return records;
    }

    public void addRecord(JDBRecord record){
        records.add(record);
    }

    public void append(JDBScript dbScript) {
        records.addAll(dbScript.records);
    }

    @Override
    public String toString() {
        String result = "JDBScript[\n";
        for(var item: records) {
            result += item.toString() + "\n";
        }
        result +="]";
        return result;
    }
}
