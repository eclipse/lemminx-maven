package net.upperlimit.tools.retry.springframework;

import org.springframework.retry.RecoveryCallback;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryState;
import org.springframework.retry.support.RetryTemplate;

/**
 * Definition of fault tolerant persistence action exposing
 * {@link RetryTemplate#execute(RetryCallback, RecoveryCallback, RetryState)}
 * functionality
 * 
 * @author net.upperlimit.tools.retry.springframework
 */
public final class RetryDefinitionWithRecoveryAndState<T, E extends Throwable> {

	private final RetryCallback<T, E> retryCallback;
	private final RecoveryCallback<T> recoveryCallback;
	private final RetryState retryState;

	public RetryCallback<T, E> getRetryCallback() {

		return retryCallback;
	}

	public RecoveryCallback<T> getRecoveryCallback() {

		return recoveryCallback;
	}

	public RetryState getRetryState() {

		return retryState;
	}

	public RetryDefinitionWithRecoveryAndState( //
			final RetryCallback<T, E> retryCallback, //
			final RecoveryCallback<T> recoveryCallback, //
			final RetryState retryState //
	) {

		this.retryCallback = retryCallback;
		this.recoveryCallback = recoveryCallback;
		this.retryState = retryState;
	}
}
