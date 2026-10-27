package comp3011;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.bytedeco.javacv.Frame;

class FrameProcessingSessionTest {

	// verifies that a null effect configuration is rejected.
	@Test
	void rejectsNullEffectConfiguration() {

	    InfoVideo info = new InfoVideo(
	            "test",
	            1,
	            32,
	            24,
	            Frame.DEPTH_UBYTE,
	            3,
	            32 * 3,
	            25.0,
	            25,
	            40000,
	            0
	    );

	    assertThrows(
	            NullPointerException.class,
	            () -> new FrameProcessingSession(null, info, 2, 4)
	    );
	}

    // verifies that submitted frames finish and drain after session closure.
    @Test
    void drainsOutstandingFramesAfterFinishSubmitting() throws Exception {

        InfoVideo info = new InfoVideo(
                "session-test",
                4,
                32,
                24,
                Frame.DEPTH_UBYTE,
                3,
                32 * 3,
                25.0,
                25,
                40000,
                0
        );

        try (FrameProcessingSession session =
                     new FrameProcessingSession(
                             List.of(),
                             info,
                             2,
                             4
                     )) {

            for (int i = 0; i < 4; i++) {

                Frame frame = new Frame(
                        32, 24, Frame.DEPTH_UBYTE, 3
                );

                VideoFrameJob job = new VideoFrameJob(
                        i,
                        0,
                        frame,
                        new InfoFrame(i, i * 40000L),
                        i * 40000L,
                        i * 40000L,
                        0
                );

                assertTrue(session.submit(job));
            }

            session.finishSubmitting();


            try (Frame rejectedFrame =
                         new Frame(32, 24, Frame.DEPTH_UBYTE, 3)) {

                VideoFrameJob rejectedJob = new VideoFrameJob(
                        4,
                        0,
                        rejectedFrame,
                        new InfoFrame(4, 160000L),
                        160000L,
                        160000L,
                        0
                );

                assertThrows(
                        IllegalStateException.class,
                        () -> session.submit(rejectedJob)
                );
            }


            int completed = 0;

            long deadline = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(10);

            while (completed < 4 && System.nanoTime() < deadline) {

                var ready = session.pollReady();

                if (ready.isPresent()) {
                    ProcessedVideoFrame result = ready.get();

                    try {
                        assertEquals(
                                completed,
                                result.sequenceNumber()
                        );
                    } finally {
                        result.frame().close();
                    }

                    completed++;
                } else {
                    Thread.sleep(1);
                }
            }

            assertEquals(4, completed);

            while (!session.isDrained()
                    && System.nanoTime() < deadline) {
                Thread.sleep(1);
            }

            assertTrue(session.isDrained());

            session.disposeIfDrained();
        }
    }

}
