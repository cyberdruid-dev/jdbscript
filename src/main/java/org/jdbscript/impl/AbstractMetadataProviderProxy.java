package org.jdbscript.impl;

import org.jdbscript.DBMSType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * An {@link IMetadataProvider} that forwards the fetching methods to a {@code delegate}. The
 * derived (default) methods run on the proxy itself, so they see a subclass's overrides.
 */
public abstract class AbstractMetadataProviderProxy implements IMetadataProvider {

    private final IMetadataProvider delegate;

    protected AbstractMetadataProviderProxy(IMetadataProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public DBMSType getDbmsType() {
        return delegate.getDbmsType();
    }

    @Override
    public List<String> getAllTables() {
        return delegate.getAllTables();
    }

    @Override
    public Set<String> getTableDependencies(String tableName) {
        return delegate.getTableDependencies(tableName);
    }

    @Override
    public List<String> getPrimaryKeyColumns(String dbTableName) {
        return delegate.getPrimaryKeyColumns(dbTableName);
    }

    @Override
    public List<String> getColumns(String dbTableName) {
        return delegate.getColumns(dbTableName);
    }

    @Override
    public Map<String, Integer> getLobColumns(String dbTableName) {
        return delegate.getLobColumns(dbTableName);
    }

    @Override
    public Map<String, String> getColumnTypes(String dbTableName) {
        return delegate.getColumnTypes(dbTableName);
    }

    @Override
    public Optional<String> getIdentityColumn(String dbTableName) {
        return delegate.getIdentityColumn(dbTableName);
    }

    @Override
    public String getIdentifierQuote() {
        return delegate.getIdentifierQuote();
    }
}
