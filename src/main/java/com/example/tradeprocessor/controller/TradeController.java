package com.example.tradeprocessor.controller;

import com.example.tradeprocessor.model.InputTrade;
import com.example.tradeprocessor.model.CanonicalTrade;
import com.example.tradeprocessor.service.TradeProcessorService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
 
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
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

    @PostMapping(consumes = "application/json")
    public ResponseEntity<String> ingest(@Valid @RequestBody InputTrade trade) {
        var id = service.processSync(trade);
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    @PostMapping(path = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<Object> uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
        var text = new String(file.getBytes());
        List<InputTrade> trades = new ArrayList<>();
        // try parse as JSON array
        try {
            trades = objectMapper.readValue(text, new TypeReference<List<InputTrade>>(){});
        } catch (Exception e) {
            // fallback: newline separated JSON
            for (var line : text.split("\\r?\\n")) {
                if (line.isBlank()) continue;
                trades.add(objectMapper.readValue(line, InputTrade.class));
            }
        }

        var accepted = new ArrayList<String>();
        Map<Integer, List<String>> errors = new HashMap<>();

        for (int i = 0; i < trades.size(); i++) {
            var t = trades.get(i);
            Set<ConstraintViolation<InputTrade>> violations = validator.validate(t);
            if (violations.isEmpty()) {
                service.processAsync(t);
                accepted.add(t.getTradeId() == null ? "<no-trade-id>" : t.getTradeId());
            } else {
                var msgs = new ArrayList<String>();
                for (var v : violations) msgs.add(v.getPropertyPath() + ": " + v.getMessage());
                errors.put(i, msgs);
            }
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("accepted", accepted);
        resp.put("errors", errors);
        return ResponseEntity.accepted().body(resp);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CanonicalTrade> get(@PathVariable String id) {
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
}
