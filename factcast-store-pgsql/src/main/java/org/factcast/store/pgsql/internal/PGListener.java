package org.factcast.store.pgsql.internal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import com.impossibl.postgres.api.jdbc.PGConnection;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
class PGListener implements InitializingBean, DisposableBean {
	// must not be wrapped by any ConnectionPool

	private final PGConnectionSupplier connSup;

	private final @NonNull EventBus bus;

	private final @NonNull Predicate<Connection> tester;

	private PGConnection connection = null;

	private String dataSourceUrl = null;

	@Scheduled(fixedRate = 10000)
	public synchronized void check() {
		if (!tester.test(connection)) {
			log.warn("Reconnecting");
			Connection oldConnection = connection;
			CompletableFuture.runAsync(() -> {
				try {
					oldConnection.close();
				} catch (Throwable e) {
					// silently swallow, connection is probably gone anyway
				}
			});

			listen();
		}
	}

	@VisibleForTesting
	synchronized void listen() {

		try {
			this.connection = connSup.get();
			connection.addNotificationListener(this.getClass().getSimpleName(), PGConstants.CHANNEL_NAME,
					(pid, name, msg) -> {
						log.trace("Recieved event from pg name={} message={}", name, msg);
						bus.post(new FactInsertionEvent(name));

					});
			try (PreparedStatement s = connection.prepareStatement("LISTEN " + PGConstants.CHANNEL_NAME);) {
				s.execute();
			}

		} catch (Throwable e) {
			log.error("Unable to retrieve jdbc Connection: ", e);
		}
	}

	@Value
	public static class FactInsertionEvent {
		String name;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		listen();
	}

	@Override
	public synchronized void destroy() throws Exception {
		if (connection != null) {
			try {
				connection.removeNotificationListener(this.getClass().getSimpleName());
				connection.close();
			} catch (SQLException e) {
				log.warn("During shutdown: ", e);
			}
		}
		connection = null;
	}

	@Autowired
	@org.springframework.beans.factory.annotation.Value("${spring.datasource.url}")
	public void setDataSourceUrl(String dataSourceUrl) {
		this.dataSourceUrl = dataSourceUrl;
	}
}
