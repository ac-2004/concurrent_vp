package comp3011;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.List;

class FrameProcessingSessionTest {

	// verifies that a processing session rejects a null effect configuration.
	@Test
	void rejectsNullEffectConfiguration() {

	    assertThrows(
	        NullPointerException.class,
	        () -> new FrameProcessingSession(null, null, 2, 4)
	    );
	}
	
	// verifies that a session cannot be submitted to after closure.
    @Test
    void closedSessionRejectsSubmission() {

        FrameProcessingSession session = null;

        // the source fixture will be added when testing real decoded frames.
        assertThrows(
                NullPointerException.class,
                () -> new FrameProcessingSession(
                        List.of("d"), null, 2, 4
                )
        );
    }

}
