package org.jdbscript.examples.insertpower;

import org.jdbscript.IDBSchema;

public interface IAppSchema extends IDBSchema {

    INotificationRecord notifications();

    interface INotificationRecord extends IDBRecord {
        INotificationRecord id(Integer id);

        INotificationRecord message(String message);
    }
}
