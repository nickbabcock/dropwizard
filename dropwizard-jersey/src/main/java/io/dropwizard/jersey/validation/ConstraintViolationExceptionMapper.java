package io.dropwizard.jersey.validation;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import io.dropwizard.validation.ConstraintViolations;

import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Set;

@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {
    @Override
    public Response toResponse(ConstraintViolationException exception) {
        final boolean serverResponse =
                Strings.nullToEmpty(exception.getMessage()).contains("server response");
        final Set<ConstraintViolation<?>> violations = exception.getConstraintViolations();
        final ImmutableList<String> errors = violations.isEmpty() ?
                ImmutableList.of(Strings.nullToEmpty(exception.getMessage())) :
                transformedViolations(violations, serverResponse);

        int status = serverResponse ? 500 :
                ConstraintViolations.determineStatus(exception.getConstraintViolations());

        return Response.status(status)
                .entity(new ValidationErrorMessage(errors))
                .build();
    }

    private ImmutableList<String> transformedViolations(final Set<ConstraintViolation<?>> violations,
                                                        final boolean serverResponse) {
        return FluentIterable.from(violations)
            .transform(new Function<ConstraintViolation<?>, String>() {
                @Override
                public String apply(ConstraintViolation<?> v) {
                    if (serverResponse) {
                        return "server response " + ConstraintMessage.getMessage(v);
                    }
                    return ConstraintMessage.getMessage(v);
                }
            }).toList();
    }
}
