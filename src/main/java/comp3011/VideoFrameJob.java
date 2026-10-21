package comp3011;

import java.util.Objects;

import org.bytedeco.javacv.Frame;

// this class represents one decoded video frame awaiting processing
public record VideoFrameJob(
	long sequenceNumber,
	long generation,
	Frame frame,
	InfoFrame info
) {
	// validates required job data
	public VideoFrameJob {
		if (sequenceNumber < 0) {
            throw new IllegalArgumentException(
                "Sequence number cannot be negative"
            );
        }

        if (generation < 0) {
            throw new IllegalArgumentException(
                "Generation cannot be negative"
            );
        }

        Objects.requireNonNull(frame, "frame cannot be null");
        Objects.requireNonNull(info, "frame metadata cannot be null");
	}
}
