package org.jdbscript.impl.sql;

import java.nio.ByteBuffer;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

class OracleStrategy extends DefaultSqlExecutorStrategy {

    @Override
    public void setUUID(PreparedStatement stmt, int i, UUID uuid) throws SQLException {
        byte[] bytes = uuid == null? null : toBytes(uuid);
        stmt.setBytes(i, bytes);
    }

    @Override
    public void afterInsert(Connection cnn) throws SQLException {
        resetOracleSequences(cnn);
    }

    public void resetOracleSequences(Connection cnn) throws SQLException {
            try(Statement stmt = cnn.createStatement()) {
                String sql;
                Map<String, Long> seqValueMap = getSequences(stmt);
                for (String seqName : seqValueMap.keySet()) {
                    Long increment = 10000 - seqValueMap.get(seqName);
                    if (increment == 0) {
                        continue;
                    }
                    if (seqName.startsWith("ISEQ$$")) {
                        sql = """
                            DECLARE
                            maxseq NUMBER;
                            temp NUMBER;
                            BEGIN
                              SELECT %s.NEXTVAL INTO maxseq FROM DUAL;
                              FOR i IN maxseq .. 10000
                              LOOP
                                SELECT %s.NEXTVAL INTO temp FROM DUAL;
                              END LOOP;
                            END;
                        """.formatted(seqName, seqName);
                        CallableStatement call = cnn.prepareCall(sql);
                        call.execute();
                        call.close();
                    } else {
                        stmt.executeUpdate("alter sequence " + seqName + " increment by " + increment);
                        stmt.executeQuery("select " + seqName + ".nextval from dual").close();
                        stmt.executeUpdate("alter sequence " + seqName + " increment by 1");
                        stmt.executeQuery("select " + seqName + ".nextval from dual").close();
                        stmt.executeUpdate("alter sequence " + seqName + " nocache");
                    }
                }
            }
    }

    private Map<String, Long> getSequences(Statement stmt) throws SQLException {
        Map<String, Long> result = new HashMap<>();
        String sql = "SELECT SEQUENCE_NAME,LAST_NUMBER FROM user_sequences";// WHERE SEQUENCE_OWNER=";
        try(ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String seqName = rs.getString("SEQUENCE_NAME");
                Long seqValue = rs.getLong("LAST_NUMBER");
                result.put(seqName, seqValue);
            }
        }
        return result;
    }

    private byte[] toBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES*2);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }
}
