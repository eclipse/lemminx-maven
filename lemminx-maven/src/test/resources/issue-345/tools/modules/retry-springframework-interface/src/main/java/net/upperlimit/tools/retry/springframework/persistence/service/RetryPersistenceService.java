package net.upperlimit.tools.retry.springframework.persistence.service;

/**
 * Fault tolerance entry-point interface for retry of both front-end and
 * back-end persistence actions
 * 
 * @author eliasbalasis
 */
public interface RetryPersistenceService {

	RetryPersistenceFrontEndService getFrontEndRetryService();

	RetryPersistenceBackEndService getBackEndRetryService();
}
