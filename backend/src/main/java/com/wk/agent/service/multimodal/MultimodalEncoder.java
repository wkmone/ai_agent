package com.wk.agent.service.multimodal;

import java.util.Map;

public interface MultimodalEncoder {

    String getModalityType();

    boolean supports(String contentType);

    float[] encode(byte[] data);

    float[] encode(String content);

    Map<String, Object> extractMetadata(byte[] data);

    int getEmbeddingDimension();
}
