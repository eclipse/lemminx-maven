package net.upperlimit.tools.retry.springframework.persistence.service.impl.dummy;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import net.upperlimit.tools.retry.springframework.persistence.service.impl.AbstractRetryPersistenceFrontEndService;

/**
 * Dummy fault tolerance implementation for retry of front-end persistence
 * actions, for use in environments where fault tolerance is not applicable
 * 
 * @author eliasbalasis
 */
@Component
public class RetryPersistenceFrontEndServiceImpl //
		extends AbstractRetryPersistenceFrontEndService //
		implements InitializingBean //
{

	private RetryTemplate retryTemplate;

	@Override
	protected RetryTemplate getRetryTemplate() {

		return retryTemplate;
	}

	@Override
	public void afterPropertiesSet() throws Exception {

		retryTemplate = //
				new RetryTemplate();
		retryTemplate.setRetryPolicy( //
				new NeverRetryPolicy() //
		);
	}
}
