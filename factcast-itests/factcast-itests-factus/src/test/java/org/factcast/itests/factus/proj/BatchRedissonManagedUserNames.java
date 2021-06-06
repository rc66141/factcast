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
package org.factcast.itests.factus.proj;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.Handler;
import org.factcast.factus.projection.BatchApply;
import org.factcast.factus.redis.AbstractRedisProjection;
import org.factcast.factus.redis.RedissonTxManager;
import org.factcast.factus.redis.UUIDCodec;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.event.UserDeleted;
import org.redisson.api.RBatch;
import org.redisson.api.RMap;
import org.redisson.api.RMapAsync;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.codec.CompositeCodec;
import org.redisson.codec.LZ4Codec;
import org.redisson.codec.MarshallingCodec;

@Slf4j
@BatchApply(size = 50)
public class BatchRedissonManagedUserNames extends AbstractRedisProjection {

  private final Codec codec =
      new CompositeCodec(UUIDCodec.INSTANCE, new LZ4Codec(new MarshallingCodec()));

  public BatchRedissonManagedUserNames(RedissonClient redisson) {
    super(redisson);
  }

  public RMap<UUID, String> userNames() {
    return redisson.getMap(redisKey(), codec);
  }

  public int count() {
    return userNames().size();
  }

  public boolean contains(String name) {
    return userNames().containsValue(name);
  }

  public Set<String> names() {
    return new HashSet<>(userNames().values());
  }

  public void clear() {
    userNames().clear();
  }

  // ---- processing:

  // variant 1
  @SneakyThrows
  @Handler
  void apply(UserCreated created, RBatch tx) {
    //    redissonTxManager()
    //        .join(
    //            tx2 -> {
    //              RMap<UUID, String> userNames = tx2.getMap(redisKey());
    //              userNames.put(created.aggregateId(), created.userName());
    //            });

    RMapAsync<Object, Object> userNames = tx.getMap(redisKey(), codec);
    userNames.fastPutAsync(created.aggregateId(), created.userName());
  }

  //  // variant 2
  //  @Handler
  //  void apply(UserDeleted deleted) {
  //    RMap<UUID, String> userNames = redissonTXManager().getOrCreate().getMap(redisKey());
  //    userNames.remove(deleted.aggregateId());
  //  }

  //  // variant 3
  @Handler
  void apply(UserDeleted deleted) {
    RedissonTxManager txm = RedissonTxManager.get(redisson);
    txm.join(
        tx -> {
          tx.getMap(redisKey(), codec).fastRemove(deleted.aggregateId());
        });
  }
}
