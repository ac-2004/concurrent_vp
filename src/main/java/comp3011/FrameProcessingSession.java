
package comp3011;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

// coordinates bounded, ordered background processing of video frames
public final class FrameProcessingSession implements AutoCloseable {

    private final OrderedWorkQueue<ProcessedVideoFrame> queue;

    private final ConcurrentLinkedQueue<FrameProcessingWorker> workers =
            new ConcurrentLinkedQueue<>();

    private final ThreadLocal<FrameProcessingWorker> localWorker;

    private volatile boolean closed;

    public FrameProcessingSession(
            List<String> effectCodes,
            InfoVideo videoInfo,
            int workerCount,
            int maximumPendingFrames) {

        Objects.requireNonNull(effectCodes, "effectCodes");
        Objects.requireNonNull(videoInfo, "videoInfo");

        List<String> configuration = List.copyOf(effectCodes);

        queue = new OrderedWorkQueue<>(
                workerCount,
                maximumPendingFrames
        );

        localWorker = ThreadLocal.withInitial(() -> {
            try {
                FrameProcessingWorker worker =
                        new FrameProcessingWorker(configuration, videoInfo);

                workers.add(worker);
                return worker;

            } catch (Exception e) {
                throw new IllegalStateException(
                        "Unable to initialise frame-processing worker", e
                );
            }
        });
    }

    // submits frame foro background processing without blocking caller
    public boolean submit(VideoFrameJob job) {

        Objects.requireNonNull(job, "job");

        if (closed) {
            throw new IllegalStateException(
                    "Processing session is closed"
            );
        }

        return queue.submit(() -> {
            return localWorker.get().process(job);
        });
    }

    // retrieve only next completed frame in order
    public Optional<ProcessedVideoFrame> pollReady() {

        if (closed) {
            return Optional.empty();
        }

        return queue.pollReady();
    }

    // invalidates outstanding jobs and requests executor shutdown
    @Override
    public void close() {

        if (closed) {
            return;
        }

        closed = true;
        queue.close();
    }
}
