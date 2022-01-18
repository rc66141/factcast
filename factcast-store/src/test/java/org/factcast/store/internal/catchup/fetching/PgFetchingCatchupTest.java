/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.store.internal.catchup.fetching;

import io.micrometer.core.instrument.Counter;
import lombok.NonNull;
import lombok.SneakyThrows;

import org.factcast.core.Fact;
import org.factcast.core.TestFact;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.TransformationException;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.PgPostQueryMatcher;
import org.factcast.store.internal.StoreMetrics;
import org.factcast.store.internal.listen.PgConnectionSupplier;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.rowmapper.PgFactExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.jdbc.PgConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PgFetchingCatchupTest {

  @Mock @NonNull PgConnectionSupplier connectionSupplier;
  @Mock @NonNull StoreConfigurationProperties props;
  @Mock @NonNull SubscriptionRequestTO req;
  @Mock @NonNull PgPostQueryMatcher postQueryMatcher;
  @Mock @NonNull SubscriptionImpl subscription;
  @Mock @NonNull AtomicLong serial;
  @Mock @NonNull PgMetrics metrics;
  @InjectMocks PgFetchingCatchup underTest;

  @Nested
  class WhenRunning {
    @BeforeEach
    void setup() {}

    @SneakyThrows
    @Test
    void connectionHandling() {
      PgConnection con = mock(PgConnection.class);
      when(connectionSupplier.get()).thenReturn(con);

      final var uut = spy(underTest);
      doNothing().when(uut).fetch(any());

      uut.run();

      verify(con).setAutoCommit(false);
      verify(con).close();
    }
  }

  @Nested
  class WhenFetching {
    @Mock @NonNull JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
      Mockito.when(props.getPageSize()).thenReturn(47);
      when(metrics.counter(StoreMetrics.EVENT.CATCHUP_FACT)).thenReturn(new Counter() {
        @Override
        public void increment(double v) {

        }

        @Override
        public double count() {
          return 0;
        }

        @Override
        public Id getId() {
          return null;
        }
      });
    }

    @Test
    void setsCorrectFetchSize() {
      doNothing()
          .when(jdbc)
          .query(anyString(), any(PreparedStatementSetter.class), any(RowCallbackHandler.class));
      underTest.fetch(jdbc);
      verify(jdbc).setFetchSize(eq(props.getPageSize()));
    }
  }

  @Nested
  class WhenCreatingRowCallbackHandler {
    final boolean SKIP_TESTING = true;
    @Mock PgFactExtractor extractor;

    @BeforeEach
    void setup() {}

    @SneakyThrows
    @Test
    void skipsPostQueryMatching() {
      final var cbh = underTest.createRowCallbackHandler(true, extractor);
      cbh.processRow(mock(ResultSet.class));

      verifyNoInteractions(postQueryMatcher);
    }

    @SneakyThrows
    @Test
    void filtersInPostQueryMatching() {
      final var cbh = underTest.createRowCallbackHandler(false, extractor);
      ResultSet rs = mock(ResultSet.class);
      Fact testFact = new TestFact();
      when(extractor.mapRow(same(rs), anyInt())).thenReturn(testFact);
      when(postQueryMatcher.test(testFact)).thenReturn(false);
      cbh.processRow(rs);

      verifyNoInteractions(subscription);
    }

    @SneakyThrows
    @Test
    void notifies() {
      final var cbh = underTest.createRowCallbackHandler(false, extractor);
      ResultSet rs = mock(ResultSet.class);
      Fact testFact = new TestFact();
      when(extractor.mapRow(same(rs), anyInt())).thenReturn(testFact);
      when(postQueryMatcher.test(testFact)).thenReturn(true);
      cbh.processRow(rs);

      verify(subscription).notifyElement(testFact);
    }

    @SneakyThrows
    @Test
    void notifiesTransformationException() {
      final var cbh = underTest.createRowCallbackHandler(false, extractor);
      ResultSet rs = mock(ResultSet.class);
      Fact testFact = new TestFact();
      when(extractor.mapRow(same(rs), anyInt())).thenReturn(testFact);
      when(postQueryMatcher.test(testFact)).thenReturn(true);
      doThrow(TransformationException.class).when(subscription).notifyElement(testFact);
      // just test that it'll be escalated unchanged from the code,
      // so that it can be handled in PgSubscriptionFactory
      assertThatThrownBy(
              () -> {
                cbh.processRow(rs);
              })
          .isInstanceOf(TransformationException.class);
    }
  }
}
