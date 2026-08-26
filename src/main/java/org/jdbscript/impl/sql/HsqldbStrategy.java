package org.jdbscript.impl.sql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Set;

class HsqldbStrategy extends DefaultSqlExecutorStrategy {
    private static final Logger log = LoggerFactory.getLogger(HsqldbStrategy.class);
    private Set<String> identityInsertOn;

    @Override
    public void setByteArray(PreparedStatement stmt, int columnIndex, byte[] bytes) throws SQLException {
        if(bytes == null) {
            stmt.setNull(columnIndex, Types.BLOB);
        } else {
            super.setByteArray(stmt, columnIndex, bytes);
        }
    }

}
