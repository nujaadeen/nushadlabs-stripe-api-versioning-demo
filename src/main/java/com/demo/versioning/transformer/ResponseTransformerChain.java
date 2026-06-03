package com.demo.versioning.transformer;

import com.demo.versioning.core.PaymentResponse;
import com.demo.versioning.version.ApiVersion;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class ResponseTransformerChain {

    private final List<ResponseTransformer> transformers;
    private final ObjectMapper objectMapper;

    public ResponseTransformerChain(List<ResponseTransformer> transformers, ObjectMapper objectMapper) {
        this.transformers = new ArrayList<>(transformers);
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void sortTransformers() {
        List<ApiVersion> order = ApiVersion.ordered();
        // descending: latest fromVersion first so we walk 2024→2022→2020
        transformers.sort(Comparator.comparingInt((ResponseTransformer t) -> order.indexOf(t.fromVersion())).reversed());
    }

    public Map<String, Object> transform(PaymentResponse response, ApiVersion targetVersion) {
        Map<String, Object> body = objectMapper.convertValue(response, new TypeReference<>() {});

        for (ResponseTransformer transformer : transformers) {
            if (!transformer.toVersion().isBefore(targetVersion)) {
                body = transformer.transform(body);
            }
        }

        return body;
    }
}
