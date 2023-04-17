package net.upperlimit.tools.retry.springframework.persistence.service.impl;

import net.upperlimit.tools.retry.springframework.AbstractRetryService;
import net.upperlimit.tools.retry.springframework.persistence.service.RetryPersistenceBackEndService;

/**
 * Fault tolerance abstraction for retry of back-end persistence actions.
 * Applications must provide a concrete implementation.
 * 
 * @author eliasbalasis
 */
public abstract class AbstractRetryPersistenceBackEndService extends AbstractRetryService
		implements RetryPersistenceBackEndService {
}
