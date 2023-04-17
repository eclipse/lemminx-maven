package net.upperlimit.tools.retry.springframework;

import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;

/**
 * Definition of fault tolerant persistence action exposing
 * {@link RetryTemplate#execute(RetryCallback)} functionality
 * 
 * @author eliasbalasis
 */
public final class RetryDefinitionSimple<T, E extends Throwable> {

	private final RetryCallback<T, E> retryCallback;

	public RetryDefinitionSimple( //
			final RetryCallback<T, E> retryCallback //
	) {

		this.retryCallback = retryCallback;
	}

	public RetryCallback<T, E> getRetryCallback() {

		return retryCallback;
	}
}
