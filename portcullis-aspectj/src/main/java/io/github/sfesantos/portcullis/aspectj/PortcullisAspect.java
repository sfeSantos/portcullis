package io.github.sfesantos.portcullis.aspectj;

import io.github.sfesantos.portcullis.Portcullis;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;

@Aspect
public class PortcullisAspect {
    // "..*" also matches the nested OwnedBy.List and SameTenant.List containers.
    @Pointcut("execution(@(io.github.sfesantos.portcullis.annotation..*) public !static * *(..))")
    void annotatedMethod() {
    }

    @Pointcut("execution(public !static * *(..)) && within(@(io.github.sfesantos.portcullis.annotation..*) *)")
    void methodOfAnnotatedType() {
    }

    @Before("annotatedMethod() || methodOfAnnotatedType()")
    public void enforce(JoinPoint joinPoint) {
        // Execution join points are woven into the declaring class itself, so there is no proxy to see through.
        var signature = (MethodSignature) joinPoint.getSignature();
        var guard = Portcullis.guard();
        var policy = guard.policyFor(signature.getMethod(), signature.getDeclaringType());

        if (policy.isUnrestricted()) {
            return;
        }

        guard.enforce(policy, policy.needsArguments() ? joinPoint.getArgs() : null);
    }

    // Declared after enforce(), so access is checked before a key is reserved.
    @Around("execution(@io.github.sfesantos.portcullis.annotation.Idempotent public !static * *(..))")
    public Object idempotent(ProceedingJoinPoint joinPoint) throws Throwable {
        var signature = (MethodSignature) joinPoint.getSignature();

        return Portcullis.guard().idempotency()
                .execute(signature.getMethod(), signature.getDeclaringType(), joinPoint.getArgs(), joinPoint::proceed);
    }
}
