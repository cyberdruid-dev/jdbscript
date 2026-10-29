package org.jdbscript.usecases;

import org.jdbscript.IDBSchema;
import org.jdbscript.IDBSchema.IDBRecord;
import org.jdbscript.IJDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.jdbscript.errors.JDBScriptException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Test
public class WriteFailureMessageTest extends JdbAbstractTest {

    private interface ICustomerRecord extends IDBRecord {
        ICustomerRecord id(Integer value);
        ICustomerRecord name(String value);
    }

    private interface IOrderRecord extends IDBRecord {
        IOrderRecord id(Integer value);
        IOrderRecord customer_id(Integer value);
        IOrderRecord order_date(String value);
    }

    private interface IOrderItemRecord extends IDBRecord {
    }

    private interface IWriteSchema extends IDBSchema {
        ICustomerRecord customers();
        IOrderRecord orders();
        IOrderItemRecord order_items();
    }

    private final IJDBEngine<IWriteSchema> engine = createEngine(IWriteSchema.class);

    @BeforeMethod
    public void before() {
        cleanupTables("order_items", "orders", "customers");
    }

    public void insertDB_failure_names_the_table_and_the_written_values() {
        assertThatThrownBy(() -> engine.insertDB(db -> {
            db.customers().id(1).name("Ann");
            db.orders().id(101).customer_id(null).order_date("2023-10-27");
        })).isInstanceOf(JDBScriptException.class)
                .hasMessageStartingWith("insertDB failed for orders: id=101, customer_id=null, order_date='2023-10-27': ")
                .satisfies(e -> {
                    assertThat(e.getCause()).isInstanceOf(SQLException.class);
                    assertThat(e.getMessage()).endsWith(e.getCause().getMessage());
                });
    }

    public void updateDB_failure_names_the_table_and_the_written_values() {
        engine.insertDB(db -> {
            db.customers().id(1).name("Ann");
            db.orders().id(101).customer_id(1).order_date("2023-10-27");
        });

        assertThatThrownBy(() -> engine.updateDB(db -> {
            db.orders().id(101).customer_id(null);
        })).isInstanceOf(JDBScriptException.class)
                .hasMessageStartingWith("updateDB failed for orders: id=101, customer_id=null: ")
                .satisfies(e -> {
                    assertThat(e.getCause()).isInstanceOf(SQLException.class);
                    assertThat(e.getMessage()).endsWith(e.getCause().getMessage());
                });
    }
}
