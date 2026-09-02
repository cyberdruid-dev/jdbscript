package org.jdbscript.impl.sql;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.jdbscript.errors.Checks.checkNotNull;
import static org.jdbscript.errors.JdbsErrors.DATASOURCE_IS_NOT_CONFIGURED;
import static org.jdbscript.errors.JdbsErrors.DATASOURCE_IS_NULL;

public class SqlConnectionProvider {

    private final DataSource dataSource;

    @FunctionalInterface
    public interface JdbcConnectionConsumer<T> {

        void accept(Connection cnn) throws Exception;

    }

    public SqlConnectionProvider(DataSource dataSource) {
        this.dataSource = checkNotNull(dataSource, DATASOURCE_IS_NULL);

    }

    public void withConnection(JdbcConnectionConsumer<Connection> consumer) {
        try(Connection cnn = dataSource.getConnection()) {
            cnn.getMetaData().getDriverName();
            cnn.setAutoCommit(false);
            consumer.accept(cnn);
            cnn.commit();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
