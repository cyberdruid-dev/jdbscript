package org.jdbscript.utils;

import org.jdbscript.impl.IMetadataProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public abstract class StubMetadataProvider implements IMetadataProvider {

    @Override
    public Set<String> getTableDependencies(String tableName) {
        return Set.of();
    }

    @Override
    public List<String> getPrimaryKeyColumns(String dbTableName) {
        return List.of();
    }

    @Override
    public List<String> getColumns(String dbTableName) {
        return List.of();
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
