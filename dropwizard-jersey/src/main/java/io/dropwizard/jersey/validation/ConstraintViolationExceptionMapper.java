package io.dropwizard.jersey.validation;

import com.google.common.base.Function;
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

@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {
    @Override
    public Response toResponse(ConstraintViolationException exception) {
        final boolean serverResponse = ("Server response".equals(exception.getMessage()));

        final ImmutableList<String> errors = FluentIterable.from(exception.getConstraintViolations())
                .transform(new Function<ConstraintViolation<?>, String>() {
                    @Override
                    public String apply(ConstraintViolation<?> v) {
                        if (serverResponse) {
                            return "server response " + ConstraintMessage.getMessage(v);
                        }
                        return ConstraintMessage.getMessage(v);
                    }
                }).toList();

        int status = serverResponse ? 500 :
                ConstraintViolations.determineStatus(exception.getConstraintViolations());

        return Response.status(status)
                .entity(new ValidationErrorMessage(errors))
                .build();
    }
}
