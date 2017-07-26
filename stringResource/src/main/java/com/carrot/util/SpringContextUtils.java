package com.carrot.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class SpringContextUtils implements ApplicationContextAware {

	// Spring ApplicationContext
	private static ApplicationContext applicationContext;

	public static boolean cameraHealthOK = false;

	public static boolean connectStateOK = true;

	// public static Client client;

	/**
	 *  
	 */
	public void setApplicationContext(ApplicationContext applicationContext) {
		// client = new Client();
		// linkedList = new LinkedList<List<FaceInfoModel>>();
		SpringContextUtils.applicationContext = applicationContext;
	}

	/**
	 * @return ApplicationContext
	 */
	public static ApplicationContext getApplicationContext() {
		// client = new Client();

		return applicationContext;
	}

	/**
	 * 
	 * @Title: getBean @Description: TODO @param @param
	 *         name @param @return @param @throws BeansException @return
	 *         Object @throws
	 */
	public static Object getBean(String name) throws BeansException {
		return applicationContext.getBean(name);
	}
}
