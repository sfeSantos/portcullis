package io.github.sfesantos.portcullis.policy;

import io.github.sfesantos.portcullis.PolicyDefinitionException;
import io.github.sfesantos.portcullis.annotation.Authenticated;
import io.github.sfesantos.portcullis.annotation.OwnedBy;
import io.github.sfesantos.portcullis.annotation.PublicAccess;
import io.github.sfesantos.portcullis.annotation.RequiresPermission;
import io.github.sfesantos.portcullis.annotation.RequiresRole;
import io.github.sfesantos.portcullis.annotation.SameTenant;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PolicyCoverage {

    private static final Comparator<Method> BY_SIGNATURE = Comparator.comparing(Method::getName)
            .thenComparing(m -> Arrays.toString(m.getParameterTypes()));

    private final Set<Class<?>> types;

    private PolicyCoverage(Set<Class<?>> types) {
        this.types = types;
    }

    public static PolicyCoverage of(String... packages) {
        return new PolicyCoverage(Set.of()).and(packages);
    }

    public static PolicyCoverage of(Class<?>... types) {
        return new PolicyCoverage(Set.of()).and(types);
    }

    public PolicyCoverage and(String... packages) {
        var all = new LinkedHashSet<>(types);

        for (var packageName : packages) {
            all.addAll(ClassPathScanner.classesIn(packageName));
        }

        return new PolicyCoverage(all);
    }

    public PolicyCoverage and(Class<?>... more) {
        var all = new LinkedHashSet<>(types);
        all.addAll(Arrays.asList(more));

        return new PolicyCoverage(all);
    }

    public List<Method> uncovered() {
        var found = new ArrayList<Method>();

        for (var type : types) {
            if (!isOperationType(type)) {
                continue;
            }

            var methods = type.getDeclaredMethods();
            Arrays.sort(methods, BY_SIGNATURE);

            for (var method : methods) {
                if (isIntercepted(method) && !isCovered(new AnnotatedMethod(method, type))) {
                    found.add(method);
                }
            }
        }

        return List.copyOf(found);
    }

    public void requireCovered() {
        var uncovered = uncovered();

        if (uncovered.isEmpty()) {
            return;
        }

        var message = new StringBuilder("public methods with no access check and no @PublicAccess:");

        for (var method : uncovered) {
            message.append("\n  ").append(new AnnotatedMethod(method, method.getDeclaringClass()).qualifiedName());
        }

        throw new PolicyDefinitionException(message.toString());
    }

    // Records and enums are data, not operations; their accessors would only be noise.
    private static boolean isOperationType(Class<?> type) {
        return !type.isInterface() && !type.isRecord() && !type.isEnum()
                && !type.isAnonymousClass() && !type.isLocalClass() && !type.isSynthetic();
    }

    // The same methods the aspect's pointcuts can match: public, non static, with a body in this type.
    private static boolean isIntercepted(Method method) {
        var modifiers = method.getModifiers();

        return Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers) && !Modifier.isAbstract(modifiers)
                && !method.isBridge() && !method.isSynthetic() && !overridesObject(method);
    }

    private static boolean overridesObject(Method method) {
        try {
            Object.class.getMethod(method.getName(), method.getParameterTypes());

            return true;
        } catch (NoSuchMethodException _) {
            return false;
        }
    }

    // Mirrors what PolicyCompiler turns into a check that needs a user, without needing any resolver.
    private static boolean isCovered(AnnotatedMethod method) {
        return method.onMethod(PublicAccess.class)
                || method.onMethodOrType(Authenticated.class) != null
                || method.onMethodOrType(RequiresRole.class) != null
                || method.onMethodOrType(RequiresPermission.class) != null
                || method.repeatable(SameTenant.class).length > 0
                || method.repeatable(OwnedBy.class).length > 0;
    }
}
