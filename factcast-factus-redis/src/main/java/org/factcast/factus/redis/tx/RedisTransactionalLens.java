package org.factcast.factus.redis.tx;

import com.google.common.annotations.VisibleForTesting;
import java.util.function.Function;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.factus.projector.AbstractTransactionalLens;
import org.factcast.factus.redis.RedisProjection;
import org.factcast.factus.redis.tx.RedisTransactional.Defaults;
import org.redisson.api.RTransaction;
import org.redisson.api.RedissonClient;
import org.redisson.api.TransactionOptions;

@Slf4j
public class RedisTransactionalLens extends AbstractTransactionalLens {

  private final RedissonTxManager redissonTxManager;

  public RedisTransactionalLens(@NonNull RedisProjection p, RedissonClient redissonClient) {
    this(p, RedissonTxManager.get(redissonClient), createOpts(p));
  }

  @VisibleForTesting
  RedisTransactionalLens(
      @NonNull RedisProjection p, RedissonTxManager txman, TransactionOptions opts) {
    super(p);

    redissonTxManager = txman;
    txman.options(opts);

    bulkSize = Math.max(1, getSize(p));
    flushTimeout = calculateFlushTimeout(opts);
    log.trace(
        "Created {} instance for {} with batchsize={},timeout={}",
        getClass().getSimpleName(),
        p,
        bulkSize,
        flushTimeout);
  }

  @VisibleForTesting
  static int getSize(RedisProjection p) {
    RedisTransactional transactional = p.getClass().getAnnotation(RedisTransactional.class);
    if (transactional == null) {
      throw new IllegalStateException(
          "Projection "
              + p.getClass()
              + " is expected to have an annotation @"
              + RedisTransactional.class.getSimpleName());
    }
    return transactional.size();
  }

  @VisibleForTesting
  static TransactionOptions createOpts(RedisProjection p) {
    RedisTransactional transactional = p.getClass().getAnnotation(RedisTransactional.class);
    if (transactional == null) {
      throw new IllegalStateException(
          "Projection "
              + p.getClass()
              + " is expected to have an annotation @"
              + RedisTransactional.class.getSimpleName());
    }
    return Defaults.with(transactional);
  }

  @VisibleForTesting
  static long calculateFlushTimeout(TransactionOptions opts) {
    // "best" guess
    long flush = opts.getTimeout() / 10 * 8;
    if (flush < 80) {
      // disable batching altogether as it is too risky
      flush = 0;
    }
    return flush;
  }

  @Override
  public Function<Fact, ?> parameterTransformerFor(Class<?> type) {
    if (RTransaction.class.equals(type)) {
      return f -> {
        redissonTxManager.startOrJoin();
        return redissonTxManager.getCurrentTransaction();
      };
    }
    return null;
  }

  @Override
  public void doClear() {
    if (redissonTxManager.inTransaction()) {
      redissonTxManager.rollback();
    }
  }

  @Override
  public void doFlush() {
    // otherwise we can silently commit, not to "flush" the logs
    if (redissonTxManager.inTransaction()) {
      redissonTxManager.commit();
    }
  }
}
