package org.jdbscript.impl.javassist;

import org.jdbscript.IDbSchema;
import org.jdbscript.JDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.jdbscript.errors.JDBScriptException;
import org.testng.annotations.Test;

import static org.testng.Assert.assertThrows;

public class ClassScriptWrapperRequirementTest extends JdbAbstractTest {

    private interface ITestSchema extends IDbSchema {
        ITable1Record table_1();
    }

    private interface ITable1Record extends IDbSchema.IDBRecord {
        ITable1Record col(String val);
    }

    public static abstract class ScriptWithParamConstructor implements ITestSchema {
        public ScriptWithParamConstructor(String param) {
        }
    }

    @Test
    public void should_throw_error_if_constructor_has_parameters() {
        JDBEngine<ITestSchema> engine = createEngine(ITestSchema.class);
        assertThrows(JDBScriptException.class, () -> {
            engine.resetDB(db -> {
                db.include(ScriptWithParamConstructor.class);
            });
        });
    }

    public static abstract class ScriptWithNoParamConstructor implements ITestSchema {
        public ScriptWithNoParamConstructor() {
        }
    }

    @Test
    public void should_work_with_no_param_constructor() {
        JDBEngine<ITestSchema> engine = createEngine(ITestSchema.class);
        engine.resetDB(db -> {
            db.include(ScriptWithNoParamConstructor.class);
        });
    }
}
