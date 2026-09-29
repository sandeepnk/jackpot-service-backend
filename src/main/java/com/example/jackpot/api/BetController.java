package com.example.jackpot.api;

import com.example.jackpot.domain.Bet;
import com.example.jackpot.domain.BetResult;
import com.example.jackpot.service.BetService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bets")
public class BetController {
  private final BetService service;

  public BetController(BetService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<AcceptedBet> publish(@Valid @RequestBody Bet bet) {
    service.submit(bet);
    String resultUrl = "/api/v1/bets/" + bet.betId();
    return ResponseEntity.accepted()
        .location(URI.create(resultUrl))
        .body(new AcceptedBet(bet.betId(), "ACCEPTED", resultUrl));
  }

  @GetMapping("/{betId}")
  public BetResult result(@PathVariable String betId) {
    return service.evaluate(betId);
  }

  /** Evaluation is performed once during consumption; this operation returns that decision. */
  @PostMapping("/{betId}/evaluation")
  public BetResult evaluate(@PathVariable String betId) {
    return service.evaluate(betId);
  }

  public record AcceptedBet(String betId, String status, String resultUrl) {}
}
