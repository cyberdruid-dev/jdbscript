package org.jdbscript.datatypes;

import org.jdbscript.IDbSchema;
import org.jdbscript.IDbSchema.IDBRecord;
import org.jdbscript.IJDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.jdbscript.DbmsType.SQLITE;

@Test
public class BlobTest extends JdbAbstractTest {

    private final static String TABLE_NAME = "blob_table";

    private interface IBlobTable extends IDBRecord {
        IBlobTable blob_column(byte[] data);
    }
    private interface IBlobTestSchema extends IDbSchema {

        IBlobTable blob_table();

    }

    private interface IInputStreamBlobTable extends IDBRecord {
        IInputStreamBlobTable blob_column(InputStream data);
    }
    private interface IInputStreamBlobTestSchema extends IDbSchema {

        IInputStreamBlobTable blob_table();

    }

    @BeforeMethod
    public void beforeMethod(){
        skipFor("blobs", SQLITE);
        cleanupTables(TABLE_NAME);
    }

    private final IJDBEngine<IBlobTestSchema> engine = createEngine(IBlobTestSchema.class);
    private final IJDBEngine<IInputStreamBlobTestSchema> inputStreamEngine = createEngine(IInputStreamBlobTestSchema.class);


    public void test_insert_blob_data() {
        byte[] data = new byte[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20};

        engine.resetDB((db)->{
            db.blob_table().blob_column(data);
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
            db.blob_table().blob_column(in);
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
            db.blob_table().blob_column(data);
            db.blob_table().blob_column(null);
        });

        assertTableValues(table(TABLE_NAME,
                columns("blob_column:blob"),
                row(new Object[]{data}),
                row(new Object[]{null})
        ));
    }

}
