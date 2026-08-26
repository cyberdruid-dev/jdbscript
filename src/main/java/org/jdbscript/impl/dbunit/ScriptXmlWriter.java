package org.jdbscript.impl.dbunit;

import org.jdbscript.DbmsType;
import org.jdbscript.impl.JDbRecord;
import org.jdbscript.impl.JDbScript;

import java.util.Map;

class ScriptXmlWriter {
    private final JDbScript script;
    private final String schemaName;
    private final DbUnitTypeConverter typeConverter = new DbUnitTypeConverter();
    private final DbmsType dbmsType;


    ScriptXmlWriter(JDbScript script, String schemaName, DbmsType dbmsType) {
        this.script = script;
        this.schemaName = schemaName;
        this.dbmsType = dbmsType;
    }

    public String getScript() {

        StringBuilder builder = new StringBuilder();

        //sortRecords(records);
        builder.append("<dataset>\n");
        for (JDbRecord record : script.getRecords()) {
            appendRecord(record, builder);
        }
        builder.append("</dataset>");
        String xmlScript = builder.toString();
        return xmlScript;
    }

    private void appendRecord(JDbRecord record, StringBuilder builder) {
        builder.append('<');
        if (schemaName != null) {
            builder.append(schemaName);
            builder.append('.');
        }
        builder.append(record.getTableName());
        Map<String, Object> parts = record.getColumns();
        for (String name : parts.keySet()) {
            Object value = parts.get(name);
            if (value != null) {
                builder.append(' ');
                builder.append(name);
                builder.append("=\"");
                builder.append(filter(value));
                builder.append('"');
            }

        }
        builder.append("/>\n");
    }

    private String filter(Object value) {
        return typeConverter.convert(value, dbmsType);
    }

    public Map<String, Object> getReplacements() {
        return typeConverter.getReplacements();
    }
}
