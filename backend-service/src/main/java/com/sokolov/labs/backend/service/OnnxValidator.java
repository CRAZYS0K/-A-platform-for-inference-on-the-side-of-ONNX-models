package com.sokolov.labs.backend.service;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;

@Component
public class OnnxValidator {

    public record ModelSchema(String inputName, String inputShape,
                              String outputName, String outputShape) {
    }

    public ModelSchema validate(byte[] modelBytes) {
        if (modelBytes == null || modelBytes.length < 16) {
            throw new InvalidOnnxModelException("Файл слишком мал для ONNX-модели");
        }
        // ONNX is a protobuf — quick sanity check on the first wire-format byte.
        // Tag for field 1 (ir_version, varint) = 0x08. Most legit ONNX models start with it.
        if (modelBytes[0] != 0x08) {
            throw new InvalidOnnxModelException(
                    "Файл не похож на ONNX-модель (неверная сигнатура protobuf)");
        }
        try (OrtEnvironment env = OrtEnvironment.getEnvironment();
             OrtSession session = env.createSession(modelBytes, new OrtSession.SessionOptions())) {
            String inputName = firstName(session.getInputInfo());
            String outputName = firstName(session.getOutputInfo());
            String inputShape = shapeOf(session.getInputInfo().get(inputName));
            String outputShape = shapeOf(session.getOutputInfo().get(outputName));
            return new ModelSchema(inputName, inputShape, outputName, outputShape);
        } catch (OrtException e) {
            throw new InvalidOnnxModelException("ONNX модель повреждена: " + e.getMessage(), e);
        }
    }

    private static String firstName(Map<String, NodeInfo> info) {
        return info.keySet().iterator().next();
    }

    private static String shapeOf(NodeInfo nodeInfo) {
        if (nodeInfo == null || !(nodeInfo.getInfo() instanceof TensorInfo tensorInfo)) {
            return "unknown";
        }
        return Arrays.toString(tensorInfo.getShape());
    }

    public static class InvalidOnnxModelException extends RuntimeException {
        public InvalidOnnxModelException(String message) {
            super(message);
        }

        public InvalidOnnxModelException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
