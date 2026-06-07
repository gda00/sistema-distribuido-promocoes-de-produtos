package com.microsservico_gateway.controller;

import com.microsservico_gateway.service.GatewayService;
import com.seguranca.DadosEvento;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;

@RestController
@CrossOrigin(origins = "*")
public class GatewayController {

    private final GatewayService gatewayService;

    public GatewayController(GatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @PostMapping("api/promocoes")
    public ResponseEntity<String> cadastrarPromocao(@RequestBody DadosEvento dados) {
        try {
            gatewayService.cadastrarPromocao(dados);
            return ResponseEntity.ok("Promoção enviada.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Erro: " + e.getMessage());
        }
    }

    @GetMapping("api/promocoes")
    public ResponseEntity<List<String>> listarPromocoes() {
        return ResponseEntity.ok(gatewayService.listarPromocoes());
    }

    @PostMapping("api/promocoes/{id}/votos")
    public ResponseEntity<String> votar(
            @PathVariable String id,
            @RequestBody DadosEvento voto) {
        try {
            gatewayService.votar(id, voto);
            return ResponseEntity.ok("Voto registrado.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Erro: " + e.getMessage());
        }
    }

    @PostMapping("api/interesses")
    public ResponseEntity<String> adicionarInteresse(@RequestBody Map<String, String> body) {
        gatewayService.adicionarInteresse(body.get("clientId"), body.get("categoria"));
        return ResponseEntity.ok("Interesse registrado.");
    }

    @DeleteMapping("api/interesses/{categoria}")
    public ResponseEntity<String> removerInteresse(
            @PathVariable String categoria,
            @RequestParam String clientId) {
        gatewayService.removerInteresse(clientId, categoria);
        return ResponseEntity.ok("Interesse cancelado.");
    }

    @GetMapping(value = "api/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sse(@RequestParam String clientId) {
        return gatewayService.novaConexao(clientId);
    }
}