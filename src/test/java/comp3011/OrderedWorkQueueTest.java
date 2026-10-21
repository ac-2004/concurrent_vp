package comp3011;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class OrderedWorkQueueTest {

	// jobs should return in submission order
	@Test
    void resultsAreReturnedInSubmissionOrder() throws Exception {

        try (OrderedWorkQueue<Integer> queue =
                new OrderedWorkQueue<>(2, 2)) {

            CountDownLatch releaseFirst = new CountDownLatch(1);
            CountDownLatch secondCompleted = new CountDownLatch(1);

            // first task cannot finish until we explicitly release it
            assertTrue(queue.submit(() -> {
                releaseFirst.await();
                return 100;
            }));

            // second task can finish independently.
            assertTrue(queue.submit(() -> {
                secondCompleted.countDown();
                return 101;
            }));

            try {
                // confirm the second task has reached completion.
                assertTrue(secondCompleted.await(2, TimeUnit.SECONDS));
                
                assertEquals(Optional.empty(), queue.pollReady());

            } finally {
                // always release the worker, even if an assertion fails.
                releaseFirst.countDown();
            }

            // repeatedly check for the first result, with a deadline.
            Optional<Integer> first = Optional.empty();
            long deadline = System.nanoTime() +
                    TimeUnit.SECONDS.toNanos(2);

            while (first.isEmpty() && System.nanoTime() < deadline) {
                first = queue.pollReady();
                Thread.onSpinWait();
            }

            assertEquals(Optional.of(100), first);

            // second result must follow the first.
            assertEquals(Optional.of(101), queue.pollReady());
        }
    }
	
	// capacity enforced?
	@Test
	void queueDoesNotExceedCapacity() throws Exception {
	
	    try (OrderedWorkQueue<Integer> queue =
	            new OrderedWorkQueue<>(1, 2)) {
	
	        CountDownLatch releaseWorker = new CountDownLatch(1);
	
	        try {
	            assertTrue(queue.submit(() -> {
	                releaseWorker.await();
	                return 1;
	            }));
	
	            assertTrue(queue.submit(() -> 2));
	
	            // both permits are occupied.
	            assertFalse(queue.submit(() -> 3));
	
	        } finally {
	            releaseWorker.countDown();
	        }
	    }
	}
	
	// closed queues rejects submissions
	@Test
	void closedQueueRejectsNewWork() {
	
	    OrderedWorkQueue<Integer> queue =
	            new OrderedWorkQueue<>(2, 4);
	
	    queue.close();
	
	    assertThrows(
	        IllegalStateException.class,
	        () -> queue.submit(() -> 10)
	    );
	
	    // closing an already closed queue must remain safe.
	    assertDoesNotThrow(queue::close);
	}


}
