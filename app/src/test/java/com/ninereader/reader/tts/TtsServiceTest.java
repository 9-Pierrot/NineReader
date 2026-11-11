package com.ninereader.reader.tts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.speech.tts.TextToSpeech;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowService;

/**
 * Read aloud must never stop its service before startForeground() was called for a pending
 * startForegroundService() request: Android kills the app (ForegroundServiceDidNotStartInTimeException).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TtsServiceTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    private Intent action(String a) {
        return new Intent(context, TtsService.class).setAction(a);
    }

    @Test
    public void engineFailingDuringCreateStillGoesForegroundBeforeStopping() {
        ServiceController<TtsService> c = Robolectric.buildService(TtsService.class).create();
        TtsService service = c.get();
        ShadowService shadow = shadowOf((Service) service);
        // The engine cannot be reached: TextToSpeech calls onInit(ERROR) from the constructor, in onCreate.
        service.onInit(TextToSpeech.ERROR);
        assertFalse("must not stop before the start request is handled", shadow.isStoppedBySelf());
        service.onStartCommand(action(TtsService.ACTION_START), 0, 1);
        // startForeground() was called for the request, then the foreground was left before stopping.
        assertTrue(shadow.isForegroundStopped());
        assertTrue(shadow.isStoppedBySelf());
        assertEquals("stops for this request only", 1, shadow.getStopSelfId());
        shadowOf(android.os.Looper.getMainLooper()).idle();
        assertTrue("the reader shows the engine error", TtsController.engineError().getValue());
    }

    @Test
    public void stoppingKeepsTheServiceForANewerPendingStart() {
        ServiceController<TtsService> c = Robolectric.buildService(TtsService.class).create();
        TtsService service = c.get();
        ShadowService shadow = shadowOf((Service) service);
        service.onStartCommand(action(TtsService.ACTION_START), 0, 1);
        service.onStartCommand(action(TtsService.ACTION_STOP), 0, 2);
        // stopSelf(2): a start request 3 already queued by the system would keep the service alive.
        assertEquals(2, shadow.getStopSelfId());
    }

    @Test
    public void commandForAReadingThatIsNotRunningJustStops() {
        ServiceController<TtsService> c = Robolectric.buildService(TtsService.class).create();
        TtsService service = c.get();
        ShadowService shadow = shadowOf((Service) service);
        service.onStartCommand(action(TtsService.ACTION_PAUSE), 0, 1);
        assertNull("no foreground for a plain command", shadow.getLastForegroundNotification());
        assertTrue(shadow.isStoppedBySelf());
        assertEquals(1, shadow.getStopSelfId());
    }
}
