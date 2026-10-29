package org.jdbscript.impl;

import org.jdbscript.DBMSType;
import org.jdbscript.errors.JDBScriptException;
import org.testng.annotations.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MetadataProviderNameResolutionTest {

    private final IMetadataProvider provider = new StubMetadataProvider();

    @Test
    public void toDbColumnName_should_resolve_a_column_case_insensitively() {
        assertThat(provider.toDbColumnName("customers", "full_name")).isEqualTo("FULL_NAME");
    }

    @Test
    public void toDbColumnName_should_fail_for_an_unknown_column() {
        assertThatThrownBy(() -> provider.toDbColumnName("customers", "name"))
                .isInstanceOf(JDBScriptException.class)
                .hasMessage("No column 'name' in table CUSTOMERS (columns: ID, FULL_NAME)");
    }

    @Test
    public void toDbColumnName_should_keep_the_name_when_the_table_has_no_column_metadata() {
        IMetadataProvider withoutColumns = new StubMetadataProvider() {
            @Override
            public List<String> getColumns(String dbTableName) {
                return List.of();
            }
        };

        assertThat(withoutColumns.toDbColumnName("customers", "name")).isEqualTo("name");
    }

    private static class StubMetadataProvider implements IMetadataProvider {
        @Override
        public DBMSType getDbmsType() {
            return DBMSType.H2;
        }

        @Override
        public List<String> getAllTables() {
            return List.of("CUSTOMERS");
        }

        @Override
        public Map<String, Set<String>> getTableDependencies(Collection<String> tableNames) {
            return Map.of();
        }

        @Override
        public List<String> getPrimaryKeyColumns(String dbTableName) {
            return List.of();
        }

        @Override
        public List<String> getColumns(String dbTableName) {
            return List.of("ID", "FULL_NAME");
        }

        @Override
        public Map<String, Integer> getLobColumns(String dbTableName) {
            return Map.of();
        }

        @Override
        public Map<String, String> getColumnTypes(String dbTableName) {
            return Map.of();
        }

        @Override
        public Optional<String> getIdentityColumn(String dbTableName) {
            return Optional.empty();
        }

        @Override
        public String getIdentifierQuote() {
            return "\"";
        }
    }
}
