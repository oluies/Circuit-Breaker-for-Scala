/*
 * Copyright Christopher Schmidt 2010
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.circuitbreaker

/**
 * CircuitBreaker states base class
 */
private[circuitbreaker] abstract class States {
  def preInvoke: Unit
  def postInvoke: Unit
  def onError(e: Throwable): Unit
}

/**
 * CircuitBreaker is closed, normal operation
 */
private[circuitbreaker] class ClosedState(cb: CircuitBreaker) extends States {
  def onError(e: Throwable): Unit = {
    val currentCount = cb.failureCount
    val threshold = cb.failureThreshold
    if (currentCount >= threshold)
      cb.trip
  }

  def postInvoke: Unit = cb.resetFailureCount

  def preInvoke: Unit = ()
}

/**
 * CircuitBreaker is open. Calls are failing fast
 */
private[circuitbreaker] class OpenState(cb: CircuitBreaker) extends States {
  def onError(e: Throwable): Unit = ()

  def postInvoke: Unit = ()

  def preInvoke: Unit = {
    val now = System.currentTimeMillis
    val elapsed = now - cb.tripTime
    if (elapsed <= cb.timeout)
      throw new CircuitBreakerOpenException("Circuit Breaker is open; calls are failing fast")
    cb.attemptReset
  }
}

/**
 * CircuitBreaker is half open. Calls are still failing after timeout
 */
private[circuitbreaker] class HalfOpenState(cb: CircuitBreaker) extends States {
  def onError(e: Throwable): Unit = {
    cb.trip
    throw new CircuitBreakerHalfOpenException("Circuit Breaker is half open; calls are still failing after timeout", e)
  }

  def postInvoke: Unit = cb.reset

  def preInvoke: Unit = ()
}
