package com.sokolov.labs.worker.inference;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AccuracyCalculatorTest {

    @Test
    void perfectPredictionGivesFullScore() {
        Detection truth = new Detection(0, 0, 100, 100, 1, 0,
                List.of(20.0, 30.0, 70.0, 80.0));
        Detection prediction = new Detection(0, 0, 100, 100, 0.9, 0,
                List.of(20.0, 30.0, 70.0, 80.0));

        AccuracyCalculator.FrameScore score = AccuracyCalculator.evaluate(
                List.of(prediction), List.of(truth));

        assertThat(score.detectionRecall()).isEqualTo(1.0);
        assertThat(score.pck()).isEqualTo(1.0);
        assertThat(score.keypointPairs()).isEqualTo(2);
    }

    @Test
    void missedDetectionGivesZeroRecall() {
        Detection truth = new Detection(0, 0, 100, 100, 1, 0, List.of());
        Detection prediction = new Detection(200, 200, 300, 300, 0.9, 0, List.of());
        AccuracyCalculator.FrameScore score = AccuracyCalculator.evaluate(
                List.of(prediction), List.of(truth));
        assertThat(score.detectionRecall()).isEqualTo(0.0);
    }

    @Test
    void faraawayKeypointFailsPck() {
        Detection truth = new Detection(0, 0, 100, 100, 1, 0,
                List.of(20.0, 30.0));
        Detection prediction = new Detection(0, 0, 100, 100, 0.9, 0,
                List.of(80.0, 90.0));
        AccuracyCalculator.FrameScore score = AccuracyCalculator.evaluate(
                List.of(prediction), List.of(truth));
        assertThat(score.detectionRecall()).isEqualTo(1.0);
        assertThat(score.pck()).isEqualTo(0.0);
    }

    @Test
    void nearKeypointWithinThresholdPasses() {
        Detection truth = new Detection(0, 0, 100, 100, 1, 0,
                List.of(20.0, 30.0));
        Detection prediction = new Detection(0, 0, 100, 100, 0.9, 0,
                List.of(22.0, 31.0));
        AccuracyCalculator.FrameScore score = AccuracyCalculator.evaluate(
                List.of(prediction), List.of(truth));
        assertThat(score.pck()).isCloseTo(1.0, within(1e-6));
    }
}
