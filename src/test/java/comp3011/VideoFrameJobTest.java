
package comp3011;

import static org.junit.jupiter.api.Assertions.*;

import org.bytedeco.javacv.Frame;
import org.junit.jupiter.api.Test;

class VideoFrameJobTest {

    // Verifies that a job retains its frame identity and playback timing.
    @Test
    void jobPreservesMetadata() {

        Frame frame = new Frame();
        InfoFrame info = new InfoFrame(100, 4_000_000L);

        VideoFrameJob job = new VideoFrameJob(
                100,
                2,
                frame,
                info,
                4_000_000L,
                4_000_000L,
                8_000_000_000L
        );

        assertEquals(100, job.sequenceNumber());
        assertEquals(2, job.generation());
        assertSame(frame, job.frame());
        assertSame(info, job.info());
        assertEquals(4_000_000L, job.mediaTimestampUs());
        assertEquals(8_000_000_000L, job.targetTimeNs());
    }

    // Verifies that jobs reject invalid sequence and generation identifiers.
    @Test
    void invalidJobIdentifiersAreRejected() {

        Frame frame = new Frame();
        InfoFrame info = new InfoFrame(1, 0);

        assertThrows(
                IllegalArgumentException.class,
                () -> new VideoFrameJob(-1, 0, frame, info, 0, 0, 0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new VideoFrameJob(1, -1, frame, info, 0, 0, 0)
        );
    }
}
