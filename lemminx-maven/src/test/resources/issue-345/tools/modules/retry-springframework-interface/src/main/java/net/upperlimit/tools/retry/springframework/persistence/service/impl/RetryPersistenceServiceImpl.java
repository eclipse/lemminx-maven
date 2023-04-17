package net.upperlimit.tools.retry.springframework.persistence.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.upperlimit.tools.retry.springframework.persistence.service.RetryPersistenceBackEndService;
import net.upperlimit.tools.retry.springframework.persistence.service.RetryPersistenceFrontEndService;
import net.upperlimit.tools.retry.springframework.persistence.service.RetryPersistenceService;

/**
 * Fault tolerance entry-point implementation for retry of both front-end and
 * back-end persistence actions
 * 
 * @author eliasbalasis
 */
@Component
public class RetryPersistenceServiceImpl implements RetryPersistenceService {

	@Autowired
	private AbstractRetryPersistenceFrontEndService frontEndService;

	@Autowired
	private AbstractRetryPersistenceBackEndService backEndService;

	@Override
	public RetryPersistenceFrontEndService getFrontEndRetryService() {
		return frontEndService;
	}

	@Override
	public RetryPersistenceBackEndService getBackEndRetryService() {
		return backEndService;
	}
}
