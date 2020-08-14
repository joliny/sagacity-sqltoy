package org.sagacity.sqltoy.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SqltoryBeanManager implements BeanManager {
    private static Map<String, Object> hmBeans = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "sqltoy";
    }

    @Override
    public Object getBean(String name) {

        return hmBeans.get(name);
    }

    @Override
    public void setBean(String name, Object bean) {
        synchronized (hmBeans) {
            hmBeans.put(name, bean);
        }
    }

    @Override
    public boolean containsBean(String beanName) {
        return hmBeans.containsKey(beanName);
    }

    @Override
    public Object getBean(Class<?> forName) {
        return hmBeans.get(forName.getCanonicalName());
    }

    @Override
    public <T> Map<String, T> getBeansOfType(Class<T> beanClass) {
        Map<String, T> retMap = new HashMap<>();
        Set<Map.Entry<String, Object>> entrySet = hmBeans.entrySet();
        for (Map.Entry<String, Object> entry : entrySet) {
            if (entry.getKey().equals(beanClass.getCanonicalName())) {
                retMap.put(entry.getKey(), (T) entry.getValue());
            }
        }
        return retMap;
    }
}
