package com.sokolov.labs.worker.inference;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class YoloLabelTest {

    @Test
    void parsesPoseLineWithTwoKeypoints() {
        String text = "1 0.5 0.5 0.4 0.4 0.4 0.6 0.6 0.4";
        List<YoloLabel.Annotation> annotations = YoloLabel.parse(text);
        assertThat(annotations).hasSize(1);
        YoloLabel.Annotation a = annotations.get(0);
        assertThat(a.classId()).isEqualTo(1);
        assertThat(a.xCenter()).isCloseTo(0.5, within(1e-6));
        assertThat(a.keypoints()).hasSize(4);
    }

    @Test
    void convertsToPixelDetection() {
        YoloLabel.Annotation a = new YoloLabel.Annotation(0, 0.5, 0.5, 0.4, 0.4,
                List.of(0.4, 0.6, 0.6, 0.4));
        Detection d = YoloLabel.toDetection(a, 1000, 800);
        assertThat(d.x1()).isCloseTo(300, within(0.1));
        assertThat(d.y1()).isCloseTo(240, within(0.1));
        assertThat(d.x2()).isCloseTo(700, within(0.1));
        assertThat(d.y2()).isCloseTo(560, within(0.1));
        assertThat(d.keypoints()).containsExactly(400.0, 480.0, 600.0, 320.0);
    }

    @Test
    void skipsBlankAndCommentLines() {
        String text = "\n# comment\n0 0.1 0.1 0.2 0.2\n";
        assertThat(YoloLabel.parse(text)).hasSize(1);
    }
}
