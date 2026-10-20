package comp3011;

// separately creating frame processing effects so they can be repeated in the processing sequence. 

public class FrameProcessorFactory {
	// prevent unecessary instantiation
	private FrameProcessorFactory() {
	}
	
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
        		"unknown frame processor: " + code
        	);
		};
	}
}
