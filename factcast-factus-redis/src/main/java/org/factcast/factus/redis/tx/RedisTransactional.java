package org.factcast.factus.redis.tx;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;
import lombok.val;
import org.redisson.api.TransactionOptions;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RedisTransactional {
  int size() default 50;

  long timeout() default Defaults.timeout;

  long responseTimeout() default Defaults.responseTimeout;

  int retryAttempts() default Defaults.retryAttempts;

  long retryInterval() default Defaults.retryInterval;

  class Defaults {
    static final long timeout = 30000;
    static final long responseTimeout = 5000;
    static final int retryAttempts = 5;
    static final long retryInterval = 3000;

    static TransactionOptions create() {
      return TransactionOptions.defaults()
          .timeout(timeout, TimeUnit.MILLISECONDS)
          .responseTimeout(responseTimeout, TimeUnit.MILLISECONDS)
          .retryAttempts(retryAttempts)
          .retryInterval(retryInterval, TimeUnit.MILLISECONDS);
    }

    public static TransactionOptions with(RedisTransactional transactional) {
      val opts = create();

      if (transactional != null) {

        long responseTimeout = transactional.responseTimeout();
        if (responseTimeout > 0) {
          opts.responseTimeout(responseTimeout, TimeUnit.MILLISECONDS);
        }

        int retryAttempts = transactional.retryAttempts();
        if (retryAttempts > 0) {
          opts.retryAttempts(retryAttempts);
        }

        long retryInterval = transactional.retryInterval();
        if (retryInterval > 0) {
          opts.retryInterval(retryInterval, TimeUnit.MILLISECONDS);
        }

        long timeout = transactional.timeout();
        if (timeout > 0) {
          opts.timeout(timeout, TimeUnit.MILLISECONDS);
        }
      }

      return opts;
    }
  }
}
