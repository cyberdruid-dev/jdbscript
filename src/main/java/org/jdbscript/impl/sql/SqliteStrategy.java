package org.jdbscript.impl.sql;

import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

class SqliteStrategy extends DefaultSqlExecutorStrategy {
    private final DateFormat LOCAL_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private final DateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
    @Override
    public void setInputStream(PreparedStatement stmt, int columnIndex, InputStream value) throws SQLException {
        if (value == null) {
            stmt.setNull(columnIndex, Types.BLOB);
        } else {
            stmt.setBinaryStream(columnIndex, value);
        }
    }

    @Override
    public void setByteArray(PreparedStatement stmt, int columnIndex, byte[] bytes) throws SQLException {
        if (bytes == null) {
            stmt.setNull(columnIndex, Types.BLOB);
        } else {
            stmt.setBytes(columnIndex, bytes);
        }
    }

    @Override
    public Object getColumnValue(ResultSet rs, int columnIndex, String expectedType) throws SQLException {
        try {
            if ("LocalDate".equals(expectedType)) {
                return asLocalDate(rs.getString(columnIndex));
            } else if ("Date".equals(expectedType)) {
                Timestamp ts = asTimestamp(rs.getString(columnIndex));
                return ts == null ? null : new Date(ts.getTime());
            } else if ("Timestamp".equals(expectedType)) {
                return asTimestamp(rs.getString(columnIndex));
            }
        } catch (ParseException e) {
            throw new SQLException("Failed to parse SQLite date/timestamp", e);
        }
        return super.getColumnValue(rs, columnIndex, expectedType);
    }

    private Timestamp asTimestamp(String timestampStr) throws ParseException {
        if (timestampStr == null) return null;
        try {
            return new Timestamp(Long.parseLong(timestampStr));
        } catch (NumberFormatException e) {
            return new Timestamp(TIMESTAMP_FORMAT.parse(timestampStr).getTime());
        }
    }

    private LocalDate asLocalDate(String dateStr) throws ParseException {
        if (dateStr == null) return null;
        try {
            return asLocalDate(new Date(Long.parseLong(dateStr)));
        } catch (NumberFormatException e) {
            return asLocalDate(LOCAL_DATE_FORMAT.parse(dateStr));
        }
    }

    private LocalDate asLocalDate(Date date) {
        if (date == null) {
            return null;
        }
        return LocalDate.ofInstant(new Date(date.getTime()).toInstant(), ZoneId.systemDefault());
    }
}
