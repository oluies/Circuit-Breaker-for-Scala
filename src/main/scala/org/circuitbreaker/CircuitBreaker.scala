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

import scala.collection.immutable.HashMap
import java.util.concurrent.atomic.{AtomicLong, AtomicReference, AtomicInteger}

/**
 * holder companion object for creating and retrieving all
 * configured CircuitBreaker (CircuitBreaker) instances
 * (Enhancements could be to put some clever ThreadLocal stuff in here)
 *
 * @author Christopher Schmidt
 */
object CircuitBreaker {

  private var circuitBreaker = HashMap[String, CircuitBreaker]()

  /**
   * factory method
   * creates a new CircuitBreaker with a given name and configuration
   */
  def addCircuitBreaker(name: String, config: CircuitBreakerConfiguration): Unit = synchronized {
    circuitBreaker.get(name) match {
      case None    => circuitBreaker += ((name, new CircuitBreakerImpl(config)))
      case Some(_) => throw new IllegalArgumentException("CircuitBreaker " + name + " already configured")
    }
  }

  /** Remove a CircuitBreaker from the registry. Primarily useful in tests. */
  def removeCircuitBreaker(name: String): Unit = synchronized {
    circuitBreaker -= name
  }

  /** CircuitBreaker retrieve method */
  private[circuitbreaker] def apply(name: String): CircuitBreaker = synchronized {
    circuitBreaker.getOrElse(
      name,
      throw new IllegalArgumentException("CircuitBreaker " + name + " not configured")
    )
  }
}

/**
 * Basic MixIn for using CircuitBreaker Scope method
 */
trait UsingCircuitBreaker {
  def withCircuitBreaker[T](name: String)(f: => T): T =
    CircuitBreaker(name).invoke(f)
}

/**
 * simple case class that holds configuration parameter
 *
 * @param timeout timeout for trying again (in milliseconds)
 * @param failureThreshold threshold of errors till breaker will open
 */
case class CircuitBreakerConfiguration(timeout: Long, failureThreshold: Int)

/**
 * Interface definition for CircuitBreaker
 */
private[circuitbreaker] trait CircuitBreaker {
  var failureCount: Int
  var tripTime: Long

  def invoke[T](f: => T): T
  def trip: Unit
  def resetFailureCount: Unit
  def attemptReset: Unit
  def reset: Unit
  def failureThreshold: Int
  def timeout: Long
}

/**
 * CircuitBreaker base class for all configuration things
 * holds all thread safe (atomic) private members
 */
private[circuitbreaker] abstract class CircuitBreakerBase(config: CircuitBreakerConfiguration) extends CircuitBreaker {

  private val _state = new AtomicReference[States]
  private val _failureThreshold = new AtomicInteger(config.failureThreshold)
  private val _timeout = new AtomicLong(config.timeout)
  private val _failureCount = new AtomicInteger(0)
  private val _tripTime = new AtomicLong

  protected def state_=(s: States): Unit = _state.set(s)
  protected def state: States = _state.get

  def failureThreshold: Int = _failureThreshold.get
  def timeout: Long = _timeout.get

  def failureCount_=(i: Int): Unit = _failureCount.set(i)
  def failureCount: Int = _failureCount.incrementAndGet

  def tripTime_=(l: Long): Unit = _tripTime.set(l)
  def tripTime: Long = _tripTime.get
}

/**
 * CircuitBreaker implementation class for changing states
 */
private[circuitbreaker] class CircuitBreakerImpl(config: CircuitBreakerConfiguration) extends CircuitBreakerBase(config) {
  reset

  def reset: Unit = {
    resetFailureCount
    state = new ClosedState(this)
  }

  def resetFailureCount: Unit =
    failureCount = 0

  def attemptReset: Unit =
    state = new HalfOpenState(this)

  def trip: Unit = {
    tripTime = System.currentTimeMillis
    state = new OpenState(this)
  }

  def invoke[T](f: => T): T = {
    state.preInvoke
    try {
      val ret = f
      state.postInvoke
      ret
    } catch {
      case e: Throwable =>
        state.onError(e)
        throw e
    }
  }
}
