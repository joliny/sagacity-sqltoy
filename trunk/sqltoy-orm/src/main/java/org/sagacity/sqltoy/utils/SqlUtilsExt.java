/**
 * 
 */
package org.sagacity.sqltoy.utils;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.Date;
import java.util.List;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.plugins.AbstractTypeHandler;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sqltoy-orm
 * @description 提供针对SqlUtil类的扩展,提供更有针对性的操作,提升性能
 * @author zhongxuchen
 * @version v1.0,Date:2015年4月22日
 */
public class SqlUtilsExt {
	/**
	 * 定义日志
	 */
	private final static Logger logger = LoggerFactory.getLogger(SqlUtilsExt.class);

	private SqlUtilsExt() {
	}

	/**
	 * @todo 通过jdbc方式批量插入数据，一般提供给数据采集时或插入临时表使用
	 * @param typeHandler
	 * @param updateSql
	 * @param rowDatas
	 * @param fieldsType
	 * @param fieldsDefaultValue
	 * @param fieldsNullable
	 * @param batchSize
	 * @param autoCommit
	 * @param conn
	 * @param dbType
	 * @return
	 * @throws Exception
	 */
	public static Long batchUpdateByJdbc(AbstractTypeHandler typeHandler, final String updateSql, final List<Object[]> rowDatas,
                                         final Integer[] fieldsType, final String[] fieldsDefaultValue, final Boolean[] fieldsNullable,
                                         final int batchSize, final Boolean autoCommit, final Connection conn, final Integer dbType)
			throws Exception {
		if (rowDatas == null || rowDatas.isEmpty()) {
			logger.warn("batchUpdateByJdbc批量插入或修改数据库操作数据为空!");
			return 0L;
		}
		long updateCount = 0;
		PreparedStatement pst = null;
		// 判断是否通过default转换方式插入
		boolean supportDefaultValue = (fieldsDefaultValue != null && fieldsNullable != null) ? true : false;
		try {
			boolean hasSetAutoCommit = false;
			// 是否自动提交
			if (autoCommit != null && autoCommit.booleanValue() != conn.getAutoCommit()) {
				conn.setAutoCommit(autoCommit.booleanValue());
				hasSetAutoCommit = true;
			}
			pst = conn.prepareStatement(updateSql);
			int totalRows = rowDatas.size();
			// 只有一条记录不采用批量
			boolean useBatch = (totalRows > 1) ? true : false;
			Object[] rowData;
			// 批处理计数器
			int meter = 0;
			Object cellValue;
			int fieldType;
			boolean hasFieldType = (fieldsType != null);
			int[] updateRows;
			for (int i = 0; i < totalRows; i++) {
				rowData = rowDatas.get(i);
				if (rowData != null) {
					// 使用对象properties方式传值
					for (int j = 0, n = rowData.length; j < n; j++) {
						fieldType = (hasFieldType) ? fieldsType[j] : -1;
						if (supportDefaultValue) {
							cellValue = getDefaultValue(rowData[j], fieldsDefaultValue[j], fieldType,
									fieldsNullable[j]);
						} else {
							cellValue = rowData[j];
						}
						SqlUtil.setParamValue(typeHandler, conn, dbType, pst, cellValue, fieldType, j + 1);
					}
					meter++;
					// 批量
					if (useBatch) {
						pst.addBatch();
						// 判断是否是最后一条记录或到达批次量,执行批处理
						if ((meter % batchSize) == 0 || i + 1 == totalRows) {
							updateRows = pst.executeBatch();
							for (int t : updateRows) {
								updateCount = updateCount + ((t > 0) ? t : 0);
							}
							pst.clearBatch();
						}
					} else {
						pst.execute();
						updateCount = updateCount + ((pst.getUpdateCount() > 0) ? pst.getUpdateCount() : 0);
					}
				}
			}
			// 恢复conn原始autoCommit默认值
			if (hasSetAutoCommit) {
				conn.setAutoCommit(!autoCommit);
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			throw e;
		} finally {
			try {
				if (pst != null) {
					pst.close();
					pst = null;
				}
			} catch (SQLException se) {
				logger.error(se.getMessage(), se);
			}
		}
		return updateCount;
	}

	/**
	 * @TODO 针对sqlserver进行特殊化处理(主要针对Timestamp类型的兼容)
	 * @param typeHandler
	 * @param updateSql
	 * @param rowDatas
	 * @param fieldsType
	 * @param fieldsDefaultValue
	 * @param fieldsNullable
	 * @param batchSize
	 * @param autoCommit
	 * @param conn
	 * @param dbType
	 * @return
	 * @throws Exception
	 */
	public static Long batchUpdateBySqlServer(AbstractTypeHandler typeHandler, final String updateSql,
                                              final List<Object[]> rowDatas, final Integer[] fieldsType, final String[] fieldsDefaultValue,
                                              final Boolean[] fieldsNullable, final int batchSize, final Boolean autoCommit, final Connection conn,
                                              final Integer dbType) throws Exception {
		if (rowDatas == null || rowDatas.isEmpty()) {
			logger.warn("batchUpdateByJdbc批量插入或修改数据库操作数据为空!");
			return 0L;
		}
		long updateCount = 0;
		PreparedStatement pst = null;
		// 判断是否通过default转换方式插入
		boolean supportDefaultValue = (fieldsDefaultValue != null && fieldsNullable != null) ? true : false;
		try {
			boolean hasSetAutoCommit = false;
			// 是否自动提交
			if (autoCommit != null && autoCommit.booleanValue() != conn.getAutoCommit()) {
				conn.setAutoCommit(autoCommit.booleanValue());
				hasSetAutoCommit = true;
			}
			pst = conn.prepareStatement(updateSql);
			int totalRows = rowDatas.size();
			// 只有一条记录不采用批量
			boolean useBatch = (totalRows > 1) ? true : false;
			Object[] rowData;
			// 批处理计数器
			int meter = 0;
			int index = 0;
			Object cellValue;
			int fieldType;
			int[] updateRows;
			boolean hasFieldType = (fieldsType != null);
			for (int i = 0; i < totalRows; i++) {
				rowData = rowDatas.get(i);
				fieldType = -1;
				if (rowData != null) {
					// sqlserver 针对timestamp类型不能进行赋值
					if (hasFieldType) {
						index = 0;
						for (int j = 0, n = rowData.length; j < n; j++) {
							fieldType = fieldsType[j];
							// 非timestamp类型
							if (fieldType != java.sql.Types.TIMESTAMP) {
								if (supportDefaultValue) {
									cellValue = getDefaultValue(rowData[j], fieldsDefaultValue[j], fieldType,
											fieldsNullable[j]);
								} else {
									cellValue = rowData[j];

								}
								SqlUtil.setParamValue(typeHandler, conn, dbType, pst, cellValue, fieldType, index + 1);
								index++;
							}
						}
					} else {
						for (int j = 0, n = rowData.length; j < n; j++) {
							if (supportDefaultValue) {
								cellValue = getDefaultValue(rowData[j], fieldsDefaultValue[j], -1, fieldsNullable[j]);
							} else {
								cellValue = rowData[j];
							}
							SqlUtil.setParamValue(typeHandler, conn, dbType, pst, cellValue, -1, j + 1);
						}
					}
					meter++;
					// 批量
					if (useBatch) {
						pst.addBatch();
						// 判断是否是最后一条记录或到达批次量,执行批处理
						if ((meter % batchSize) == 0 || i + 1 == totalRows) {
							updateRows = pst.executeBatch();
							for (int t : updateRows) {
								updateCount = updateCount + ((t > 0) ? t : 0);
							}
							pst.clearBatch();
						}
					} else {
						pst.execute();
						updateCount = updateCount + ((pst.getUpdateCount() > 0) ? pst.getUpdateCount() : 0);
					}
				}
			}
			// 恢复conn原始autoCommit默认值
			if (hasSetAutoCommit) {
				conn.setAutoCommit(!autoCommit);
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			throw e;
		} finally {
			try {
				if (pst != null) {
					pst.close();
					pst = null;
				}
			} catch (SQLException se) {
				logger.error(se.getMessage(), se);
			}
		}
		return updateCount;
	}

	/**
	 * @todo 自动进行类型转换,设置sql中的参数条件的值
	 * @param typeHandler
	 * @param conn
	 * @param dbType
	 * @param pst
	 * @param params
	 * @param entityMeta
	 * @throws SQLException
	 * @throws IOException
	 */
	public static void setParamsValue(AbstractTypeHandler typeHandler, Connection conn, final Integer dbType,
                                      PreparedStatement pst, Object[] params, final EntityMeta entityMeta) throws SQLException, IOException {
		if (null != params && params.length > 0) {
			Object cellValue;
			int fieldType;
			for (int i = 0, n = params.length; i < n; i++) {
				fieldType = entityMeta.getFieldsTypeArray()[i];
				cellValue = getDefaultValue(params[i], entityMeta.getFieldsDefaultValue()[i], fieldType,
						entityMeta.getFieldsNullable()[i]);
				SqlUtil.setParamValue(typeHandler, conn, dbType, pst, cellValue, fieldType, 1 + i);
			}
		}
	}

	/**
	 * @TODO 针对默认值进行处理
	 * @param paramValue
	 * @param defaultValue
	 * @param jdbcType
	 * @param isNullable
	 * @return
	 */
	private static Object getDefaultValue(Object paramValue, String defaultValue, int jdbcType, boolean isNullable) {
		Object realValue = paramValue;
		// 当前值为null且默认值不为null、且字段不允许为null
		if (realValue == null && defaultValue != null && !isNullable) {
			if (jdbcType == java.sql.Types.DATE) {
				realValue = new Date();
			} else if (jdbcType == java.sql.Types.TIMESTAMP) {
				realValue = DateUtil.getTimestamp(null);
			} else if (jdbcType == java.sql.Types.INTEGER || jdbcType == java.sql.Types.BIGINT
					|| jdbcType == java.sql.Types.TINYINT) {
				realValue = Integer.valueOf(defaultValue);
			} else if (jdbcType == java.sql.Types.DECIMAL || jdbcType == java.sql.Types.NUMERIC) {
				realValue = new BigDecimal(defaultValue);
			} else if (jdbcType == java.sql.Types.DOUBLE) {
				realValue = Double.valueOf(defaultValue);
			} else if (jdbcType == java.sql.Types.BOOLEAN) {
				realValue = Boolean.parseBoolean(defaultValue);
			} else if (jdbcType == java.sql.Types.FLOAT || jdbcType == java.sql.Types.REAL) {
				realValue = Float.valueOf(defaultValue);
			} else if (jdbcType == java.sql.Types.TIME) {
				realValue = java.sql.Time.valueOf(LocalTime.now());
			} else {
				realValue = defaultValue;
			}
		}
		return realValue;
	}

	/**
	 * @TODO 对sql增加签名,便于通过db来追溯sql(目前通过将sql id以注释形式放入sql)
	 * @param sql
	 * @param dbType       传递过来具体数据库类型,便于对不支持的数据库做区别处理
	 * @param sqlToyConfig
	 * @return
	 */
	public static String signSql(String sql, Integer dbType, SqlToyConfig sqlToyConfig) {
		// 判断是否打开sql签名,提供开发者通过SqlToyContext
		// dialectProperties设置:sqltoy.open.sqlsign=false 来关闭
		// elasticsearch类型 不支持
		if (!SqlToyConstants.openSqlSign() || dbType.equals(DBType.ES)) {
			return sql;
		}
		// 目前几乎所有数据库都支持/* xxx */ 形式的注释
		if (sqlToyConfig != null && StringUtil.isNotBlank(sqlToyConfig.getId())) {
			return "/* id=".concat(sqlToyConfig.getId()).concat(" */ ").concat(sql);
		}
		return sql;
	}
}
