package com.demo.versioning.response;

import com.demo.versioning.core.PaymentResponse;
import com.demo.versioning.version.ApiVersion;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ResponseCompatibilityService {

    private final ObjectMapper objectMapper;
    private final List<ResponseTransformer> transformers; // injected in @Order order

    public Map<String, Object> toVersionedResponse(PaymentResponse paymentResponse, ApiVersion targetVersion) {
        Map<String, Object> response = objectMapper.convertValue(paymentResponse, new TypeReference<>() {});

        for (ResponseTransformer transformer : transformers) {
            response = transformer.transform(response, targetVersion);
        }

        return response;
    }
}
