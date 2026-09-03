package org.jdbscript.examples.converters;

import org.jdbscript.impl.conversion.IJDBTypeConverter;

import java.math.BigDecimal;

/**
 * Teaches jdbscript how to store {@link Money}. The whole contract is these two methods — no
 * registration beyond passing an instance to {@code .converter(...)} on the engine builder.
 * <p>
 * This lives in {@code src/test}, not {@code src/main}: {@link Money} is a domain type a real
 * app would ship, but the jdbscript-specific glue for it is test-fixture code.
 */
public class MoneyConverter implements IJDBTypeConverter {

    @Override
    public boolean canConvert(Object value) {
        return value instanceof Money;
    }

    @Override
    public Object convert(Object value) {
        return BigDecimal.valueOf(((Money) value).cents(), 2);
    }
}
