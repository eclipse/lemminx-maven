package net.upperlimit.tools.springframework.context;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public final class SpringFrameworkContextAccess implements ApplicationContextAware {

	private static ApplicationContext applicationContext;

	public static <BEAN> BEAN getBean(final Class<BEAN> beanClass) {

		return applicationContext.getBean(beanClass);
	}

	@Override
	public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException {

		SpringFrameworkContextAccess.applicationContext = applicationContext;
	}

	protected SpringFrameworkContextAccess() {
	}
}
