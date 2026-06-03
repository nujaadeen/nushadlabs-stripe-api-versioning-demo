package com.demo.versioning.gate;

import com.demo.versioning.version.ApiVersion;

import java.util.Map;

public interface RequestGate {

    Map<String, Object> open(Map<String, Object> body);

    ApiVersion fromVersion();

    ApiVersion toVersion();
}
