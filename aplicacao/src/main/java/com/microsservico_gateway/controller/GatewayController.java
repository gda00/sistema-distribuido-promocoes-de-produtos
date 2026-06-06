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

    @PostMapping("/promocoes")
    public ResponseEntity<String> cadastrarPromocao(@RequestBody DadosEvento dados) {
        try {
            gatewayService.cadastrarPromocao(dados);
            return ResponseEntity.ok("Promoção enviada.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Erro: " + e.getMessage());
        }
    }

    @GetMapping("/promocoes")
    public ResponseEntity<List<String>> listarPromocoes() {
        return ResponseEntity.ok(gatewayService.listarPromocoes());
    }

    @PostMapping("/promocoes/{id}/votos")
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

    @PostMapping("/interesses")
    public ResponseEntity<String> adicionarInteresse(
            @RequestHeader("X-Client-Id") String clienteId,
            @RequestBody Map<String, String> body) {
        gatewayService.adicionarInteresse(clienteId, body.get("categoria"));
        return ResponseEntity.ok("Interesse registrado em: " + body.get("categoria"));
    }

    @DeleteMapping("/interesses/{categoria}")
    public ResponseEntity<String> removerInteresse(
            @RequestHeader("X-Client-Id") String clienteId,
            @PathVariable String categoria) {
        gatewayService.removerInteresse(clienteId, categoria);
        return ResponseEntity.ok("Interesse cancelado: " + categoria);
    }

    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sse() {
        String clienteId = UUID.randomUUID().toString();
        SseEmitter emitter = gatewayService.novaConexao(clienteId);

        try {
            emitter.send(SseEmitter.event().name("connected").data(clienteId));
        } catch (IOException ignored) {
        }

        return emitter;
    }
}