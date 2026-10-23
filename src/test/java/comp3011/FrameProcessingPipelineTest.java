
package comp3011;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class FrameProcessingPipelineTest {

    // Verifies that frames cannot be processed before pipeline initialisation.
    @Test
    void processingBeforeInitialisationIsRejected() {

        try (FrameProcessingPipeline pipeline =
                new FrameProcessingPipeline(List.of())) {

            assertThrows(
                IllegalStateException.class,
                () -> pipeline.process(null, null)
            );
        }
    }

    // Verifies that a closed pipeline cannot be initialised.
    @Test
    void closedPipelineRejectsInitialisation() {

        FrameProcessingPipeline pipeline =
                new FrameProcessingPipeline(List.of());

        pipeline.close();

        assertThrows(
            IllegalStateException.class,
            () -> pipeline.initialise(null)
        );
    }

    // Verifies that closing a pipeline multiple times is safe.
    @Test
    void closingPipelineTwiceIsSafe() {

        FrameProcessingPipeline pipeline =
                new FrameProcessingPipeline(List.of());

        assertDoesNotThrow(pipeline::close);
        assertDoesNotThrow(pipeline::close);
    }
}
