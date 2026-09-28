package org.jdbscript.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Overrides a real {@link IMetadataProvider}'s table-<em>ordering</em> methods with a fixed,
 * user-supplied parent-to-child order - an escape hatch for when FK auto-detection can't be relied
 * on (missing FK metadata, views, cyclic dependencies).
 * <p>
 * {@link #getAllTables()} and {@link #getDbmsType()} are deliberately left forwarded to the real
 * provider (inherited from {@link AbstractMetadataProviderProxy}, untouched): they're the ground
 * truth {@link SchemaValidator} checks the schema interface against, and overriding them with the
 * user's own list would silently disable that drift check exactly when auto-detection (and thus
 * possibly the user's understanding of the DB) is least trustworthy.
 */
public class TableDependencyOrderMetadataProvider extends AbstractMetadataProviderProxy {

    private final List<String> parentToChildOrder;

    public TableDependencyOrderMetadataProvider(IMetadataProvider delegate, List<String> parentToChildOrder) {
        super(delegate);
        this.parentToChildOrder = parentToChildOrder;
    }

    @Override
    public List<String> getSortedTables() {
        // Resolved against the real, DB-reported casing (via getAllTables(), untouched by this
        // override) rather than returned verbatim: getTableNames() uses these strings directly for
        // cleanup SQL, and some DBMSes (MySQL/MariaDB on Linux, by default) treat unquoted table
        // names as case-sensitive - "DELETE FROM Orders" fails there if the real table is "orders",
        // even though the same statement is perfectly fine on H2/SQLite/DuckDB/HSQLDB.
        List<String> realTables = getAllTables();
        return parentToChildOrder.stream()
                .map(name -> resolveRealCasing(name, realTables))
                .toList();
    }

    private static String resolveRealCasing(String name, List<String> realTables) {
        for (String real : realTables) {
            if (real.equalsIgnoreCase(name)) {
                return real;
            }
        }
        // Not a real DB table (e.g. a stray extra entry) - fall back to what the caller gave.
        return name;
    }

    @Override
    public Comparator<String> getParentChildTableComparator() {
        return (t1, t2) -> {
            int i1 = indexOfIgnoreCase(parentToChildOrder, t1);
            int i2 = indexOfIgnoreCase(parentToChildOrder, t2);
            if (i1 == -1 || i2 == -1) {
                return t1.compareToIgnoreCase(t2);
            }
            return Integer.compare(i1, i2);
        };
    }

    @Override
    public List<String> sortTablesByDependencies(Collection<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return List.of();
        }
        // Preserve the casing the caller used, not the manual order's - matches the real
        // provider's contract (it sorts the given names, it doesn't rename them). Duplicates in
        // tableNames collapse to one entry, matching the real provider's behavior too.
        Map<String, String> remaining = new LinkedHashMap<>();
        for (String name : tableNames) {
            remaining.putIfAbsent(name.toUpperCase(), name);
        }

        List<String> result = new ArrayList<>();
        for (String ordered : parentToChildOrder) {
            String match = remaining.remove(ordered.toUpperCase());
            if (match != null) {
                result.add(match);
            }
        }
        // Requested but not found in the manual order - shouldn't normally happen, since
        // tableDependencyOrder is validated to cover every schema-interface table at build time.
        // Append alphabetically, for a deterministic result rather than an arbitrary one.
        remaining.values().stream().sorted(String.CASE_INSENSITIVE_ORDER).forEach(result::add);
        return result;
    }

    private static int indexOfIgnoreCase(List<String> list, String name) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }
}
