package comp3011;

import java.util.List;
import java.util.Objects;

import org.bytedeco.javacv.Frame;


// owns processing resources used to prepare frames
public final class FrameProcessingPipeline implements AutoCloseable {
	// processors applied in order
	private final List<FrameProcessor> processors;
	
	// tracks whether processor initialisation has occurred.
    private boolean initialised;

    private boolean closed;
    
    // constructs pipeline using supplied processor instances
    public FrameProcessingPipeline(List<FrameProcessor> processors) {

        this.processors = List.copyOf(
            Objects.requireNonNull(processors, "processors")
        );

        this.initialised = false;
        this.closed = false;
    }
    
    // initialises every configured effect using source video metadata
    public void initialise(InfoVideo videoInfo) throws Exception {

        if (closed) {
            throw new IllegalStateException("Pipeline has been closed");
        }

        if (initialised) {
            throw new IllegalStateException(
                "Pipeline has already been initialised"
            );
        }

        Objects.requireNonNull(videoInfo, "videoInfo");

        // preserve the exact command-line effect order.
        for (FrameProcessor processor : processors) {
            processor.initialise(videoInfo);
        }

        initialised = true;
    }
    
    // applies the configured effects to an exclusively owned frame and converts result into a displayable JavaFX img.
    public Frame process(Frame frame, InfoFrame info) throws Exception {

        if (closed) {
            throw new IllegalStateException("Pipeline has been closed");
        }

        if (!initialised) {
            throw new IllegalStateException(
                "Pipeline must be initialised before processing"
            );
        }

        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(info, "info");

        // apply effects sequentially to preserve compositing order.
        for (FrameProcessor processor : processors) {
            processor.process(frame, info);
        }

        return frame;
    }
    
    // process independently owned video-frame job
    public ProcessedVideoFrame process(VideoFrameJob job)
            throws Exception {

        Objects.requireNonNull(job, "job");

        Frame processedFrame = process(job.frame(), job.info());

        return new ProcessedVideoFrame(
                job.sequenceNumber(),
                job.generation(),
                processedFrame,
                job.info(),
                job.mediaTimestampUs(),
                job.logicalTimestampUs(),
                job.targetTimeNs(),
                System.nanoTime()
        );
    }
    
    // release conversion resources owned by pipeline
    @Override
    public void close() {

        if (closed) {
            return;
        }

        closed = true;
    }
}
