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
}

