package io.dropwizard.jersey.validation;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import io.dropwizard.validation.ConstraintViolations;
import io.dropwizard.validation.Validated;
import org.glassfish.jersey.server.internal.inject.ConfiguredValidator;
import org.glassfish.jersey.server.model.Invocable;
import org.glassfish.jersey.server.model.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.*;
import javax.validation.executable.ExecutableValidator;
import javax.validation.groups.Default;
import javax.validation.metadata.BeanDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

public class DropwizardConfiguredValidator implements ConfiguredValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DropwizardConfiguredValidator.class);

    /**
     * The default group array used in case any of the validate methods is called without a group.
     */
    private static final Class<?>[] DEFAULT_GROUP_ARRAY = new Class<?>[]{Default.class};

    private final Validator validator;

    public DropwizardConfiguredValidator(Validator validator) {
        this.validator = checkNotNull(validator);
    }

    @Override
    public void validateResourceAndInputParams(Object o, final Invocable invocable, Object[] objects) throws ConstraintViolationException {
        final Set<ConstraintViolation<Object>> constraintViolations = forExecutables().validateParameters(o, invocable.getHandlingMethod(), objects);
        final List<Parameter> params = invocable.getParameters();


        // Get the index of the parameter that is to be the request entity
        int entityIndex = -1;
        for (int i = 0; i < params.size(); i++) {
            if (params.get(i).getSource().equals(Parameter.Source.UNKNOWN)) {
                entityIndex = i;
            }
        }

        final int entityIndex2 = entityIndex;

        // Remove all the constraints that occurred on the request entity thus narrowing down to
        // violations on params like QueryParam and HeaderParam
        final ImmutableSet<ConstraintViolation<Object>> paramViolations =
                FluentIterable.from(constraintViolations).filter(new Predicate<ConstraintViolation<Object>>() {
                    @Override
                    public boolean apply(ConstraintViolation<Object> violation) {
                        for (Path.Node node : violation.getPropertyPath()) {
                            if (node.getKind() == ElementKind.PARAMETER) {
                                return node.as(Path.ParameterNode.class).getParameterIndex() != entityIndex2;
                            }
                        }

                        // Should never get to this point as all the constraint violations should be
                        // parameters.
                        return false;
                    }
                }).toSet();

        if (!paramViolations.isEmpty()) {
            throw new ConstraintViolationException(constraintViolations);
        }

        // Finally, manually validate the request entity
        if (entityIndex > -1) {
            validate(objects[entityIndex], params.get(entityIndex).getDeclaredAnnotations(), false);
        }
    }

    @Override
    public void validateResult(Object resource, Invocable invocable, Object value) throws ConstraintViolationException {
        final Method method = invocable.getHandlingMethod();

        if (value != null) {
            final Set<ConstraintViolation<Object>> violations = forExecutables().validateReturnValue(resource, method, value);
            if (!violations.isEmpty()) {
                throw new ConstraintViolationException(violations);
            }
        }

        // If the class is annotated with @Validated, we must validate it manually.
        validate(value, method.getDeclaredAnnotations(), true);
    }

    private void validate(Object value, Annotation[] annotations, boolean isResponse) {
        final Class<?>[] classes = findValidationGroups(annotations);
        String a = isResponse ? "server response" : "request" ;

        if (classes != null && value == null) {
            String msg = String.format("The %s entity was empty", a);
            throw new ConstraintViolationException(msg,
                    Collections.<ConstraintViolation<Object>>emptySet());
        }

        if (classes != null) {
            Set<ConstraintViolation<Object>> violations = null;

            if (value instanceof Map) {
                violations = validate(((Map) value).values(), classes);
            } else if (value instanceof Iterable) {
                violations = validate((Iterable) value, classes);
            } else if (value.getClass().isArray()) {
                violations = new HashSet<>();

                Object[] values = (Object[]) value;
                for (Object item : values) {
                    violations.addAll(validator.validate(item, classes));
                }
            } else {
                violations = validator.validate(value, classes);
            }

            if (!violations.isEmpty()) {
                Set<ConstraintViolation<?>> constraintViolations = ConstraintViolations.copyOf(violations);
                LOGGER.trace("Validation failed: {}; original data was {}",
                        ConstraintViolations.formatUntyped(constraintViolations), value);
                throw new ConstraintViolationException(a,
                        constraintViolations);
            }
        }
    }

    private Class<?>[] findValidationGroups(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType() == Valid.class) {
                return DEFAULT_GROUP_ARRAY;
            } else if (annotation.annotationType() == Validated.class) {
                return ((Validated) annotation).value();
            }
        }
        return null;
    }

    private Set<ConstraintViolation<Object>> validate(Iterable values, Class<?>[] classes) {
        Set<ConstraintViolation<Object>> violations = new HashSet<>();
        for (Object value : values) {
            violations.addAll(validator.validate(value, classes));
        }

        return violations;
    }

    @Override
    public <T> Set<ConstraintViolation<T>> validate(T t, Class<?>... classes) {
        return validator.validate(t, classes);
    }

    @Override
    public <T> Set<ConstraintViolation<T>> validateProperty(T t, String s, Class<?>... classes) {
        return validator.validateProperty(t, s, classes);
    }

    @Override
    public <T> Set<ConstraintViolation<T>> validateValue(Class<T> aClass, String s, Object o, Class<?>... classes) {
        return validator.validateValue(aClass, s, o, classes);
    }

    @Override
    public BeanDescriptor getConstraintsForClass(Class<?> aClass) {
        return validator.getConstraintsForClass(aClass);
    }

    @Override
    public <T> T unwrap(Class<T> aClass) {
        return validator.unwrap(aClass);
    }

    @Override
    public ExecutableValidator forExecutables() {
        return validator.forExecutables();
    }
}
