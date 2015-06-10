package io.dropwizard.validation;

import com.google.common.base.Joiner;
import com.google.common.collect.*;

import javax.validation.ConstraintViolation;
import javax.validation.ElementKind;
import javax.validation.Path;
import java.util.Set;

public class ConstraintViolations {
    private static final Joiner DOT_JOINER = Joiner.on('.');

    private ConstraintViolations() { /* singleton */ }

    public static <T> String format(ConstraintViolation<T> v) {
        if (v.getConstraintDescriptor().getAnnotation() instanceof ValidationMethod) {
            return validationMethodFormatted(v);
        } else {
            return String.format("%s %s", v.getPropertyPath(), v.getMessage());
        }
    }

    public static <T> String validationMethodFormatted(ConstraintViolation<T> v) {
        final ImmutableList<Path.Node> nodes = ImmutableList.copyOf(v.getPropertyPath());

        // It is possible that a BeanParam may contain a ValidationMethod, and in which case, it has the not
        // client friendly name of <function>.<arg index>.<validation method message>. This will trim it off.
        int paramIndex = -1;
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).getKind() == ElementKind.PARAMETER) {
                paramIndex = i;
            }
        }

        String usefulNodes = DOT_JOINER.join(nodes.subList(paramIndex + 1, nodes.size() - 1));
        String msg = usefulNodes + (v.getMessage().startsWith(".") ? "" : " ") + v.getMessage();
        return msg.trim();
    }

    public static <T> ImmutableList<String> format(Set<ConstraintViolation<T>> violations) {
        final Set<String> errors = Sets.newHashSet();
        for (ConstraintViolation<?> v : violations) {
            errors.add(format(v));
        }
        return ImmutableList.copyOf(Ordering.natural().sortedCopy(errors));
    }

    public static ImmutableList<String> formatUntyped(Set<ConstraintViolation<?>> violations) {
        final Set<String> errors = Sets.newHashSet();
        for (ConstraintViolation<?> v : violations) {
            errors.add(format(v));
        }
        return ImmutableList.copyOf(Ordering.natural().sortedCopy(errors));
    }

    public static <T> ImmutableSet<ConstraintViolation<?>> copyOf(Set<ConstraintViolation<T>> violations) {
        final ImmutableSet.Builder<ConstraintViolation<?>> builder = ImmutableSet.builder();
        for (ConstraintViolation<T> violation : violations) {
            builder.add(violation);
        }
        return builder.build();
    }

    public static <T extends ConstraintViolation<?>> int determineStatus(Set<T> violations) {
        // Detect where the constraint validation occurred so we can return an appropriate status
        // code. If the constraint failed with a *Param annotation, return a bad request. If it
        // failed validating the return value, return internal error. Else return unprocessable
        // entity.
        if (violations.size() > 0) {
            ConstraintViolation<?> violation = violations.iterator().next();
            for (Path.Node node : violation.getPropertyPath()) {
                switch (node.getKind()) {
                    case RETURN_VALUE:
                        return 500;
                    case PARAMETER:
                        return 400;
                    default:
                        continue;
                }
            }
        }

        // When Jackson deserializes and validates POST, PUT, etc and constraint violations occur,
        // they occur from the entity's properties and not as parameter from the resource endpoint.
        return 422;
    }
}
