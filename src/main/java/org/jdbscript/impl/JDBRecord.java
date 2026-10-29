package org.jdbscript.impl;

import java.util.LinkedHashMap;
import java.util.Map;

public class JDBRecord {
    private String tableName;
    private Map<String, Object> columns = new LinkedHashMap<>();
    // Converters rewrite columns in place; assertion messages show what the script wrote.
    private final Map<String, Object> writtenValues = new LinkedHashMap<>();

    public JDBRecord(String tableName){
        this.tableName = tableName;
    }

    public void setColumnValue(String columnName, Object value) {
        columns.put(columnName, value);
        writtenValues.put(columnName, value);
    }

    public String getTableName() {
        return tableName;
    }

    public Map<String, Object> getColumns() {
        return columns;
    }

    public Map<String, Object> getWrittenValues() {
        return writtenValues;
    }

    @Override
    public String toString() {
        return "JDBRecord [tableName=" + tableName + ", columns=" + columns + "]";
    }

    public boolean hasValueFor(String columnName) {
        return columns.containsKey(columnName);
    }
}
