package comp3011;


import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

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
	
	// verifies that the factory constructs all eleven processor types in order.
	@Test
	void createsAllProcessorTypesInOrder() {

	    List<FrameProcessor> processors =
	            FrameProcessorFactory.createPipeline(
	                List.of("n", "s", "f", "w", "y", "v",
	                        "d", "j", "m", "b", "p")
	            );

	    assertEquals(11, processors.size());

	    assertInstanceOf(FrameNumberer.class, processors.get(0));
	    assertInstanceOf(FrameScratcher.class, processors.get(1));
	    assertInstanceOf(FrameFlickerer.class, processors.get(2));
	    assertInstanceOf(FrameBlackAndWhiter.class, processors.get(3));
	    assertInstanceOf(FrameYellower.class, processors.get(4));
	    assertInstanceOf(FrameVignetter.class, processors.get(5));
	    assertInstanceOf(FrameDuster.class, processors.get(6));
	    assertInstanceOf(FrameJitterer.class, processors.get(7));
	    assertInstanceOf(FrameMottler.class, processors.get(8));
	    assertInstanceOf(FrameBleeder.class, processors.get(9));
	    assertInstanceOf(FramePepperer.class, processors.get(10));
	}
	
	// verifies that equivalent pipelines never share processor instances.
	@Test
	void separatePipelinesHaveIndependentProcessors() {

	    List<String> effects = List.of("d", "d", "f", "j");

	    List<FrameProcessor> first =
	            FrameProcessorFactory.createPipeline(effects);

	    List<FrameProcessor> second =
	            FrameProcessorFactory.createPipeline(effects);

	    assertEquals(first.size(), second.size());

	    for (int i = 0; i < first.size(); i++) {
	        assertEquals(first.get(i).getClass(), second.get(i).getClass());
	        assertNotSame(first.get(i), second.get(i));
	    }
	}
	
	// verifies that negative effect-occurrence indices are rejected.
	@Test
	void negativeOccurrenceIndexIsRejected() {

	    assertThrows(
	        IllegalArgumentException.class,
	        () -> FrameProcessorFactory.create("d", -1)
	    );
	}
	

	// verifies that explicit effect identities are preserved across pipelines.
	@Test
	void concurrentPipelinesUseConsistentEffectIdentities() {

	    List<FrameProcessor> first =
	            FrameProcessorFactory.createPipeline(
	                    List.of("s", "s", "f", "d", "m", "p", "j")
	            );

	    List<FrameProcessor> second =
	            FrameProcessorFactory.createPipeline(
	                    List.of("s", "s", "f", "d", "m", "p", "j")
	            );

	    assertEquals(first.size(), second.size());

	    for (int i = 0; i < first.size(); i++) {
	        assertEquals(first.get(i).getClass(), second.get(i).getClass());
	        assertNotSame(first.get(i), second.get(i));
	    }
	}

}
