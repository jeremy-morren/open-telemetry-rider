package jeremymorren.opentelemetry.tests;

import jeremymorren.opentelemetry.util.TimeSpanSerializer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import java.time.Duration;
import java.util.Collection;

/**
 * Test cases for parsing C# time spans to Duration "HH:mm:ss", "d.HH:mm:ss", or "d.HH:mm:ss.SSSSSSSSS".
 */
@RunWith(Parameterized.class)
public class TimeSpanSerializerTests {
    private final String timeSpanString;
    private final Duration expectedDuration;

    public TimeSpanSerializerTests(String timeSpanString, Duration expectedDuration) {
        this.timeSpanString = timeSpanString;
        this.expectedDuration = expectedDuration;
    }

    @Test
    public void testParseTimeSpan() {
        Duration parsedDuration = TimeSpanSerializer.Companion.parse(timeSpanString);
        assert parsedDuration.equals(expectedDuration) :
            "Expected: " + expectedDuration + ", but got: " + parsedDuration;
    }


    @Parameters
    public static Collection<Object[]> timeSpans() {
        return java.util.Arrays.asList(new Object[][]{
                {"00:00:00", Duration.ZERO},
                {"00:00:01", Duration.ofSeconds(1)},
                {"00:01:00", Duration.ofMinutes(1)},
                {"01:00:00", Duration.ofHours(1)},
                {"01:01:01", Duration.ofHours(1).plusMinutes(1).plusSeconds(1)},
                {"02:30:15", Duration.ofHours(2).plusMinutes(30).plusSeconds(15)},
                {"12:34:56", Duration.ofHours(12).plusMinutes(34).plusSeconds(56)},
                {"23:59:59", Duration.ofHours(23).plusMinutes(59).plusSeconds(59)},
                {"24:00:00", Duration.ofHours(24)},
                {"1.00:00:00", Duration.ofDays(1)},
                {"23.05:12:11", Duration.ofDays(23).plusHours(5).plusMinutes(12).plusSeconds(11)},
                {"1.02:03:04", Duration.ofDays(1).plusHours(2).plusMinutes(3).plusSeconds(4)},
                {"00:00:00.123", Duration.ofMillis(123)},
                {"00:00:00.2223334", fromSeconds(0.2223334)},
                {"00:00:01.000000001", fromSeconds(1.000000001)},
                {"1.02:03:04.567890123", Duration.ofDays(1).plusHours(2).plusMinutes(3).plusSeconds(4).plusNanos(567_890_123)},
                {"-00:00:01", Duration.ofSeconds(-1)},
                {"-01:00:00", Duration.ofHours(-1)},
                {"-23:59:59", Duration.ofHours(-23).plusMinutes(-59).plusSeconds(-59)},
                {"-24:00:00", Duration.ofHours(-24)},
                {"-24:00:00.2242", Duration.ofHours(-24).minus(fromSeconds(0.2242))},
                {"-1.02:03:04", Duration.ofDays(-1).minusHours(2).minusMinutes(3).minusSeconds(4)},
                {"-1.02:03:04.567890123", Duration.ofDays(-1).minusHours(2).minusMinutes(3).minusSeconds(4).minusNanos(567_890_123)},
        });
    }

    private static Duration fromSeconds(double seconds) {
        long nanos = (long) (seconds * 1_000_000_000);
        return Duration.ofNanos(nanos);
    }
}
