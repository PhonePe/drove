package com.phonepe.drove.controller.helpers;

import com.phonepe.drove.common.discovery.leadership.LeadershipObserver;
import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.models.info.nodedata.ControllerNodeData;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import lombok.val;
import org.eclipse.jetty.http.HttpStatus;
import org.glassfish.jersey.client.ClientProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 *
 */
@ExtendWith(DropwizardExtensionsSupport.class)
class LeadershipCheckFilterTest {
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    public static class FilterTestResource {
        @GET
        @Path("/ok")
        public Response ok() {
            return Response.ok().build();
        }

        @GET
        @Path("/ui/ok")
        public Response uiOk() {
            return Response.ok().build();
        }
    }

    private static final LeadershipObserver observer = mock(LeadershipObserver.class);
    private static final LeadershipEnsurer ensurer = mock(LeadershipEnsurer.class);

    private static final ResourceExtension EXT = ResourceExtension.builder()
            .addResource(new FilterTestResource())
            .addProvider(new LeadershipCheckFilter(ensurer, observer))
            .build();

    @AfterEach
    public void resetMocks() {
        reset(observer, ensurer);
    }

    @Test
    void testOkWhenLeader() {
        when(ensurer.isLeader()).thenReturn(true);
        try (val r = EXT.target("/ok")
                .request()
                .get()) {
            assertEquals(HttpStatus.OK_200, r.getStatus());
        }
    }

    @Test
    void testBadRequestWhenNotLeaderAndNoLeader() {
        when(ensurer.isLeader()).thenReturn(false);
        when(observer.leader()).thenReturn(Optional.empty());
        try (val r = EXT.target("/ok")
                .request()
                .get()) {
            assertEquals(HttpStatus.BAD_REQUEST_400, r.getStatus());
        }
    }

    @Test
    void testBadRequestWhenOtherLeader() {
        when(ensurer.isLeader()).thenReturn(false);
        when(observer.leader()).thenReturn(Optional.of(new ControllerNodeData("host2",
                                                                              8080,
                                                                              NodeTransportType.HTTP,
                                                                              new Date(),
                                                                              true)));
        try (val r = EXT.target("/ok")
                .request()
                .get()) {
            assertEquals(HttpStatus.BAD_REQUEST_400, r.getStatus());
        }
    }

    @Test
    void testBadRequestUIRedirection() {
        when(ensurer.isLeader()).thenReturn(false);
        when(observer.leader()).thenReturn(Optional.of(new ControllerNodeData("host2",
                                                                              8080,
                                                                              NodeTransportType.HTTPS,
                                                                              new Date(),
                                                                              true)));
        try (val r = EXT.target("/ui/ok")
                .property(ClientProperties.FOLLOW_REDIRECTS, Boolean.FALSE)
                .request()
                .get()) {
            assertEquals(HttpStatus.SEE_OTHER_303, r.getStatus());
            assertEquals("https://host2:8080/ui/ok", r.getHeaderString(HttpHeaders.LOCATION));
        }
    }
}