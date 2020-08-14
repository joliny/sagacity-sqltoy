package org.sagacity.sqltoy.utils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;

public interface ConnectionManager {
    String name();

    Connection getConnection(DataSource datasource);

    void commit();
    void close();
    void rollback();

    void releaseConnection(Connection conn, DataSource datasource);

    static Map<String, ConnectionManager> cachedManager = new java.util.concurrent.ConcurrentHashMap<>();

    public static ConnectionManager getConnectionManager(String name) {
        ConnectionManager connectionManager = cachedManager.get(name);
        if (connectionManager != null) {
            return connectionManager;
        }

        ServiceLoader<ConnectionManager> connectionManagerServices = ServiceLoader.load(ConnectionManager.class);
        Iterator<ConnectionManager> it = connectionManagerServices.iterator();
        while (it.hasNext()) {
            ConnectionManager service = it.next();
            if (service.name().equals(name)) {
                synchronized (cachedManager) {
                    cachedManager.put(name, service);
                    return service;
                }
            }
        }
        return null;
    }
}
