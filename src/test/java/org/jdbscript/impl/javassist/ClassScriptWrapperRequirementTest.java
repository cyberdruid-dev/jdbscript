package org.jdbscript.impl.javassist;

import org.jdbscript.JDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.jdbscript.db.ITestDBSchema;
import org.jdbscript.errors.JDBScriptException;
import org.testng.annotations.Test;

import static org.testng.Assert.assertThrows;

public class ClassScriptWrapperRequirementTest extends JdbAbstractTest {

    public static abstract class ScriptWithParamConstructor implements ITestDBSchema {
        public ScriptWithParamConstructor(String param) {
        }
    }

    @Test
    public void should_throw_error_if_constructor_has_parameters() {
        JDBEngine<ITestDBSchema> engine = createEngine(ITestDBSchema.class);
        assertThrows(JDBScriptException.class, () -> {
            engine.resetDB(db -> {
                db.include(ScriptWithParamConstructor.class);
            });
        });
    }

    public static abstract class ScriptWithNoParamConstructor implements ITestDBSchema {
        public ScriptWithNoParamConstructor() {
        }
    }

    @Test
    public void should_work_with_no_param_constructor() {
        JDBEngine<ITestDBSchema> engine = createEngine(ITestDBSchema.class);
        engine.resetDB(db -> {
            db.include(ScriptWithNoParamConstructor.class);
        });
    }
}
