package org.jdbscript;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DuckdbSupportTest {

    @Test
    public void test_duckdb_detection_from_url() {
        DBMSType type = DBMSType.getTypeFromUrl("jdbc:duckdb:./test.db");
        Assert.assertEquals(type, DBMSType.DUCKDB);
    }

    @Test
    public void test_duckdb_detection_from_metadata() throws SQLException {
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(metaData.getURL()).thenReturn("jdbc:duckdb::memory:");
        
        DBMSType type = DBMSType.getType(metaData);
        Assert.assertEquals(type, DBMSType.DUCKDB);
    }

}
