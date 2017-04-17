package org.factcast.core.store.subscription;

import org.factcast.core.Fact;
import org.slf4j.LoggerFactory;

/**
 * Observer that consumes either Facts or Fact-Ids
 *
 * @author usr
 *
 * @param <T>
 */
public interface FactStoreObserver {

	void onNext(Fact f);

	default void onCatchup() {
	}

	default void onComplete() {
	}

	default void onError(Throwable e) {
		LoggerFactory.getLogger(FactStoreObserver.class).warn("Unhandled onError:", e);
	}

}
