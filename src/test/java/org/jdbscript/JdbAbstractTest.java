package org.jdbscript;

import org.jdbscript.impl.conversion.IJDBTypeConverter;
import org.jdbscript.utils.TestConfiguration;
import org.jdbscript.utils.TestDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.sql.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.Date;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.assertTrue;

public class JdbAbstractTest {
    protected final Logger log = LoggerFactory.getLogger(this.getClass());
    private final DateFormat LOCAL_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private final DateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

    protected static final TestConfiguration testConfiguration = new TestConfiguration();
    protected final TestDataSource dataSource = new TestDataSource(testConfiguration.getDataSource());

    @BeforeMethod
    public void resetOpenConnectionTracking() {
        dataSource.resetOpenConnectionTracking();
    }

    @AfterMethod
    public void assertAllConnectionsClosed() {
        dataSource.assertAllConnectionsClosed();
    }

    @AfterClass
    public void afterClassClass() {
    }

    protected <T extends IDbSchema> JDBEngine<T> createEngine(Class<T> schemaClass) {
        return createEngine(schemaClass, null);
    }

    protected <T extends IDbSchema> JDBEngine<T> createEngine(Class<T> schemaClass, List<IJDBTypeConverter> converters) {
        return JDBEngine.builder(schemaClass)
                .dataSource(()->dataSource)
                .converters(converters == null ? null : converters.toArray(new IJDBTypeConverter[0]))
                .executor(testConfiguration.getScriptExecutor())
                .build();
    }



    protected void executeUpdate(String sql, Object... replacements) {
        sql = sql.formatted(replacements);
        try(Connection cnn = dataSource.getConnection()) {
            cnn.createStatement().execute(sql);
            if(!cnn.getAutoCommit()) {
                cnn.commit();
            }
        } catch (Exception e) {
            Assert.fail("Fail to executeUpdate('%s')".formatted(sql), e);
        }
    }

    protected void cleanupTables(String... tables) {
        for (String table : tables) {
            executeUpdate("DELETE FROM "+table);
        }
    }

    protected void assertTableEmpty(String tableName) {
        int rowCount = withResultSet("SELECT COUNT(*) FROM "+tableName, (rs,columns, types) -> {
            rs.next();
            return rs.getInt(1);
        });

        if(rowCount != 0) {
            Assert.fail("Expecting empty table '" + tableName + "', but found " + rowCount + " rows");
        }
    }

    @FunctionalInterface
    protected interface IResultSetTransformer {

        Object transform(ResultSet rs, List<String> columns, List<Integer> types) throws Exception;

    }

    protected <R> R withResultSet(String sql, IResultSetTransformer consumer) {
        try(Connection cnn = dataSource.getConnection()) {
            cnn.setAutoCommit(false);
            ResultSet rs = cnn.createStatement().executeQuery(sql);
            List<String> columns = new ArrayList<>();
            List<Integer> types = new ArrayList<>();
            int columnCount = rs.getMetaData().getColumnCount();
            ResultSetMetaData metadata = rs.getMetaData();
            for (int i = 1; i <= columnCount; i++) {
                columns.add(metadata.getColumnName(i));
                types.add(metadata.getColumnType(i));
            }
            R result = (R) consumer.transform(rs, columns, types);
            cnn.commit();
            cnn.close();
            return result;
        } catch (Exception e) {
            Assert.fail("Fail to process result set for SQL: '%s'".formatted(sql), e);
            return null;
        }
    }

    protected void assertTableValues(ExpectedTable expectedTable) {
        DbmsType dbmsType = testConfiguration.getDbmsType();
        org.jdbscript.impl.sql.ISqlExecutorStrategy strategy = org.jdbscript.impl.sql.SqlExecutorStrategyFactory.getStrategy(dbmsType);
        String tableName = expectedTable.tableName;
        String sql = "SELECT * FROM "+tableName;
        if(expectedTable.whereCondition != null) {
            sql += " WHERE "+expectedTable.whereCondition;
        }
        List<String> columns = expectedTable.header.columns;
        ExpectedTable actualTable = withResultSet(sql, (rs, dbColumns, types)->{
            List<ExpectedTableRow> rows = new ArrayList<>();
            while(rs.next()){
                List<Object> values = new ArrayList<>();
                for(String column: columns) {
                    String expectedType = null;
                    if(column.contains(":")){
                        expectedType = column.split(":")[1];
                        column = column.split(":")[0];
                    }
                    int columnIndex = findColumn(dbColumns, column);
                    Object value = strategy.getColumnValue(rs, columnIndex, expectedType);
                    values.add(value);
                }
                rows.add(new ExpectedTableRow(values));
            }
            return new ExpectedTable(expectedTable.tableName, new TableColumns(columns), rows);
        });
        assertThat(actualTable)
                .usingRecursiveComparison()
                .ignoringFields("whereCondition")
                .ignoringCollectionOrderInFields("rows")
                .withEqualsForType(this::numberEquals, Number.class)
                .withEqualsForType(this::dateEquals, Date.class)
                .withEqualsForType(this::byteArrayEquals, byte[].class)
                .isEqualTo(expectedTable);
    }

    protected LocalDate asLocalDate(Date date) {
        if(date == null) {
            return null;
        }
        return LocalDate.ofInstant(new Date(date.getTime()).toInstant(), ZoneId.systemDefault());
    }

    private boolean byteArrayEquals(byte[] bytes1, byte[] bytes2) {
        boolean result = Arrays.equals(bytes1, bytes2);
        if(!result) {
            log.debug("arrays {} != {}", bytes1, bytes2);
        }
        return result;
    };

    private boolean dateEquals(Date date1, Date date2) {
        if(date1 == null || date2 == null && date1 != date2) {
            return false;
        }
        date1 = new Date(date1.getTime());
        date2 = new Date(date2.getTime());
        return date1.equals(date2);
    }

    private int findColumn(List<String> dbColumns, String column) {
        int columnIndex = dbColumns.indexOf(column)+1;
        if(columnIndex<=0){
            columnIndex = dbColumns.indexOf(column.toUpperCase())+1;
        }
        if(columnIndex<=0) {
            String msg = "Column '" + column + "' does not exist.\n";
            msg += "  Existing columns: "+ dbColumns;
            throw new RuntimeException(msg);
        }
        return columnIndex;
    }

    private boolean numberEquals(Number number1, Number number2) {
        if(number1  == null || number2  == null) {
            return number1 == number2;
        } else if(number1 instanceof Integer || number2 instanceof Integer) {
            return number1.intValue() == number2.intValue();
        } else if(number1 instanceof Long || number2 instanceof Long) {
            return number1.longValue() == number2.longValue();
        } else if(number1 instanceof Double || number2 instanceof Double) {
            return Math.abs(number1.doubleValue()-number2.doubleValue()) < 0.0001;
        } else if(number1 instanceof Float || number2 instanceof Float) {
            return Math.abs(number1.doubleValue()-number2.doubleValue()) < 0.0001;
        } else if(number1 instanceof Short || number2 instanceof Short) {
            return number1.shortValue() == number2.shortValue();
        } else {
            String msg = "Don't know how to compare %s to %s".formatted(number1.getClass(), number2.getClass());
            throw new RuntimeException(msg);
        }
    }

    protected void skipFor(String featureName, DbmsType type) {
        skipFor(featureName, type, null, null);
    }

    protected void skipFor(String featureName, DbmsType type, Class<?> scriptExecutorClass, String reason) {
        DbmsType dbmsType = testConfiguration.getDbmsType();
        Class<? extends IScriptExecutor> currentExecutorClass = testConfiguration.getScriptExecutor().getClass();
        if(dbmsType == type
                && (scriptExecutorClass == null || scriptExecutorClass.isAssignableFrom(currentExecutorClass))
        ) {
            String msg = "%s not supported on %s.".formatted(featureName, dbmsType);
            if(reason != null && !reason.isEmpty()) {
                msg += " Reason: " + reason;
            }
            throw new SkipException(msg);
        }
    }

    protected ExpectedTable table(String tableName, String where, TableColumns columns, ExpectedTableRow... rows){
        return new ExpectedTable(tableName, where, columns, List.of(rows));
    }

    protected ExpectedTable table(String tableName, TableColumns columns, ExpectedTableRow... rows){
        return table(tableName, null, columns, rows);
    }

    protected static ExpectedTableRow row(Object... values) {
        ArrayList<Object> valueList = new ArrayList<>();
        for(int i=0; i<values.length; i++) {
            valueList.add(values[i]);
        }
        return new ExpectedTableRow(valueList);
    }

    protected static class ExpectedTable {
        public final String tableName;
        public final String whereCondition;
        public final TableColumns header;
        public final List<ExpectedTableRow> rows;


        public ExpectedTable(String tableName, TableColumns header, List<ExpectedTableRow> rows) {
            this(tableName, null, header, rows);
        }
        public ExpectedTable(String tableName, String whereCondition, TableColumns header, List<ExpectedTableRow> rows) {
            this.tableName = tableName;
            this.header = header;
            this.rows = rows;
            this.whereCondition = whereCondition;
        }

        @Override
        public String toString() {
            String result =  "table: "+tableName + "\n";
            result += header+"\n";
            for(var row: rows) {
                result += row +"\n";
            }
            return result;
        }
    }

    public static class ExpectedTableRow {
        public List<Object> values;

        private ExpectedTableRow(List<Object> values){
            this.values = values;
        }

        @Override
        public String toString() {
            return "row: "+values.toString();
        }
    }

    public static class TableColumns {

        public List<String> columns;
        private TableColumns(List<String> columns){
            this.columns = columns;
        }

        @Override
        public String toString() {
            return "columns:"+columns.toString();
        }
    }

    protected static TableColumns columns(String... columns) {
        return new TableColumns(List.of(columns));
    }

    protected static <T> void assertAnyMatch(Collection<T> actualCollection, Predicate<T> predicate, String message) {
        assertTrue(actualCollection.stream().anyMatch(predicate), message);
    }

    protected void skipIfNotH2(Class<?> testClass) {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new SkipException("H2 driver not found in classpath, skipping "+testClass.getSimpleName());
        }
    }

}