package net.upperlimit.tools.retry.springframework;

import org.springframework.retry.ExhaustedRetryException;
import org.springframework.retry.RecoveryCallback;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryState;
import org.springframework.retry.support.RetryTemplate;

/**
 * Fault tolerance abstraction for retry of actions.
 * 
 * @author eliasbalasis
 */
public abstract class AbstractRetryService implements RetryService {

	@Override
	public final <T, E extends Throwable> T execute( //
			final RetryDefinitionSimple<T, E> executor //
	) throws E {

		final RetryCallback<T, E> retryCallBack = //
				executor.getRetryCallback();
		return //
		getRetryTemplate().execute( //
				retryCallBack //
		);
	}

	@Override
	public final <T, E extends Throwable> T execute( //
			final RetryDefinitionWithRecovery<T, E> executor //
	) throws E {

		final RetryCallback<T, E> retryCallBack = //
				executor.getRetryCallback();
		final RecoveryCallback<T> recoveryCallback = //
				executor.getRecoveryCallback();
		return //
		getRetryTemplate().execute( //
				retryCallBack, //
				recoveryCallback //
		);
	}

	@Override
	public final <T, E extends Throwable> T execute( //
			final RetryDefinitionWithState<T, E> executor //
	) throws E {

		final RetryCallback<T, E> retryCallBack = //
				executor.getRetryCallback();
		final RetryState retryState = //
				executor.getRetryState();
		return //
		getRetryTemplate().execute( //
				retryCallBack, //
				retryState //
		);
	}

	@Override
	public final <T, E extends Throwable> T execute( //
			final RetryDefinitionWithRecoveryAndState<T, E> executor //
	) throws E {

		final RetryCallback<T, E> retryCallBack = //
				executor.getRetryCallback();
		final RecoveryCallback<T> recoveryCallback = //
				executor.getRecoveryCallback();
		final RetryState retryState = //
				executor.getRetryState();

		return //
		getRetryTemplate().execute( //
				retryCallBack, //
				recoveryCallback, //
				retryState //
		);
	}

	@Override
	public final <T, E extends Throwable> T execute( //
			final RetryCallback<T, E> retryCallback //
	) throws E {

		return //
		getRetryTemplate().execute( //
				retryCallback //
		);
	}

	@Override
	public final <T, E extends Throwable> T execute( //
			final RetryCallback<T, E> retryCallback, //
			final RecoveryCallback<T> recoveryCallback //
	) throws E {

		return //
		getRetryTemplate().execute( //
				retryCallback, //
				recoveryCallback //
		);
	}

	@Override
	public final <T, E extends Throwable> T execute( //
			final RetryCallback<T, E> retryCallback, //
			final RetryState retryState //
	) throws E, ExhaustedRetryException {

		return //
		getRetryTemplate().execute( //
				retryCallback, //
				retryState //
		);
	}

	@Override
	public final <T, E extends Throwable> T execute( //
			final RetryCallback<T, E> retryCallback, //
			final RecoveryCallback<T> recoveryCallback, //
			final RetryState retryState //
	) throws E, ExhaustedRetryException {

		return //
		getRetryTemplate().execute( //
				retryCallback, //
				recoveryCallback, //
				retryState //
		);
	}

	protected abstract RetryTemplate getRetryTemplate();
}
