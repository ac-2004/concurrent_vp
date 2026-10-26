package comp3011;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;

import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

// bounded concurrent work queue. 
public final class OrderedWorkQueue<T> implements AutoCloseable {
	// execute submitted processing tasks concurrently
	private final ExecutorService executor;
	
	// store submitted jobs in original order
	private final Queue<Future<T>> pending;
	
	// limits number of outstanding processing jobs
	private final Semaphore capacity;
	
	// prevents further submissions after the queue has been closed.
	private boolean closed = false;
	
	// create concurrent queue with fixed number of workers
	public OrderedWorkQueue(int workerCount, int maxPending) {
		if (workerCount < 1) {
			throw new IllegalArgumentException(
				"Workers must be at least 1");
		}
		
		if (maxPending < workerCount) {
            throw new IllegalArgumentException(
                "Queue capacity must be at least the worker count"
            );
		}
		
		this.executor = Executors.newFixedThreadPool(workerCount);
		this.pending = new ArrayDeque<>();
        this.capacity = new Semaphore(maxPending);
	}
	
	public boolean submit(Callable<T> task) {
		Objects.requireNonNull(task, "task");
		
		if (closed) {
	        throw new IllegalStateException("Work queue is closed");
	    }
		
		// Do not block the caller when the queue is full.
	    if (!capacity.tryAcquire()) {
	        return false;
	    }
	    
	    try {
	    	// submit task to worker pool
	    	Future<T> future = executor.submit(task);
	    	
	    	// store Future in submission order
	    	pending.add(future);
	    	
	    	return true;
	    } catch (RejectedExecutionException e) {
	    	// submission failed: return permit
	    	capacity.release();
	        throw e;
	    }
	}
	
	// retrieve oldest submitted result, provided its doen
	// results returned in submission order, regardless of order in which they are finished
	public Optional<T> pollReady() {
		// look at oldest outstanding job without removing it
		Future<T> future = pending.peek();
		
		if (future == null || !future.isDone()) {
			return Optional.empty();
		}
		
		// oldest job done, remove it
		pending.remove();
		
		try {
			return Optional.of(future.get());
		} catch (InterruptedException e) {
			// preserve the thread's interrupted status.
	        Thread.currentThread().interrupt();

	        throw new CompletionException(e);
		} catch (ExecutionException e) {
			// propagate the original task failure to the caller.
	        throw new CompletionException(e.getCause());
		} catch (CancellationException e) {
	        // cancelled task has no usable processing result.
	        throw new CompletionException(e);
		} finally {
			// all jobs removed must release capacity permit
			capacity.release();
		}
	}
	
	// return number of submitted jobs not yet retrieved
	public int pendingCount() {
	    return pending.size();
	}
	
	// every submitted result retrieved
	public boolean isEmpty() {
	    return pending.isEmpty();
	}
	
	// cancel work from previous position that may be obsolete
	public void cancelPending() {

	    Future<T> future;

	    while ((future = pending.poll()) != null) {

	        // request interruption if the task is currently running.
	        future.cancel(true);

	        // return the permit associated with this queued job.
	        capacity.release();
	    }
	}
	
	// stops accepting new work while allowing submitted jobs to finish.
	public void finishSubmitting() {

	    if (closed) {
	        return;
	    }

	    closed = true;
	    executor.shutdown();
	}
	
	// returns true when all executor tasks have actually terminated.
	public boolean isTerminated() {
	    return executor.isTerminated();
	}
	
	// stop accepting new work, cancels outstanding tasks and requests
	@Override
	public void close() {

	    if (closed) {
	        return;
	    }

	    closed = true;

	    cancelPending();

	    // request interruption of running workers and discard queued work.
	    executor.shutdownNow();
	}
}
