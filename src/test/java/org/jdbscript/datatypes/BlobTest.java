package org.jdbscript.datatypes;

import org.jdbscript.DBMSType;
import org.jdbscript.IDBSchema;
import org.jdbscript.IDBSchema.IDBRecord;
import org.jdbscript.IJDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Test
public class BlobTest extends JdbAbstractTest {

    private final static String TABLE_NAME = "blob_table";

    private interface IBlobTable extends IDBRecord {
        IBlobTable id(int value);
        IBlobTable blob_column(byte[] data);
        IBlobTable bytea_column(byte[] data);
    }
    private interface IBlobTestSchema extends IDBSchema {

        IBlobTable blob_table();

    }

    private interface IInputStreamBlobTable extends IDBRecord {
        IInputStreamBlobTable id(int value);
        IInputStreamBlobTable blob_column(InputStream data);
        IInputStreamBlobTable bytea_column(InputStream data);
    }
    private interface IInputStreamBlobTestSchema extends IDBSchema {

        IInputStreamBlobTable blob_table();

    }

    @BeforeMethod
    public void beforeMethod(){
        cleanupTables(TABLE_NAME);
    }

    private final IJDBEngine<IBlobTestSchema> engine = createEngine(IBlobTestSchema.class);
    private final IJDBEngine<IInputStreamBlobTestSchema> inputStreamEngine = createEngine(IInputStreamBlobTestSchema.class);


    public void test_insert_blob_data() {
        byte[] data = new byte[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20};

        engine.resetDB((db)->{
            db.blob_table().id(1).blob_column(data);
        });

        assertTableValues(table(TABLE_NAME,
                columns("blob_column:blob"),
                row(data)
        ));
    }

    public void blob_fields_should_accept_InputStream() {
        byte[] data = new byte[]{1,2,3,4,5,6,7,8,9,10};
        InputStream in = new ByteArrayInputStream(data);

        inputStreamEngine.resetDB((db)->{
            db.blob_table().id(1).blob_column(in);
        });

        assertTableValues(table(TABLE_NAME,
                columns("blob_column:blob"),
                row(data)
        ));
    }


    @Test(dependsOnMethods = "test_insert_blob_data")
    public void blob_field_should_accept_null() {
        byte[] data = new byte[]{1,2,3,4,5,6,7,8,9,10};
        engine.resetDB((db)->{
            db.blob_table().id(1).blob_column(data);
            db.blob_table().id(2).blob_column(null);
        });

        assertTableValues(table(TABLE_NAME,
                columns("blob_column:blob"),
                row(new Object[]{data}),
                row(new Object[]{null})
        ));
    }

    // bytea_column is bytea on Postgres, the same type as blob_column elsewhere.
    public void bytea_column_should_accept_byte_array() {
        byte[] data = new byte[]{1,2,3,4,5};

        engine.resetDB((db)->{
            db.blob_table().id(1).bytea_column(data);
        });

        assertTableValues(table(TABLE_NAME,
                columns("bytea_column:blob"),
                row(data)
        ));
    }

    public void bytea_column_should_accept_InputStream() {
        byte[] data = new byte[]{1,2,3,4,5};
        InputStream in = new ByteArrayInputStream(data);

        inputStreamEngine.resetDB((db)->{
            db.blob_table().id(1).bytea_column(in);
        });

        assertTableValues(table(TABLE_NAME,
                columns("bytea_column:blob"),
                row(data)
        ));
    }

    public void bytea_column_should_accept_null() {
        engine.resetDB((db)->{
            db.blob_table().id(1).bytea_column(null);
        });

        assertTableValues(table(TABLE_NAME,
                columns("bytea_column:blob"),
                row(new Object[]{null})
        ));
    }

    public void assertDBHas_should_match_bytea_column() {
        // Comparing binary columns with = isn't portable (e.g. Oracle rejects it for BLOB).
        skipUnless("bytea equality in assertDBHas", DBMSType.POSTGRESQL, DBMSType.COCKROACHDB);
        byte[] data = new byte[]{1,2,3,4,5};
        engine.resetDB((db)->{
            db.blob_table().id(1).bytea_column(data);
        });

        engine.assertDBHas((db)->{
            db.blob_table().id(1).bytea_column(data);
        });
    }
}
