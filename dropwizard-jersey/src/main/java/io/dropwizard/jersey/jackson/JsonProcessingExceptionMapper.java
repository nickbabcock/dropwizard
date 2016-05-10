package io.dropwizard.jersey.jackson;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.PropertyBindingException;
import com.google.common.base.Throwables;
import io.dropwizard.jersey.errors.ErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.List;

@Provider
public class JsonProcessingExceptionMapper implements ExceptionMapper<JsonProcessingException> {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonProcessingExceptionMapper.class);
    private final boolean showDetails;

    public JsonProcessingExceptionMapper() {
        this(false);
    }

    public JsonProcessingExceptionMapper(boolean showDetails) {
        this.showDetails = showDetails;
    }

    @Override
    public Response toResponse(JsonProcessingException exception) {
        /*
         * If the error is in the JSON generation, it's a server error.
         */
        if (exception instanceof JsonGenerationException) {
            LOGGER.warn("Error generating JSON", exception);
            return Response.serverError().build();
        }

        final String message = exception.getOriginalMessage();

        /*
         * If we can't deserialize the JSON because someone forgot a no-arg
         * constructor, or it is not known how to serialize the type it's
         * a server error and we should inform the developer.
         */
        if (exception instanceof JsonMappingException) {
            final JsonMappingException ex = (JsonMappingException) exception;
            final List<JsonMappingException.Reference> path = ex.getPath();
            final Throwable cause = Throwables.getRootCause(ex);

            // IOException occurs when user provides a malformed url to deserialize
            final boolean clientIoEx = cause.getMessage().startsWith("Unexpected IOException");

            // Exceptions that denote an error on the client side
            final boolean clientCause = cause instanceof InvalidFormatException ||
                cause instanceof PropertyBindingException || clientIoEx;

            final boolean beanError = cause.getMessage().startsWith("No suitable constructor found") ||
                cause.getMessage().startsWith("Can not construct instance") ||
                cause.getMessage().startsWith("Can not instantiate value");

            // Detects if the object that caused the failure is a field of the
            // object or an index of the array.
            boolean pathFailure = path.isEmpty();

            if ((cause instanceof JsonProcessingException && pathFailure || beanError) && !clientCause) {
                LOGGER.error("Unable to serialize or deserialize the specific type", exception);
                return Response.serverError().build();
            }
        }

        /*
         * Otherwise, it's those pesky users.
         */
        LOGGER.debug("Unable to process JSON", exception);
        final ErrorMessage errorMessage = new ErrorMessage(Response.Status.BAD_REQUEST.getStatusCode(),
                "Unable to process JSON", showDetails ? message : null);
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(errorMessage)
                .build();
    }
}
