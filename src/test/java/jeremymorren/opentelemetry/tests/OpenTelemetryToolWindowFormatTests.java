package jeremymorren.opentelemetry.tests;

import jeremymorren.opentelemetry.ui.OpenTelemetryToolWindow;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;

/**
 * Tests for compact number formatting used by the tool window counters.
 */
public class OpenTelemetryToolWindowFormatTests {
    private Locale previousLocale;

    @Before
    public void setUpLocale() {
        previousLocale = Locale.getDefault();
        Locale.setDefault(Locale.US);
    }

    @After
    public void restoreLocale() {
        Locale.setDefault(previousLocale);
    }

    @Test
    public void formatInteger_belowThreshold_noSuffix() throws Exception {
        assertEquals("999", OpenTelemetryToolWindow.format(999));
    }

    @Test
    public void formatInteger_thousand_usesKSuffix() throws Exception {
        assertEquals("1.00 K", OpenTelemetryToolWindow.format(1_000));
    }

    @Test
    public void formatInteger_million_usesMSuffix() throws Exception {
        assertEquals("1.00 M", OpenTelemetryToolWindow.format(1_000_000));
    }

    @Test
    public void formatLong_roundsAndUsesKSuffix() throws Exception {
        assertEquals("1.50 K", OpenTelemetryToolWindow.format(1_500L));
    }

    @Test
    public void formatLong_roundsAndUsesMSuffix() throws Exception {
        assertEquals("2.50 M", OpenTelemetryToolWindow.format(2_500_000L));
    }

    @Test
    public void formatDouble_belowThreshold_noSuffix() throws Exception {
        assertEquals("999.9", OpenTelemetryToolWindow.format(999.9));
    }

    @Test
    public void formatDouble_thousand_usesKSuffix() throws Exception {
        assertEquals("1.0 K", OpenTelemetryToolWindow.format(1_000.0));
    }

    @Test
    public void formatDouble_million_usesMSuffix() throws Exception {
        assertEquals("1.0 M", OpenTelemetryToolWindow.format(1_000_000.0));
    }
}
