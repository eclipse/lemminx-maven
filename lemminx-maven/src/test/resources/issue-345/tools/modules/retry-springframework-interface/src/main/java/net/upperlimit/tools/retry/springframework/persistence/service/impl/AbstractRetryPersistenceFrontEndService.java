package net.upperlimit.tools.retry.springframework.persistence.service.impl;

import net.upperlimit.tools.retry.springframework.AbstractRetryService;
import net.upperlimit.tools.retry.springframework.persistence.service.RetryPersistenceFrontEndService;

/**
 * Fault tolerance abstraction for retry of front-end persistence actions.
 * Applications must provide a concrete implementation.
 * 
 * @author eliasbalasis
 */
public abstract class AbstractRetryPersistenceFrontEndService extends AbstractRetryService
		implements RetryPersistenceFrontEndService {
}
