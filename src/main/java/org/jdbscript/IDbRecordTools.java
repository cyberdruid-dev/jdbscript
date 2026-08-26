package org.jdbscript;

import org.jdbscript.impl.JDbRecord;

/**
 * TODO: document it!
 */
public interface IDbRecordTools {
    void setRecord(JDbRecord record);

    int nextIntId(String name, int firstValue);
    long nextLongId(String name, long firstValue);

    String strValue(String s);
}
