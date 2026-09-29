package com.example.jackpot.domain;

import java.math.BigDecimal;

public record JackpotSnapshot(String jackpotId, BigDecimal initialPool, BigDecimal currentPool) {}
