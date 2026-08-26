package org.jdbscript.impl.dbunit;

import org.jdbscript.DbmsType;
import org.jdbscript.impl.TypedNull;
import org.dbunit.util.Base64;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.jdbscript.DbmsType.*;

public class DbUnitTypeConverter {
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private Map<String, Object> replacements = new HashMap<>();

    public String convert(Object value, DbmsType dbmsType) {
        Object result = value;
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        if(result instanceof InputStream) {
            InputStream in = (InputStream) result;
            try {
                result = in.readAllBytes();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if(result instanceof UUID && (dbmsType == ORACLE || dbmsType == HSQLDB)) {
            result = toBytes((UUID) result);
        }
        if(result instanceof UUID && dbmsType == MARIADB) {
            result = replace("uuid::" + result, result);
        }
        if(result instanceof byte[]) {
            byte[] bytes = (byte[]) result;
            result = "[BASE64]"+Base64.encodeBytes(bytes);
        }
        if(result instanceof Date) {
            result = dateFormat.format((Date)result);
        }
        if(result == null || value instanceof TypedNull) {
            result = replace("[NULL]", null);
        }
        return result+"";
    }

    private String replace(String xmlValue, Object realValue) {
        replacements.put(xmlValue, realValue);
        return xmlValue;
    }

    public byte[] toBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES*2);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    public Map<String, Object> getReplacements() {
        return replacements;
    }
}
