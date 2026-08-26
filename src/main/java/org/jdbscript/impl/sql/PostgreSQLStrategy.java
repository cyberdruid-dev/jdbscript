package org.jdbscript.impl.sql;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class PostgreSQLStrategy extends  DefaultSqlExecutorStrategy{
    @Override
    public void afterInsert(Connection cnn) throws SQLException {
        resetPostgreSequences(cnn);
    }

    @Override
    public void setUUID(PreparedStatement stmt, int i, UUID uuid) throws SQLException {
        stmt.setObject(i, uuid);
    }

    public void resetPostgreSequences(Connection cnn) throws SQLException {
            try (Statement stmt = cnn.createStatement()) {
                int majorVersion = getMajorVersion(stmt);
                List<String> seqNames;
                if(majorVersion >= 16) {
                    seqNames = getSequences16(stmt, cnn);
                } else {
                    seqNames = getSequences12(stmt, cnn);
                }
                String sql = "SELECT setval('%s', 10000, true);";
                for (String seqName : seqNames) {
                    stmt.executeQuery(String.format(sql, seqName));
                }
            }
    }

    private int getMajorVersion(Statement stmt) throws SQLException {
        try(ResultSet rs = stmt.executeQuery("SELECT version();")){
            rs.next();
            String version = rs.getString(1);
            Matcher m = Pattern.compile("PostgreSQL\\s(\\d+)\\.").matcher(version);
            if(!m.find()) {
                throw new RuntimeException("Can not detect PostgreSQL version from versin string '"+version+"'");
            }
            return Integer.parseInt(m.group(1));
        }
    }

    private List<String> getSequences12(Statement stmt, Connection cnn) throws SQLException {
        List<String> result = new ArrayList<>();
        String sql = String.format(""" 
                            SELECT sequence_name FROM information_schema.sequences
                            WHERE sequence_catalog ='%s';
                        """, cnn.getCatalog());
        ResultSet rs = stmt.executeQuery(sql);
        while (rs.next()) {
            result.add(rs.getString(1));
        }
        return result;
    }


    private List<String> getSequences16(Statement stmt, Connection cnn) throws SQLException {
        List<String> result = new ArrayList<>();
        String sql = String.format(""" 
                            SELECT sequencename FROM pg_sequences
                        """, cnn.getCatalog());
        ResultSet rs = stmt.executeQuery(sql);
        while (rs.next()) {
            result.add(rs.getString(1));
        }
        return result;
    }
}
