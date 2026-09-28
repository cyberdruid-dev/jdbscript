package org.jdbscript.impl;

import org.jdbscript.DBMSType;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * An {@link IMetadataProvider} that forwards every method to a {@code delegate} by default.
 * Subclasses override only the methods whose behavior they want to change.
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
    public List<String> getSortedTables() {
        return delegate.getSortedTables();
    }

    @Override
    public Comparator<String> getParentChildTableComparator() {
        return delegate.getParentChildTableComparator();
    }

    @Override
    public List<String> sortTablesByDependencies(Collection<String> tableNames) {
        return delegate.sortTablesByDependencies(tableNames);
    }
}
