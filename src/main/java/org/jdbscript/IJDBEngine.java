package org.jdbscript;

import java.util.function.Consumer;

public interface IJDBEngine <T extends IDbSchema>{

    void resetDB(Class<? extends T> scriptClass);

    void insertDB(Class<? extends T> scriptClass);

    void resetDB(Consumer<T> db);

    void insertDB(Consumer<T> db);

    void cleanupDB();
}
