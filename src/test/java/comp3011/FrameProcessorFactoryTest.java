package comp3011;


import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class FrameProcessorFactoryTest {
	// create correct processor
	@Test
	void correctProc() {
		FrameProcessor processor = FrameProcessorFactory.create("d");
		
		assertInstanceOf(FrameDuster.class, processor);
	}
	
	// repeated effect
	@Test
	void sameEffect() {
		FrameProcessor first = FrameProcessorFactory.create("d");
        FrameProcessor second = FrameProcessorFactory.create("d");
        
        assertInstanceOf(FrameDuster.class, first);
        assertInstanceOf(FrameDuster.class, second);
        
        // both must occupy different memory identities
        assertNotSame(first, second);
	}
	
	// invalid code 
	@Test
	void invalidCode() {
		assertThrows(
			IllegalArgumentException.class,
			() -> FrameProcessorFactory.create("z")
		);
	}
}
