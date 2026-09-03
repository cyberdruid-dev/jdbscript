package org.jdbscript.examples.defaults;

import org.jdbscript.IDBSchema;
import org.jdbscript.RecordTools;

/**
 * Compare this to a DBUnit flat-file/XML dataset: there, every row needs a literal id and a
 * literal value for every column — if two rows need related-but-distinct values (an id and an
 * SKU derived from it), you compute and paste them in yourself, or write custom dataset-decorator
 * code outside the dataset format. Here it's just Java on the record interface.
 */
public interface IAppSchema extends IDBSchema {

    IProductRecord products();

    interface IProductRecord extends IDBRecord {
        IProductRecord id(Integer id);

        IProductRecord sku(String sku);

        IProductRecord name(String name);

        /**
         * Runs for any column you didn't set explicitly in the script — see
         * {@link RecordToolsDefaultsTest} for what "explicitly" means in practice.
         * <p>
         * {@code sku} is {@code NOT NULL} in the schema. A test that only cares about
         * {@code name} still needs some value there, or the insert fails on a constraint it
         * doesn't actually care about testing — this is what {@code defaults()} is for.
         * <p>
         * Note: {@code tools.nextIntId(...)} advances its counter every time this method runs,
         * even for a record where {@code id} ends up not being applied because you already set
         * it yourself. Don't mix explicit and generated ids in the same script if you're relying
         * on the sequence being gap-free.
         */
        default void defaults(RecordTools tools) {
            id(tools.nextIntId("product_id", 1));
            sku(tools.strValue("SKU-${id}"));
        }
    }
}
