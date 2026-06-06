package com.microsservico_gateway.controller;

import com.microsservico_gateway.service.GatewayService;
import com.seguranca.DadosEvento;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
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

    @PostMapping("/votos")
    public ResponseEntity<String> votar(@RequestBody DadosEvento dados) {
        try {
            gatewayService.votar(dados);
            return ResponseEntity.ok("Voto registrado.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Erro: " + e.getMessage());
        }
    }

    @GetMapping("/promocoes")
    public ResponseEntity<List<String>> listarPromocoes() {
        return ResponseEntity.ok(gatewayService.listarPromocoes());
    }
}