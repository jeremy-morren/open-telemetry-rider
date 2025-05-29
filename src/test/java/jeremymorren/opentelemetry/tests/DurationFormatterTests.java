package jeremymorren.opentelemetry.tests;

import jeremymorren.opentelemetry.util.DurationFormatter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import java.time.Duration;
import java.util.Collection;

/**
 * Test cases for formatting Duration to a string representation.
 */
@RunWith(Parameterized.class)
public class DurationFormatterTests {
    private final Duration duration;
    private final String expectedString;

    public DurationFormatterTests(Duration duration, String expectedString) {
        this.duration = duration;
        this.expectedString = expectedString;
    }

    @Test
    public void testFormatDuration() {
        var formatted = DurationFormatter.Companion.format(duration);
        assert formatted.equals(expectedString) :
            "Expected: " + expectedString + ", but got: " + formatted + ". Input: " + duration;
    }

    @Test
    public void testFormatNegativeDuration() {
        if (duration == Duration.ZERO) {
            // Skip negative test for zero duration, as it has no negative representation
            return;
        }
        var negativeDuration = duration.negated();
        var expectedNegativeString = "-" + expectedString;
        var formatted = DurationFormatter.Companion.format(negativeDuration);
        assert formatted.equals(expectedNegativeString) :
            "Expected: " + expectedNegativeString + ", but got: " + formatted + ". Input: " + duration;
    }

    @SuppressWarnings("UnnecessaryUnicodeEscape")
    @Parameters
    public static Collection<Object[]> durations() {
        return java.util.Arrays.asList(new Object[][]{
                {Duration.ZERO, "-"},

                {Duration.ofMillis(50), "50.0 ms"},
                {Duration.ofMillis(200), "200.0 ms"},
                {Duration.ofSeconds(1), "1.0 s"},

                {fromSeconds(1.2), "1.2 s"},
                {fromSeconds(10.923), "10.9 s"},
                {fromSeconds(5.46), "5.5 s"},

                {fromMilliseconds(400), "400.0 ms"},
                {fromMilliseconds(440), "440.0 ms"},
                {fromMilliseconds(100.98), "101.0 ms"},
                {fromMilliseconds(4.2), "4.2 ms"},

                {fromMicroseconds(2), "2.0 \u00B5s"},
                {fromMicroseconds(95.8), "95.8 \u00B5s"},
                {fromMicroseconds(901), "901.0 \u00B5s"},

                {Duration.ofNanos(50), "50 ns"},
                {Duration.ofNanos(560), "560 ns"},

                {Duration.ofMinutes(1), "1m 0.0s"},
                {Duration.ofMinutes(20).plusSeconds(30), "20m 30.0s"},
                {Duration.ofMinutes(3).plus(fromSeconds(2.86)), "3m 2.9s"},

                {Duration.ofHours(1), "1h 0.0m"},
                {Duration.ofHours(2).plusMinutes(15), "2h 15.0m"},
                {Duration.ofHours(1).plus(fromMinutes(30.5)), "1h 30.5m"},
                {Duration.ofHours(10).plus(fromMinutes(10.44)), "10h 10.4m"},
        });
    }

    private static Duration fromMinutes(double minutes) {
        long nanos = (long) (minutes * 60 * 1_000_000_000);
        return Duration.ofNanos(nanos);
    }

    private static Duration fromSeconds(double seconds) {
        long nanos = (long) (seconds * 1_000_000_000);
        return Duration.ofNanos(nanos);
    }

    private static Duration fromMilliseconds(double milliseconds) {
        long nanos = (long) (milliseconds * 1_000_000);
        return Duration.ofNanos(nanos);
    }

    private static Duration fromMicroseconds(double microseconds) {
        long nanos = (long) (microseconds * 1_000);
        return Duration.ofNanos(nanos);
    }
}
