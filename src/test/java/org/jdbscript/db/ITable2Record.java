package org.jdbscript.db;


import org.jdbscript.IDBSchema.IDBRecord;

public interface ITable2Record extends IDBRecord {
    ITable2Record id(int value);
    ITable2Record int_column_1(Integer value);
    ITable2Record long_column_1(Long value);

    default void defaults() {
        int_column_1(7);
    }
}
