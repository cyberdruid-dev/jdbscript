package org.jdbscript.impl.javassist;

import org.jdbscript.IDBSchema;
import org.jdbscript.errors.JDBScriptException;
import org.jdbscript.errors.JDBErrors;
import org.jdbscript.impl.JDBScript;
import javassist.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.invoke.MethodHandles.Lookup.ClassOption.NESTMATE;
import static org.jdbscript.errors.Checks.checkThat;
import static java.lang.reflect.Modifier.isStatic;

public class ClassScriptWrapper<T extends IDBSchema> {
    protected static final Logger log = LoggerFactory.getLogger(ClassScriptWrapper.class);
    private final static String IMPLEMENTATION_SUFFIX = "_jdbscript";
    private final static Map<Class<?>, Class<?>> implementations = new ConcurrentHashMap<>();

    private final Class<? extends T> scriptClass;
    private final Class<T> schemaClass;
    private final String javassistClassName;
    private final ClassPool classPool = ClassPool.getDefault();
    private Class newClass;

    public ClassScriptWrapper(Class<? extends T> scriptClass, Class<T> schemaClass) {
        this.scriptClass = scriptClass;
        this.schemaClass = schemaClass;
        this.javassistClassName = this.scriptClass.getName()+IMPLEMENTATION_SUFFIX;
        newClass = implementations.computeIfAbsent(scriptClass, k -> implementScriptClass());
    }

    private String normalizeClassName(String className) {
        return className.replace('$','.');
    }

    private String normalizeClassName(Class clazz) {
        return normalizeClassName(clazz.getName());
    }

    private Class implementScriptClass() {
        try {
            log.debug("implementScriptClass: {}", javassistClassName);
            CtClass cc = classPool.getAndRename(scriptClass.getName(), javassistClassName);
            checkRequirements(cc);
            addScriptField(cc);
            modifyConstructor(cc);

            cc.setModifiers(Modifier.PUBLIC);
            addStaticScriptGetter(cc);
            implementMethods(cc);
            // The rename leaves duplicate CONSTANT_Class entries; the pre-super() `this.script = $1`
            // must reference this_class itself, or the verifier rejects it in a hidden class.
            cc.getClassFile().compact();
            return defineClass(cc.toBytecode());
        } catch (JDBScriptException e) {
            throw e;
        } catch (Exception e) {
            log.error("Fail to wrap script class {}. message: {}", scriptClass.getName(), e.getMessage());
            throw new RuntimeException(e);
        }

    }

    // A plain copy isn't listed in the nest of the script's outer class, so it can't touch the outer
    // class's private members; a hidden NESTMATE class joins that nest. defineHiddenClass needs full
    // privilege access, which a script class from another class loader doesn't grant, so fall back.
    private Class<?> defineClass(byte[] bytecode) throws IllegalAccessException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(scriptClass, MethodHandles.lookup());
        if (lookup.hasFullPrivilegeAccess()) {
            return lookup.defineHiddenClass(bytecode, true, NESTMATE).lookupClass();
        }
        return lookup.defineClass(bytecode);
    }

    private void modifyConstructor(CtClass cc) throws NotFoundException, CannotCompileException {

        String expectedName = cc.getName()+"()";
        for(var b: cc.getDeclaredBehaviors()){
            if(b.getLongName().equals(expectedName)){
                b.setModifiers(Modifier.PUBLIC);
                CtClass scriptClass = classPool.get(this.schemaClass.getName());
                b.addParameter(scriptClass);
                b.insertBefore("""
                        {this.script = $1;}
                        """);
            }
        }
    }

    private void checkRequirements(CtClass cc) throws NotFoundException {
        if(cc.getDeclaringClass() != null) {
            checkThat(isStatic(cc.getModifiers()), JDBErrors.INNER_CLASS_SHOULD_BE_STATIC);
        }

        for (CtConstructor constructor : cc.getDeclaredConstructors()) {
            if (constructor.getParameterTypes().length > 0) {
                throw JDBErrors.SCRIPT_CONSTRUCTOR_HAS_PARAMETERS.get(cc.getName());
            }
        }
    }

    private void implementMethods(CtClass cc) throws CannotCompileException, NotFoundException {
        for(Method jMethod: findSchemaMethods()) {
            log.debug("Implementing: {}.{}()", cc.getSimpleName(), jMethod.getName());
            String body = jMethod.getReturnType() == void.class
                    ? "{ this.script.%s($$); }"
                    : "{ return this.script.%s($$); }";
            body = body.formatted(jMethod.getName());
            log.trace("method body: {}", body);
            CtMethod method = CtNewMethod.make(Modifier.PUBLIC, toCtClass(jMethod.getReturnType()), jMethod.getName(),
                    toCtClasses(jMethod.getParameterTypes()), null, body, cc);
            cc.addMethod(method);
        }
    }

    private CtClass toCtClass(Class<?> type) throws NotFoundException {
        return classPool.get(type.getTypeName());
    }

    private CtClass[] toCtClasses(Class<?>[] types) throws NotFoundException {
        CtClass[] result = new CtClass[types.length];
        for (int i = 0; i < types.length; i++) {
            result[i] = toCtClass(types[i]);
        }
        return result;
    }

    private void addScriptField(CtClass cc) throws CannotCompileException {
        String fieldDefinition = "private final %s script;";

        fieldDefinition = fieldDefinition.formatted(
                normalizeClassName(this.schemaClass)
        );
        log.debug("adding field: {}", fieldDefinition);
        CtField field = CtField.make(fieldDefinition, cc);
        cc.addField(field);

    }

    private void addStaticScriptGetter(CtClass cc) throws CannotCompileException {
        String body = """
        public static void applyScript(%s proxy) {
            new %s(proxy);
        }
        """;
        body = body.formatted(
                normalizeClassName(this.schemaClass.getName()),
                normalizeClassName(javassistClassName));
        log.debug("addStaticMethod: {}", body);
        CtMethod method = CtNewMethod.make(body, cc);
        cc.addMethod(method);
    }

    private List<Method> findSchemaMethods() {
        return Arrays.stream(scriptClass.getMethods())
                .filter(m -> Modifier.isAbstract(m.getModifiers()))
                .filter(m -> m.getDeclaringClass().isAssignableFrom(schemaClass))
                .toList();
    }

    public JDBScript getDbScript(T scriptProxy){
        try {
            Method m = newClass.getMethod("applyScript", schemaClass);
            return (JDBScript) m.invoke(null, scriptProxy);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

}