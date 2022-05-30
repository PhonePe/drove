package com.phonepe.drove.controller.statedb;

import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.controller.testsupport.InMemoryApplicationStateDB;
import com.phonepe.drove.models.application.ApplicationInfo;
import io.appform.signals.signals.ConsumingSyncSignal;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.IntStream;

import static com.phonepe.drove.common.CommonUtils.sublist;
import static com.phonepe.drove.controller.utils.ControllerUtils.appId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class CachingProxyApplicationStateDBTest extends ControllerTestBase {

    @Test
    void testCaching() {
        val root = new InMemoryApplicationStateDB();
        val leadershipSignal = new ConsumingSyncSignal<Boolean>();
        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        when(leadershipEnsurer.onLeadershipStateChanged()).thenReturn(leadershipSignal);
        val db = new CachingProxyApplicationStateDB(root, leadershipEnsurer);

        assertTrue(db.applications(0, Integer.MAX_VALUE).isEmpty());

        val rng = new SecureRandom();
        val generatedInfo = new ArrayList<ApplicationInfo>();
        assertTrue(IntStream.rangeClosed(1, 100)
                           .mapToObj(i -> {
                               val spec = ControllerTestUtils.appSpec("TEST_SPEC_" + i, 1);
                               val now = new Date();
                               return new ApplicationInfo(appId(spec), spec, rng.nextInt(10), now, now);
                           })
                           .peek(generatedInfo::add)
                           .allMatch(info -> db.updateApplicationState(info.getAppId(), info)));
        assertEquals(100, db.applications(0, Integer.MAX_VALUE).size());
        matchListElements(db, generatedInfo);
        assertTrue(sublist(generatedInfo, 0, 50)
                .stream()
                .map(ApplicationInfo::getAppId)
                .allMatch(db::deleteApplicationState));
        assertEquals(50, db.applications(0, Integer.MAX_VALUE).size());
        val remainingInfo = sublist(generatedInfo, 50, 50);
        matchListElements(db, remainingInfo);

        //Delete from backend then trigger signal to purge local cache and test that local is now empty
        remainingInfo.forEach(info -> root.deleteApplicationState(info.getAppId()));
        leadershipSignal.dispatch(true);
        assertTrue(db.applications(0, Integer.MAX_VALUE).isEmpty());
    }

    private void matchListElements(ApplicationStateDB db, List<ApplicationInfo> original) {
        original.forEach(info -> {
            val r = db.application(info.getAppId());
            assertTrue(r.isPresent());
            assertEquals(info, r.get());
        });
    }

}