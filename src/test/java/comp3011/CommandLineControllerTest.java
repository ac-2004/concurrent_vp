package comp3011;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

public class CommandLineControllerTest {
	
	// test command line parser:
	// preserving order and repitition
	@Test
	void abbrevOptsPreserveOrderDup() {
		// arrange part
		String[] args = {"-jfddn", "--help"};
		
		// define exact sequence
		List<String> expected = List.of("j", "f", "d", "d", "n");
		
		// act part: create controller
		CommandLineController controller = new CommandLineController(args);
		
		// assert part: compare
		assertEquals(expected, controller.getRequestedEffects());
	}
	
	// testing long form options
	@Test
	void longOpts() {
		String[] args = {"--dust-frames", "--number-frames", "--help"};
		
		List<String> expected = List.of("d", "n");
		
		CommandLineController controller = new CommandLineController(args);
		
		assertEquals(expected, controller.getRequestedEffects());
	}
	
	// testing repeated effects captuer
	@Test
	void repeatedEffects() {
		String[] args = {"-d", "-d", "-d", "--help"};
		
		List<String> expected = List.of("d", "d", "d");
		
		CommandLineController controller = new CommandLineController(args);
		
		assertEquals(expected, controller.getRequestedEffects());
	}
	
	// mixed, short and long form
	@Test
	void mixedOptions() {
		String[] args = {"-jf", "--dust-frames", "-d", "--help"};
	
		List<String> expected = List.of("j", "f", "d", "d");
		
		CommandLineController controller = new CommandLineController(args);
		
		assertEquals(expected, controller.getRequestedEffects());
	}
	
	// unkown effect
	@Test
	void invalidOption() {
		String[] args = {"--random-effect", "--help"};
		
		CommandLineController controller = new CommandLineController(args);

		assertEquals(
			"unknown option: --random-effect",
			controller.getErrorMessage()
		);
		
		assertEquals(1, controller.getExitCode());
	}
	
	// invalid characters in short form options
	@Test
	void invalidShortOptions() {
		String[] args = {"-jfz", "--help"};
		
		CommandLineController controller = new CommandLineController(args);
		
		assertEquals(
			"unknown option: -z",
			controller.getErrorMessage()
		);
		
		assertEquals(1, controller.getExitCode());
	}
	
	// verifying all 11 frame-processing options are recognised and stored in the same order as requested.
	@Test
	void optionsRecognised() {
		// arrange part
		String[] args = {
			"--number-frames",
	        "--scratch-frames",
	        "--flicker-frames",
	        "--black-and-white",
	        "--yellow-frames",
	        "--vignette-frames",
	        "--dust-frames",
	        "--jitter-frames",
	        "--mottle-frames",
	        "--bleed-frames",
	        "--pepper-frames",
	        "--help"
		};
		
		// expected
		List<String> expected = List.of(
	        "n", "s", "f", "w", "y", "v", "d", "j", "m", "b", "p"
	    );
		
		// act
		CommandLineController controller = new CommandLineController(args);
		
		// assert
		assertEquals(expected, controller.getRequestedEffects());
	}
	
	// verify complete stacked argument from assignment is parsed correctly
	@Test
	void fullArg() {
		// arrange
		String[] args = {
	        "-nssnfwyvdjmbp",
	        "--help"
	    };
		
		List<String> expected = List.of(
	        "n", "s", "s", "n", "f", "w",
	        "y", "v", "d", "j", "m", "b", "p"
	    );
		
		CommandLineController controller = new CommandLineController(args);
		
		assertEquals(expected, controller.getRequestedEffects());
	}
	
	// making sure original options still work
	@Test
	void originalOptionsTest() {
		String[] args = {
	        "-a",              // Enable audio
	        "-x",              // Maximise player
	        "-jf",             // Jitter and flicker
	        "--dust-frames",   // Dust
	        "--help"
	    };
		
		CommandLineController controller = new CommandLineController(args);
		
		assertEquals(
	        List.of("j", "f", "d"),
	        controller.getRequestedEffects()
	    );
		
		assertNull(controller.getErrorMessage());
	}
}

