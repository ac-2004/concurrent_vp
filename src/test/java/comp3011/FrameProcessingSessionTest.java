package comp3011;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FrameProcessingSessionTest {

	// verifies that a processing session rejects a null effect configuration.
	@Test
	void rejectsNullEffectConfiguration() {

	    assertThrows(
	        NullPointerException.class,
	        () -> new FrameProcessingSession(null, null, 2, 4)
	    );
	}
}
