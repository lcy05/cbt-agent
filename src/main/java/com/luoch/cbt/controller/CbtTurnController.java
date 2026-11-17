package com.luoch.cbt.controller;

import com.luoch.cbt.dto.TurnRequest;
import com.luoch.cbt.dto.TurnResponse;
import com.luoch.cbt.service.CbtTurnService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CbtTurnController {

    private final CbtTurnService service;

    public CbtTurnController(CbtTurnService service) {
        this.service = service;
    }

    @PostMapping("/api/cbt/turn")
    public TurnResponse turn(@RequestBody TurnRequest request) {
        return service.turn(request);
    }
}
