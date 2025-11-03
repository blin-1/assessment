package com.example.tradeprocessor.controller;

import com.example.tradeprocessor.model.InputTrade;
import com.example.tradeprocessor.model.CanonicalTrade;
import com.example.tradeprocessor.service.TradeProcessorService;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Parameter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

@RestController
@Tag(name = "Trades", description = "APIs to ingest and retrieve trades")
@RequestMapping("/api/trades")
public class TradeController {
    private final TradeProcessorService service;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public TradeController(TradeProcessorService service, ObjectMapper objectMapper, Validator validator) {
        this.service = service;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @Operation(summary = "Ingest a single trade", description = "Accepts a JSON payload describing a trade and returns the canonical trade id when created")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Created" , content = @Content(schema = @Schema(implementation = String.class))),
        @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content)
    })
    @PostMapping(consumes = "application/json")
    public ResponseEntity<String> ingest(@Valid @RequestBody @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Trade to ingest", required = true, content = @Content(schema = @Schema(implementation = InputTrade.class))) InputTrade trade) {
        var id = service.processSync(trade);
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    @Operation(summary = "Upload a file of trades", description = "Accepts CSV or JSON files (or newline-delimited JSON) and ingests each record")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Accepted", content = @Content),
        @ApiResponse(responseCode = "400", description = "Bad request", content = @Content)
    })
    @PostMapping(path = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<Object> uploadFile(
        @Parameter(description = "File containing trades (CSV or JSON)") @RequestParam("file") MultipartFile file,
        @Parameter(description = "When true, the first CSV record will be treated as header and skipped from data rows") @RequestParam(value = "skipHeader", required = false) Boolean skipHeader
    ) throws IOException {
    String rawFilename = file.getOriginalFilename();
    String filename = rawFilename == null ? "" : rawFilename.toLowerCase();
    String rawContentType = file.getContentType();
    String contentType = rawContentType == null ? "" : rawContentType.toLowerCase();
        boolean isCsv = filename.endsWith(".csv") || contentType.contains("csv");

        var accepted = new ArrayList<String>();
        Map<Integer, List<String>> errors = new HashMap<>();
        Map<Integer, List<String>> parseErrors = new HashMap<>();

        // Stream the file to avoid loading large uploads into memory.
        try (InputStream in = file.getInputStream(); Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            if (isCsv) {
                boolean skipHeaderFlag = skipHeader == null ? true : skipHeader.booleanValue();
                var format = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(skipHeaderFlag)
                    .setIgnoreSurroundingSpaces(true)
                    .build();
                var parser = format.parse(reader);
                var headerMap = parser.getHeaderMap();
                for (CSVRecord record : parser) {
                    int originalRow = (int) record.getRecordNumber() - 1; // zero-based
                    try {
                        var t = new InputTrade();
                        String v;
                        v = csvValue(record, headerMap, "tradeId", "trade_id", "tradeid");
                        if (v != null && !v.isBlank()) t.setTradeId(stripQuotes(v));

                        v = csvValue(record, headerMap, "accountNumber", "account_number", "accountnumber");
                        if (v != null && !v.isBlank()) t.setAccountNumber(stripQuotes(v));

                        v = csvValue(record, headerMap, "accountName", "account_name", "accountname");
                        if (v != null && !v.isBlank()) t.setAccountName(stripQuotes(v));

                        v = csvValue(record, headerMap, "amount");
                        if (v != null && !v.isBlank()) t.setAmount(Double.parseDouble(stripQuotes(v)));

                        v = csvValue(record, headerMap, "currency");
                        if (v != null && !v.isBlank()) t.setCurrency(stripQuotes(v));

                        v = csvValue(record, headerMap, "tradeDate", "trade_date", "tradedate");
                        if (v != null && !v.isBlank()) t.setTradeDate(Instant.parse(stripQuotes(v)));

                        Set<ConstraintViolation<InputTrade>> violations = validator.validate(t);
                        if (violations.isEmpty()) {
                            service.processAsync(t);
                            accepted.add(t.getTradeId() == null ? "<no-trade-id>" : t.getTradeId());
                        } else {
                            var msgs = new ArrayList<String>();
                            for (var v2 : violations) msgs.add(v2.getPropertyPath() + ": " + v2.getMessage());
                            errors.put(originalRow, msgs);
                        }
                    } catch (Exception e) {
                        parseErrors.computeIfAbsent(originalRow, k -> new ArrayList<>()).add("parse error: " + e.getMessage());
                    }
                }
            } else {
                // Try streaming JSON array or newline-delimited JSON
                try {
                    MappingIterator<InputTrade> it = objectMapper.readerFor(InputTrade.class).readValues(reader);
                    int idx = 0;
                    while (it.hasNext()) {
                        try {
                            InputTrade t = it.next();
                            Set<ConstraintViolation<InputTrade>> violations = validator.validate(t);
                            if (violations.isEmpty()) {
                                service.processAsync(t);
                                accepted.add(t.getTradeId() == null ? "<no-trade-id>" : t.getTradeId());
                            } else {
                                var msgs = new ArrayList<String>();
                                for (var v2 : violations) msgs.add(v2.getPropertyPath() + ": " + v2.getMessage());
                                errors.put(idx, msgs);
                            }
                        } catch (Exception e) {
                            parseErrors.computeIfAbsent(idx, k -> new ArrayList<>()).add("parse error: " + e.getMessage());
                        }
                        idx++;
                    }
                } catch (Exception e) {
                    // Fallback: try line-delimited JSON
                    try (InputStream in2 = file.getInputStream(); BufferedReader br = new BufferedReader(new InputStreamReader(in2, StandardCharsets.UTF_8))) {
                        String line;
                        int idx = 0;
                        while ((line = br.readLine()) != null) {
                            if (line.isBlank()) { idx++; continue; }
                            try {
                                InputTrade t = objectMapper.readValue(line, InputTrade.class);
                                Set<ConstraintViolation<InputTrade>> violations = validator.validate(t);
                                if (violations.isEmpty()) {
                                    service.processAsync(t);
                                    accepted.add(t.getTradeId() == null ? "<no-trade-id>" : t.getTradeId());
                                } else {
                                    var msgs = new ArrayList<String>();
                                    for (var v2 : violations) msgs.add(v2.getPropertyPath() + ": " + v2.getMessage());
                                    errors.put(idx, msgs);
                                }
                            } catch (Exception ex) {
                                parseErrors.computeIfAbsent(idx, k -> new ArrayList<>()).add("parse error: " + ex.getMessage());
                            }
                            idx++;
                        }
                    }
                }
            }
        }

        // merge parse errors (if any) into the errors map so callers see which rows failed to parse
        for (var e : parseErrors.entrySet()) {
            errors.put(e.getKey(), e.getValue());
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("accepted", accepted);
        resp.put("errors", errors);
        return ResponseEntity.accepted().body(resp);
    }

    // Backwards-compatible overload used by some unit tests / programmatic callers.
    public ResponseEntity<Object> uploadFile(MultipartFile file) throws IOException {
        return uploadFile(file, null);
    }

    @Operation(summary = "Get canonical trade by id", description = "Returns the canonical representation of a previously ingested trade")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CanonicalTrade.class))),
        @ApiResponse(responseCode = "404", description = "Not found", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<CanonicalTrade> get(@Parameter(description = "Canonical id of the trade to retrieve", required = true) @PathVariable String id) {
        var c = service.getCanonical(id);
        if (c == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(c);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err -> errors.put(err.getField(), err.getDefaultMessage()));
        Map<String, Object> body = new HashMap<>();
        body.put("message", "Validation failed");
        body.put("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    private String stripQuotes(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("^\"|\"$", "");
    }

    // find a header in headerMap that matches any of the provided names (case-insensitive)
    private String csvValue(CSVRecord record, Map<String,Integer> headerMap, String... names) {
        if (headerMap == null || headerMap.isEmpty()) return null;
        for (String want : names) {
            // try exact first
            if (headerMap.containsKey(want)) {
                try { return record.get(want); } catch (IllegalArgumentException ignored) {}
            }
        }
        // try case-insensitive match
        for (String key : headerMap.keySet()) {
            for (String want : names) {
                if (key.equalsIgnoreCase(want)) {
                    try { return record.get(key); } catch (IllegalArgumentException ignored) {}
                }
            }
        }
        return null;
    }
}
