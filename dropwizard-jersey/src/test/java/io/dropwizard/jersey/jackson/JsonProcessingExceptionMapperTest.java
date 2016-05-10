package io.dropwizard.jersey.jackson;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import io.dropwizard.jersey.DropwizardResourceConfig;
import io.dropwizard.logging.BootstrapLogging;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.junit.Test;

import javax.validation.Validator;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonProcessingExceptionMapperTest extends JerseyTest {
    static {
        BootstrapLogging.bootstrap();
    }

    @Override
    protected Application configure() {
        forceSet(TestProperties.CONTAINER_PORT, "0");
        return DropwizardResourceConfig.forTesting(new MetricRegistry())
                .property(ServerProperties.RESPONSE_SET_STATUS_OVER_SEND_ERROR, "true")
                .packages("io.dropwizard.jersey.jackson");
    }

    @Override
    protected void configureClient(ClientConfig config) {
        final ObjectMapper mapper = new ObjectMapper();
        final JacksonMessageBodyProvider provider = new JacksonMessageBodyProvider(mapper);
        config.register(provider);
    }

    @Test
    public void returnsA500ForNonDeserializableRepresentationClasses() throws Exception {
        Response response = target("/json/broken").request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(new BrokenRepresentation(ImmutableList.of("whee")), MediaType.APPLICATION_JSON));
        assertThat(response.getStatus()).isEqualTo(500);
    }

    @Test
    public void returnsA500ForListNonDeserializableRepresentationClasses() throws Exception {
        final ImmutableList<BrokenRepresentation> ent =
            ImmutableList.of(new BrokenRepresentation(ImmutableList.of()),
                new BrokenRepresentation(ImmutableList.of("whoo")));

        Response response = target("/json/brokenList").request(MediaType.APPLICATION_JSON)
            .post(Entity.entity(ent, MediaType.APPLICATION_JSON));
        assertThat(response.getStatus()).isEqualTo(500);
    }

    @Test
    public void returnsA500ForNonSerializableRepresentationClassesOutbound() throws Exception {
        Response response = target("/json/brokenOutbound").request(MediaType.APPLICATION_JSON).get();
        assertThat(response.getStatus()).isEqualTo(500);
    }

    @Test
    public void returnsA500ForAbstractEntities() throws Exception {
        Response response = target("/json/interface").request(MediaType.APPLICATION_JSON)
            .post(Entity.entity("\"hello\"", MediaType.APPLICATION_JSON));
        assertThat(response.getStatus()).isEqualTo(500);
    }

    @Test
    public void returnsA500ForListAbstractEntities() throws Exception {
        Response response = target("/json/interfaceList").request(MediaType.APPLICATION_JSON)
            .post(Entity.entity("[\"hello\"]", MediaType.APPLICATION_JSON));
        assertThat(response.getStatus()).isEqualTo(500);
    }

    @Test
    public void returnsA400ForNonDeserializableRequestEntities1() throws Exception {
        Response response = target("/json/url").request(MediaType.APPLICATION_JSON)
            .post(Entity.entity("\"no-scheme.com\"", MediaType.APPLICATION_JSON));
        assertThat(response.getStatus()).isEqualTo(400);

        JsonNode errorMessage = response.readEntity(JsonNode.class);
        assertThat(errorMessage.get("code").asInt()).isEqualTo(400);
        assertThat(errorMessage.get("message").asText()).isEqualTo("Unable to process JSON");
        assertThat(errorMessage.has("details")).isFalse();
    }

    @Test
    public void returnsA400ForNonDeserializableRequestEntities2() throws Exception {
        Response response = target("/json/urlList").request(MediaType.APPLICATION_JSON)
            .post(Entity.entity("[\"no-scheme.com\"]", MediaType.APPLICATION_JSON));
        assertThat(response.getStatus()).isEqualTo(400);

        JsonNode errorMessage = response.readEntity(JsonNode.class);
        assertThat(errorMessage.get("code").asInt()).isEqualTo(400);
        assertThat(errorMessage.get("message").asText()).isEqualTo("Unable to process JSON");
        assertThat(errorMessage.has("details")).isFalse();
    }

    @Test
    public void returnsA400ForNonDeserializableRequestEntities() throws Exception {
        Response response = target("/json/ok").request(MediaType.APPLICATION_JSON)
            .post(Entity.entity(new UnknownRepresentation(100), MediaType.APPLICATION_JSON));
        assertThat(response.getStatus()).isEqualTo(400);

        JsonNode errorMessage = response.readEntity(JsonNode.class);
        assertThat(errorMessage.get("code").asInt()).isEqualTo(400);
        assertThat(errorMessage.get("message").asText()).isEqualTo("Unable to process JSON");
        assertThat(errorMessage.has("details")).isFalse();
    }

    @Test
    public void returnsA500ForNonDeserializableRequestEntities5() throws Exception {
        Response response = target("/json/ok").request(MediaType.APPLICATION_JSON)
            .post(Entity.entity("false", MediaType.APPLICATION_JSON));
        assertThat(response.getStatus()).isEqualTo(500);
    }

    @Test
    public void returnsA400ForInvalidFormatRequestEntities() throws Exception {
        assertEndpointReturns400("{\"message\": \"a\", \"date\": \"2016-01-01\"}");
    }

    @Test
    public void returnsA400ForInvalidFormatRequestEntitiesWrapped() throws Exception {
        assertEndpointReturns400("{\"message\": \"1\", \"date\": \"a\"}");
    }

    @Test
    public void returnsA400ForInvalidFormatRequestEntitiesArray() throws Exception {
        assertEndpointReturns400("{\"message\": \"1\", \"date\": [1,1,1,1]}");
    }

    @Test
    public void returnsA400ForSemanticInvalidDate() throws Exception {
        assertEndpointReturns400("{\"message\": \"1\", \"date\": [-1,-1,-1]}");
    }

    private void assertEndpointReturns400(String entity) {
        Response response = target("/json/ok").request(MediaType.APPLICATION_JSON)
            .post(Entity.entity(entity, MediaType.APPLICATION_JSON));
        assertThat(response.getStatus()).isEqualTo(400);

        JsonNode errorMessage = response.readEntity(JsonNode.class);
        assertThat(errorMessage.get("code").asInt()).isEqualTo(400);
        assertThat(errorMessage.get("message").asText()).isEqualTo("Unable to process JSON");
        assertThat(errorMessage.has("details")).isFalse();
    }


}
