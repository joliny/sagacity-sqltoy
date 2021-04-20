package org.sagacity.sqltoy.dialect.utils;

import java.io.IOException;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.sagacity.sqltoy.SqlExecuteStat;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.AbstractPreparedStatementResultHandler;
import org.sagacity.sqltoy.callback.AbstractReflectPropertyHandler;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.EntityMeta;
import org.sagacity.sqltoy.config.model.FieldMeta;
import org.sagacity.sqltoy.config.model.PKStrategy;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.dialect.model.ReturnPkType;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.ReservedWordsUtil;
import org.sagacity.sqltoy.utils.SqlUtil;
import org.sagacity.sqltoy.utils.SqlUtilsExt;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sqltoy-orm
 * @description 提供click数据库通用的操作功能实现,为不同版本提供支持
 * @author zhongxuchen
 * @version v1.0,Date:2020年1月20日
 */
public class ClickHouseDialectUtils {
	/**
	 * 定义日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(ClickHouseDialectUtils.class);

	/**
	 * @todo 保存对象
	 * @param sqlToyContext
	 * @param entityMeta
	 * @param insertSql
	 * @param entity
	 * @param conn
	 * @param dbType
	 * @return
	 * @throws Exception
	 */
	public static Object save(SqlToyContext sqlToyContext, final EntityMeta entityMeta, final String insertSql,
			Serializable entity, final Connection conn, final Integer dbType) throws Exception {
		PKStrategy pkStrategy = entityMeta.getIdStrategy();
		ReturnPkType returnPkType = (pkStrategy != null && pkStrategy.equals(PKStrategy.SEQUENCE))
				? ReturnPkType.GENERATED_KEYS
				: ReturnPkType.PREPARD_ID;
		final boolean isIdentity = (pkStrategy != null && pkStrategy.equals(PKStrategy.IDENTITY));
		final boolean isSequence = (pkStrategy != null && pkStrategy.equals(PKStrategy.SEQUENCE));
		String[] reflectColumns;
		boolean isAssignPK = isAssignPKValue(pkStrategy);
		if ((isIdentity && !isAssignPK) || (isSequence && !isAssignPK)) {
			reflectColumns = entityMeta.getRejectIdFieldArray();
		} else {
			reflectColumns = entityMeta.getFieldsArray();
		}
		// 构造全新的新增记录参数赋值反射(覆盖之前的)
		AbstractReflectPropertyHandler handler = DialectUtils.getAddReflectHandler(sqlToyContext, null);
		Object[] fullParamValues = BeanUtil.reflectBeanToAry(entity, reflectColumns, null, handler);
		boolean needUpdatePk = false;

		int pkIndex = entityMeta.getIdIndex();
		// 是否存在业务ID
		boolean hasBizId = (entityMeta.getBusinessIdGenerator() == null) ? false : true;
		int bizIdColIndex = hasBizId ? entityMeta.getFieldIndex(entityMeta.getBusinessIdField()) : 0;
		// 标识符
		String signature = entityMeta.getBizIdSignature();
		Integer[] relatedColumn = entityMeta.getBizIdRelatedColIndex();
		String[] relatedColumnNames = entityMeta.getBizIdRelatedColumns();
		int relatedColumnSize = (relatedColumn == null) ? 0 : relatedColumn.length;
		// 主键采用assign方式赋予，则调用generator产生id并赋予其值
		if (entityMeta.getIdStrategy() != null && null != entityMeta.getIdGenerator()) {
			int bizIdLength = entityMeta.getBizIdLength();
			int idLength = entityMeta.getIdLength();
			Object[] relatedColValue = null;
			String businessIdType = hasBizId ? entityMeta.getColumnJavaType(entityMeta.getBusinessIdField()) : "";
			if (relatedColumn != null) {
				relatedColValue = new Object[relatedColumnSize];
				for (int meter = 0; meter < relatedColumnSize; meter++) {
					relatedColValue[meter] = fullParamValues[relatedColumn[meter]];
					if (StringUtil.isBlank(relatedColValue[meter])) {
						throw new IllegalArgumentException("对象:" + entityMeta.getEntityClass().getName()
								+ " 生成业务主键依赖的关联字段:" + relatedColumnNames[meter] + " 值为null!");
					}
				}
			}
			if (StringUtil.isBlank(fullParamValues[pkIndex])) {
				// id通过generator机制产生，设置generator产生的值
				fullParamValues[pkIndex] = entityMeta.getIdGenerator().getId(entityMeta.getTableName(), signature,
						entityMeta.getBizIdRelatedColumns(), relatedColValue, null, entityMeta.getIdType(), idLength,
						entityMeta.getBizIdSequenceSize());
				needUpdatePk = true;
			}
			if (hasBizId && StringUtil.isBlank(fullParamValues[bizIdColIndex])) {
				fullParamValues[bizIdColIndex] = entityMeta.getBusinessIdGenerator().getId(entityMeta.getTableName(),
						signature, entityMeta.getBizIdRelatedColumns(), relatedColValue, null, businessIdType,
						bizIdLength, entityMeta.getBizIdSequenceSize());
				// 回写业务主键值
				BeanUtil.setProperty(entity, entityMeta.getBusinessIdField(), fullParamValues[bizIdColIndex]);
			}
		}
		SqlExecuteStat.showSql("执行单记录插入", insertSql, null);
		final Object[] paramValues = fullParamValues;
		final Integer[] paramsType = entityMeta.getFieldsTypeArray();
		PreparedStatement pst = null;
		Object result = SqlUtil.preparedStatementProcess(null, pst, null, new AbstractPreparedStatementResultHandler() {
			@Override
            public void execute(Object obj, PreparedStatement pst, ResultSet rs) throws SQLException, IOException {
				if (isIdentity || isSequence) {
					if (returnPkType.equals(ReturnPkType.GENERATED_KEYS)) {
						pst = conn.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS);
					} else if (returnPkType.equals(ReturnPkType.PREPARD_ID)) {
						pst = conn.prepareStatement(insertSql,
								new String[] { entityMeta.getColumnName(entityMeta.getIdArray()[0]) });
					} else {
						pst = conn.prepareStatement(insertSql);
					}
				} else {
					pst = conn.prepareStatement(insertSql);
				}
				SqlUtil.setParamsValue(sqlToyContext.getTypeHandler(), conn, dbType, pst, paramValues, paramsType, 0);
				ResultSet keyResult = null;
				if ((isIdentity || isSequence) && returnPkType.equals(ReturnPkType.RESULT_GET)) {
					keyResult = pst.executeQuery();
				} else {
					pst.execute();
				}
				if (isIdentity || isSequence) {
					if (!returnPkType.equals(ReturnPkType.RESULT_GET)) {
						keyResult = pst.getGeneratedKeys();
					}
					if (keyResult != null) {
						List result = new ArrayList();
						while (keyResult.next()) {
							result.add(keyResult.getObject(1));
						}
						if (result.size() == 1) {
							this.setResult(result.get(0));
						} else {
							this.setResult(result.toArray());
						}
					}
				}
			}
		});
		// 无主键直接返回null
		if (entityMeta.getIdArray() == null) {
			return null;
		}
		if (result == null) {
			result = fullParamValues[pkIndex];
		}
		// 回置到entity 主键值
		if (needUpdatePk || isIdentity || isSequence) {
			BeanUtil.setProperty(entity, entityMeta.getIdArray()[0], result);
		}
		return result;
	}

	/**
	 * @todo 保存批量对象数据
	 * @param sqlToyContext
	 * @param entityMeta
	 * @param insertSql
	 * @param entities
	 * @param batchSize
	 * @param reflectPropertyHandler
	 * @param conn
	 * @param dbType
	 * @param autoCommit
	 * @return
	 * @throws Exception
	 */
	public static Long saveAll(SqlToyContext sqlToyContext, EntityMeta entityMeta, String insertSql, List<?> entities,
                               final int batchSize, AbstractReflectPropertyHandler reflectPropertyHandler, Connection conn, final Integer dbType,
                               final Boolean autoCommit) throws Exception {
		PKStrategy pkStrategy = entityMeta.getIdStrategy();
		boolean isIdentity = pkStrategy != null && pkStrategy.equals(PKStrategy.IDENTITY);
		boolean isSequence = pkStrategy != null && pkStrategy.equals(PKStrategy.SEQUENCE);
		String[] reflectColumns;
		boolean isAssignPK = isAssignPKValue(pkStrategy);
		if ((isIdentity && !isAssignPK) || (isSequence && !isAssignPK)) {
			reflectColumns = entityMeta.getRejectIdFieldArray();
		} else {
			reflectColumns = entityMeta.getFieldsArray();
		}
		// 构造全新的新增记录参数赋值反射(覆盖之前的)
		AbstractReflectPropertyHandler handler = DialectUtils.getAddReflectHandler(sqlToyContext, reflectPropertyHandler);
		List paramValues = BeanUtil.reflectBeansToInnerAry(entities, reflectColumns, null, handler);
		int pkIndex = entityMeta.getIdIndex();
		// 是否存在业务ID
		boolean hasBizId = (entityMeta.getBusinessIdGenerator() == null) ? false : true;
		int bizIdColIndex = hasBizId ? entityMeta.getFieldIndex(entityMeta.getBusinessIdField()) : 0;
		// 标识符
		String signature = entityMeta.getBizIdSignature();
		Integer[] relatedColumn = entityMeta.getBizIdRelatedColIndex();
		String[] relatedColumnNames = entityMeta.getBizIdRelatedColumns();
		int relatedColumnSize = (relatedColumn == null) ? 0 : relatedColumn.length;
		// 无主键值以及多主键以及assign或通过generator方式产生主键策略
		if (pkStrategy != null && null != entityMeta.getIdGenerator()) {
			int bizIdLength = entityMeta.getBizIdLength();
			int idLength = entityMeta.getIdLength();
			Object[] rowData;
			boolean isAssigned = true;
			String idJdbcType = entityMeta.getIdType();
			Object[] relatedColValue = null;
			String businessIdType = hasBizId ? entityMeta.getColumnJavaType(entityMeta.getBusinessIdField()) : "";
			List<Object[]> idSet = new ArrayList<Object[]>();
			for (int i = 0, s = paramValues.size(); i < s; i++) {
				rowData = (Object[]) paramValues.get(i);
				// 判断主键策略关联的字段是否有值,合法性验证
				if (relatedColumn != null) {
					relatedColValue = new Object[relatedColumnSize];
					for (int meter = 0; meter < relatedColumnSize; meter++) {
						relatedColValue[meter] = rowData[relatedColumn[meter]];
						if (StringUtil.isBlank(relatedColValue[meter])) {
							throw new IllegalArgumentException("对象:" + entityMeta.getEntityClass().getName()
									+ " 生成业务主键依赖的关联字段:" + relatedColumnNames[meter] + " 值为null!");
						}
					}
				}
				// 主键值为null,调用主键生成策略并赋值
				if (StringUtil.isBlank(rowData[pkIndex])) {
					isAssigned = false;
					rowData[pkIndex] = entityMeta.getIdGenerator().getId(entityMeta.getTableName(), signature,
							entityMeta.getBizIdRelatedColumns(), relatedColValue, null, idJdbcType, idLength,
							entityMeta.getBizIdSequenceSize());
				}
				if (hasBizId && StringUtil.isBlank(rowData[bizIdColIndex])) {
					rowData[bizIdColIndex] = entityMeta.getBusinessIdGenerator().getId(entityMeta.getTableName(),
							signature, entityMeta.getBizIdRelatedColumns(), relatedColValue, null, businessIdType,
							bizIdLength, entityMeta.getBizIdSequenceSize());
					// 回写业务主键值
					BeanUtil.setProperty(entities.get(i), entityMeta.getBusinessIdField(), rowData[bizIdColIndex]);
				}
				idSet.add(new Object[] { rowData[pkIndex] });
			}
			// 批量反向设置最终得到的主键值
			if (!isAssigned) {
				BeanUtil.mappingSetProperties(entities, entityMeta.getIdArray(), idSet, new int[] { 0 }, true);
			}
		}
		SqlExecuteStat.showSql("批量保存[" + paramValues.size() + "]条记录", insertSql, null);
		return SqlUtilsExt.batchUpdateByJdbc(sqlToyContext.getTypeHandler(), insertSql, paramValues,
				entityMeta.getFieldsTypeArray(), entityMeta.getFieldsDefaultValue(), entityMeta.getFieldsNullable(),
				batchSize, autoCommit, conn, dbType);
	}

	/**
	 * @todo 删除单个对象以及其级联表数据
	 * @param sqlToyContext
	 * @param entity
	 * @param conn
	 * @param dbType
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public static Long delete(SqlToyContext sqlToyContext, Serializable entity, Connection conn, final Integer dbType,
			final String tableName) throws Exception {
		if (entity == null) {
			return 0L;
		}
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		if (null == entityMeta.getIdArray()) {
			throw new IllegalArgumentException("delete table:" + entityMeta.getSchemaTable(tableName)
					+ " no primary key,please check table design!");
		}
		Object[] idValues = BeanUtil.reflectBeanToAry(entity, entityMeta.getIdArray());
		Integer[] parameterTypes = new Integer[idValues.length];
		boolean validator = true;
		// 判断主键值是否为空
		for (int i = 0, n = idValues.length; i < n; i++) {
			parameterTypes[i] = entityMeta.getColumnJdbcType(entityMeta.getIdArray()[i]);
			if (StringUtil.isBlank(idValues[i])) {
				validator = false;
				break;
			}
		}
		if (!validator) {
			throw new IllegalArgumentException(entityMeta.getSchemaTable(tableName)
					+ "delete operate is illegal,table must has primary key and all primaryKey's value must has value!");
		}

		String deleteSql = "alter table ".concat(entityMeta.getSchemaTable(tableName)).concat(" delete ")
				.concat(entityMeta.getIdArgWhereSql());
		return SqlUtil.executeSql(sqlToyContext.getTypeHandler(), deleteSql, idValues, parameterTypes, conn, dbType,
				null);
	}

	/**
	 * @todo 批量删除对象并级联删除掉子表数据
	 * @param sqlToyContext
	 * @param entities
	 * @param batchSize
	 * @param conn
	 * @param dbType
	 * @param autoCommit
	 * @param tableName
	 * @return
	 * @throws Exception
	 */
	public static Long deleteAll(SqlToyContext sqlToyContext, List<?> entities, final int batchSize, Connection conn,
			final Integer dbType, final Boolean autoCommit, final String tableName) throws Exception {
		if (null == entities || entities.isEmpty()) {
			return 0L;
		}
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entities.get(0).getClass());
		String realTable = entityMeta.getSchemaTable(tableName);
		if (null == entityMeta.getIdArray() || entityMeta.getIdArray().length == 0) {
			throw new IllegalArgumentException("delete/deleteAll 操作,表:" + realTable + "没有主键,请检查表设计!");
		}
		int idSize = entityMeta.getIdArray().length;
		// 构造delete 语句(clickhouse 记录删除语法特殊)
		StringBuilder deleteSql = new StringBuilder();
		deleteSql.append("alter table ");
		deleteSql.append(realTable);
		deleteSql.append(" delete where ");
		String field;
		SqlToyResult sqlToyResult = null;
		String colName;
		//单主键
		if (idSize == 1) {
			Object[] idValues = BeanUtil.sliceToArray(entities, entityMeta.getIdArray()[0]);
			if (idValues == null || idValues.length == 0) {
				throw new IllegalArgumentException(
						tableName + " deleteAll method must assign value for pk field:" + entityMeta.getIdArray()[0]);
			}
			field = entityMeta.getIdArray()[0];
			colName = ReservedWordsUtil.convertWord(entityMeta.getColumnName(field), dbType);
			deleteSql.append(colName);
			deleteSql.append(" in (?) ");
			sqlToyResult = SqlConfigParseUtils.processSql(deleteSql.toString(), null, new Object[] { idValues });
		} else {
			List<Object[]> idValues = BeanUtil.reflectBeansToInnerAry(entities, entityMeta.getIdArray(), null, null);
			int dataSize = idValues.size();
			Object[] rowData;
			Object cellValue;
			// 将条件构造成一个数组
			Object[] realValues = new Object[idValues.size() * idSize];
			int index = 0;
			for (int i = 0; i < dataSize; i++) {
				rowData = idValues.get(i);
				for (int j = 0; j < idSize; j++) {
					cellValue = rowData[j];
					// 验证主键值是否合法
					if (StringUtil.isBlank(cellValue)) {
						throw new IllegalArgumentException(tableName + " deleteAll method must assign value for pk,row:"
								+ i + " pk field:" + entityMeta.getIdArray()[j]);
					}
					realValues[index] = cellValue;
					index++;
				}
			}
			// 复合主键构造 (field1=? and field2=?)
			String condition = " (";
			for (int i = 0, n = idSize; i < n; i++) {
				field = entityMeta.getIdArray()[i];
				colName = ReservedWordsUtil.convertWord(entityMeta.getColumnName(field), dbType);
				if (i > 0) {
					condition = condition.concat(" and ");
				}
				condition = condition.concat(colName).concat("=?");
			}
			condition = condition.concat(")");
			// 构造 (field1=? and field2=?) or (field1=? and field2=?)
			for (int i = 0; i < dataSize; i++) {
				if (i > 0) {
					deleteSql.append(" or ");
				}
				deleteSql.append(condition);
			}
			sqlToyResult = SqlConfigParseUtils.processSql(deleteSql.toString(), null, realValues);
		}
		return SqlUtil.executeSql(sqlToyContext.getTypeHandler(), sqlToyResult.getSql(), sqlToyResult.getParamsValue(),
				null, conn, dbType, autoCommit);
	}

	public static Long update(SqlToyContext sqlToyContext, Serializable entity, String nullFunction,
			String[] forceUpdateFields, Connection conn, final Integer dbType, String tableName) throws Exception {
		EntityMeta entityMeta = sqlToyContext.getEntityMeta(entity.getClass());
		String realTable = entityMeta.getSchemaTable(tableName);
		// 无主键
		if (entityMeta.getIdArray() == null) {
			throw new IllegalArgumentException("表:" + realTable + " 无主键,不符合update/updateAll规则,请检查表设计是否合理!");
		}
		// 全部是主键则无需update
		if (entityMeta.getRejectIdFieldArray() == null) {
			logger.warn("表:" + realTable + " 字段全部是主键不存在更新字段,无需执行更新操作!");
			return 0L;
		}
		// 构造全新的修改记录参数赋值反射(覆盖之前的)
		AbstractReflectPropertyHandler handler = DialectUtils.getUpdateReflectHandler(sqlToyContext, null, forceUpdateFields);
		// 排除分区字段
		String[] fields = entityMeta.getFieldsNotPartitionKey();
		Object[] fieldsValues = BeanUtil.reflectBeanToAry(entity, fields, null, handler);
		// 判断主键是否为空
		int end = fields.length;
		int pkIndex = end - entityMeta.getIdArray().length;
		for (int i = pkIndex; i < end; i++) {
			if (StringUtil.isBlank(fieldsValues[i])) {
				throw new IllegalArgumentException("通过对象对表:" + realTable + " 进行update操作,主键字段必须要赋值!");
			}
		}
		// 构建update语句
		String updateSql = generateUpdateSql(dbType, entityMeta, nullFunction, forceUpdateFields, tableName);
		if (updateSql == null) {
			throw new IllegalArgumentException("update sql is null,引起问题的原因是没有设置需要修改的字段!");
		}
		Long updateCnt = SqlUtil.executeSql(sqlToyContext.getTypeHandler(), updateSql, fieldsValues,
				entityMeta.getFieldsTypeArray(), conn, dbType, null);
		return updateCnt;
	}

	private static String generateUpdateSql(Integer dbType, EntityMeta entityMeta, String nullFunction,
			String[] forceUpdateFields, String tableName) {
		if (entityMeta.getIdArray() == null) {
			return null;
		}
		StringBuilder sql = new StringBuilder(entityMeta.getFieldsArray().length * 30 + 30);
		sql.append(" alter table  ");
		sql.append(entityMeta.getSchemaTable(tableName));
		sql.append(" update ");
		String columnName;
		// 需要被强制修改的字段
		HashSet<String> fupc = new HashSet<String>();
		if (forceUpdateFields != null) {
			for (String field : forceUpdateFields) {
				fupc.add(ReservedWordsUtil.convertWord(entityMeta.getColumnName(field), dbType));
			}
		}
		FieldMeta fieldMeta;
		int meter = 0;
		for (int i = 0, n = entityMeta.getRejectIdFieldArray().length; i < n; i++) {
			fieldMeta = entityMeta.getFieldMeta(entityMeta.getRejectIdFieldArray()[i]);
			// 排除分区字段
			if (!fieldMeta.isPartitionKey()) {
				columnName = ReservedWordsUtil.convertWord(fieldMeta.getColumnName(), dbType);
				if (meter > 0) {
					sql.append(",");
				}
				sql.append(columnName);
				sql.append("=");
				if (fupc.contains(columnName)) {
					sql.append("?");
				} else {
					sql.append(nullFunction);
					sql.append("(?,").append(columnName).append(")");
				}
				meter++;
			}
		}
		sql.append(" where ");
		for (int i = 0, n = entityMeta.getIdArray().length; i < n; i++) {
			columnName = entityMeta.getColumnName(entityMeta.getIdArray()[i]);
			columnName = ReservedWordsUtil.convertWord(columnName, dbType);
			if (i > 0) {
				sql.append(" and ");
			}
			sql.append(columnName);
			sql.append("=?");
		}
		return sql.toString();
	}

	public static boolean isAssignPKValue(PKStrategy pkStrategy) {
		if (pkStrategy == null) {
			return true;
		}
		if (pkStrategy.equals(PKStrategy.SEQUENCE)) {
			return true;
		}
		if (pkStrategy.equals(PKStrategy.IDENTITY)) {
			return false;
		}
		return true;
	}
}
