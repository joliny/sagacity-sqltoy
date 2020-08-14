/**
 *
 */
package org.sagacity.sqltoy.plugins.sharding;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.utils.BeanManager;
import org.sagacity.sqltoy.utils.ConnectionManager;
import org.sagacity.sqltoy.utils.DataSourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @project sagacity-sqltoy
 * @description 检测sharding涉及到的数据库连接状况, 动态调整权重
 * @author zhongxuchen
 * @version v1.0, Date:2019年9月10日
 * @modify 2019年9月10日, 修改说明
 */
public class IdleConnectionMonitor extends Thread {
    /**
     * 定义日志
     */
    private final Logger logger = LoggerFactory.getLogger(IdleConnectionMonitor.class);

    private Object[][] dataSourceWeightConfig;


    private int[] weights;

    private Integer delaySeconds;

    private Integer intervalSeconds;

    public IdleConnectionMonitor(Object[][] dataSourceWeightConfig,
                                 int[] weights, Integer delaySeconds, Integer intervalSeconds) {
        this.dataSourceWeightConfig = dataSourceWeightConfig;
        this.weights = weights;
        this.delaySeconds = delaySeconds;
        this.intervalSeconds = intervalSeconds;
    }

    @Override
    public void run() {
        // 延时
        try {
            if (delaySeconds >= 1) {
                Thread.sleep(1000 * delaySeconds);
            }
        } catch (InterruptedException e) {
        }
        boolean isRun = true;
        while (isRun) {
            DataSource dataSource = null;
            Connection conn = null;
            PreparedStatement pst = null;
            ResultSet rs = null;
            int i = 0;
            for (Object[] dataBase : dataSourceWeightConfig) {
                try {
                    SqlToyContext sqlToyContext = SqlToyContext.getInstance();
                    String beanManagerName = sqlToyContext.getBeanManagerName();
                    String connectionManagerName = sqlToyContext.getConnectionManagerName();
                    BeanManager beanManager = BeanManager.getBeanManager(beanManagerName);

//                    dataSource = (DataSource) applicationContext.getBean(dataBase[0].toString());
                    dataSource = (DataSource) beanManager.getBean(dataBase[0].toString());
                    // 权重大于零且数据源不为null
                    if (((Integer) dataBase[1]).intValue() > 0 && null != dataSource) {
                        ConnectionManager connectionManager = ConnectionManager.getConnectionManager(connectionManagerName);
//                        conn = org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource);
                        conn = connectionManager.getConnection(dataSource);
                        pst = conn.prepareStatement(DataSourceUtils.getValidateQuery(conn));
                        rs = pst.executeQuery();
                        weights[i] = (Integer) dataBase[1];
                    } else {
                        weights[i] = 0;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    // 发生异常时将权重置为0
                    weights[i] = 0;
                } finally {
                    if (rs != null) {
                        try {
                            rs.close();
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                    }
                    if (pst != null) {
                        try {
                            pst.close();
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                    }
                    if (dataSource != null) {
                        SqlToyContext sqlToyContext = SqlToyContext.getInstance();
                        String connectionManagerName = sqlToyContext.getConnectionManagerName();
                        ConnectionManager connectionManager = ConnectionManager.getConnectionManager(connectionManagerName);
                        connectionManager.releaseConnection(conn, dataSource);
                    }
                }
                i++;
            }

            try {
                // 一秒钟监测一次是否有到时的检测任务
                Thread.sleep(1000 * intervalSeconds);
            } catch (InterruptedException e) {
                logger.warn("datasource sharding 可用性检测监测将终止!{}", e.getMessage(), e);
                isRun = false;
            }
        }
    }
}
