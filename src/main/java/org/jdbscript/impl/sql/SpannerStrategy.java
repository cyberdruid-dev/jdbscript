package org.jdbscript.impl.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.concurrent.ThreadLocalRandom;
import org.jdbscript.impl.JDBScript;
import org.jdbscript.impl.JDBRecord;

class SpannerStrategy extends DefaultSqlExecutorStrategy {

    @Override
    public void beforeInsert(Connection cnn, JDBScript dbScript) throws SQLException {
        for (JDBRecord record : dbScript.getRecords()) {
            String tableName = record.getTableName();
            String pkName = "generated_int_id_table".equalsIgnoreCase(tableName) ? "generated_id_column" : "id";
            if (!record.getColumns().containsKey(pkName)) {
                // Use a random long for bigint PK to avoid collisions and satisfy Spanner PK requirement
                record.getColumns().put(pkName, ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
            }
        }
    }

    @Override
    public void setObject(PreparedStatement stmt, int i, Object value) throws SQLException {
        if (value instanceof Timestamp) {
            Timestamp ts = (Timestamp) value;
            if (ts.getNanos() == 0 && ts.getHours() == 0 && ts.getMinutes() == 0 && ts.getSeconds() == 0) {
                value = new java.sql.Date(ts.getTime());
            }
        }
        super.setObject(stmt, i, value);
    }
}
