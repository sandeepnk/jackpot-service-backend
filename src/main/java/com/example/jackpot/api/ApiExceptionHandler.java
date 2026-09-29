package com.example.jackpot.api;

import com.example.jackpot.domain.JackpotException;
import com.example.jackpot.messaging.PublicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(JackpotException.class)
  ProblemDetail domain(JackpotException exception) {
    HttpStatus status =
        switch (exception.code()) {
          case JACKPOT_NOT_FOUND, BET_NOT_PROCESSED -> HttpStatus.NOT_FOUND;
          case BET_ID_CONFLICT -> HttpStatus.CONFLICT;
          case INVALID_BET -> HttpStatus.BAD_REQUEST;
        };
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
    problem.setProperty("code", exception.code().name());
    return problem;
  }

  @ExceptionHandler(PublicationException.class)
  ResponseEntity<ProblemDetail> publication(PublicationException exception) {
    log.warn("Bet publication was not confirmed", exception);
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .header(HttpHeaders.RETRY_AFTER, "1")
        .body(
            ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage()));
  }

  @ExceptionHandler(TransientDataAccessException.class)
  ResponseEntity<ProblemDetail> busy(TransientDataAccessException exception) {
    log.warn("Transient database failure", exception);
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .header(HttpHeaders.RETRY_AFTER, "1")
        .body(
            ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "Service is busy; retry with the same bet ID"));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail conflict(DataIntegrityViolationException exception) {
    log.warn("Database constraint rejected a bet", exception);
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT, "Bet conflicts with stored data; verify the bet ID");
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail unexpected(Exception exception) {
    log.error("Unexpected request failure", exception);
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
  }
}
