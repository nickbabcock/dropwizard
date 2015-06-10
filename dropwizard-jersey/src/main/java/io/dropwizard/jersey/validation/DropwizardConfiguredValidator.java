package io.dropwizard.jersey.validation;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import io.dropwizard.validation.Validated;
import org.glassfish.jersey.server.internal.inject.ConfiguredValidator;
import org.glassfish.jersey.server.model.Invocable;
import org.glassfish.jersey.server.model.Parameter;

import javax.validation.*;
import javax.validation.executable.ExecutableValidator;
import javax.validation.groups.Default;
import javax.validation.metadata.BeanDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

public class DropwizardConfiguredValidator implements ConfiguredValidator {

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

        // Remove all the constraints that occurred on the request entity thus narrowing down to violations on params like QueryParam and HeaderParam
        final ImmutableSet<ConstraintViolation<Object>> paramViolations =
                FluentIterable.from(constraintViolations).filter(new Predicate<ConstraintViolation<Object>>() {
                    @Override
                    public boolean apply(ConstraintViolation<Object> violation) {
                        for (Path.Node node : violation.getPropertyPath()) {
                            if (node.getKind() == ElementKind.PARAMETER) {
                                return node.as(Path.ParameterNode.class).getParameterIndex() != entityIndex2;
                            }
                        }

                        // Should never get to this point as all the constraint violations should be parameters.
                        return false;
                    }
                }).toSet();

        if (!paramViolations.isEmpty()) {
            throw new ConstraintViolationException(constraintViolations);
        }

        // Finally, manually validate the request entity
        if (entityIndex > -1) {
            final Class<?>[] classes = findValidationGroups(params.get(entityIndex).getDeclaredAnnotations());
            final Set<ConstraintViolation<Object>> validate = validate(objects[entityIndex], classes);
            if (!validate.isEmpty()) {
                throw new ConstraintViolationException(validate);
            }
        }
    }

    private static final Class<?>[] DEFAULT_GROUP_ARRAY = new Class<?>[]{Default.class};

    @Override
    public void validateResult(Object o, Invocable invocable, Object o1) throws ConstraintViolationException {
        final Method method = invocable.getHandlingMethod();

        final Set<ConstraintViolation<Object>> violations1 = forExecutables().validateReturnValue(o, method, o1);
        if (!violations1.isEmpty()) {
            throw new ConstraintViolationException(violations1);
        }

        // If the class is annotated with @Validated, we must validate it manually.
        final Class<?>[] classes = findValidationGroups(method.getDeclaredAnnotations());
        if (classes != null) {
            final Set<ConstraintViolation<Object>> violations = validate(o1, classes);
            if (!violations.isEmpty()) {
                throw new ConstraintViolationException("Server response", violations);
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
