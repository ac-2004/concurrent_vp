
package comp3011;

import java.util.List;
import java.util.Objects;

// owns an independent frame-processing pipeline.
public final class FrameProcessingWorker implements AutoCloseable {

    private final FrameProcessingPipeline pipeline;

    // constructs and initialises this worker's independent processors.
    public FrameProcessingWorker(
            List<String> effectCodes,
            InfoVideo videoInfo) throws Exception {

        Objects.requireNonNull(effectCodes, "effectCodes");
        Objects.requireNonNull(videoInfo, "videoInfo");

        pipeline = new FrameProcessingPipeline(
                FrameProcessorFactory.createPipeline(effectCodes)
        );

        pipeline.initialise(videoInfo);
    }

    // processes one video job using this worker's independent pipeline.
    public ProcessedVideoFrame process(VideoFrameJob job)
            throws Exception {

        return pipeline.process(job);
    }

    // closes this worker's processing pipeline.
    @Override
    public void close() {
        pipeline.close();
    }
}
