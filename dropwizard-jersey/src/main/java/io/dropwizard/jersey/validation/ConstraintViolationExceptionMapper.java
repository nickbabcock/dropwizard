package io.dropwizard.jersey.validation;

import com.google.common.collect.Iterables;
import org.eclipse.jetty.http.HttpStatus;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Path.Node;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Set;
import java.util.regex.Pattern;

@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {
    private static final Pattern argPattern = Pattern.compile("^arg\\d+$");

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        int status = HttpStatus.UNPROCESSABLE_ENTITY_422;

        // Detect where the constraint validation occurred so we can return an appropriate status
        // code. If the constraint failed with a *Param annotation, return a bad request. If it
        // failed validating the return value, return internal error. Else return unprocessable
        // entity.
        Set<ConstraintViolation<?>> violations = exception.getConstraintViolations();
        if (violations.size() > 0) {
            ConstraintViolation<?> violation = violations.iterator().next();
            Node node = Iterables.getLast(violation.getPropertyPath());
            if (node.getName().equals("<return value>")) {
                status = HttpStatus.INTERNAL_SERVER_ERROR_500;
            }
            else if (argPattern.matcher(node.getName()).matches()) {
                status = HttpStatus.BAD_REQUEST_400;
            }
        }

        final ValidationErrorMessage message = new ValidationErrorMessage(exception.getConstraintViolations());
        return Response.status(status)
                       .entity(message)
                       .build();
    }
}
