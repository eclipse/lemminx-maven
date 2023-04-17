package net.upperlimit.tools.retry.springframework;

import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryState;
import org.springframework.retry.support.RetryTemplate;

/**
 * Definition of fault tolerant persistence action exposing
 * {@link RetryTemplate#execute(RetryCallback, RetryState)} functionality
 * 
 * @author eliasbalasis
 */
public final class RetryDefinitionWithState<T, E extends Throwable> {

	private final RetryCallback<T, E> retryCallback;
	private final RetryState retryState;

	public RetryCallback<T, E> getRetryCallback() {

		return retryCallback;
	}

	public RetryState getRetryState() {

		return retryState;
	}

	public RetryDefinitionWithState( //
			final RetryCallback<T, E> retryCallback, //
			final RetryState retryState //
	) {

		this.retryCallback = retryCallback;
		this.retryState = retryState;
	}
}
