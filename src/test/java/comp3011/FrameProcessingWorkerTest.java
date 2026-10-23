
package comp3011;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class FrameProcessingWorkerTest {

    // verifies that null effect configurations are rejected.
    @Test
    void rejectsNullEffectConfiguration() {

        assertThrows(
                NullPointerException.class,
                () -> new FrameProcessingWorker(null, null)
        );
    }
}
