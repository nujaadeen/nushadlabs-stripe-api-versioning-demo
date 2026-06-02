package com.demo.versioning.response;

import com.demo.versioning.version.ApiVersion;

import java.util.Map;

public interface ResponseTransformer {
    Map<String, Object> transform(Map<String, Object> response, ApiVersion targetVersion);
}
