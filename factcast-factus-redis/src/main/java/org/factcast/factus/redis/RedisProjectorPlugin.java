package org.factcast.factus.redis;

import java.util.Collection;
import java.util.Collections;
import javax.annotation.Nullable;
import org.factcast.factus.projection.Projection;
import org.factcast.factus.projector.ProjectorLens;
import org.factcast.factus.projector.ProjectorPlugin;
import org.factcast.factus.redis.batch.RedisBatched;
import org.factcast.factus.redis.batch.RedisBatchedLens;
import org.factcast.factus.redis.tx.RedisTransactional;
import org.factcast.factus.redis.tx.RedisTransactionalLens;
import org.redisson.api.RedissonClient;

public class RedisProjectorPlugin implements ProjectorPlugin {

  @Nullable
  @Override
  public Collection<ProjectorLens> lensFor(Projection p) {
    if (p instanceof RedisManagedProjection) {

      RedisTransactional transactional = p.getClass().getAnnotation(RedisTransactional.class);
      RedisBatched batched = p.getClass().getAnnotation(RedisBatched.class);

      if (transactional != null && batched != null) {
        throw new IllegalStateException(
            "RedisProjections cannot use both @"
                + RedisTransactional.class.getSimpleName()
                + " and @"
                + RedisBatched.class.getSimpleName()
                + ". Offending class:"
                + p.getClass().getName());
      }

      RedisManagedProjection redisManagedProjection = (RedisManagedProjection) p;
      RedissonClient redissonClient = redisManagedProjection.redisson();

      if (transactional != null) {
        return Collections.singletonList(
            new RedisTransactionalLens(redisManagedProjection, redissonClient));
      }

      //noinspection SingleStatementInBlock
      if (batched != null) {
        return Collections.singletonList(
            new RedisBatchedLens(redisManagedProjection, redissonClient));
      }
    }

    // any other case:
    return Collections.emptyList();
  }
}
