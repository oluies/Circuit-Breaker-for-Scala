/*
 * Copyright Christopher Schmidt 2010
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.circuitbreaker

import munit.FunSuite

class CircuitBreakerSuite extends FunSuite {

  /** Helper that uses a freshly-named CircuitBreaker per test to avoid global-state collisions. */
  private class Caller(name: String) extends UsingCircuitBreaker {
    def ok(): Unit = withCircuitBreaker(name) { () }
    def fail(): Unit = withCircuitBreaker(name) {
      throw new IllegalArgumentException("boom")
    }
  }

  private def register(name: String, timeoutMs: Long = 100L, threshold: Int = 10): Unit = {
    CircuitBreaker.removeCircuitBreaker(name)
    CircuitBreaker.addCircuitBreaker(name, CircuitBreakerConfiguration(timeoutMs, threshold))
  }

  test("addCircuitBreaker rejects duplicate names") {
    val name = "dup"
    register(name)
    intercept[IllegalArgumentException] {
      CircuitBreaker.addCircuitBreaker(name, CircuitBreakerConfiguration(100, 5))
    }
  }

  test("looking up an unregistered breaker fails") {
    intercept[IllegalArgumentException] {
      new Caller("never-registered-xyz").ok()
    }
  }

  test("remains closed when no exceptions are thrown") {
    val name = "closed"
    register(name)
    val caller = new Caller(name)
    for (_ <- 1 to 50) caller.ok()
  }

  test("trips open after failureThreshold consecutive failures") {
    val name = "trips"
    register(name, timeoutMs = 200L, threshold = 5)
    val caller = new Caller(name)

    // First `threshold` failures pass through as IllegalArgumentException
    for (_ <- 1 to 5) {
      intercept[IllegalArgumentException](caller.fail())
    }

    // Subsequent calls fail fast with CircuitBreakerOpenException while still inside timeout
    for (_ <- 1 to 5) {
      intercept[CircuitBreakerOpenException](caller.fail())
    }
  }

  test("transitions to half-open after timeout, then re-trips on failure") {
    val name = "half-open"
    val timeoutMs = 100L
    register(name, timeoutMs = timeoutMs, threshold = 3)
    val caller = new Caller(name)

    // Trip the breaker.
    for (_ <- 1 to 3) intercept[IllegalArgumentException](caller.fail())

    // Confirm it's open.
    intercept[CircuitBreakerOpenException](caller.fail())

    // Wait past the timeout.
    Thread.sleep(timeoutMs * 2)

    // Next failing call should report as half-open and re-trip.
    intercept[CircuitBreakerHalfOpenException](caller.fail())

    // After re-trip, calls fail fast as open again.
    for (_ <- 1 to 3) intercept[CircuitBreakerOpenException](caller.fail())
  }

  test("recovers (closes) when the half-open call succeeds") {
    val name = "recover"
    val timeoutMs = 80L
    register(name, timeoutMs = timeoutMs, threshold = 2)
    val caller = new Caller(name)

    for (_ <- 1 to 2) intercept[IllegalArgumentException](caller.fail())
    intercept[CircuitBreakerOpenException](caller.fail())

    Thread.sleep(timeoutMs * 2)

    // Successful call in half-open state -> reset to closed.
    caller.ok()

    // Closed again: many more successes succeed without throwing.
    for (_ <- 1 to 10) caller.ok()
  }

  test("returns the wrapped value on success") {
    val name = "return-value"
    register(name)
    val caller = new Object with UsingCircuitBreaker
    val result = caller.withCircuitBreaker(name) { 42 }
    assertEquals(result, 42)
  }
}
