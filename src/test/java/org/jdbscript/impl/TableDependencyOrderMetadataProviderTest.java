package org.jdbscript.impl;

import org.jdbscript.DBMSType;
import org.testng.annotations.Test;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TableDependencyOrderMetadataProviderTest {

    private static IMetadataProvider realProviderReporting(List<String> realCasedTables) {
        return new IMetadataProvider() {
            @Override
            public DBMSType getDbmsType() {
                return DBMSType.MYSQL;
            }

            @Override
            public List<String> getAllTables() {
                return realCasedTables;
            }

            @Override
            public List<String> getSortedTables() {
                throw new AssertionError("should not be consulted - overridden");
            }

            @Override
            public Comparator<String> getParentChildTableComparator() {
                throw new AssertionError("should not be consulted - overridden");
            }

            @Override
            public List<String> sortTablesByDependencies(Collection<String> tableNames) {
                throw new AssertionError("should not be consulted - overridden");
            }
        };
    }

    @Test
    public void getSortedTables_should_use_the_real_DB_casing_not_the_configured_casing() {
        // MySQL/MariaDB (on Linux, the common default lower_case_table_names=0) treat unquoted
        // table names as case-sensitive: "DELETE FROM Orders" fails if the real table is "orders".
        // getTableNames() uses getSortedTables()'s returned strings directly for cleanup SQL, so
        // the configured order must resolve to the real, DB-reported casing, not echo back
        // whatever case the caller happened to type in tableDependencyOrder(...).
        IMetadataProvider delegate = realProviderReporting(List.of("customers", "orders", "order_items"));
        TableDependencyOrderMetadataProvider provider = new TableDependencyOrderMetadataProvider(
                delegate, List.of("CUSTOMERS", "Orders", "order_items"));

        assertThat(provider.getSortedTables()).containsExactly("customers", "orders", "order_items");
    }
}
