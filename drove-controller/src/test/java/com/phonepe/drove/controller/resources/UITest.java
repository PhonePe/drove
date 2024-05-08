package com.phonepe.drove.controller.resources;

import com.phonepe.drove.auth.model.DroveExternalUser;
import com.phonepe.drove.auth.model.DroveUserRole;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.vyarus.guicey.gsp.views.template.TemplateContext;

import javax.ws.rs.WebApplicationException;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 *
 */
@ExtendWith(DropwizardExtensionsSupport.class)
class UITest {
    private static final ApplicationStateDB applicationStateDB = mock(ApplicationStateDB.class);
    private static final ApplicationInstanceInfoDB applicationInstanceInfoDB = mock(ApplicationInstanceInfoDB.class);
    private static final TaskDB taskDB = mock(TaskDB.class);

    private static final UI resource = new UI(applicationStateDB,
                                              applicationInstanceInfoDB,
                                              taskDB,
                                              ControllerOptions.DEFAULT,
                                              null);

    @BeforeAll
    static void initializeTemplate() {
        val tc = mock(TemplateContext.class);
        when(tc.lookupTemplatePath(anyString())).thenReturn("/");
        mockStatic(TemplateContext.class).when(TemplateContext::getInstance).thenReturn(tc);
    }

    @AfterEach
    void resetMocks() {
        reset(applicationStateDB,
              applicationInstanceInfoDB,
              taskDB);
    }

    @Test
    void testHome() {
        assertNotNull(resource.home());
    }

    @Test
    void testApplicationDetails() {
        when(applicationStateDB.application("TEST_APP"))
                .thenReturn(Optional.of(new ApplicationInfo("TEST_APP",
                                                            ControllerTestUtils.appSpec(),
                                                            1,
                                                            new Date(),
                                                            new Date())));
        assertThrows(WebApplicationException.class,
                     () -> resource.applicationDetails(null));
        assertThrows(WebApplicationException.class,
                     () ->resource.applicationDetails("WrongApp"));
        assertNotNull(resource.applicationDetails("TEST_APP"));
    }

    @Test
    void testInstanceDetailsPage() {


        when(applicationStateDB.application("TEST_APP"))
                .thenReturn(Optional.of(new ApplicationInfo("TEST_APP",
                                                            ControllerTestUtils.appSpec(),
                                                            1,
                                                            new Date(),
                                                            new Date())));
        assertThrows(WebApplicationException.class,
                     () -> resource.instanceDetailsPage(null,
                                                        null,
                                                        null));
        assertThrows(WebApplicationException.class,
                     () -> resource.instanceDetailsPage(null,
                                                        "WrongApp",
                                                        null));
        assertNotNull(resource.instanceDetailsPage(new DroveExternalUser("BLAH", DroveUserRole.EXTERNAL_READ_ONLY, null),
                                                   "TEST_APP",
                                                   null));
    }

    @Test
    void testAppLogPailerPage() {
        when(applicationStateDB.application("TEST_APP"))
                .thenReturn(Optional.of(new ApplicationInfo("TEST_APP",
                                                            ControllerTestUtils.appSpec(),
                                                            1,
                                                            new Date(),
                                                            new Date())));
        assertThrows(WebApplicationException.class,
                     () -> resource.appLogPailerPage(null,
                                                        null,
                                                        null));
        assertThrows(WebApplicationException.class,
                     () -> resource.appLogPailerPage("WrongApp",
                                                        null,
                                                     null));
        assertNotNull(resource.appLogPailerPage("TEST_APP",
                                                   "TI",
                                                   "test.log"));
    }

    @Test
    void testTaskDetailsPage() {
        val ti = mock(TaskInfo.class);
        when(taskDB.task("TEST_APP", "TEST_TASK"))
                .thenReturn(Optional.of(ti));
        assertThrows(WebApplicationException.class,
                     () -> resource.taskDetailsPage(null,
                                                        null));
        assertThrows(WebApplicationException.class,
                     () -> resource.taskDetailsPage("WrongApp",
                                                        null));
        assertThrows(WebApplicationException.class,
                     () -> resource.taskDetailsPage("TEST_APP",
                                                        null));
        assertNotNull(resource.taskDetailsPage("TEST_APP",
                                                   "TEST_TASK"));
    }

    @Test
    void testTaskLogPailerPage() {
        val ti = mock(TaskInfo.class);
        when(taskDB.task("TEST_APP", "TEST_TASK"))
                .thenReturn(Optional.of(ti));
        assertThrows(WebApplicationException.class,
                     () -> resource.taskLogPailerPage(null,
                                                        null,
                                                      null));
        assertThrows(WebApplicationException.class,
                     () -> resource.taskLogPailerPage("WrongApp",
                                                        null,
                                                      null));
        assertThrows(WebApplicationException.class,
                     () -> resource.taskLogPailerPage("TEST_APP",
                                                        null,
                                                      null));
        assertNotNull(resource.taskLogPailerPage("TEST_APP",
                                                   "TEST_TASK",
                                                 "output.log"));
    }

    @Test
    void testExecutorDetails() {
        assertThrows(WebApplicationException.class,
                     () -> resource.executorDetails(null));
        assertNotNull(resource.executorDetails("TestExec"));
    }


}