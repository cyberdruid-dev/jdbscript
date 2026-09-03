package org.jdbscript.impl.javassist;

import org.jdbscript.IDbSchema;
import org.jdbscript.errors.JDBScriptException;
import org.jdbscript.errors.JdbsErrors;
import org.jdbscript.impl.JDbScript;
import javassist.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.jdbscript.errors.Checks.checkThat;
import static java.lang.reflect.Modifier.isStatic;

public class ClassScriptWrapper<T extends IDbSchema> {
    protected static final Logger log = LoggerFactory.getLogger(ClassScriptWrapper.class);
    private final static String IMPLEMENTATION_SUFFIX = "_jdbscript";

    private final Class<? extends T> scriptClass;
    private final Class<T> schemaClass;
    private final String javassistClassName;
    private final ClassPool classPool = ClassPool.getDefault();
    private Class newClass;

    public ClassScriptWrapper(Class<? extends T> scriptClass, Class<T> schemaClass) {
        this.scriptClass = scriptClass;
        this.schemaClass = schemaClass;
        this.javassistClassName = this.scriptClass.getName()+IMPLEMENTATION_SUFFIX;
        newClass = loadClass(javassistClassName)
                .orElseGet(this::implementScriptClass);
    }

    private String normalizeClassName(String className) {
        return className.replace('$','.');
    }

    private String normalizeClassName(Class clazz) {
        return normalizeClassName(clazz.getName());
    }

    private Optional<Class> loadClass(String className){
        try {
            log.debug("loading class: {}...",className);
            return Optional.of(classPool.getClassLoader().loadClass(className));
        } catch (ClassNotFoundException e) {
            log.debug("class NOT FOUND: {}",className);
            return Optional.empty();
        }
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
            return cc.toClass(scriptClass);
        } catch (JDBScriptException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

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
            checkThat(isStatic(cc.getModifiers()), JdbsErrors.INNER_CLASS_SHOULD_BE_STATIC);
        }

        for (CtConstructor constructor : cc.getDeclaredConstructors()) {
            if (constructor.getParameterTypes().length > 0) {
                throw JdbsErrors.SCRIPT_CONSTRUCTOR_HAS_PARAMETERS.get(cc.getName());
            }
        }
    }

    private void implementMethods(CtClass cc) throws CannotCompileException {
        for(Method jMethod: findDBRecordMethods()) {
            log.debug("Implementing: {}.{}()", cc.getSimpleName(), jMethod.getName());
            String methodBody = """
                public %s %s() {
                    return this.script.%s();
                }
            """;
            methodBody = methodBody.formatted(
                    normalizeClassName(jMethod.getReturnType()),
                    jMethod.getName(),
                    jMethod.getName());
            log.trace("method body: {}", methodBody);
            CtMethod method = CtNewMethod.make(methodBody, cc);
            cc.addMethod(method);
        }
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

    private List<Method> findDBRecordMethods() {
        return Arrays.stream(scriptClass.getMethods())
                .filter(m-> IDbSchema.IDBRecord.class.isAssignableFrom(m.getReturnType()))
                .toList();
    }

    public JDbScript getDbScript(T scriptProxy){
        try {
            Method m = newClass.getMethod("applyScript", schemaClass);
            return (JDbScript) m.invoke(null, scriptProxy);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

}