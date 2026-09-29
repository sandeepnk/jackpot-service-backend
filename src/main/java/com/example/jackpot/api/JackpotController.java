package com.example.jackpot.api;

import com.example.jackpot.domain.JackpotSnapshot;
import com.example.jackpot.persistence.JackpotRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jackpots")
public class JackpotController {
  private final JackpotRepository repository;

  public JackpotController(JackpotRepository repository) {
    this.repository = repository;
  }

  @GetMapping
  public List<JackpotSnapshot> list() {
    return repository.findAll();
  }
}
