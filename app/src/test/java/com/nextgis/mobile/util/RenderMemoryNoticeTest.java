package com.nextgis.mobile.util;
import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
public class RenderMemoryNoticeTest {
    @Test public void differentMapsRecreationsAndFurtherPressureShareTheSameSession() {
        RenderMemoryNotice.Session session = new RenderMemoryNotice.Session();
        assertTrue(session.claim());
        for (int i = 0; i < 50; i++) assertFalse(session.claim());
        assertTrue(new RenderMemoryNotice.Session().claim());
    }
    @Test public void simultaneousRestrictionsStillHaveOnlyOneNotice() throws Exception {
        RenderMemoryNotice.Session session = new RenderMemoryNotice.Session();
        AtomicInteger notices = new AtomicInteger(); CountDownLatch done = new CountDownLatch(20);
        for (int i = 0; i < 20; i++) new Thread(() -> {
            if (session.claim()) notices.incrementAndGet(); done.countDown();
        }).start();
        assertTrue(done.await(5, java.util.concurrent.TimeUnit.SECONDS)); assertEquals(1, notices.get());
    }
}
