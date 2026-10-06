package io.github.sfesantos.portcullis.policy;

import io.github.sfesantos.portcullis.annotation.ResourceId;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Answers which Portcullis annotations apply to a call, for {@link PolicyCompiler}.
 *
 * <p>The method the aspect sees is not always the one carrying the annotations. A call through an
 * interface arrives as the interface method, and a framework proxy (CGLIB, ByteBuddy, Hibernate) is a
 * generated subclass with no annotations of its own. So this class keeps both the invoked method and
 * the most specific implementation on the target class, and every lookup checks the implementation
 * first and falls back to the invoked method.
 *
 * <p>Everything here is reflection, which is why it never runs on the hot path. An instance lives only
 * while {@link PolicyCompiler} compiles a policy, once per target class and method; the compiled
 * {@link MethodPolicy} keeps the resulting rules and parameter indexes, and this object is discarded.
 */
final class AnnotatedMethod {

    private final Method invoked;
    private final Method implementation;

    AnnotatedMethod(Method invoked, Class<?> targetClass) {
        this.invoked = invoked;
        this.implementation = mostSpecific(invoked, targetClass);
    }

    String operation() {
        return implementation.getDeclaringClass().getSimpleName() + "#" + implementation.getName();
    }

    String qualifiedName() {
        var name = new StringBuilder(implementation.getDeclaringClass().getName())
                .append('#').append(implementation.getName()).append('(');
        var types = implementation.getParameterTypes();

        for (var i = 0; i < types.length; i++) {
            name.append(i == 0 ? "" : ",").append(types[i].getSimpleName());
        }

        return name.append(')').toString();
    }

    boolean onMethod(Class<? extends Annotation> kind) {
        return implementation.isAnnotationPresent(kind) || invoked.isAnnotationPresent(kind);
    }

    <A extends Annotation> A onMethodOnly(Class<A> kind) {
        var found = implementation.getAnnotation(kind);

        return found != null ? found : invoked.getAnnotation(kind);
    }

    <A extends Annotation> A onMethodOrType(Class<A> kind) {
        var found = onMethodOnly(kind);

        return found != null ? found : implementation.getDeclaringClass().getAnnotation(kind);
    }

    <A extends Annotation> A[] repeatable(Class<A> kind) {
        var found = implementation.getAnnotationsByType(kind);

        return found.length > 0 ? found : invoked.getAnnotationsByType(kind);
    }

    ResourceId[] resourceIds() {
        var fromImplementation = resourceIds(implementation);

        return hasAny(fromImplementation) ? fromImplementation : resourceIds(invoked);
    }

    private static ResourceId[] resourceIds(Method method) {
        var parameters = method.getParameters();
        var ids = new ResourceId[parameters.length];

        for (var i = 0; i < parameters.length; i++) {
            ids[i] = parameters[i].getAnnotation(ResourceId.class);
        }

        return ids;
    }

    private static boolean hasAny(ResourceId[] ids) {
        for (var id : ids) {
            if (id != null) {
                return true;
            }
        }

        return false;
    }

    // Called through an interface or a framework proxy, the annotations live on the implementation.
    private static Method mostSpecific(Method invoked, Class<?> targetClass) {
        if (targetClass == null || targetClass == invoked.getDeclaringClass()) {
            return invoked;
        }

        for (var type = targetClass; type != null && type != Object.class; type = type.getSuperclass()) {
            if (isGeneratedProxy(type)) {
                continue;
            }

            try {
                var candidate = type.getDeclaredMethod(invoked.getName(), invoked.getParameterTypes());

                if (!candidate.isBridge() && !candidate.isSynthetic()) {
                    return candidate;
                }
            } catch (NoSuchMethodException _) {
            }
        }

        return invoked;
    }

    // CGLIB, ByteBuddy and Hibernate subclasses: '$$' in the name and no annotations of their own.
    private static boolean isGeneratedProxy(Class<?> type) {
        return type.getName().contains("$$");
    }
}
