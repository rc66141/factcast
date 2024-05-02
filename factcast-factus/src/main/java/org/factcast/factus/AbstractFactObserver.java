/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.factus;

import static org.factcast.factus.metrics.TagKeys.CLASS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.subscription.FactStreamInfo;
import org.factcast.core.subscription.observer.BatchingFactObserver;
import org.factcast.core.util.ExceptionHelper;
import org.factcast.factus.metrics.FactusMetrics;
import org.factcast.factus.metrics.TimedOperation;
import org.factcast.factus.projection.ProgressAware;
import org.factcast.factus.projection.tx.TransactionAware;

@Slf4j
abstract class AbstractFactObserver extends BatchingFactObserver {
  private static final int MAX_DEFAULT = 1000;

  private final ProgressAware target;
  private final long interval;
  private final FactusMetrics metrics;

  @VisibleForTesting private FactStreamInfo info;

  private long lastProgress = System.currentTimeMillis();
  private boolean caughtUp = false;

  protected AbstractFactObserver(ProgressAware target, long interval, FactusMetrics metrics) {
    super(discoverMaxSize(target));

    this.target = target;
    this.interval = interval;
    this.metrics = metrics;
  }

  private static int discoverMaxSize(ProgressAware target) {
    if (target instanceof TransactionAware) {
      return ((TransactionAware) target).maxBatchSizePerTransaction();
    }
    return MAX_DEFAULT;
  }

  @Override
  public final void onFactStreamInfo(@NonNull FactStreamInfo info) {
    log.trace("received info {}", info);
    this.info = info;
  }

  @SuppressWarnings("java:S2142")
  @Override
  public final void onNext(@NonNull List<Fact> elements) {

    CompletableFuture<Void> reported = null;

    if (caughtUp) {
      // start async
      reported = CompletableFuture.runAsync(() -> reportProcessingLatency(elements));
    }

    onNextFacts(elements);

    // not yet caught up
    long now = System.currentTimeMillis();
    if (info != null && now - lastProgress > interval) {
      lastProgress = now;
      Fact last = Iterables.getLast(elements);
      if (last != null) {
        //noinspection DataFlowIssue
        target.catchupPercentage(info.calculatePercentage(last.header().serial()));
      }
    }

    if (reported != null) {
      // wait for async report to return
      try {
        reported.get();
      } catch (Exception e) {
        throw ExceptionHelper.toRuntime(e);
      }
    }
  }

  @Override
  public final void onCatchup() {
    caughtUp = true;
    disableProgressTracking();
    // disable progress tracking
    onCatchupSignal();
  }

  @VisibleForTesting
  void disableProgressTracking() {
    info = null;
  }

  @VisibleForTesting
  // intentionally not async, as metrics timed already is.
  void reportProcessingLatency(@NonNull List<Fact> elements) {
    long nowInMillis = Instant.now().toEpochMilli();
    elements.forEach(
        element -> {
          String ts = element.meta("_ts");
          // _ts might not be there in unit testing for instance.
          if (ts != null) {
            long latency = nowInMillis - Long.parseLong(ts);
            metrics.timed(
                TimedOperation.EVENT_PROCESSING_LATENCY,
                Tags.of(Tag.of(CLASS, target.getClass().getName())),
                latency);
          }
        });
  }

  protected abstract void onCatchupSignal();

  protected abstract void onNextFacts(List<Fact> element);

  FactStreamInfo info() {
    return this.info;
  }
}
