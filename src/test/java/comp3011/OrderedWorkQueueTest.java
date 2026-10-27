package comp3011;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;

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

	// Verifies that the pending count includes submitted jobs awaiting retrieval.
	@Test
	void pendingCountTracksSubmittedJobs() throws Exception {

	    try (OrderedWorkQueue<Integer> queue =
	            new OrderedWorkQueue<>(2, 4)) {

	        assertEquals(0, queue.pendingCount());

	        assertTrue(queue.submit(() -> 10));
	        assertTrue(queue.submit(() -> 20));

	        assertEquals(2, queue.pendingCount());
	        assertFalse(queue.isEmpty());
	    }
	}
	
	// Verifies that a newly created work queue contains no pending jobs.
	@Test
	void newQueueIsEmpty() {

	    try (OrderedWorkQueue<Integer> queue =
	            new OrderedWorkQueue<>(2, 4)) {

	        assertTrue(queue.isEmpty());
	        assertEquals(0, queue.pendingCount());
	    }
	}
	

	// Verifies that graceful shutdown preserves previously submitted results.
	@Test
	void gracefulShutdownAllowsPendingResultsToFinish() throws Exception {

	    try (OrderedWorkQueue<Integer> queue =
	            new OrderedWorkQueue<>(2, 4)) {

	        assertTrue(queue.submit(() -> 10));
	        assertTrue(queue.submit(() -> 20));

	        queue.finishSubmitting();

	        assertThrows(
	                IllegalStateException.class,
	                () -> queue.submit(() -> 30)
	        );

	        List<Integer> results = new ArrayList<>();

	        long deadline = System.nanoTime()
	                + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);

	        while (results.size() < 2 && System.nanoTime() < deadline) {

	            queue.pollReady().ifPresent(results::add);

	            if (results.size() < 2) {
	                Thread.sleep(1);
	            }
	        }

	        assertEquals(List.of(10, 20), results);
	        assertTrue(queue.isEmpty());
	    }
	}
	

    // verifies that a failed task does not prevent later result retrieval.
    @Test
    void failedTaskDoesNotBlockFollowingResults() throws Exception {

        try (OrderedWorkQueue<Integer> queue =
                     new OrderedWorkQueue<>(2, 2)) {

            assertTrue(queue.submit(() -> {
                throw new IllegalStateException("Processing failed");
            }));

            assertTrue(queue.submit(() -> 42));

            long deadline = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(5);

            boolean failureObserved = false;

            while (!failureObserved && System.nanoTime() < deadline) {
                try {
                    queue.pollReady();
                } catch (CompletionException failure) {
                    assertInstanceOf(
                            IllegalStateException.class,
                            failure.getCause()
                    );
                    failureObserved = true;
                }

                if (!failureObserved) {
                    Thread.sleep(1);
                }
            }

            assertTrue(failureObserved);

            Optional<Integer> next = Optional.empty();

            while (next.isEmpty() && System.nanoTime() < deadline) {
                next = queue.pollReady();

                if (next.isEmpty()) {
                    Thread.sleep(1);
                }
            }

            assertEquals(Optional.of(42), next);
            assertTrue(queue.isEmpty());

            queue.finishSubmitting();
        }
    }


	
}
