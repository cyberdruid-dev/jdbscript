package org.jdbscript;

import org.jdbscript.db.ITestDbSchema;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@Test
public class TheTest extends JdbAbstractTest {

    private abstract static class SomeDBScript implements ITestDbSchema {{
        table_1().str_column_1("Hello");
        table_1().str_column_1("Good Bye");
    }};

    private final JDBEngine<ITestDbSchema> engine = JDBEngine.builder(ITestDbSchema.class)
            .dataSource(dataSource)
            .build();

    @BeforeMethod
    public void beforeMethod() {
        cleanupTables("table_1");
    }

    @Test
    public void testInlineScript() throws SQLException {
        engine.resetDB((db)->{
            db.table_1().str_column_1("Hello").str_column_2("Good Bye");
            db.table_2().int_column_1(10);
        });


        assertTableValues(table(
                "table_1",
                columns("str_column_1", "str_column_2","int_column_1","long_column_1"),
                row("Hello", "Good Bye", null, null)

        ));
        assertTableValues(table(
                "table_2",
                columns("int_column_1","long_column_1"),
                row( 10, null)

        ));
    }


    @Test()
    public void testClassScript() throws SQLException {
        engine.resetDB(SomeDBScript.class);

        assertTableValues(table(
                "table_1",
                columns("str_column_1"),
                row("Hello"),
                row( "Good Bye")
        ));
    }
}
