package org.jdbscript.impl;

import java.util.HashMap;
import java.util.Map;

public class JDBRecord {
    private String tableName;
    private Map<String, Object> columns = new HashMap<>();

    public JDBRecord(String tableName){
        this.tableName = tableName;
    }

    public void setColumnValue(String columnName, Object value) {
        columns.put(columnName, value);
    }

    public String getTableName() {
        return tableName;
    }

    public Map<String, Object> getColumns() {
        return columns;
    }

    @Override
    public String toString() {
        return "JDBRecord [tableName=" + tableName + ", columns=" + columns + "]";
    }

    public boolean hasValueFor(String columnName) {
        return columns.containsKey(columnName);
    }
}
