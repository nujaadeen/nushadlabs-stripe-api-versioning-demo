package com.demo.versioning.transformer;

import com.demo.versioning.version.ApiVersion;

import java.util.Map;

public interface ResponseTransformer {

    Map<String, Object> transform(Map<String, Object> body);

    ApiVersion fromVersion();

    ApiVersion toVersion();
}
