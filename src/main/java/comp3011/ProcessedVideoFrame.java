
package comp3011;

import java.util.Objects;

import org.bytedeco.javacv.Frame;

// represents frame whose visual effects have finished processing
public record ProcessedVideoFrame(
        long sequenceNumber,
        long generation,
        Frame frame,
        InfoFrame info,
        long mediaTimestampUs,
        long logicalTimestampUs,
        long targetTimeNs,
        long preparedAtNs) {

    public ProcessedVideoFrame {

        if (sequenceNumber < 0 || generation < 0) {
            throw new IllegalArgumentException(
                "Sequence and generation must not be negative"
            );
        }

        Objects.requireNonNull(frame, "frame cannot be null");
        Objects.requireNonNull(info, "frame metadata cannot be null");
    }
}
