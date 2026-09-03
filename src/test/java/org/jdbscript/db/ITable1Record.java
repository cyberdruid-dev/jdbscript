package org.jdbscript.db;


import org.jdbscript.IDBSchema.IDBRecord;

public interface ITable1Record extends IDBRecord {
    ITable1Record str_column_1(String value);
    ITable1Record str_column_2(String value);
    ITable1Record int_column_1(int value);
    ITable1Record long_column_2(Long value);

}
