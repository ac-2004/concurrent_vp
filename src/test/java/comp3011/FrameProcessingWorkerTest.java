
package comp3011;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import org.bytedeco.javacv.Frame;

class FrameProcessingWorkerTest {

    // verifies that null effect configurations are rejected.
    @Test
    void rejectsNullEffectConfiguration() {

        assertThrows(
                NullPointerException.class,
                () -> new FrameProcessingWorker(null, null)
        );
    }
    

    // checks that independent workers produce identical pixels for the same frame.
    @Test
    void independentWorkersProduceIdenticalPixels() throws Exception {

        String effects =
                "sfwyvdjmbppbmjdvywfssfwyvdjmbppbmjdvywfs";

        List<String> effectCodes = effects.chars()
                .mapToObj(c -> Character.toString((char) c))
                .toList();

        try (Frame firstFrame = new Frame(96, 64, Frame.DEPTH_UBYTE, 3);
             Frame secondFrame = new Frame(96, 64, Frame.DEPTH_UBYTE, 3)) {

            InfoVideo videoInfo = new InfoVideo(
                    "movie.mp4",
                    1200,
                    firstFrame.imageWidth,
                    firstFrame.imageHeight,
                    firstFrame.imageDepth,
                    firstFrame.imageChannels,
                    firstFrame.imageStride,
                    24.0,
                    24,
                    41667,
                    0
            );

            // give both frames identical, non-uniform pixel data.
            ByteBuffer firstPixels = (ByteBuffer) firstFrame.image[0];
            ByteBuffer secondPixels = (ByteBuffer) secondFrame.image[0];

            for (int i = 0; i < firstPixels.capacity(); i++) {
                byte value = (byte) ((i * 37 + 19) & 0xff);
                firstPixels.put(i, value);
                secondPixels.put(i, value);
            }

            try (FrameProcessingWorker firstWorker =
                         new FrameProcessingWorker(effectCodes, videoInfo);
                 FrameProcessingWorker secondWorker =
                         new FrameProcessingWorker(effectCodes, videoInfo)) {

                InfoFrame info = new InfoFrame(946, 0);

                VideoFrameJob firstJob = new VideoFrameJob(
                        0, 0, firstFrame, info, 0, 0, 0
                );

                VideoFrameJob secondJob = new VideoFrameJob(
                        0, 0, secondFrame, info, 0, 0, 0
                );

                firstWorker.process(firstJob);
                secondWorker.process(secondJob);

                byte[] firstOutput = new byte[firstPixels.capacity()];
                byte[] secondOutput = new byte[secondPixels.capacity()];

                for (int i = 0; i < firstOutput.length; i++) {
                    firstOutput[i] = firstPixels.get(i);
                    secondOutput[i] = secondPixels.get(i);
                }

                assertArrayEquals(
                        firstOutput,
                        secondOutput,
                        "Independent workers produced different pixels for frame 946"
                );
            }
        }
    }
    

    // checks whether processing earlier frames changes a later frame's pixels.
    @Test
    void processingHistoryDoesNotChangeFrameOutput() throws Exception {

        String effects =
                "sfwyvdjmbppbmjdvywfssfwyvdjmbppbmjdvywfs";

        List<String> effectCodes = effects.chars()
                .mapToObj(c -> Character.toString((char) c))
                .toList();

        try (Frame template = new Frame(96, 64, Frame.DEPTH_UBYTE, 3)) {

            ByteBuffer pixels = (ByteBuffer) template.image[0];

            for (int i = 0; i < pixels.capacity(); i++) {
                pixels.put(i, (byte) ((i * 37 + 19) & 0xff));
            }

            InfoVideo videoInfo = new InfoVideo(
                    "movie.mp4",
                    1200,
                    template.imageWidth,
                    template.imageHeight,
                    template.imageDepth,
                    template.imageChannels,
                    template.imageStride,
                    24.0,
                    24,
                    41667,
                    0
            );

            try (FrameProcessingWorker freshWorker =
                         new FrameProcessingWorker(effectCodes, videoInfo);
                 FrameProcessingWorker experiencedWorker =
                         new FrameProcessingWorker(effectCodes, videoInfo)) {

                // Process earlier frames on the second worker only.
                for (int frameNumber = 940; frameNumber < 946; frameNumber++) {

                    Frame previousFrame = template.clone();

                    try {
                        VideoFrameJob previousJob = new VideoFrameJob(
                                frameNumber,
                                0,
                                previousFrame,
                                new InfoFrame(frameNumber, frameNumber * 41667L),
                                frameNumber * 41667L,
                                frameNumber * 41667L,
                                0
                        );

                        experiencedWorker.process(previousJob);
                    } finally {
                        previousFrame.close();
                    }
                }

                // both workers now process identical copies of frame 946.
                try (Frame freshFrame = template.clone();
                     Frame experiencedFrame = template.clone()) {

                    InfoFrame info = new InfoFrame(946, 946 * 41667L);

                    ProcessedVideoFrame freshResult =
                            freshWorker.process(new VideoFrameJob(
                                    946, 0, freshFrame, info, 0, 0, 0
                            ));

                    ProcessedVideoFrame experiencedResult =
                            experiencedWorker.process(new VideoFrameJob(
                                    946, 0, experiencedFrame, info, 0, 0, 0
                            ));

                    ByteBuffer freshPixels =
                            (ByteBuffer) freshResult.frame().image[0];

                    ByteBuffer experiencedPixels =
                            (ByteBuffer) experiencedResult.frame().image[0];

                    assertEquals(
                            freshPixels.capacity(),
                            experiencedPixels.capacity()
                    );

                    byte[] freshOutput = new byte[freshPixels.capacity()];
                    byte[] experiencedOutput =
                            new byte[experiencedPixels.capacity()];

                    for (int i = 0; i < freshOutput.length; i++) {
                        freshOutput[i] = freshPixels.get(i);
                        experiencedOutput[i] = experiencedPixels.get(i);
                    }

                    assertArrayEquals(
                            freshOutput,
                            experiencedOutput,
                            "Processing history changed the output of frame 946"
                    );
                }
            }
        }
    }


}
