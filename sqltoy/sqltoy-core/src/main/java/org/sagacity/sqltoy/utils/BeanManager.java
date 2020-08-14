package org.sagacity.sqltoy.utils;

import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;

public interface BeanManager {
    public String name();

    public Object getBean(String name);

    public void setBean(String name, Object bean);

    static Map<String, BeanManager> cachedManager = new java.util.concurrent.ConcurrentHashMap<>();

    public static BeanManager getBeanManager(String name) {
        BeanManager beanManager = cachedManager.get(name);
        if (beanManager != null) {
            return beanManager;
        }

        ServiceLoader<BeanManager> beanManagerServices = ServiceLoader.load(BeanManager.class);
        Iterator<BeanManager> it = beanManagerServices.iterator();
        while (it.hasNext()) {
            BeanManager service = it.next();
            if (service.name().equals(name)) {
                synchronized (cachedManager) {
                    cachedManager.put(name, service);
                    return service;
                }
            }
        }
        return null;
    }

    boolean containsBean(String beanName);

    Object getBean(Class<?> forName);

    <T> Map<String, T> getBeansOfType(Class<T> dataSourceClass);
}
