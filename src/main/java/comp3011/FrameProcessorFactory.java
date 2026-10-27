package comp3011;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// separately creating frame processing effects so they can be repeated in the processing sequence. 

public class FrameProcessorFactory {
	// prevent unecessary instantiation
	private FrameProcessorFactory() {
	}
	
	/**
	 * Creates a FrameProcessor for a command-line effect code.
	 *
	 * To add a new effect:
	 * 1. Implement the FrameProcessor interface.
	 * 2. Add its command-line code to the supported effect options.
	 * 3. Add a case here to construct the processor.
	 * 4. Update the command-line help and parsing tests.
	 *
	 * Effects are created in command-line order. Stateful processors
	 * must be independently instantiated for each worker pipeline.
	 */
	
	// create frame processor according to supplied identifier
	public static FrameProcessor create(String code) {

	    return switch (code) {
	        case "n" -> new FrameNumberer();
	        case "s" -> new FrameScratcher();
	        case "f" -> new FrameFlickerer();
	        case "w" -> new FrameBlackAndWhiter();
	        case "y" -> new FrameYellower();
	        case "v" -> new FrameVignetter();
	        case "d" -> new FrameDuster();
	        case "j" -> new FrameJitterer();
	        case "m" -> new FrameMottler();
	        case "b" -> new FrameBleeder();
	        case "p" -> new FramePepperer();

	        default -> throw new IllegalArgumentException(
	            "Unknown frame processor: " + code
	        );
	    };
	}
	
	// explicit-identity factory
	public static FrameProcessor create(String code, int occurrenceIndex) {
		if (occurrenceIndex < 0) {
	        throw new IllegalArgumentException(
	            "Occurrence index cannot be negative"
	        );
	    }
		
		return switch (code) {
			case "n" -> new FrameNumberer();
            case "w" -> new FrameBlackAndWhiter();
            case "y" -> new FrameYellower();
            case "v" -> new FrameVignetter();
            case "b" -> new FrameBleeder();
            case "s" -> new FrameScratcher(occurrenceIndex);
            case "f" -> new FrameFlickerer(occurrenceIndex);
            case "d" -> new FrameDuster(occurrenceIndex);
            case "j" -> new FrameJitterer(occurrenceIndex);
            case "m" -> new FrameMottler(occurrenceIndex);
            case "p" -> new FramePepperer(occurrenceIndex);
            
            default -> throw new IllegalArgumentException(
        		"unknown frame processor: " + code
        	);
		};
	}
	
	// builds an ordered processor sequence with deterministic identities.
	public static List<FrameProcessor> createPipeline(
	        List<String> effectCodes) {

	    Objects.requireNonNull(effectCodes, "effectCodes");

	    List<FrameProcessor> processors = new ArrayList<>();

	    // track the number of occurrences of each effect independently.
	    Map<String, Integer> occurrences = new HashMap<>();

	    for (String code : effectCodes) {

	        // first occurrence receives index 0; the next receives index 1.
	        int index = occurrences.getOrDefault(code, 0);

	        processors.add(create(code, index));

	        occurrences.put(code, index + 1);
	    }

	    return List.copyOf(processors);
	}
}
