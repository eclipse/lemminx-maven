package net.upperlimit.tools.retry.springframework;

import org.springframework.retry.RecoveryCallback;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;

/**
 * Definition of fault tolerant persistence action exposing
 * {@link RetryTemplate#execute(RetryCallback, RecoveryCallback)} functionality
 * 
 * @author eliasbalasis
 */
public final class RetryDefinitionWithRecovery<T, E extends Throwable> {

	private final RetryCallback<T, E> retryCallback;
	private final RecoveryCallback<T> recoveryCallback;

	public RetryCallback<T, E> getRetryCallback() {

		return retryCallback;
	}

	public RecoveryCallback<T> getRecoveryCallback() {

		return recoveryCallback;
	}

	public RetryDefinitionWithRecovery( //
			final RetryCallback<T, E> retryCallback, //
			final RecoveryCallback<T> recoveryCallback //
	) {

		this.retryCallback = retryCallback;
		this.recoveryCallback = recoveryCallback;
	}
}
