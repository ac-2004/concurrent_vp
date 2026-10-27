
package comp3011;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.bytedeco.javacv.Frame;
import org.junit.jupiter.api.Test;

/**
 * Compares single-worker and four-worker frame processing.
 *
 * Measures effect processing throughput, excluding video decoding
 * and JavaFX display. Uses an identical synthetic workload.
 */
public class PerformanceComparisonIT {

    private static final int FRAME_COUNT = 80;
    private static final int WIDTH = 160;
    private static final int HEIGHT = 90;

    private static final List<String> EFFECTS =
            "sfwyvdjmbppbmjdvywfs".chars()
                    .mapToObj(c -> Character.toString((char) c))
                    .toList();

    // compares processing throughput and verifies ordered frame delivery.
    @Test
    void compareSingleWorkerWithFourWorkers() throws Exception {

        InfoVideo videoInfo = new InfoVideo(
                "synthetic-benchmark",
                FRAME_COUNT,
                WIDTH,
                HEIGHT,
                Frame.DEPTH_UBYTE,
                3,
                WIDTH * 3,
                25.0,
                25,
                40000,
                0
        );

        // warm up processing before measuring performance.
        measure(videoInfo, 1, 12);
        measure(videoInfo, 4, 12);

        long singleNs = measure(videoInfo, 1, FRAME_COUNT);
        long parallelNs = measure(videoInfo, 4, FRAME_COUNT);

        double singleFps = FRAME_COUNT * 1e9 / singleNs;
        double parallelFps = FRAME_COUNT * 1e9 / parallelNs;
        double improvement = 100.0 * parallelFps / singleFps;

        System.out.println();
        System.out.println("========== PERFORMANCE COMPARISON ==========");

        System.out.printf("Video frames: %d%n", FRAME_COUNT);
        System.out.printf("Resolution: %dx%d%n", WIDTH, HEIGHT);
        System.out.printf("Effects per frame: %d%n", EFFECTS.size());

        System.out.printf("Single-worker FPS: %.2f%n", singleFps);
        System.out.printf("Four-worker FPS: %.2f%n", parallelFps);
        System.out.printf("Relative performance: %.2f%%%n", improvement);

        System.out.println("============================================");

        assertTrue(singleNs > 0);
        assertTrue(parallelNs > 0);
    }

    // processes a fixed workload and returns its elapsed time.
    private static long measure(
            InfoVideo videoInfo,
            int workerCount,
            int frameCount) throws Exception {

        try (FrameProcessingSession session =
                     new FrameProcessingSession(
                             EFFECTS,
                             videoInfo,
                             workerCount,
                             8
                     )) {

            int submitted = 0;
            int completed = 0;

            long start = System.nanoTime();
            long deadline =
                    start + TimeUnit.MINUTES.toNanos(3);

            while (completed < frameCount) {

                if (System.nanoTime() > deadline) {
                    throw new AssertionError(
                            "Performance benchmark timed out"
                    );
                }

                // submit more frames while capacity is available.
                if (submitted < frameCount
                        && session.pendingCount() < 8) {

                    Frame frame = new Frame(
                            WIDTH,
                            HEIGHT,
                            Frame.DEPTH_UBYTE,
                            3
                    );

                    fillFrame(frame, submitted);

                    int frameNumber = submitted;

                    VideoFrameJob job = new VideoFrameJob(
                            frameNumber,
                            0,
                            frame,
                            new InfoFrame(
                                    frameNumber,
                                    frameNumber * 40000L
                            ),
                            frameNumber * 40000L,
                            frameNumber * 40000L,
                            0
                    );

                    if (session.submit(job)) {
                        submitted++;
                    } else {
                        frame.close();
                    }
                }

                // retrieve processed frames in submission order.
                Optional<ProcessedVideoFrame> ready =
                        session.pollReady();

                if (ready.isPresent()) {

                    ProcessedVideoFrame result = ready.get();

                    try {
                        assertEquals(
                                completed,
                                result.sequenceNumber(),
                                "Frames must be returned in order"
                        );
                    } finally {
                        result.frame().close();
                    }

                    completed++;

                } else {
                    Thread.onSpinWait();
                }
            }

            long elapsed = System.nanoTime() - start;

            // allow processing workers to finish cleanly.
            session.finishSubmitting();

            long cleanupDeadline =
                    System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(10);

            while (!session.isDrained()
                    && System.nanoTime() < cleanupDeadline) {
                Thread.onSpinWait();
            }

            assertTrue(
                    session.isDrained(),
                    "Processing session must drain correctly"
            );

            session.disposeIfDrained();

            assertEquals(frameCount, submitted);

            return elapsed;
        }
    }

    //fills each frame with deterministic image data.
    private static void fillFrame(Frame frame, int frameNumber) {

        ByteBuffer pixels = (ByteBuffer) frame.image[0];

        for (int i = 0; i < pixels.capacity(); i++) {
            pixels.put(
                    i,
                    (byte) ((i * 31 + frameNumber * 17) & 0xff)
            );
        }
    }
}
