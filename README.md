# COMP3011 Assignment 2 - Java Local Project

## Project Overview
This project works on the supplied JavaFX video player and extends it into a multithreaded application capable of applying multiple visual effects concurrently while maintaining ordered video playback. 

The original implementation decoded, processed and displayed video frames cooperatively on the JavaFX application thread. As additional effects were applied, processing increasingly competed with playback scheduling and user-interface operations.

The modified implementation eases the computationally expensive frame processing using a bounded worker pool. Each worker owns an independent effect-processing pipeline, allowing multiple frames to be processes simultaneously while completed frames are presented in their original order.

The implementation focuses on:

- Improving playback performance when multiple effects are applied.
- Supporting command-line effect ordering and repetition.
- Preserving existing playback controls.
- Preventing unsafe sharing of mutable processor state.
- Bounding outstanding work and native frame memory.
- Supporting safe seeking, shutdown and processing-session retirement.
- Providing automated correctness and performance tests.

## Technologies and Requirements
The application uses:
Java 25: For its application and concurrency implementation
JavaFX 26.0.2: For graphical interface and playback presentation
JavaCV 1.5.13/FFmpeg: For video decoding and frame handling
Maven: For dependency management and builds
JUnit 5: For unit and regression testing
Maven Surefire: For unit-test execution
Maven Failsafe: For performance integration-test execution

The application requires a compatible Java 25 installation and a graphical desktop environment for JavaFX playback.

## Building and Running
To run the player, create a Maven build configuration with the goals:
compile javafx:run

Specify the video and desired effects using the javafx.args property.

Example:

compile javafx:run -Djavafx.args="/absolute/path/to/video.mp4

Using Terminal, from the project root on macOS or Linux:
./mvnw clean compile

Run a video:
./mvnw javafx:run "-Djavafx.args=/absolute/path/to/video.mp4

## Command-Line Interface
The player supports the original command-line controls and eleven visual effects.
Read the below as:
Short option, Long option, Description
-h, --help, display help
-a --audio, enable audio
-x, --maximise, start maximised
-1, --monitor-1, Select display 1
-2, --monitor-2, Select display 2
-n, --number-frames, Overlay frame numbers
-s, --scratch-frames, Apply film scratches
-f, --flicker-frames, Apply flickering
-w, --black-and-white, Convert to black and white
-y, --yellow-frames, Apply a warmer colour tone
-v, --vignette-frames, Darken frame edges
-d, --dust-frames, Apply dust and hair marks
-j, --jitter-frames, Apply frame jitter
-m, --mottle-frames, Apply cloudy emulsion effects
-b, --bleed-frames, Apply light bleeding
-p, --pepper-frames, Apply dark spots

Effects are applied in their specified command-line order and can appear multiple times.

For example:
video.mp4 -nssnfwyvdjmbp

This applies frame numbering, scratches twice, frame numbering again, and the remaining effects in sequence.

Both grouped short options and long-form options are supported.

### Extending the Effects
New effects can be added by implementing the existing FrameProcessor interface and registering the effect in FrameProcessorFactory. The command-line parser and help text must also be updated to recognise the new option.

Each worker's processing pipeline is constructed independently. New effects containing mutable state must preserve that worker-local ownership model.

## Concurrency Architecture
The concurrency design separates playback coordination from computationally expensive frame effects. 

Processing flow:
JavaFX application thread ->
FFmpeg video decoding ->
create independent VideoFrameJob ->
FrameProcessingSession ->
Bounded OrderedWorkQueue ->
+ Worker 1 - FrameProcessingPipeline
+ Worker 2 - FrameProcessingPipeline
+ Worker 3 - FrameProcessingPipeline
+ Worker N - FrameProcessingPipeline ->
Ordered processed-frame collection ->
JavaFX image conversion ->
Scheduled video presentation

### Main Components of Video Player
#### VideoPlayerModel
Coordinates decoding, playback timing, frame submission and collection, audio scheduling, seeking, and presentation.

FFmpeg decoder access and JavaFX presentation remain on the JavaFX Application Thread. Computationally expensive visual effects execute on background workers.

#### FrameProcessingSession
Manages the lifetime of a group of background processing jobs. It owns the ordered work queue and creates worker-local processors.

#### OrderedWorkQueue
Maintains a bounded executor and retrieves results in submission order, even when tasks finish out of order.

#### FrameProcessingWorker
Processes video jobs using an independent effect pipeline.

#### FrameProcessingPipeline
Applies the selected effects sequentially within one job, preserving command-line order.

#### ProcessedVideoFrame
Carries the processed JavaCV frame together with its sequence and playback metadata.

### Processing Configuration
These constants control the parallel processing in VideoPlayerModel:

* may be different in code *
private static final int PROCESSING_WORKERS = 6;
private static final int MAX_PENDING_VIDEO_FRAMES = 8;
private static final int DECODE_PER_PUMP = 3;
private static final int MAX_GRABS_PER_SUBMISSION = 8;

Worker count determines available processing parallelism. Maximum pending frames bounds outstanding work and memory use. The decoding limits reduce the work performed during one JavaFX callback.

Increasing worker count does not guarantee better overall playback, since workers compete with video decoding and JavaFX rendering for available CPU resources.

## Concurrency Correctness and Thread Safety
The design reduces the need for explicit locking by limiting mutable state sharing.

### Thread Ownership
The JavaFX application thread owns the playback state, the FFmpeg grabber, audio scheduling, image conversion and GUI presentation. 

Processing workers do not directly manipulate the JavaFX controls or concurrently operate on the shared decoder.

Each job submitted contains an independently cloned video frame. Each procesing worker also owns its own FrameProcessingPipeline, so mutable processor instances are not shared between workers.

### Ordered Results
Parallel processing doesn't guarantee that jobs finish in submission order. 

Therefore, OrderedWorkQueue preserves the submission order of results. A later frame is not retrieved ahead of an earlier outstanding frame, protecting playback sequencing. 

The player collects completed frames without waiting synchronously for unfinished jobs during normal playback.

### Bounded Outstanding Work
The work queue limits the number of outstanding processing jobs. 

This is important because decoded frames contain native image buffers, and unrestricted submission could lead to excessive memory consumption.

### Seeking and Obsolete Jobs
Seeking can occur while background jobs are still processing frames from the previous playback position.

The player uses playback generations and session retirement to prevent results from an obsolete playback position from being presented after seeking.

Old sessions are drained separately, and their completed frame resources are discarded.

### Shutdown and Resource Ownership
Native JavaCV frame buffers require explicit resource cleanup.

The processing lifecycle distinguishes between stopping new submissions and draining work already in progress.

Completed frames are released after conversion or when discarded. Worker pipelines are disposed once their processing sessions have drained.

The application also provides a shutdown cleanup path that does not depend on continued JavaFX animation callbacks after the window closes.

### Failure Handling
Processing exceptions are propagated through the task results. Tests verify that a failed queued task does not prevent later results from being retrieved.

The design aims to ensure that failures do not permanently block ordered processing or leave worker sessions indefinitely active.

### Performance and Responsiveness
The multithreaded architecture improves processing throughput by allowing multiple frames to undergo effect processing simultaneously.

Performance is evaluated using both the university's TITAN runner and an automated local processing benchmark.

Using 4 workers and 6 pending frames, the relative performance was around 277.85%, with startup speed of 91%.

Using 5 workers and 8 pending frames, this improved to 345% with a startup speed of 90%. 

Further testing is to be executed.

### Automated Processing Benchmark
PerformanceComparisonIT.java compares one processing worker against six workers using a reproducible synthetic workload.

The test uses deterministic frame contents and measures effect-processing throughput in frames per second.

It also verifies ordered result retrieval and successful completion.

Previous local executions demonstrated approximately 3.2–4.0 times the single-worker throughput, depending on the run.

This benchmark measures effect processing only. It does not include FFmpeg decoding, JavaFX rendering, audio playback or GUI responsiveness and must not be interpreted as a complete end-to-end playback benchmark.

### Automated Testing
The project includes JUnit tests for command-line behaviour, processor creation, worker consistency, queue coordination and processing lifecycle behaviour.


#### Unit and Regression Tests
Read as: 
Test Class: Purpose
CommandLineControllerTest: Effect parsing, ordering, repitition and invalid arguments
FrameProcessorFactoryTest: Effect construction and independent pipelines
FrameProcessingPipelineTest: Effect pipeline behaviour
FrameProcessingWorkerTest: worker processing and deterministic output
FrameProcessingSessionTest: Session validation, submission and draining
OrderedWorkQueueTest: Queue ordering, bounded capacity and failure handling
VideoFrameJobTest: frame-processing job properties.

Targeted concurrency tests cover independent worker pipelines, consistency of output across processing histories, graceful session draining and retrieval following task failures.

#### Running Unit Tests
Using Terminal:
./mvnw clean test

Or run 'clean test' through Eclipse's Maven build configuration.

#### Running Integration Tests
Using Terminal:
./mvnw clean verify

This also executes PerformanceComparisonIT through the Maven Failsafe plugin.

## Original Implementation and Refactoring
The project began with the COMP3011 Assignment 2 single-threaded video-player source.

The original architecture applied frame-processing effects within cooperative playback execution.

The modified design replaces that processing path with worker-owned pipelines and a bounded ordered queue. Command-line configuration is passed as effect codes rather than constructing a shared processor list before playback.

The original JavaFX interface, decoder integration and playback controls are retained within the existing application structure.

Historical changes and the transition from Part 1 to Part 2 can be reviewed using the project's Git history.

## Attribution
Student: Ayush Chauhan
Student ID: a1870640
Course: COMP3011 — Assignment 2

The project is based on starter source supplied by Simon Ratcliffe for COMP3011, created in collaboration with GPT-5.6 Terra, as identified in the original source headers.

The modifications for this assignment include command-line effect handling, the concurrent processing architecture, frame lifecycle coordination, testing and performance evaluation.

Original source attribution and copyright notices are retained in modified starter files.

## Summary
This assignment demonstrates how an existing single-threaded video-processing application can be adapted to use concurrent execution while retaining ordered output and controlled resource ownership.

The primary improvement is the separation of frame-effect processing from playback coordination through bounded, independently initialised processing workers.

The design emphasises concurrency correctness, maintainable class responsibilities, measurable performance improvements and targeted regression testing.









