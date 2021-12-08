package org.factcast.server.grpc.metrics;

import io.micrometer.core.instrument.Tags;
import java.util.function.Supplier;
import lombok.SneakyThrows;
import org.factcast.factus.metrics.RunnableWithException;
import org.factcast.factus.metrics.SupplierWithException;
import org.factcast.server.grpc.metrics.ServerMetrics.EVENT;
import org.factcast.server.grpc.metrics.ServerMetrics.OP;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NOPServerMetricsTest {

  @InjectMocks private NOPServerMetrics underTest;

  @Nested
  class WhenTimeding {
    private final OP OPERATION = OP.HANDSHAKE;
    @Mock  Runnable r;
    @Mock private RunnableWithException re;
    @Mock Supplier s;
    @Mock SupplierWithException se;

    @BeforeEach
    void setup() {}

    @Test
    void timedOPRunnable() {
      underTest.timed(OP.HANDSHAKE, r);
      Mockito.verify(r).run();
    }

    @Test
    void timedOPTagRunnable() {
      underTest.timed(OP.HANDSHAKE, Tags.of("foo", "bar"), r);
      Mockito.verify(r).run();
    }

    @SneakyThrows
    @Test
    void timedOPExceptionRunnable() {
      underTest.timed(OP.HANDSHAKE, IllegalArgumentException.class, re);
      Mockito.verify(re).run();
    }

    @SneakyThrows
    @Test
    void timedOPExceptionTAgRunnable() {
      underTest.timed(OP.HANDSHAKE, IllegalArgumentException.class, Tags.of("foo", "bar"), re);
      Mockito.verify(re).run();
    }

    // --------

    @Test
    void timedOPSupplier() {
      underTest.timed(OP.HANDSHAKE, s);
      Mockito.verify(s).get();
    }

    @Test
    void timedOPTagSupplier() {
      underTest.timed(OP.HANDSHAKE, Tags.of("foo", "bar"), s);
      Mockito.verify(s).get();
    }

    @SneakyThrows
    @Test
    void timedOPExceptionSupplier() {
      underTest.timed(OP.HANDSHAKE, IllegalArgumentException.class, se);
      Mockito.verify(se).get();
    }

    @SneakyThrows
    @Test
    void timedOPExceptionTagSupplier() {
      underTest.timed(OP.HANDSHAKE, IllegalArgumentException.class, Tags.of("foo", "bar"), se);
      Mockito.verify(se).get();
    }
  }

  @Nested
  class WhenCounting {
    private final EVENT E = EVENT.SOME_EVENT_CHANGE_ME;

    @BeforeEach
    void setup() {}

    @Test
    void count() {
      underTest.count(E);
      // does not throw
    }

    @Test
    void countTag() {
      underTest.count(E, Tags.of("foo", "bar"));
      // does not throw
    }
  }

}