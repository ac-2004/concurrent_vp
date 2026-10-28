/*
 * Starter code supplied for Adelaide University COMP3011 Assignment 2.
 * Students are free to modify this file for assessment purposes.
 * 
 * Authors:
 *   1. Simon Ratcliffe, in collaboration with GPT-5.6 Terra
 *   2. Ayush Chauhan, a1870640
 *
 * Copyright 2026 Simon Ratcliffe
 */
package comp3011;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.JavaFXFrameConverter;

import javafx.animation.AnimationTimer;
import javafx.scene.image.Image;


/**
 * Coordinates video playback within the model-view-controller architecture.
 *
 * <p>
 * Owns FFmpeg decoding, playback timing, audio scheduling, seeking and
 * presentation coordination. Computationally expensive frame effects
 * are delegated to independent background processing workers.
 * </p>
 *
 * <p>
 * The JavaFX Application Thread controls playback state and collects
 * processed results in submission order. Workers operate on cloned
 * frames and independently initialised effect pipelines, avoiding
 * concurrent access to the decoder and shared mutable processors.
 * </p>
 *
 * <p>
 * Seeking and stopping retire outstanding processing sessions.
 * Results belonging to obsolete sessions are discarded rather
 * than presented.
 * </p>
 */

public class VideoPlayerModel {
    private static final long NO_SEEK_REQUEST = -1; // Sentinel value used when no seek position is active.
    private static final long FIVE_SECONDS_US = 5_000_000L;
    private static final long AUDIO_LEAD_NS = 30_000_000L;
    
    /**
     * Concurrency model:
     * - FFmpeg decoding and JavaFX presentation are owned by the FX thread.
     * - Processing workers operate on independent cloned frames.
     * - Each worker owns an independent FrameProcessingPipeline.
     * - Completed frames are collected in submission order.
     * - The number of outstanding jobs is bounded to limit memory use.
     * - Seeking retires the old session and invalidates stale results.
     *
     * These ownership rules prevent concurrent access to the FFmpeg
     * grabber and prevent stale processing results from being displayed.
     */
    
    // processing capacity is bounded to limit CPU contention and frame memory.
    private static final int PROCESSING_WORKERS = 6;
    private static final int MAX_PENDING_VIDEO_FRAMES = 8;
    
    private static final int DECODE_PER_PUMP = 3;
    
    // limits decoding attempts during one submission call.
    private static final int MAX_GRABS_PER_SUBMISSION = 8;

    private final List<FrameProcessingSession> retiringSessions =
            new ArrayList<>();

    private final Queue<PendingAudio> pendingAudio = new ArrayDeque<>();
    private final BiConsumer<Integer, Integer> videoSizeChangedHandler;
    private final Consumer<Image> frameReadyHandler;
    private final Consumer<String> statusChangedHandler;
    private final BiConsumer<Boolean, Boolean> playbackStateChangedHandler;
    private final Consumer<Boolean> audioOutputStateChangedHandler;

    // This is the critical wiring that allows the framework (JavaFX) to call
    // into our model logic every time it goes around its event loop. Every GUI
    // system has an event loop so that button clicks, key presses and window
    // resizes can be responded to. Most offer ways of running additional logic
    // in either idle time (when there are no pending UI events to process) or
    // on a regular heart beat, such as with this JavaFX AnimationTimer. Here
    // we just create a little anonymous local subclass and override the handle
    // method to call the method we want run every heart beat.
    private final AnimationTimer playbackTimer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            pumpPlayback(now);
        }
    };

    private File videoFile; // Path to the media that comes from the command line
    private FFmpegFrameGrabber grabber; // This is the 3rd party video media decoder. It deals in JavaCV Frame objects.
    private JavaFXFrameConverter converter; // Takes Frame objects to JavaFX Image objects, which can be put on screen.
    private AudioPlayer audioPlayer; // This is ours. It has some real time buffering smarts.
    // Frame currently selected for presentation on the JavaFX thread.
    private PreparedFrame preparedFrame;
    private final Queue<PreparedFrame> readyFrames =
            new ArrayDeque<>();
    private boolean playbackOpen; // True when playback is happening.
    private boolean pauseRequested;
    private boolean audioOutputEnabled;
    private boolean audioAvailable;
    private boolean frameProcessorsInitialised;
    // owns concurrent frameprocessing workers
    private FrameProcessingSession processingSession;
    // metadata needed to initialise independent worker pipelines
    private InfoVideo currentVideoInfo;
    private long currentTimestampUs;
    private long videoDurationUs = NO_SEEK_REQUEST;
    private int videoFrameDurationUs;
    private double frameRate;
    private int intFrameRate;
    private int totalVideoFrames;
    private long firstTimestampUs = NO_SEEK_REQUEST;
    private long logicalPlaybackBaseUs;
    private long playbackStartNs;
    private long pauseStartedNs;
    private long relativeSeekBaseUs = NO_SEEK_REQUEST;
    
    // added
    // identify current playback session
    private long playbackGeneration = 0;
    // assign monotonically increasing sequence numbers
    private long nextFrameSequence = 0;
    // distinguish decoder completion from playback completion
    private boolean decodingFinished = false;
    
    // prevents shutdown cleanup from being started more than once.
    private boolean shutdownRequested = false;
    
    private final List<String> effectCodes;

    public VideoPlayerModel(
            boolean audioEnabled,
            List<String> effectCodes,
            BiConsumer<Integer, Integer> videoSizeChangedHandler,
            Consumer<Image> frameReadyHandler,
            Consumer<String> statusChangedHandler,
            BiConsumer<Boolean, Boolean> playbackStateChangedHandler,
            Consumer<Boolean> audioOutputStateChangedHandler) {
        audioOutputEnabled = audioEnabled;
        this.videoSizeChangedHandler = videoSizeChangedHandler;
        this.frameReadyHandler = frameReadyHandler;
        this.statusChangedHandler = statusChangedHandler;
        this.playbackStateChangedHandler = playbackStateChangedHandler;
        this.audioOutputStateChangedHandler = audioOutputStateChangedHandler;
        this.effectCodes = List.copyOf(effectCodes);

    }

    public void play(File file) {
        videoFile = file;
        videoDurationUs = NO_SEEK_REQUEST;
        videoFrameDurationUs = 0;
        startPlayback(0, false, NO_SEEK_REQUEST);
    }

    public void startOver() {
        if (videoFile == null) {
            return;
        }

        seekTo(0);
    }

    public void backFiveSeconds() {
        seekRelative(-FIVE_SECONDS_US);
    }

    public void forwardFiveSeconds() {
        seekRelative(FIVE_SECONDS_US);
    }

    public void togglePause() {
        if (!playbackOpen) {
            if (videoFile != null) {
                startPlayback(displayableSeekTimestamp(currentTimestampUs), false, currentTimestampUs);
            }
            return;
        }

        pauseRequested = !pauseRequested;
        relativeSeekBaseUs = NO_SEEK_REQUEST;

        if (pauseRequested) {
            pauseStartedNs = System.nanoTime();
            flushAudioOutput();
        } else {
            resumePlaybackClock(System.nanoTime());
        }

        notifyPlaybackStateChanged();
    }

    public void toggleAudioOutput() {
        audioOutputEnabled = !audioOutputEnabled;
        pendingAudio.clear();
        flushAudioOutput();
        notifyAudioOutputStateChanged();
    }

    public boolean isAudioOutputEnabled() {
        return audioOutputEnabled;
    }

    public void stopPlayback() {
        closePlaybackResources();
        currentTimestampUs = 0;
        pauseRequested = false;
        notifyStatusChanged("Stopped");
        notifyFrameReady(null);
        notifyPlaybackStateChanged();
    }


    public void shutdown() {
    	if (shutdownRequested) {
            return;
        }

        shutdownRequested = true;
        
        closePlaybackResources();
        playbackTimer.stop();

        List<FrameProcessingSession> sessions =
                new ArrayList<>(retiringSessions);
        retiringSessions.clear();

        if (!sessions.isEmpty()) {
            Thread cleanupThread = new Thread(
                    () -> {
                        for (FrameProcessingSession session : sessions) {
                            drainSessionOnShutdown(session);
                        }
                    },
                    "video-processing-cleanup"
            );

            cleanupThread.start();
        }
    }
    
    // drains completed frames and releases workers when the application exits.
    private void drainSessionOnShutdown(FrameProcessingSession session) {
        session.finishSubmitting();

        while (!session.isDrained()) {
            try {
                var result = session.pollReady();

                if (result.isPresent()) {
                    result.get().frame().close();
                } else {
                    java.util.concurrent.locks.LockSupport.parkNanos(
                            5_000_000L
                    );
                }

            } catch (java.util.concurrent.CompletionException
                    | java.util.concurrent.CancellationException failure) {
                // continue draining remaining results.
            }
        }

        session.disposeIfDrained();
    }

    private void seekRelative(long offsetUs) {
        if (videoFile == null) {
            return;
        }

        long baseTimestampUs = relativeSeekBaseUs != NO_SEEK_REQUEST
                ? relativeSeekBaseUs
                : currentTimestampUs;
        seekTo(baseTimestampUs + offsetUs);
    }

    private void seekTo(long timestampUs) {
        long logicalTimestampUs = clampSeekTimestamp(timestampUs);
        long grabTimestampUs = displayableSeekTimestamp(logicalTimestampUs);

        if (!playbackOpen) {
            startPlayback(grabTimestampUs, pauseRequested, logicalTimestampUs);
            return;
        }

        try {
        	retireProcessingSession();
            grabber.setTimestamp(grabTimestampUs);
            resetPlaybackClock(logicalTimestampUs);
            flushAudioOutput();
            prepareNextFrame();
            notifyPlaybackStateChanged();
        } catch (Exception e) {
            handlePlaybackError(e);
        }
    }

    private void startPlayback(long startTimestampUs, boolean initiallyPaused, long initialRelativeSeekBaseUs) {
        closePlaybackResources();

        pauseRequested = initiallyPaused;
        currentTimestampUs = initialRelativeSeekBaseUs != NO_SEEK_REQUEST
                ? initialRelativeSeekBaseUs
                : startTimestampUs;
        relativeSeekBaseUs = initialRelativeSeekBaseUs;

        notifyStatusChanged(videoFile.getName());

        try {
            openPlaybackResources(startTimestampUs);
            resetPlaybackClock(currentTimestampUs);
            playbackOpen = true;
            playbackTimer.start();
            prepareNextFrame();
            notifyPlaybackStateChanged();
        } catch (Exception e) {
            handlePlaybackError(e);
        }
    }

    private void openPlaybackResources(long startTimestampUs) throws Exception {
        converter = new JavaFXFrameConverter();
        grabber = new FFmpegFrameGrabber(videoFile);
        grabber.setImageMode(FrameGrabber.ImageMode.COLOR);
        grabber.setSampleMode(FrameGrabber.SampleMode.SHORT);
        grabber.start();

        notifyVideoSizeChanged(grabber.getImageWidth(), grabber.getImageHeight());

        frameRate = grabber.getFrameRate();
        intFrameRate = (int) Math.round(frameRate);
        videoFrameDurationUs = frameRate > 0
                ? (int) Math.round(1_000_000.0 / frameRate)
                : 0;
        totalVideoFrames = grabber.getLengthInVideoFrames();

        long durationUs = grabber.getLengthInTime();
        videoDurationUs = durationUs > 0
                ? durationUs
                : NO_SEEK_REQUEST;

        audioAvailable = grabber.hasAudio();
        audioPlayer = new AudioPlayer();
        if (audioAvailable) {
            audioPlayer.open(grabber.getSampleRate(), grabber.getAudioChannels());
        }

        if (startTimestampUs > 0) {
            grabber.setTimestamp(startTimestampUs);
        }
    }

    private void resetPlaybackClock(long logicalTimestampUs) {
        // invalidate frames belonging to previous playback position
    	playbackGeneration++;
    	// begin new ordered frame sequence
        nextFrameSequence = 0;
        // decoder not yet reached end of this playback
        decodingFinished = false;
        
        frameProcessorsInitialised = false;
        currentVideoInfo = null;
        
    	pendingAudio.clear();
        preparedFrame = null;
        readyFrames.clear();
        currentTimestampUs = logicalTimestampUs;
        relativeSeekBaseUs = logicalTimestampUs;
        firstTimestampUs = NO_SEEK_REQUEST;
        logicalPlaybackBaseUs = logicalTimestampUs;
        playbackStartNs = 0;
        pauseStartedNs = pauseRequested ? System.nanoTime() : 0;
    }

    private void resumePlaybackClock(long now) {
        if (pauseStartedNs > 0 && playbackStartNs > 0) {
            playbackStartNs += now - pauseStartedNs;
        }
        pauseStartedNs = 0;
    }


	private void prepareNextFrame() {
	
	    if (!playbackOpen || decodingFinished) {
	        return;
	    }
	
	    try {
	        for (int i = 0; i < DECODE_PER_PUMP; i++) {
	
	            if (processingSession != null
	                    && processingSession.pendingCount()
	                            >= MAX_PENDING_VIDEO_FRAMES) {
	                break;
	            }
	
	            if (!submitNextVideoFrame()) {
	                break;
	            }
	
	            if (pauseRequested) {
	                break;
	            }
	        }
	
	    } catch (Exception e) {
	        handlePlaybackError(e);
	    }
	}
	

	// retrieves the next completed frame without blocking JavaFX.
	private void collectCompletedFrame() {
	
		if (processingSession == null
		        || readyFrames.size() >= MAX_PENDING_VIDEO_FRAMES) {
		    return;
		}
	
	    var ready = processingSession.pollReady();
	
	    if (ready.isEmpty()) {
	        return;
	    }
	
	    ProcessedVideoFrame result = ready.get();
	
	    try {
	        if (result.generation() != playbackGeneration) {
	            return;
	        }
	
	        Image image = converter.convert(result.frame());
	
	        if (image == null) {
	            throw new IllegalStateException(
	                    "JavaFX conversion returned a null image"
	            );
	        }
	
	        readyFrames.add(new PreparedFrame(
	                image,
	                result.info().frameNumber(),
	                result.mediaTimestampUs(),
	                result.logicalTimestampUs(),
	                result.targetTimeNs(),
	                System.nanoTime()
	        ));
	
	    } finally {
	        result.frame().close();
	    }
	}


    
    // create an independently owned processing job for decoded video frame. 
    private VideoFrameJob createVideoFrameJob(
    		Frame frame,
    		long timestampUs,
    		int frameNumber) {
    	
    	if (firstTimestampUs == NO_SEEK_REQUEST) {
    		firstTimestampUs = timestampUs;
    		playbackStartNs = System.nanoTime();
    		
    		if (pauseRequested) {
    			pauseStartedNs = playbackStartNs;
    		}
    	}
    	
    	long relativeTimestampUs =
                Math.max(0, timestampUs - firstTimestampUs);

        long logicalTimestampUs =
                logicalPlaybackBaseUs + relativeTimestampUs;

        long targetTimeNs =
                playbackStartNs + relativeTimestampUs * 1_000L;

        InfoFrame info = new InfoFrame(frameNumber, timestampUs);
        
        // job receives its own image-buffer copy
        Frame ownedFrame = frame.clone();
        
        return new VideoFrameJob(
                nextFrameSequence++,
                playbackGeneration,
                ownedFrame,
                info,
                timestampUs,
                logicalTimestampUs,
                targetTimeNs
        );
    }
    
    // initialising concurrent processing
    private void initialiseConcurrentProcessing(Frame frame) {

        InfoVideo info = new InfoVideo(
                mediaName(videoFile),
                totalVideoFrames,
                frame.imageWidth,
                frame.imageHeight,
                frame.imageDepth,
                frame.imageChannels,
                frame.imageStride,
                frameRate,
                intFrameRate,
                videoFrameDurationUs,
                grabber.getPixelFormat()
        );

        initialiseProcessingSession(info);
        frameProcessorsInitialised = true;
    }

    private boolean submitNextVideoFrame() throws Exception {
    	if (!playbackOpen || decodingFinished) {
    	    return false;
    	}

    	if (processingSession != null
    	        && processingSession.pendingCount() >= MAX_PENDING_VIDEO_FRAMES) {
    	    return false;
    	}
    	
    	for (int grabs = 0;
    	        playbackOpen && grabs < MAX_GRABS_PER_SUBMISSION;
    	        grabs++) {
            Frame frame = grabFrame();
            if (frame == null) {
                decodingFinished = true;

                if (processingSession != null) {
                    processingSession.finishSubmitting();
                }

                return false;
            }

            long timestampUs = grabber.getTimestamp();
            if (audioAvailable && frame.samples != null) {
                queueAudio(timestampUs, frame);
            }

            if (frame.image == null) {
                continue;
            }

            if (!frameProcessorsInitialised) {
                initialiseConcurrentProcessing(frame);
            }

            // obtain the frame number from the decoder.
            int frameNumber = grabber.getFrameNumber();

            // create an independent frame copy with its playback metadata.
            VideoFrameJob job = createVideoFrameJob(
                    frame,
                    timestampUs,
                    frameNumber
            );

            boolean submitted;

            try {
                submitted = processingSession.submit(job);
            } catch (RuntimeException | Error failure) {
                job.frame().close();
                throw failure;
            }

            if (!submitted) {
                job.frame().close();
                throw new IllegalStateException(
                        "Processing queue rejected a frame unexpectedly"
                );
            }

            return true;
        }
    	return false;
    }
    
    // stop new submissiosn to current session
    private void retireProcessingSession() {

        if (processingSession == null) {
            return;
        }

        processingSession.finishSubmitting();
        retiringSessions.add(processingSession);
        processingSession = null;
    }
    
    // release results from obsolete sessions without displaying them
    private void reapRetiringSessions() {

        var iterator = retiringSessions.iterator();

        while (iterator.hasNext()) {

            FrameProcessingSession session = iterator.next();

            while (!session.isEmpty()) {

                try {
                    var ready = session.pollReady();

                    if (ready.isEmpty()) {
                        break;
                    }

                    ready.get().frame().close();

                } catch (java.util.concurrent.CompletionException
                        | java.util.concurrent.CancellationException failure) {

                    failure.printStackTrace();
                }
            }

            if (session.isDrained()) {
                session.disposeIfDrained();
                iterator.remove();
            }
        }
    }

    /*
    Coordinates playback on each JavaFX animation callback.
    
    Collects completed processing jobs without blocking, schedules audio,
    displays at most one due frame and submits additional decoding work.
    
    Processing runs on background workers, but decoding and JavaFX
    conversion remain on this thread. The bounded submission limits
    help preserve time for GUI events and rendering.
    */

	private void pumpPlayback(long now) {
	
	    // clean up completed jobs belonging to previous playback positions.
	    reapRetiringSessions();
	
	    if (!playbackOpen) {
	        if (retiringSessions.isEmpty()) {
	            playbackTimer.stop();
	        }
	        return;
	    }
	
	    try {
	    	for (int i = 0; i < MAX_PENDING_VIDEO_FRAMES; i++) {

	    	    if (processingSession == null
	    	            || readyFrames.size() >= MAX_PENDING_VIDEO_FRAMES) {
	    	        break;
	    	    }

	    	    int previousCount = readyFrames.size();

	    	    collectCompletedFrame();

	    	    if (readyFrames.size() == previousCount) {
	    	        break;
	    	    }
	    	}
	
	        // continue scheduling audio playback.
	        writeDueAudio(now);
	
	        // Present at most one frame per JavaFX animation callback.
	        if (!readyFrames.isEmpty()
	                && (pauseRequested
	                    || readyFrames.peek().targetTimeNs() <= now)) {

	            preparedFrame = readyFrames.remove();
	            displayPreparedFrame(now);
	        }
	
	        // keep multiple processing jobs supplied to the workers.
	        if (!pauseRequested) {
	            prepareNextFrame();
	        }
	
	        // finish only after decoding and outstanding playback are complete.
	        if (decodingFinished
	                && preparedFrame == null
	                && readyFrames.isEmpty()
	                && (processingSession == null || processingSession.isEmpty())
	                && pendingAudio.isEmpty()) {
	            finishPlayback();
	        }
	
	    } catch (Exception e) {
	        handlePlaybackError(e);
	    }
	}


    private void displayPreparedFrame(long now) {
        PreparedFrame frame = preparedFrame;
        preparedFrame = null;
        currentTimestampUs = frame.logicalTimestampUs();
        relativeSeekBaseUs = NO_SEEK_REQUEST;

        // Dump some logging to the console once per second so that real time performance can be monitored.
        if (intFrameRate > 0 && frame.frameNumber() % intFrameRate == 0) {
            long remainingUs = (frame.targetTimeNs() - frame.preparedAtNs()) / 1_000L;
            if (remainingUs < 0) {
                System.out.printf("Frame headroom is \u001B[31m%5dus\u001B[0m out of %dus spare.%n",
                        remainingUs,
                        videoFrameDurationUs);
            } else {
                System.out.printf("Frame headroom is \u001B[32m%5dus\u001B[0m out of %dus spare.%n",
                        remainingUs,
                        videoFrameDurationUs);
            }
        }

        notifyFrameReady(frame.image());
    }

    private void queueAudio(long timestampUs, Frame frame) {
        if (!audioOutputEnabled || audioPlayer == null) {
            return;
        }

        byte[] samples = audioPlayer.copySamples(frame);
        if (samples.length > 0) {
            pendingAudio.add(new PendingAudio(timestampUs, samples));
        }
    }

    private void writeDueAudio(long now) {
        // Handle some obvious early exits
        if (!audioOutputEnabled || audioPlayer == null) {
            pendingAudio.clear();
            return;
        }
        if (pauseRequested || firstTimestampUs == NO_SEEK_REQUEST || playbackStartNs <= 0) {
            return;
        }

        // Here is the real time dependent logic
        long dueTimestampUs = firstTimestampUs + (now + AUDIO_LEAD_NS - playbackStartNs) / 1_000L;
        while (!pendingAudio.isEmpty()) {
            PendingAudio audio = pendingAudio.peek();
            if (audio.timestampUs() > dueTimestampUs) {
                return;
            }

            int written = audioPlayer.write(audio.samples(), audio.offset(), audio.remaining());
            if (written <= 0) {
                return;
            }

            audio.advance(written);
            if (!audio.finished()) {
                return;
            }
            pendingAudio.remove();
        }
    }

    private void finishPlayback() {
        long endTimestampUs = videoDurationUs != NO_SEEK_REQUEST
                ? videoDurationUs
                : currentTimestampUs;

        closePlaybackResources();
        pauseRequested = true;
        currentTimestampUs = endTimestampUs;
        relativeSeekBaseUs = endTimestampUs;
        notifyStatusChanged("Playback finished");
        notifyPlaybackStateChanged();
    }

    private void handlePlaybackError(Exception e) {
        e.printStackTrace();
        closePlaybackResources();
        pauseRequested = false;
        notifyStatusChanged("Error: " + e.getMessage());
        notifyPlaybackStateChanged();
    }

    private void closePlaybackResources() {
        playbackOpen = false;
        preparedFrame = null;
        readyFrames.clear();
        retireProcessingSession();
        pendingAudio.clear();
        firstTimestampUs = NO_SEEK_REQUEST;
        playbackStartNs = 0;
        pauseStartedNs = 0;
        audioAvailable = false;
        frameProcessorsInitialised = false;
        currentVideoInfo = null;
        decodingFinished = false;

        if (audioPlayer != null) {
            audioPlayer.close();
            audioPlayer = null;
        }

        if (grabber != null) {
            try {
                grabber.stop();
            } catch (Exception e) {
                // The resource is being closed; there is no useful recovery action.
            }
            try {
                grabber.close();
            } catch (Exception e) {
                // The resource is being closed; there is no useful recovery action.
            }
            grabber = null;
        }

        if (converter != null) {
            converter.close();
            converter = null;
        }
        
        // continue disposing old processing results after playback stops
        if (retiringSessions.isEmpty()) {
            playbackTimer.stop();
        } else {
            playbackTimer.start();
        }
    }

    private String mediaName(File file) {
        String fileName = file.getName();
        int extensionStart = fileName.lastIndexOf('.');
        if (extensionStart <= 0) {
            return fileName;
        }
        return fileName.substring(0, extensionStart);
    }

    private long clampSeekTimestamp(long timestampUs) {
        long clampedTimestampUs = Math.max(0, timestampUs);
        long durationUs = videoDurationUs;
        if (durationUs != NO_SEEK_REQUEST) {
            clampedTimestampUs = Math.min(clampedTimestampUs, durationUs);
        }
        return clampedTimestampUs;
    }

    private long displayableSeekTimestamp(long logicalTimestampUs) {
        long durationUs = videoDurationUs;
        if (durationUs == NO_SEEK_REQUEST || logicalTimestampUs < durationUs) {
            return logicalTimestampUs;
        }

        int frameDurationUs = videoFrameDurationUs;
        if (frameDurationUs <= 0) {
            return Math.max(0, durationUs - 1);
        }

        return Math.max(0, durationUs - frameDurationUs);
    }
    


	// creates a concurrent processing session using decoded video metadata.
	private void initialiseProcessingSession(InfoVideo videoInfo) {
	
	    if (processingSession != null) {
	        throw new IllegalStateException(
	            "Processing session has already been initialised"
	        );
	    }
	
	    currentVideoInfo = videoInfo;
	
	    processingSession = new FrameProcessingSession(
	            effectCodes,
	            currentVideoInfo,
	            PROCESSING_WORKERS,
	            MAX_PENDING_VIDEO_FRAMES
	    );
	}


    private Frame grabFrame() throws Exception {
        if (audioAvailable) {
            return grabber.grab();
        }
        return grabber.grabImage();
    }

    private void flushAudioOutput() {
        if (audioPlayer != null) {
            audioPlayer.flush();
        }
    }

    private void notifyVideoSizeChanged(int width, int height) {
        videoSizeChangedHandler.accept(width, height);
    }

    private void notifyFrameReady(Image image) {
        frameReadyHandler.accept(image);
    }

    private void notifyStatusChanged(String status) {
        statusChangedHandler.accept(status);
    }

    private void notifyPlaybackStateChanged() {
        playbackStateChangedHandler.accept(playbackOpen, pauseRequested);
    }

    private void notifyAudioOutputStateChanged() {
        audioOutputStateChangedHandler.accept(audioOutputEnabled);
    }

    private record PreparedFrame(
            Image image,
            int frameNumber,
            long mediaTimestampUs,
            long logicalTimestampUs,
            long targetTimeNs,
            long preparedAtNs) {
    }

    private static class PendingAudio {
        private final long timestampUs;
        private final byte[] samples;
        private int offset;

        PendingAudio(long timestampUs, byte[] samples) {
            this.timestampUs = timestampUs;
            this.samples = samples;
        }

        long timestampUs() {
            return timestampUs;
        }

        byte[] samples() {
            return samples;
        }

        int offset() {
            return offset;
        }

        int remaining() {
            return samples.length - offset;
        }

        void advance(int byteCount) {
            offset += byteCount;
        }

        boolean finished() {
            return offset >= samples.length;
        }
    }
}
