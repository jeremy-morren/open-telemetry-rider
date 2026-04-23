package jeremymorren.opentelemetry.tests;

import jeremymorren.opentelemetry.models.Activity;
import org.junit.Test;

public class ActivityTraceFlagsDisplayTests {
    @Test
    public void mapsNotRecordedFromZero() {
        assert "NotRecorded".equals(Activity.formatActivityTraceFlags("0"));
    }

    @Test
    public void mapsRecordedFromOne() {
        assert "Recorded".equals(Activity.formatActivityTraceFlags("1"));
    }

    @Test
    public void mapsRecordedWithIsRemoteMetadataFrom257() {
        assert "Recorded | IsRemoteMetadata".equals(Activity.formatActivityTraceFlags("257"));
    }

    @Test
    public void mapsNotRecordedWithIsRemoteMetadataFrom256() {
        assert "NotRecorded | IsRemoteMetadata".equals(Activity.formatActivityTraceFlags("256"));
    }

    @Test
    public void keepsUnrecognizedText() {
        assert "abc".equals(Activity.formatActivityTraceFlags("abc"));
    }
}



