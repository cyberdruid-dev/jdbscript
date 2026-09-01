package org.jdbscript.impl.sql;

import java.util.List;

public interface ITableSorter {

    List<String> sortTablesByDependencies(List<String> tableNames);

}
