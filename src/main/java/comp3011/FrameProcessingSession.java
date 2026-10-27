/*
 * COMP3011 Assignment 2 - Concurrent Video Player
 *
 * Author: Ayush Chauhan
 * Student ID: a1870640
 *
 * Coordinates background frame-processing sessions,
 * worker ownership and graceful lifecycle management.
 */


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
    
    // gives each executor thread its own effect pipeline, avoiding
    // concurrent access to mutable FrameProcessor instances.
    private final ThreadLocal<FrameProcessingWorker> localWorker;

    private boolean accepting = true;
    private boolean disposed = false;

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

    // submits an independently owned frame without blocking the caller.
    public boolean submit(VideoFrameJob job) {

        Objects.requireNonNull(job, "job");

        if (!accepting) {
            throw new IllegalStateException(
                "Processing session no longer accepts submissions"
            );
        }

        return queue.submit(() -> {
        	try {
                return localWorker.get().process(job);
            } catch (Exception | Error failure) {
                job.frame().close();
                throw failure;
            }
        });
    }

    // retrieve only next completed frame in order
    public Optional<ProcessedVideoFrame> pollReady() {

    	if (disposed) {
    	    return Optional.empty();
    	}

        return queue.pollReady();
    }
    
    // returns the number of processing results still awaiting retrieval.
    public int pendingCount() {
        return queue.pendingCount();
    }
    
    // returns true when no submitted results remain to be retrieved.
    public boolean isEmpty() {
        return queue.isEmpty();
    }
    
    // prevents additional submissions while allowing existing jobs to finish.
    public void finishSubmitting() {
        if (!accepting) {
            return;
        }

        accepting = false;
        queue.finishSubmitting();
    }

    // returns true once every processing task has terminated.
    public boolean isTerminated() {
        return queue.isTerminated();
    }
    
    // returns true when session stopped accepting jobs
    public boolean isDrained() {
        return !accepting && queue.isEmpty() && queue.isTerminated();
    }
    
    // dispose worker pipelines after all processing terminated
    public void disposeIfDrained() {

        if (disposed || !isDrained()) {
            return;
        }

        for (FrameProcessingWorker worker : workers) {
            worker.close();
        }

        disposed = true;
    }

    // invalidates outstanding jobs and requests executor shutdown
    @Override
    public void close() {

        finishSubmitting();
    }
}
