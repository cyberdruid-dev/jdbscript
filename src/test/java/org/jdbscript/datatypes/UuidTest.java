package org.jdbscript.datatypes;

import org.jdbscript.IDbSchema;
import org.jdbscript.IDbSchema.IDBRecord;
import org.jdbscript.IJDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.UUID;

@Test
public class UuidTest extends JdbAbstractTest {

    private final static String UUID_TABLE = "uuid_table";

    public final static UUID uuid1 = UUID.fromString("00000011-0012-0013-0014-000000000015");
    public final static UUID uuid2 = UUID.fromString("00000021-0022-0023-0024-000000000025");

    private interface IUuidTable extends IDBRecord {
        IUuidTable uuid_column(UUID value);
    }
    private interface IUuidTestSchema extends IDbSchema {

        IUuidTable uuid_table();

    }

    private static abstract class UuidClassScript implements IUuidTestSchema {{
        uuid_table().uuid_column(uuid1);
        uuid_table().uuid_column(uuid2);
    }};


    @BeforeMethod
    public void beforeMethod(){
        cleanupTables(UUID_TABLE);
    }

    private final IJDBEngine<IUuidTestSchema> engine = createEngine(IUuidTestSchema.class);

    public void test_insert_uuid() {
        engine.resetDB((db)->{
            db.uuid_table().uuid_column(uuid1);
            db.uuid_table().uuid_column(uuid2);
        });

        assertTableValues(table(UUID_TABLE,
                columns("uuid_column:UUID"),
                row(uuid1),
                row(uuid2)
        ));
    }

    @Test
    public void insert_uuid_should_work_for_class_scripts() {
        engine.resetDB(UuidClassScript.class);

        assertTableValues(table(UUID_TABLE,
                columns("uuid_column:UUID"),
                row(uuid1),
                row(uuid2)
        ));
    }

    @Test(dependsOnMethods = "test_insert_uuid")
    public void uuid_field_should_accept_null() {
        engine.resetDB((db)->{
            db.uuid_table().uuid_column(uuid1);
            db.uuid_table().uuid_column(null);
        });

        assertTableValues(table(UUID_TABLE,
                columns("uuid_column:UUID"),
                row(new Object[]{uuid1}),
                row(new Object[]{null})
        ));
    }

}
