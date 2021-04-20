package org.sagacity.sqltoy.dialect.impl;

import java.io.Serializable;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.AbstractReflectPropertyHandler;
import org.sagacity.sqltoy.callback.AbstractRowCallbackHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.dialect.Dialect;
import org.sagacity.sqltoy.dialect.utils.ClickHouseDialectUtils;
import org.sagacity.sqltoy.dialect.utils.DialectExtUtils;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.QueryExecutorExtend;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.StoreResult;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;

/**
 * @project sqltoy-orm
 * @description clickhouse 19.x版本,clickhouse 不支持updateAll,更多面向查询
 * @author zhongxuchen
 * @version v1.0,Date:2020年1月20日
 */
public class ClickHouseDialect implements Dialect {
	/**
	 * 判定为null的函数
	 */
	public static final String NVL_FUNCTION = "ifnull";

	@Override
	public boolean isUnique(SqlToyContext sqlToyContext, Serializable entity, String[] paramsNamed, Connection conn,
			final Integer dbType, final String tableName) {
		return DialectUtils.isUnique(sqlToyContext, entity, paramsNamed, conn, dbType, tableName,
				(entityMeta, realParamNamed, table, topSize) -> {
					String queryStr = DialectExtUtils.wrapUniqueSql(entityMeta, realParamNamed, dbType, table);
					return queryStr + " limit " + topSize;
				});
	}

	@Override
	public QueryResult getRandomResult(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, Long totalCount, Long randomCount, Connection conn, Integer dbType,
			String dialect) throws Exception {
		String innerSql = sqlToyConfig.isHasFast() ? sqlToyConfig.getFastSql(dialect) : sqlToyConfig.getSql(dialect);
		// select * from table order by rand() limit :randomCount 性能比较差,通过产生rand()
		// row_number 再排序方式性能稍好 同时也可以保证通用性
		StringBuilder sql = new StringBuilder();
		if (sqlToyConfig.isHasFast()) {
			sql.append(sqlToyConfig.getFastPreSql(dialect));
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(" (");
			}
		}
		sql.append("select sag_random_table1.* from (");
		// sql中是否存在排序或union,存在order 或union 则在sql外包裹一层
		if (DialectUtils.hasOrderByOrUnion(innerSql)) {
			sql.append("select rand() as sag_row_number,sag_random_table.* from (");
			sql.append(innerSql);
			sql.append(") sag_random_table ");
		} else {
			sql.append(innerSql.replaceFirst("(?i)select", "select rand() as sag_row_number,"));
		}
		sql.append(" )  as sag_random_table1 ");
		sql.append(" order by sag_random_table1.sag_row_number limit ");
		sql.append(randomCount);

		if (sqlToyConfig.isHasFast()) {
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(") ");
			}
			sql.append(sqlToyConfig.getFastTailSql(dialect));
		}
		SqlToyResult queryParam = DialectUtils.wrapPageSqlParams(sqlToyContext, sqlToyConfig, queryExecutor,
				sql.toString(), null, null);
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
		return findBySql(sqlToyContext, sqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(),
				extend.rowCallbackHandler, conn, null, dbType, dialect, extend.fetchSize, extend.maxRows);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#findPageBySql(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * org.sagacity.sqltoy.executor.QueryExecutor, java.lang.Long,
	 * java.lang.Integer, java.sql.Connection, java.lang.Integer, java.lang.String)
	 */
	@Override
	public QueryResult findPageBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, Long pageNo, Integer pageSize, Connection conn, Integer dbType, String dialect)
			throws Exception {
		StringBuilder sql = new StringBuilder();
		boolean isNamed = sqlToyConfig.isNamedParam();
		if (sqlToyConfig.isHasFast()) {
			sql.append(sqlToyConfig.getFastPreSql(dialect));
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(" (");
			}
			sql.append(sqlToyConfig.getFastSql(dialect));
		} else {
			sql.append(sqlToyConfig.getSql(dialect));
		}
		// clickhouse 分页类似于postgresql
		sql.append(" limit ");
		sql.append(isNamed ? ":" + SqlToyConstants.PAGE_FIRST_PARAM_NAME : "?");
		sql.append(" offset ");
		sql.append(isNamed ? ":" + SqlToyConstants.PAGE_LAST_PARAM_NAME : "?");
		if (sqlToyConfig.isHasFast()) {
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(") ");
			}
			sql.append(sqlToyConfig.getFastTailSql(dialect));
		}

		SqlToyResult queryParam = DialectUtils.wrapPageSqlParams(sqlToyContext, sqlToyConfig, queryExecutor,
				sql.toString(), Long.valueOf(pageSize), (pageNo - 1) * pageSize);
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
		return DialectUtils.findBySql(sqlToyContext, sqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(),
				extend.rowCallbackHandler, conn, dbType, 0, extend.fetchSize, extend.maxRows);
	}

	@Override
	public QueryResult findTopBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, QueryExecutor queryExecutor,
			Integer topSize, Connection conn, Integer dbType, String dialect) throws Exception {
		StringBuilder sql = new StringBuilder();
		if (sqlToyConfig.isHasFast()) {
			sql.append(sqlToyConfig.getFastPreSql(dialect));
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(" (");
			}
			sql.append(sqlToyConfig.getFastSql(dialect));
		} else {
			sql.append(sqlToyConfig.getSql(dialect));
		}
		sql.append(" limit ");
		sql.append(topSize);

		if (sqlToyConfig.isHasFast()) {
			if (!sqlToyConfig.isIgnoreBracket()) {
				sql.append(") ");
			}
			sql.append(sqlToyConfig.getFastTailSql(dialect));
		}

		SqlToyResult queryParam = DialectUtils.wrapPageSqlParams(sqlToyContext, sqlToyConfig, queryExecutor,
				sql.toString(), null, null);
		QueryExecutorExtend extend = queryExecutor.getInnerModel();
		return findBySql(sqlToyContext, sqlToyConfig, queryParam.getSql(), queryParam.getParamsValue(),
				extend.rowCallbackHandler, conn, null, dbType, dialect, extend.fetchSize, extend.maxRows);
	}

	@Override
	public QueryResult findBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
                                 Object[] paramsValue, AbstractRowCallbackHandler rowCallbackHandler, Connection conn, final LockMode lockMode,
                                 Integer dbType, String dialect, int fetchSize, int maxRows) throws Exception {
		// clickhouse目前不支持锁查询
		if (null != lockMode) {
			throw new UnsupportedOperationException("clickHouse lock search," + SqlToyConstants.UN_SUPPORT_MESSAGE);
		}
		return DialectUtils.findBySql(sqlToyContext, sqlToyConfig, sql, paramsValue, rowCallbackHandler, conn, dbType,
				0, fetchSize, maxRows);
	}

	@Override
	public Long getCountBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql, Object[] paramsValue,
			boolean isLastSql, Connection conn, Integer dbType, String dialect) throws Exception {
		return DialectUtils.getCountBySql(sqlToyContext, sqlToyConfig, sql, paramsValue, isLastSql, conn, dbType);
	}

	@Override
	public Serializable load(SqlToyContext sqlToyContext, Serializable entity, List<Class> cascadeTypes,
			LockMode lockMode, Connection conn, Integer dbType, String dialect, String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		// 获取loadsql(loadsql 可以通过@loadSql进行改变，所以需要sqltoyContext重新获取)
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(entityMeta.getLoadSql(tableName), SqlType.search, "");
		String loadSql = ReservedWordsUtil.convertSql(sqlToyConfig.getSql(dialect), dbType);
		return (Serializable) DialectUtils.load(sqlToyContext, sqlToyConfig, loadSql, entityMeta, entity, cascadeTypes,
				conn, dbType);
	}

	@Override
	public List<?> loadAll(SqlToyContext sqlToyContext, List<?> entities, List<Class> cascadeTypes, LockMode lockMode,
			Connection conn, Integer dbType, String dialect, String tableName) throws Exception {
		return DialectUtils.loadAll(sqlToyContext, entities, cascadeTypes, lockMode, conn, dbType, tableName, null);
	}

	@Override
	public Object save(SqlToyContext sqlToyContext, Serializable entity, Connection conn, Integer dbType,
			String dialect, String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		// clickhouse 不支持sequence，支持identity自增模式
		String insertSql = DialectExtUtils.generateInsertSql(dbType, entityMeta, entityMeta.getIdStrategy(),
				NVL_FUNCTION, "NEXTVAL FOR " + entityMeta.getSequence(),
				ClickHouseDialectUtils.isAssignPKValue(entityMeta.getIdStrategy()), tableName);
		return ClickHouseDialectUtils.save(sqlToyContext, entityMeta, insertSql, entity, conn, dbType);
	}

	@Override
	public Long saveAll(SqlToyContext sqlToyContext, List<?> entities, int batchSize,
                        AbstractReflectPropertyHandler reflectPropertyHandler, Connection conn, Integer dbType, String dialect,
                        Boolean autoCommit, String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		// clickhouse 不支持sequence，支持identity自增模式
		String insertSql = DialectExtUtils.generateInsertSql(dbType, entityMeta, entityMeta.getIdStrategy(),
				NVL_FUNCTION, "NEXTVAL FOR " + entityMeta.getSequence(),
				ClickHouseDialectUtils.isAssignPKValue(entityMeta.getIdStrategy()), tableName);
		return ClickHouseDialectUtils.saveAll(sqlToyContext, entityMeta, insertSql, entities, batchSize,
				reflectPropertyHandler, conn, dbType, autoCommit);
	}

	@Override
	public Long update(SqlToyContext sqlToyContext, Serializable entity, String[] forceUpdateFields, boolean cascade,
			Class[] forceCascadeClass, HashMap<Class, String[]> subTableForceUpdateProps, Connection conn,
			Integer dbType, String dialect, String tableName) throws Exception {
		return ClickHouseDialectUtils.update(sqlToyContext, entity, NVL_FUNCTION, forceUpdateFields, conn, dbType,
				tableName);
	}

	@Override
	public Long updateAll(SqlToyContext sqlToyContext, List<?> entities, int batchSize, String[] forceUpdateFields,
                          AbstractReflectPropertyHandler reflectPropertyHandler, Connection conn, Integer dbType, String dialect,
                          Boolean autoCommit, String tableName) throws Exception {
		throw new UnsupportedOperationException(SqlToyConstants.UN_SUPPORT_MESSAGE);
	}

	@Override
	public Long saveOrUpdate(SqlToyContext sqlToyContext, Serializable entity, String[] forceUpdateFields,
			Connection conn, Integer dbType, String dialect, Boolean autoCommit, String tableName) throws Exception {
		// 不支持
		throw new UnsupportedOperationException(SqlToyConstants.UN_SUPPORT_MESSAGE);
	}

	@Override
	public Long saveOrUpdateAll(SqlToyContext sqlToyContext, List<?> entities, int batchSize,
                                AbstractReflectPropertyHandler reflectPropertyHandler, String[] forceUpdateFields, Connection conn, Integer dbType,
                                String dialect, Boolean autoCommit, String tableName) throws Exception {
		// 不支持
		throw new UnsupportedOperationException(SqlToyConstants.UN_SUPPORT_MESSAGE);
	}

	@Override
	public Long saveAllIgnoreExist(SqlToyContext sqlToyContext, List<?> entities, int batchSize,
                                   AbstractReflectPropertyHandler reflectPropertyHandler, Connection conn, Integer dbType, String dialect,
                                   Boolean autoCommit, String tableName) throws Exception {
		// 不支持
		throw new UnsupportedOperationException(SqlToyConstants.UN_SUPPORT_MESSAGE);
	}

	@Override
	public Long delete(SqlToyContext sqlToyContext, Serializable entity, Connection conn, Integer dbType,
			String dialect, String tableName) throws Exception {
		return ClickHouseDialectUtils.delete(sqlToyContext, entity, conn, dbType, tableName);
	}

	@Override
	public Long deleteAll(SqlToyContext sqlToyContext, List<?> entities, int batchSize, Connection conn, Integer dbType,
			String dialect, Boolean autoCommit, String tableName) throws Exception {
		return ClickHouseDialectUtils.deleteAll(sqlToyContext, entities, batchSize, conn, dbType, autoCommit,
				tableName);
	}

	@Override
	public QueryResult updateFetch(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramValues, UpdateRowHandler updateRowHandler, Connection conn, Integer dbType, String dialect,
			final LockMode lockMode) throws Exception {
		// 不支持
		throw new UnsupportedOperationException(SqlToyConstants.UN_SUPPORT_MESSAGE);
	}

	@Override
	public QueryResult updateFetchTop(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramsValue, Integer topSize, UpdateRowHandler updateRowHandler, Connection conn, Integer dbType,
			String dialect) throws Exception {
		// 不支持
		throw new UnsupportedOperationException(SqlToyConstants.UN_SUPPORT_MESSAGE);
	}

	@Override
	public QueryResult updateFetchRandom(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramsValue, Integer random, UpdateRowHandler updateRowHandler, Connection conn, Integer dbType,
			String dialect) throws Exception {
		// 不支持
		throw new UnsupportedOperationException(SqlToyConstants.UN_SUPPORT_MESSAGE);
	}

	@Override
	public StoreResult executeStore(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] inParamsValue, Integer[] outParamsType, Connection conn, Integer dbType, String dialect)
			throws Exception {
		// 不支持
		throw new UnsupportedOperationException(SqlToyConstants.UN_SUPPORT_MESSAGE);
	}

}
