package com.sokolov.labs.worker.inference;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the private dataset-layout helpers in {@link InferenceRunner} via reflection.
 * Tests the rule: YOLO-style archives with {@code images/labels} + {@code train/val} splits
 * are narrowed to the val split and matched against the matching labels subdirectory.
 */
class DatasetLayoutTest {

    @Test
    void yoloLayoutPicksValAndMapsToLabels() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("images/train/a.jpg", bytes("trainA"));
        entries.put("images/train/b.jpg", bytes("trainB"));
        entries.put("images/val/c.jpg",   bytes("valC"));
        entries.put("images/val/d.jpg",   bytes("valD"));
        entries.put("labels/train/a.txt", bytes("0 0.1 0.1 0.2 0.2"));
        entries.put("labels/val/c.txt",   bytes("1 0.5 0.5 0.4 0.4"));
        entries.put("labels/val/d.txt",   bytes("2 0.6 0.6 0.3 0.3"));

        Map<String, byte[]> labels = invokeTakeLabels(entries);
        assertThat(labels).containsOnlyKeys("train/a", "val/c", "val/d");

        List<String> samples = invokeSelectSamples(entries);
        assertThat(samples).containsExactly("images/val/c.jpg", "images/val/d.jpg");

        assertThat(invokeLookupLabel(labels, "images/val/c.jpg")).isEqualTo(bytes("1 0.5 0.5 0.4 0.4"));
        assertThat(invokeLookupLabel(labels, "images/val/d.jpg")).isEqualTo(bytes("2 0.6 0.6 0.3 0.3"));
    }

    @Test
    void flatLayoutKeepsEveryEntryAsSample() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("a.jpg", bytes("a"));
        entries.put("b.jpg", bytes("b"));
        entries.put("labels/a.txt", bytes("0 0.5 0.5 0.4 0.4"));

        Map<String, byte[]> labels = invokeTakeLabels(entries);
        List<String> samples = invokeSelectSamples(entries);

        assertThat(samples).containsExactly("a.jpg", "b.jpg");
        assertThat(labels).containsOnlyKeys("a");
        assertThat(invokeLookupLabel(labels, "a.jpg")).isEqualTo(bytes("0 0.5 0.5 0.4 0.4"));
        assertThat(invokeLookupLabel(labels, "b.jpg")).isNull();
    }

    @Test
    void imagesPrefixWithoutSplitFallsBackToEverythingUnderImages() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("images/a.jpg", bytes("a"));
        entries.put("images/b.jpg", bytes("b"));
        entries.put("labels/a.txt", bytes("0 0 0 0 0"));

        List<String> samples = invokeSelectSamples(entries);
        assertThat(samples).containsExactly("images/a.jpg", "images/b.jpg");

        Map<String, byte[]> labels = invokeTakeLabels(entries);
        assertThat(invokeLookupLabel(labels, "images/a.jpg")).isEqualTo(bytes("0 0 0 0 0"));
    }

    private static byte[] bytes(String s) {
        return s.getBytes();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, byte[]> invokeTakeLabels(Map<String, byte[]> entries) throws Exception {
        Map<String, byte[]> copy = new LinkedHashMap<>(entries);
        Method m = InferenceRunner.class.getDeclaredMethod("takeLabels", Map.class);
        m.setAccessible(true);
        return (Map<String, byte[]>) m.invoke(null, copy);
    }

    @SuppressWarnings("unchecked")
    private static List<String> invokeSelectSamples(Map<String, byte[]> entries) throws Exception {
        Map<String, byte[]> copy = new LinkedHashMap<>(entries);
        copy.keySet().removeIf(k -> k.startsWith("labels/"));
        Method m = InferenceRunner.class.getDeclaredMethod("selectSamples", Map.class);
        m.setAccessible(true);
        return (List<String>) m.invoke(null, copy);
    }

    private static byte[] invokeLookupLabel(Map<String, byte[]> labels, String sample) throws Exception {
        Method m = InferenceRunner.class.getDeclaredMethod("lookupLabel", Map.class, String.class);
        m.setAccessible(true);
        return (byte[]) m.invoke(null, labels, sample);
    }
}
