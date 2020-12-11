/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.consensus.ibft;

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.ibft.ibftevent.RoundExpiry;

import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Class for starting and keeping organised round timers */
public class RoundTimer {
  private final IbftExecutors ibftExecutors;
  private Optional<ScheduledFuture<?>> currentTimerTask;
  private final IbftEventQueue queue;
  private final long baseExpiryMillis;

  /**
   * Construct a RoundTimer with primed executor service ready to start timers
   *
   * @param queue The queue in which to put round expiry events
   * @param baseExpirySeconds The initial round length for round 0
   * @param ibftExecutors executor service that timers can be scheduled with
   */
  public RoundTimer(
      final IbftEventQueue queue, final long baseExpirySeconds, final IbftExecutors ibftExecutors) {
    this.queue = queue;
    this.ibftExecutors = ibftExecutors;
    this.currentTimerTask = Optional.empty();
    this.baseExpiryMillis = baseExpirySeconds * 1000;
  }

  /** Cancels the current running round timer if there is one */
  public synchronized void cancelTimer() {
    currentTimerTask.ifPresent(t -> t.cancel(false));
    currentTimerTask = Optional.empty();
  }

  /**
   * Whether there is a timer currently running or not
   *
   * @return boolean of whether a timer is ticking or not
   */
  public synchronized boolean isRunning() {
    return currentTimerTask.map(t -> !t.isDone()).orElse(false);
  }

  /**
   * Starts a timer for the supplied round cancelling any previously active round timer
   *
   * @param round The round identifier which this timer is tracking
   */
  public synchronized void startTimer(final ConsensusRoundIdentifier round) {
    cancelTimer();

    final long expiryTime = baseExpiryMillis * (long) Math.pow(2, round.getRoundNumber());

    final Runnable newTimerRunnable = () -> queue.add(new RoundExpiry(round));

    final ScheduledFuture<?> newTimerTask =
        ibftExecutors.scheduleTask(newTimerRunnable, expiryTime, TimeUnit.MILLISECONDS);
    currentTimerTask = Optional.of(newTimerTask);
  }
}
