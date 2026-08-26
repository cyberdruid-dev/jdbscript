package org.jdbscript.db;

import org.jdbscript.IDbSchema;

public interface ITestDbSchema extends IDbSchema {

    ITable1Record table_1();
    ITable2Record table_2();
}
