package org.jdbscript.db;


import org.jdbscript.IDbSchema.IDBRecord;

public interface ITable2Record extends IDBRecord {
    ITable2Record int_column_1(int value);
    ITable2Record long_column_2(Long value);

    default void defaults() {
        int_column_1(7);
    }
}
