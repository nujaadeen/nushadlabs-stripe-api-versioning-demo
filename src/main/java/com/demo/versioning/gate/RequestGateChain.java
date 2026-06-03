package com.demo.versioning.gate;

import com.demo.versioning.version.ApiVersion;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class RequestGateChain {

    private final List<RequestGate> gates;

    public RequestGateChain(List<RequestGate> gates) {
        this.gates = new ArrayList<>(gates);
    }

    @PostConstruct
    public void sortGates() {
        List<ApiVersion> order = ApiVersion.ordered();
        gates.sort(Comparator.comparingInt(g -> order.indexOf(g.fromVersion())));
    }

    public Map<String, Object> open(Map<String, Object> rawBody, ApiVersion callerVersion) {
        Map<String, Object> body = rawBody;
        for (RequestGate gate : gates) {
            if (!gate.fromVersion().isBefore(callerVersion)) {
                body = gate.open(body);
            }
        }
        return body;
    }
}
