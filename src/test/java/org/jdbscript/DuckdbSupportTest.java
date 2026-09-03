package org.jdbscript;

import org.jdbscript.impl.sql.SqlScriptExecutor;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DuckdbSupportTest {

    @Test
    public void test_duckdb_detection_from_url() {
        DbmsType type = DbmsType.getTypeFromUrl("jdbc:duckdb:./test.db");
        Assert.assertEquals(type, DbmsType.DUCKDB);
    }

    @Test
    public void test_duckdb_detection_from_metadata() throws SQLException {
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(metaData.getURL()).thenReturn("jdbc:duckdb::memory:");
        
        DbmsType type = DbmsType.getType(metaData);
        Assert.assertEquals(type, DbmsType.DUCKDB);
    }

}
