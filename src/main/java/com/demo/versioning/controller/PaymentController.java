package com.demo.versioning.controller;

import com.demo.versioning.core.PaymentRequest;
import com.demo.versioning.core.PaymentService;
import com.demo.versioning.response.ResponseCompatibilityService;
import com.demo.versioning.version.ApiVersion;
import com.demo.versioning.version.VersionContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Stripe-style date-versioned payment API")
public class PaymentController {

    private final PaymentService paymentService;
    private final ResponseCompatibilityService responseCompatibilityService;

    @PostMapping("/payments")
    @Operation(
            summary = "Create a payment",
            description = """
                    Processes a payment and returns a response shaped to match the API version
                    supplied in the `Stripe-Api-Version` header. Omit the header to use the
                    server default (`2020-01-01`).

                    **Version behaviour**
                    | Version | billingDetails | currency | legacy_amount |
                    |---|---|---|---|
                    | 2020-01-01 | flat (billing_*) | removed | added |
                    | 2022-06-15 | flat (billing_*) | removed | — |
                    | 2024-03-10 | nested object | present | — |
                    """,
            parameters = @Parameter(
                    name = "Stripe-Api-Version",
                    description = "API version date (YYYY-MM-DD). Supported: 2020-01-01, 2022-06-15, 2024-03-10.",
                    example = "2024-03-10",
                    schema = @Schema(type = "string"),
                    required = false
            ),
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "customerId": "cust_abc123",
                                      "amount": 49.99,
                                      "currency": "USD",
                                      "billingDetails": {
                                        "name": "Jane Doe",
                                        "email": "jane@example.com",
                                        "addressLine1": "123 Main St",
                                        "city": "New York",
                                        "country": "US"
                                      }
                                    }
                                    """)
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Payment succeeded — shape varies by version"),
                    @ApiResponse(responseCode = "400", description = "Invalid version header or unsupported field for version")
            }
    )
    public ResponseEntity<Map<String, Object>> createPayment(
            @RequestHeader(name = "Stripe-Api-Version", required = false) String versionHeader,
            @RequestBody PaymentRequest paymentRequest) {

        ApiVersion version = VersionContext.get();
        var internalResponse = paymentService.process(paymentRequest);
        var versionedResponse = responseCompatibilityService.toVersionedResponse(internalResponse, version);
        return ResponseEntity.ok(versionedResponse);
    }

    @GetMapping("/versions")
    @Operation(
            summary = "List all API versions",
            description = "Returns every supported API version with its date and changelog description. Not subject to versioning transformation.",
            responses = @ApiResponse(responseCode = "200", description = "Array of version descriptors")
    )
    public ResponseEntity<List<Map<String, Object>>> listVersions() {
        List<Map<String, Object>> versions = Arrays.stream(ApiVersion.values())
                .map(v -> Map.<String, Object>of(
                        "version", v.name(),
                        "date", v.getDate().toString(),
                        "description", v.getDescription()
                ))
                .toList();
        return ResponseEntity.ok(versions);
    }

    @GetMapping("/versions/current")
    @Operation(
            summary = "Resolved version for this request",
            description = "Echoes back the `ApiVersion` that was resolved from the `Stripe-Api-Version` header (or the server default) for this request.",
            parameters = @Parameter(
                    name = "Stripe-Api-Version",
                    description = "API version date (YYYY-MM-DD). Omit to see the server default.",
                    example = "2022-06-15",
                    schema = @Schema(type = "string"),
                    required = false
            ),
            responses = @ApiResponse(responseCode = "200", description = "The resolved version descriptor")
    )
    public ResponseEntity<Map<String, Object>> currentVersion(
            @RequestHeader(name = "Stripe-Api-Version", required = false) String versionHeader) {

        ApiVersion version = VersionContext.get();
        return ResponseEntity.ok(Map.of(
                "resolvedVersion", version.name(),
                "date", version.getDate().toString(),
                "description", version.getDescription()
        ));
    }
}
