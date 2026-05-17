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
        try (OrtEnvironment env = OrtEnvironment.getEnvironment();
             OrtSession session = env.createSession(modelBytes, new OrtSession.SessionOptions())) {
            String inputName = firstName(session.getInputInfo());
            String outputName = firstName(session.getOutputInfo());
            String inputShape = shapeOf(session.getInputInfo().get(inputName));
            String outputShape = shapeOf(session.getOutputInfo().get(outputName));
            return new ModelSchema(inputName, inputShape, outputName, outputShape);
        } catch (OrtException e) {
            throw new InvalidOnnxModelException("ONNX model is invalid: " + e.getMessage(), e);
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
        public InvalidOnnxModelException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
