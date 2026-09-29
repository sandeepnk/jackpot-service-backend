package com.example.jackpot.domain;

/** Expected domain failures, translated at the HTTP and messaging boundaries. */
public class JackpotException extends RuntimeException {
  public enum Code {
    JACKPOT_NOT_FOUND,
    BET_NOT_PROCESSED,
    BET_ID_CONFLICT,
    INVALID_BET
  }

  private final Code code;

  public JackpotException(Code code, String message) {
    super(message);
    this.code = code;
  }

  public Code code() {
    return code;
  }
}
