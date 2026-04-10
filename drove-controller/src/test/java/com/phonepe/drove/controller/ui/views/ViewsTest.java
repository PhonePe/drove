/*
 * Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.drove.controller.ui.views;

import com.phonepe.drove.controller.config.InstallationMetadata;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import lombok.val;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import ru.vyarus.guicey.gsp.views.template.TemplateContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for all View classes — both long-existing and newly introduced
 * nav-section views (TasksPageView, LocalServicesPageView, ClusterPageView, ApplicationsPageView).
 */
@ExtendWith(DropwizardExtensionsSupport.class)
class ViewsTest {

    private static final InstallationMetadata META = InstallationMetadata.DEFAULT;

    private static MockedStatic<TemplateContext> mockedTemplateContext;

    @BeforeAll
    static void initializeTemplateContext() {
        val tc = mock(TemplateContext.class);
        when(tc.lookupTemplatePath(anyString())).thenReturn("/");
        mockedTemplateContext = mockStatic(TemplateContext.class);
        mockedTemplateContext.when(TemplateContext::getInstance).thenReturn(tc);
    }

    @AfterAll
    static void tearDownTemplateContext() {
        if (mockedTemplateContext != null) {
            mockedTemplateContext.close();
        }
    }

    @Test
    void homeViewConstructsCorrectly() {
        val view = new HomeView(META);
        assertNotNull(view);
        assertSame(META, view.getInstallationMetadata());
    }

    @Test
    void tasksPageViewConstructsCorrectly() {
        val view = new TasksPageView(META);
        assertNotNull(view);
        assertSame(META, view.getInstallationMetadata());
    }

    @Test
    void localServicesPageViewConstructsCorrectly() {
        val view = new LocalServicesPageView(META);
        assertNotNull(view);
        assertSame(META, view.getInstallationMetadata());
    }

    @Test
    void clusterPageViewConstructsCorrectly() {
        val view = new ClusterPageView(META);
        assertNotNull(view);
        assertSame(META, view.getInstallationMetadata());
    }

    @Test
    void applicationsPageViewConstructsCorrectly() {
        val view = new ApplicationsPageView(META);
        assertNotNull(view);
        assertSame(META, view.getInstallationMetadata());
    }

    @Test
    void allNavSectionViewsShareSameMetadata() {
        val home = new HomeView(META);
        val applications = new ApplicationsPageView(META);
        val tasks = new TasksPageView(META);
        val localServices = new LocalServicesPageView(META);
        val cluster = new ClusterPageView(META);

        assertEquals(META, home.getInstallationMetadata());
        assertEquals(META, applications.getInstallationMetadata());
        assertEquals(META, tasks.getInstallationMetadata());
        assertEquals(META, localServices.getInstallationMetadata());
        assertEquals(META, cluster.getInstallationMetadata());
    }

    @Test
    void applicationDetailsPageViewConstructsCorrectly() {
        val view = new ApplicationDetailsPageView(META, "TEST_APP-1");
        assertNotNull(view);
        assertEquals("TEST_APP-1", view.getAppId());
        assertSame(META, view.getInstallationMetadata());
    }

    @Test
    void applicationDetailsPageViewWithNullAppId() {
        // null is a valid construction — validation is done at the resource layer
        val view = new ApplicationDetailsPageView(META, null);
        assertNotNull(view);
        assertNull(view.getAppId());
    }

    @Test
    void executorDetailsPageViewConstructsCorrectly() {
        val view = new ExecutorDetailsPageView(META, "executor-42");
        assertNotNull(view);
        assertEquals("executor-42", view.getExecutorId());
        assertSame(META, view.getInstallationMetadata());
    }

    @Test
    void localServiceDetailsPageViewConstructsCorrectly() {
        val view = new LocalServiceDetailsPageView(META, "TEST_SVC-1");
        assertNotNull(view);
        assertEquals("TEST_SVC-1", view.getServiceId());
        assertSame(META, view.getInstallationMetadata());
    }

    @Test
    void taskDetailsPageViewConstructsCorrectly() {
        val view = new TaskDetailsPage(META, "SRC_APP", "task-001", null);
        assertNotNull(view);
        assertEquals("SRC_APP", view.getSourceAppName());
        assertEquals("task-001", view.getTaskId());
        assertNull(view.getInstanceInfo());
        assertSame(META, view.getInstallationMetadata());
    }

    @Test
    void logPailerPageForApplications() {
        val view = new LogPailerPage(META, "applications", "APP-1", "INST-1", "stdout.log");
        assertNotNull(view);
        assertEquals("applications", view.getLogType());
        assertEquals("APP-1", view.getAppId());
        assertEquals("INST-1", view.getInstanceId());
        assertEquals("stdout.log", view.getLogFileName());
        assertSame(META, view.getInstallationMetadata());
    }

    @Test
    void logPailerPageForTasks() {
        val view = new LogPailerPage(META, "tasks", "SRC_APP", "task-42", "output.log");
        assertNotNull(view);
        assertEquals("tasks", view.getLogType());
        assertEquals("SRC_APP", view.getAppId());
        assertEquals("task-42", view.getInstanceId());
    }

    @Test
    void logPailerPageForLocalServices() {
        val view = new LogPailerPage(META, "localservices", "SVC-1", "SVC-INST-1", "app.log");
        assertNotNull(view);
        assertEquals("localservices", view.getLogType());
    }

    @Test
    void applicationInstanceDetailsPageWithReadAccess() {
        val view = new ApplicationInstanceDetailsPage(META, "APP-1", "INST-1", null, true);
        assertNotNull(view);
        assertEquals("APP-1", view.getAppId());
        assertEquals("INST-1", view.getInstanceId());
        assertNull(view.getInstanceInfo());
        assertTrue(view.isHasReadAccess());
        assertSame(META, view.getInstallationMetadata());
    }

    @Test
    void applicationInstanceDetailsPageWithoutReadAccess() {
        val view = new ApplicationInstanceDetailsPage(META, "APP-1", "INST-1", null, false);
        assertFalse(view.isHasReadAccess());
    }

    @Test
    void localServiceInstanceDetailsPageWithReadAccess() {
        val view = new LocalServiceInstanceDetailsPage(META, "SVC-1", "SVC-INST-1", null, true);
        assertNotNull(view);
        assertEquals("SVC-1", view.getServiceId());
        assertEquals("SVC-INST-1", view.getInstanceId());
        assertNull(view.getInstanceInfo());
        assertTrue(view.isHasReadAccess());
        assertSame(META, view.getInstallationMetadata());
    }

    @Test
    void localServiceInstanceDetailsPageWithoutReadAccess() {
        val view = new LocalServiceInstanceDetailsPage(META, "SVC-1", "SVC-INST-1", null, false);
        assertFalse(view.isHasReadAccess());
    }

    @Test
    void tasksPageViewEqualityContract() {
        val v1 = new TasksPageView(META);
        val v2 = new TasksPageView(META);
        assertEquals(v1, v2);
        assertEquals(v1.hashCode(), v2.hashCode());
    }

    @Test
    void localServicesPageViewEqualityContract() {
        val v1 = new LocalServicesPageView(META);
        val v2 = new LocalServicesPageView(META);
        assertEquals(v1, v2);
        assertEquals(v1.hashCode(), v2.hashCode());
    }

    @Test
    void clusterPageViewEqualityContract() {
        val v1 = new ClusterPageView(META);
        val v2 = new ClusterPageView(META);
        assertEquals(v1, v2);
        assertEquals(v1.hashCode(), v2.hashCode());
    }

    @Test
    void applicationsPageViewEqualityContract() {
        val v1 = new ApplicationsPageView(META);
        val v2 = new ApplicationsPageView(META);
        assertEquals(v1, v2);
        assertEquals(v1.hashCode(), v2.hashCode());
    }

    @Test
    void applicationDetailsPageViewEqualityContract() {
        val v1 = new ApplicationDetailsPageView(META, "APP-1");
        val v2 = new ApplicationDetailsPageView(META, "APP-1");
        val v3 = new ApplicationDetailsPageView(META, "APP-2");
        assertEquals(v1, v2);
        assertNotEquals(v1, v3);
    }

    @Test
    void logPailerPageEqualityContract() {
        val v1 = new LogPailerPage(META, "applications", "APP-1", "I-1", "out.log");
        val v2 = new LogPailerPage(META, "applications", "APP-1", "I-1", "out.log");
        val v3 = new LogPailerPage(META, "tasks", "APP-1", "I-1", "out.log");
        assertEquals(v1, v2);
        assertNotEquals(v1, v3);
    }

    @Test
    void toStringDoesNotThrow() {
        assertDoesNotThrow(() -> new HomeView(META).toString());
        assertDoesNotThrow(() -> new ApplicationsPageView(META).toString());
        assertDoesNotThrow(() -> new TasksPageView(META).toString());
        assertDoesNotThrow(() -> new LocalServicesPageView(META).toString());
        assertDoesNotThrow(() -> new ClusterPageView(META).toString());
        assertDoesNotThrow(() -> new ApplicationDetailsPageView(META, "A").toString());
        assertDoesNotThrow(() -> new ExecutorDetailsPageView(META, "E").toString());
        assertDoesNotThrow(() -> new LocalServiceDetailsPageView(META, "S").toString());
        assertDoesNotThrow(() -> new TaskDetailsPage(META, "SRC", "T", null).toString());
        assertDoesNotThrow(() -> new LogPailerPage(META, "applications", "A", "I", "f.log").toString());
        assertDoesNotThrow(() -> new ApplicationInstanceDetailsPage(META, "A", "I", null, false).toString());
        assertDoesNotThrow(() -> new LocalServiceInstanceDetailsPage(META, "S", "I", null, false).toString());
    }
}
