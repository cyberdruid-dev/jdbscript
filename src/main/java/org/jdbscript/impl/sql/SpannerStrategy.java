package org.jdbscript.impl.sql;

import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;

class SpannerStrategy extends DefaultSqlExecutorStrategy {

    @Override
    public void setObject(PreparedStatement stmt, int i, Object value) throws SQLException {
        // Unlike most drivers, Spanner won't coerce a Timestamp into a DATE column - downcast only
        // when the target column actually is one (checked via metadata, not the value's shape).
        if (value instanceof Timestamp timestamp && isDateColumn(stmt, i)) {
            value = new java.sql.Date(timestamp.getTime());
        }
        super.setObject(stmt, i, value);
    }

    private boolean isDateColumn(PreparedStatement stmt, int columnIndex) {
        try {
            ParameterMetaData meta = stmt.getParameterMetaData();
            return meta != null && meta.getParameterType(columnIndex) == Types.DATE;
        } catch (SQLException e) {
            return false;
        }
    }
}
