package net.upperlimit.tools.retry.springframework;

import org.springframework.retry.ExhaustedRetryException;
import org.springframework.retry.RecoveryCallback;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryState;
import org.springframework.retry.support.RetryTemplate;

/**
 * Fault tolerance interface for retry of actions based on {@link RetryTemplate}
 * 
 * @author eliasbalasis
 */
public interface RetryService {

	/**
	 * Execute an action with semantics of
	 * {@link RetryTemplate#execute(RetryCallback)}
	 * 
	 * @param definition The underlying definition
	 * @return The result
	 * @throws E fatal error (if any)
	 */
	<T, E extends Throwable> //
	T execute( //
			RetryDefinitionSimple<T, E> definition //
	) throws E;

	/**
	 * Execution with semantics of
	 * {@link RetryTemplate#execute(RetryCallback, RecoveryCallback)}
	 * 
	 * @param definition The underlying definition
	 * @return The result
	 * @throws E fatal error (if any)
	 */
	<T, E extends Throwable> //
	T execute( //
			RetryDefinitionWithRecovery<T, E> definition //
	) throws E;

	/**
	 * Execute an action with semantics of
	 * {@link RetryTemplate#execute(RetryCallback, RetryState)}
	 * 
	 * @param definition The underlying definition
	 * @return The result
	 * @throws E fatal error (if any)
	 */
	<T, E extends Throwable> //
	T execute( //
			RetryDefinitionWithState<T, E> definition //
	) throws E;

	/**
	 * Execute an action with semantics of
	 * {@link RetryTemplate#execute(RetryCallback, RecoveryCallback, RetryState)}
	 * 
	 * @param definition The underlying definition
	 * @return The result
	 * @throws E fatal error (if any)
	 */
	<T, E extends Throwable> //
	T execute( //
			RetryDefinitionWithRecoveryAndState<T, E> definition //
	) throws E;

	/**
	 * see {@link RetryTemplate#execute(RetryCallback)}
	 * 
	 * @param retryCallback see {@link RetryTemplate#execute(RetryCallback)}
	 * @return see {@link RetryTemplate#execute(RetryCallback)}
	 * @throws E see {@link RetryTemplate#execute(RetryCallback)}
	 */
	<T, E extends Throwable> //
	T execute( //
			RetryCallback<T, E> retryCallback //
	) throws E;

	/**
	 * see {@link RetryTemplate#execute(RetryCallback, RecoveryCallback)}
	 * 
	 * @param retryCallback    see
	 *                         {@link RetryTemplate#execute(RetryCallback, RecoveryCallback)}
	 * @param recoveryCallback see
	 *                         {@link RetryTemplate#execute(RetryCallback, RecoveryCallback)}
	 * @return see {@link RetryTemplate#execute(RetryCallback, RecoveryCallback)}
	 * @throws E see {@link RetryTemplate#execute(RetryCallback, RecoveryCallback)}
	 */
	<T, E extends Throwable> //
	T execute( //
			RetryCallback<T, E> retryCallback, //
			RecoveryCallback<T> recoveryCallback //
	) throws E;

	/**
	 * see {@link RetryTemplate#execute(RetryCallback, RetryState)}
	 * 
	 * @param retryCallback see
	 *                      {@link RetryTemplate#execute(RetryCallback, RetryState)}
	 * @param retryState    see
	 *                      {@link RetryTemplate#execute(RetryCallback, RetryState)}
	 * @return see {@link RetryTemplate#execute(RetryCallback, RetryState)}
	 * @throws E                       see
	 *                                 {@link RetryTemplate#execute(RetryCallback, RetryState)}
	 * @throws ExhaustedRetryException see
	 *                                 {@link RetryTemplate#execute(RetryCallback, RetryState)}
	 */
	<T, E extends Throwable> //
	T execute( //
			RetryCallback<T, E> retryCallback, //
			RetryState retryState //
	) throws E, ExhaustedRetryException;

	/**
	 * see
	 * {@link RetryTemplate#execute(RetryCallback, RecoveryCallback, RetryState)}
	 * 
	 * @param retryCallback    see
	 *                         {@link RetryTemplate#execute(RetryCallback, RecoveryCallback, RetryState)}
	 * @param recoveryCallback see
	 *                         {@link RetryTemplate#execute(RetryCallback, RecoveryCallback, RetryState)}
	 * @param retryState       see
	 *                         {@link RetryTemplate#execute(RetryCallback, RecoveryCallback, RetryState)}
	 * @return see
	 *         {@link RetryTemplate#execute(RetryCallback, RecoveryCallback, RetryState)}
	 * @throws E                       see
	 *                                 {@link RetryTemplate#execute(RetryCallback, RecoveryCallback, RetryState)}
	 * @throws ExhaustedRetryException see
	 *                                 {@link RetryTemplate#execute(RetryCallback, RecoveryCallback, RetryState)}
	 */
	<T, E extends Throwable> //
	T execute( //
			RetryCallback<T, E> retryCallback, //
			RecoveryCallback<T> recoveryCallback, //
			RetryState retryState //
	) throws E, ExhaustedRetryException;
}
