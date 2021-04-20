package org.sagacity.sqltoy.dialect.impl;

import java.io.Serializable;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.sagacity.sqltoy.SqlExecuteStat;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.AbstractReflectPropertyHandler;
import org.sagacity.sqltoy.callback.AbstractRowCallbackHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.dialect.Dialect;
import org.sagacity.sqltoy.dialect.handler.AbstractGenerateSqlHandler;
import org.sagacity.sqltoy.dialect.utils.DialectExtUtils;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;
import org.sagacity.sqltoy.dialect.utils.PostgreSqlDialectUtils;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.StoreResult;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sqltoy-orm
 * @description 提供适配华为guassdb数据库方言的实现(以postgresql9.5+为蓝本实现)
 * @author zhongxuchen
 * @version v1.0,Date:2020-6-9
 * @modify {Date:2020-6-9,初始创建}
 */
public class GuassDBDialect implements Dialect {

	/**
	 * 定义日志
	 */
	protected final Logger logger = LoggerFactory.getLogger(GuassDBDialect.class);

	/**
	 * 判定为null的函数
	 */
	public static final String NVL_FUNCTION = "COALESCE";

	@Override
	public boolean isUnique(SqlToyContext sqlToyContext, Serializable entity, String[] paramsNamed, Connection conn,
			final Integer dbType, String tableName) {
		return DialectUtils.isUnique(sqlToyContext, entity, paramsNamed, conn, dbType, tableName,
				(entityMeta, realParamNamed, table, topSize) -> {
					String queryStr = DialectExtUtils.wrapUniqueSql(entityMeta, realParamNamed, dbType, table);
					return queryStr + " limit " + topSize;
				});
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#getRandomResult(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * org.sagacity.sqltoy.executor.QueryExecutor, java.lang.Long, java.lang.Long,
	 * java.sql.Connection)
	 */
	@Override
	public QueryResult getRandomResult(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, Long totalCount, Long randomCount, Connection conn, final Integer dbType,
			final String dialect) throws Exception {
		return PostgreSqlDialectUtils.getRandomResult(sqlToyContext, sqlToyConfig, queryExecutor, totalCount,
				randomCount, conn, dbType, dialect);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#findPageBySql(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * org.sagacity.sqltoy.executor.QueryExecutor, java.lang.Long,
	 * java.lang.Integer, java.sql.Connection)
	 */
	@Override
	public QueryResult findPageBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, Long pageNo, Integer pageSize, Connection conn, final Integer dbType,
			final String dialect) throws Exception {
		return PostgreSqlDialectUtils.findPageBySql(sqlToyContext, sqlToyConfig, queryExecutor, pageNo, pageSize, conn,
				dbType, dialect);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#findTopBySql(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * org.sagacity.sqltoy.executor.QueryExecutor, double, java.sql.Connection)
	 */
	@Override
	public QueryResult findTopBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, QueryExecutor queryExecutor,
			Integer topSize, Connection conn, final Integer dbType, final String dialect) throws Exception {
		return PostgreSqlDialectUtils.findTopBySql(sqlToyContext, sqlToyConfig, queryExecutor, topSize, conn, dbType,
				dialect);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#findBySql(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * java.lang.String, java.lang.Object[],
	 * org.sagacity.sqltoy.callback.RowCallbackHandler, java.sql.Connection)
	 */
	@Override
	public QueryResult findBySql(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
                                 Object[] paramsValue, AbstractRowCallbackHandler rowCallbackHandler, final Connection conn, final LockMode lockMode,
                                 final Integer dbType, final String dialect, final int fetchSize, final int maxRows) throws Exception {
		String realSql = sql.concat(getLockSql(sql, dbType, lockMode));
		return DialectUtils.findBySql(sqlToyContext, sqlToyConfig, realSql, paramsValue, rowCallbackHandler, conn,
				dbType, 0, fetchSize, maxRows);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#getCountBySql(org.sagacity.sqltoy.
	 * SqlToyContext, java.lang.String, java.lang.Object[], boolean,
	 * java.sql.Connection)
	 */
	@Override
	public Long getCountBySql(SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig, String sql,
			Object[] paramsValue, boolean isLastSql, Connection conn, final Integer dbType, final String dialect)
			throws Exception {
		return DialectUtils.getCountBySql(sqlToyContext, sqlToyConfig, sql, paramsValue, isLastSql, conn, dbType);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#load(org.sagacity.sqltoy.
	 * SqlToyContext, java.io.Serializable, java.util.List,
	 * org.sagacity.sqltoy.lock.LockMode, java.sql.Connection)
	 */
	@Override
	public Serializable load(SqlToyContext sqlToyContext, Serializable entity, List<Class> cascadeTypes,
			LockMode lockMode, Connection conn, final Integer dbType, final String dialect, final String tableName)
			throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		// 获取loadsql(loadsql 可以通过@loadSql进行改变，所以需要sqltoyContext重新获取)
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(entityMeta.getLoadSql(tableName), SqlType.search, "");
		String loadSql = ReservedWordsUtil.convertSql(sqlToyConfig.getSql(dialect), dbType);
		loadSql = loadSql.concat(getLockSql(loadSql, dbType, lockMode));
		return (Serializable) DialectUtils.load(sqlToyContext, sqlToyConfig, loadSql, entityMeta, entity, cascadeTypes,
				conn, dbType);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#loadAll(org.sagacity.sqltoy.
	 * SqlToyContext, java.util.List, java.util.List,
	 * org.sagacity.sqltoy.lock.LockMode, java.sql.Connection)
	 */
	@Override
	public List<?> loadAll(SqlToyContext sqlToyContext, List<?> entities, List<Class> cascadeTypes, LockMode lockMode,
			Connection conn, final Integer dbType, final String dialect, final String tableName) throws Exception {
		return DialectUtils.loadAll(sqlToyContext, entities, cascadeTypes, lockMode, conn, dbType, tableName,
				(sql, dbTypeValue, lockedMode) -> {
					return getLockSql(sql, dbTypeValue, lockedMode);
				});
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#save(org.sagacity.sqltoy.
	 * SqlToyContext, java.io.Serializable, java.sql.Connection)
	 */
	@Override
	public Object save(SqlToyContext sqlToyContext, Serializable entity, Connection conn, final Integer dbType,
			final String dialect, final String tableName) throws Exception {
		return PostgreSqlDialectUtils.save(sqlToyContext, entity, conn, dbType, tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#saveAll(org.sagacity.sqltoy.
	 * SqlToyContext, java.util.List,
	 * org.sagacity.sqltoy.callback.ReflectPropertyHandler, java.sql.Connection)
	 */
	@Override
	public Long saveAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
                        AbstractReflectPropertyHandler reflectPropertyHandler, Connection conn, final Integer dbType, final String dialect,
                        final Boolean autoCommit, final String tableName) throws Exception {
		return PostgreSqlDialectUtils.saveAll(sqlToyContext, entities, batchSize, reflectPropertyHandler, conn, dbType,
				autoCommit, tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#update(org.sagacity.sqltoy.
	 * SqlToyContext, java.io.Serializable, java.lang.String[], boolean,
	 * java.lang.Class[], java.util.HashMap, java.sql.Connection)
	 */
	@Override
	public Long update(SqlToyContext sqlToyContext, Serializable entity, String[] forceUpdateFields, boolean cascade,
			Class[] emptyCascadeClasses, HashMap<Class, String[]> subTableForceUpdateProps, Connection conn,
			final Integer dbType, final String dialect, final String tableName) throws Exception {
		return DialectUtils.update(sqlToyContext, entity, NVL_FUNCTION, forceUpdateFields, cascade,
				(cascade == false) ? null : new AbstractGenerateSqlHandler() {
					@Override
                    public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						String sequence = "nextval('" + entityMeta.getSequence() + "')";
						if (pkStrategy != null && pkStrategy.equals(PKStrategy.IDENTITY)) {
							// 伪造成sequence模式
							pkStrategy = PKStrategy.SEQUENCE;
							sequence = "DEFAULT";
						}
						boolean isAssignPK = PostgreSqlDialectUtils.isAssignPKValue(pkStrategy);
						return PostgreSqlDialectUtils.getSaveOrUpdateSql(dbType, entityMeta, pkStrategy, isAssignPK,
								sequence, forceUpdateFields, null);
					}
				}, emptyCascadeClasses, subTableForceUpdateProps, conn, dbType, tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#updateAll(org.sagacity.sqltoy.
	 * SqlToyContext, java.util.List, java.lang.String[],
	 * org.sagacity.sqltoy.callback.ReflectPropertyHandler, java.sql.Connection)
	 */
	@Override
	public Long updateAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
                          String[] forceUpdateFields, AbstractReflectPropertyHandler reflectPropertyHandler, Connection conn,
                          final Integer dbType, final String dialect, final Boolean autoCommit, final String tableName)
			throws Exception {
		return DialectUtils.updateAll(sqlToyContext, entities, batchSize, forceUpdateFields, reflectPropertyHandler,
				NVL_FUNCTION, conn, dbType, autoCommit, tableName, false);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#saveOrUpdate(org.sagacity.sqltoy.
	 * SqlToyContext, java.io.Serializable, java.lang.String[], java.sql.Connection,
	 * java.lang.Boolean)
	 */
	@Override
	public Long saveOrUpdate(SqlToyContext sqlToyContext, Serializable entity, String[] forceUpdateFields,
			Connection conn, final Integer dbType, final String dialect, final Boolean autoCommit,
			final String tableName) throws Exception {
		List<Serializable> entities = new ArrayList<Serializable>();
		entities.add(entity);
		return saveOrUpdateAll(sqlToyContext, entities, sqlToyContext.getBatchSize(), null, forceUpdateFields, conn,
				dbType, dialect, autoCommit, tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#saveOrUpdateAll(org.sagacity.sqltoy.
	 * SqlToyContext, java.util.List,
	 * org.sagacity.sqltoy.callback.ReflectPropertyHandler, java.lang.String[],
	 * java.sql.Connection, java.lang.Boolean)
	 */
	@Override
	public Long saveOrUpdateAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
                                AbstractReflectPropertyHandler reflectPropertyHandler, String[] forceUpdateFields, Connection conn,
                                final Integer dbType, final String dialect, final Boolean autoCommit, final String tableName)
			throws Exception {
		Long updateCnt = DialectUtils.updateAll(sqlToyContext, entities, batchSize, forceUpdateFields,
				reflectPropertyHandler, NVL_FUNCTION, conn, dbType, autoCommit, tableName, true);

		// 如果修改的记录数量跟总记录数量一致,表示全部是修改
		if (updateCnt >= entities.size()) {
			SqlExecuteStat.debug("修改记录", "修改记录量:" + updateCnt + " 条!");
			return updateCnt;
		}
		Long saveCnt = saveAllIgnoreExist(sqlToyContext, entities, batchSize, reflectPropertyHandler, conn, dbType,
				dialect, autoCommit, tableName);
		SqlExecuteStat.debug("新增记录", "新建记录数量:" + saveCnt + " 条!");
		return updateCnt + saveCnt;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#saveAllNotExist(org.sagacity.sqltoy.
	 * SqlToyContext, java.util.List,
	 * org.sagacity.sqltoy.callback.ReflectPropertyHandler, java.sql.Connection,
	 * java.lang.Boolean)
	 */
	@Override
	public Long saveAllIgnoreExist(SqlToyContext sqlToyContext, List<?> entities, final int batchSize,
                                   AbstractReflectPropertyHandler reflectPropertyHandler, Connection conn, final Integer dbType, final String dialect,
                                   final Boolean autoCommit, final String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		return DialectUtils.saveAllIgnoreExist(sqlToyContext, entities, batchSize, entityMeta,
				new AbstractGenerateSqlHandler() {
					@Override
                    public String generateSql(EntityMeta entityMeta, String[] forceUpdateFields) {
						PKStrategy pkStrategy = entityMeta.getIdStrategy();
						String sequence = "nextval('" + entityMeta.getSequence() + "')";
						if (pkStrategy != null && pkStrategy.equals(PKStrategy.IDENTITY)) {
							// 伪造成sequence模式
							pkStrategy = PKStrategy.SEQUENCE;
							sequence = "DEFAULT";
						}
						boolean isAssignPK = PostgreSqlDialectUtils.isAssignPKValue(pkStrategy);
						return DialectExtUtils.insertIgnore(dbType, entityMeta, pkStrategy, NVL_FUNCTION, sequence,
								isAssignPK, tableName);
					}
				}, reflectPropertyHandler, conn, dbType, autoCommit);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#delete(org.sagacity.sqltoy.
	 * SqlToyContext, java.io.Serializable, java.sql.Connection)
	 */
	@Override
	public Long delete(SqlToyContext sqlToyContext, Serializable entity, Connection conn, final Integer dbType,
			final String dialect, final String tableName) throws Exception {
		return DialectUtils.delete(sqlToyContext, entity, conn, dbType, tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#deleteAll(org.sagacity.sqltoy.
	 * SqlToyContext, java.util.List, java.sql.Connection)
	 */
	@Override
	public Long deleteAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize, Connection conn,
			final Integer dbType, final String dialect, final Boolean autoCommit, final String tableName)
			throws Exception {
		return DialectUtils.deleteAll(sqlToyContext, entities, batchSize, conn, dbType, autoCommit, tableName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#updateFetch(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * java.lang.String, java.lang.Object[],
	 * org.sagacity.sqltoy.callback.UpdateRowHandler, java.sql.Connection)
	 */
	@Override
	public QueryResult updateFetch(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramValues, UpdateRowHandler updateRowHandler, Connection conn, final Integer dbType,
			final String dialect, final LockMode lockMode) throws Exception {
		String realSql = sql.concat(getLockSql(sql, dbType, (lockMode == null) ? LockMode.UPGRADE : lockMode));
		return DialectUtils.updateFetchBySql(sqlToyContext, sqlToyConfig, realSql, paramValues, updateRowHandler, conn,
				dbType, 0);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#updateFetchTop(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * java.lang.String, java.lang.Object[], java.lang.Integer,
	 * org.sagacity.sqltoy.callback.UpdateRowHandler, java.sql.Connection)
	 */
	@Override
	public QueryResult updateFetchTop(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramsValue, Integer topSize, UpdateRowHandler updateRowHandler, Connection conn,
			final Integer dbType, final String dialect) throws Exception {
		String realSql = sql + " limit " + topSize + " for update nowait";
		return DialectUtils.updateFetchBySql(sqlToyContext, sqlToyConfig, realSql, paramsValue, updateRowHandler, conn,
				dbType, 0);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.sagacity.sqltoy.dialect.Dialect#updateFetchRandom(org.sagacity.sqltoy
	 * .SqlToyContext, org.sagacity.sqltoy.config.model.SqlToyConfig,
	 * java.lang.String, java.lang.Object[], java.lang.Integer,
	 * org.sagacity.sqltoy.callback.UpdateRowHandler, java.sql.Connection)
	 */
	@Override
	public QueryResult updateFetchRandom(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig, String sql,
			Object[] paramsValue, Integer random, UpdateRowHandler updateRowHandler, Connection conn,
			final Integer dbType, final String dialect) throws Exception {
		String realSql = sql + " order by random() limit " + random + " for update nowait";
		return DialectUtils.updateFetchBySql(sqlToyContext, sqlToyConfig, realSql, paramsValue, updateRowHandler, conn,
				dbType, 0);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.dialect.Dialect#findByStore(org.sagacity.sqltoy.
	 * SqlToyContext, org.sagacity.sqltoy.executor.StoreExecutor)
	 */
	@Override
	public StoreResult executeStore(SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig, final String sql,
			final Object[] inParamsValue, final Integer[] outParamsType, final Connection conn, final Integer dbType,
			final String dialect) throws Exception {
		return DialectUtils.executeStore(sqlToyConfig, sqlToyContext, sql, inParamsValue, outParamsType, conn, dbType);
	}

	private String getLockSql(String sql, Integer dbType, LockMode lockMode) {
		// 判断是否已经包含for update
		if (lockMode == null || SqlUtil.hasLock(sql, dbType)) {
			return "";
		}
		if (lockMode == LockMode.UPGRADE_NOWAIT) {
			return " for update nowait ";
		}
		if (lockMode == LockMode.UPGRADE_SKIPLOCK) {
			return " for update skip locked";
		}
		return " for update ";
	}

}
