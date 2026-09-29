package org.jdbscript.impl.javassist;

import org.jdbscript.IDBSchema;
import org.jdbscript.errors.JDBErrors;
import javassist.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
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
    private final String javassistClassName;
    private final ClassPool classPool = ClassPool.getDefault();
    private Class newClass;

    public ClassScriptWrapper(Class<? extends T> scriptClass) {
        this.scriptClass = scriptClass;
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
        checkRequirements();
        try {
            log.debug("implementScriptClass: {}", javassistClassName);
            CtClass cc = classPool.makeClass(javassistClassName, classPool.get(scriptClass.getName()));
            cc.setModifiers(Modifier.PUBLIC);
            cc.addConstructor(CtNewConstructor.defaultConstructor(cc));
            implementMethods(cc);
            byte[] bytecode = cc.toBytecode();
            cc.detach();
            return defineClass(bytecode);
        } catch (Exception e) {
            log.error("Fail to wrap script class {}. message: {}", scriptClass.getName(), e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // The script's own code (initializer, lambdas, helpers) stays in the script class, a regular
    // member of its outer class's nest, so private access and self-typed descriptors just work. The
    // generated subclass joins that nest as a hidden NESTMATE class to be allowed to call a private
    // constructor. defineHiddenClass needs full privilege access, which a script class from another
    // class loader doesn't grant, so fall back to a plain subclass there.
    private Class<?> defineClass(byte[] bytecode) throws IllegalAccessException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(scriptClass, MethodHandles.lookup());
        if (lookup.hasFullPrivilegeAccess()) {
            return lookup.defineHiddenClass(bytecode, true, NESTMATE).lookupClass();
        }
        return lookup.defineClass(bytecode);
    }

    private void checkRequirements() {
        if (scriptClass.getDeclaringClass() != null) {
            checkThat(isStatic(scriptClass.getModifiers()), JDBErrors.INNER_CLASS_SHOULD_BE_STATIC);
        }
        for (Constructor<?> constructor : scriptClass.getDeclaredConstructors()) {
            if (constructor.getParameterCount() > 0) {
                throw JDBErrors.SCRIPT_CONSTRUCTOR_HAS_PARAMETERS.get(scriptClass.getName());
            }
        }
    }

    private void implementMethods(CtClass cc) throws CannotCompileException, NotFoundException {
        for(Method jMethod: findSchemaMethods()) {
            log.debug("Implementing: {}.{}()", cc.getSimpleName(), jMethod.getName());
            String body = jMethod.getReturnType() == void.class
                    ? "{ ((%s) %s.current()).%s($$); }"
                    : "{ return ((%s) %s.current()).%s($$); }";
            // Cast to the declaring interface, not the caller's schema: the generated class is cached
            // per script class, and a script may run under the engine's schema or a sub-interface view.
            body = body.formatted(normalizeClassName(jMethod.getDeclaringClass()), ClassScriptContext.class.getName(), jMethod.getName());
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

    private List<Method> findSchemaMethods() {
        return Arrays.stream(scriptClass.getMethods())
                .filter(m -> Modifier.isAbstract(m.getModifiers()))
                .filter(m -> IDBSchema.class.isAssignableFrom(m.getDeclaringClass()))
                .toList();
    }

    public void applyScript(T scriptProxy){
        Object previous = ClassScriptContext.enter(scriptProxy);
        try {
            newClass.getConstructor().newInstance();
        } catch (InvocationTargetException e) {
            throw e.getCause() instanceof RuntimeException re ? re : new RuntimeException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        } finally {
            ClassScriptContext.exit(previous);
        }
    }

}