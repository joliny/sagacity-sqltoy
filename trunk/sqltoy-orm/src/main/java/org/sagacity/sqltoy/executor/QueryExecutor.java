/**
 * 
 */
package org.sagacity.sqltoy.executor;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.math.RoundingMode;
import java.util.Map;

import javax.sql.DataSource;

import org.sagacity.sqltoy.callback.ReflectPropertyHandler;
import org.sagacity.sqltoy.callback.RowCallbackHandler;
import org.sagacity.sqltoy.config.model.FormatModel;
import org.sagacity.sqltoy.config.model.PageOptimize;
import org.sagacity.sqltoy.config.model.SecureMask;
import org.sagacity.sqltoy.config.model.ShardingStrategyConfig;
import org.sagacity.sqltoy.config.model.Translate;
import org.sagacity.sqltoy.model.LockMode;
import org.sagacity.sqltoy.model.MaskType;
import org.sagacity.sqltoy.model.NamedValuesModel;
import org.sagacity.sqltoy.model.ParamsFilter;
import org.sagacity.sqltoy.model.QueryExecutorExtend;
import org.sagacity.sqltoy.model.TranslateExtend;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.CollectionUtil;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sqltoy-orm
 * @description 构造统一的查询条件模型
 * @author zhongxuchen
 * @version v1.0,Date:2012-9-3
 */
public class QueryExecutor implements Serializable {
	/**
	 * 定义日志
	 */
	protected final Logger logger = LoggerFactory.getLogger(QueryExecutor.class);

	/**
	 * 
	 */
	private static final long serialVersionUID = -6149173009738072148L;

	/**
	 * 扩展内部模型,减少过多get方法干扰开发
	 */
	private QueryExecutorExtend innerModel = new QueryExecutorExtend();

	public QueryExecutor(String sql) {
		innerModel.sql = sql;
	}

	/**
	 * update 2018-4-10 针对开发者将entity传入Class类别产生的bug进行提示
	 * 
	 * @param sql
	 * @param entity
	 * @throws Exception
	 */
	public QueryExecutor(String sql, Serializable entity) {
		innerModel.sql = sql;
		innerModel.entity = entity;
		if (entity != null) {
			// 避免使用{{}}双大括号来初始化对象时getClass不是VO自身的问题
			innerModel.resultType = BeanUtil.getEntityClass(entity.getClass());
			// 类型检测
			if (innerModel.resultType.equals("".getClass().getClass())) {
				throw new IllegalArgumentException("查询参数是要求传递对象的实例,不是传递对象的class类别!你的参数=" + ((Class) entity).getName());
			}
		} else {
			logger.warn("请关注:查询语句sql={} 指定的查询条件参数entity=null,将以ArrayList作为默认类型返回!", sql);
		}
	}

	/**
	 * @TODO 动态增加参数过滤,对参数进行转null或其他的加工处理
	 * @param filters
	 * @return
	 */
	public QueryExecutor filters(ParamsFilter... filters) {
		if (filters != null && filters.length > 0) {
			for (ParamsFilter filter : filters) {
				if (StringUtil.isBlank(filter.getType()) || StringUtil.isBlank(filter.getParams())) {
					throw new IllegalArgumentException("针对QueryExecutor设置条件过滤必须要设置filterParams=[" + filter.getParams()
							+ "],和filterType=[" + filter.getType() + "]!");
				}
				if (CollectionUtil.any(filter.getType(), "eq", "neq", "gt", "gte", "lt", "lte", "blank")) {
					if (StringUtil.isBlank(filter.getValue())) {
						throw new IllegalArgumentException("针对QueryExecutor设置条件过滤eq、neq、gt、lt等类型必须要设置values值!");
					}
				}
				// 存在blank 过滤器自动将blank param="*" 关闭
				if ("blank".equals(filter.getType())) {
					innerModel.blankToNull = false;
				}
				innerModel.paramFilters.add(filter);
			}
		}
		return this;
	}

	public QueryExecutor(String sql, Map<String, Object> paramsMap) {
		innerModel.sql = sql;
		NamedValuesModel model = CollectionUtil.mapToNamedValues(paramsMap);
		innerModel.paramsName = model.getNames();
		innerModel.paramsValue = model.getValues();
		innerModel.shardingParamsValue = model.getValues();
	}

	public QueryExecutor(String sql, String[] paramsName, Object[] paramsValue) {
		innerModel.sql = sql;
		innerModel.paramsName = paramsName;
		innerModel.paramsValue = paramsValue;
		innerModel.shardingParamsValue = paramsValue;
	}

	/**
	 * @TODO 设置数据源
	 * @param dataSource
	 * @return
	 */
	public QueryExecutor dataSource(DataSource dataSource) {
		innerModel.dataSource = dataSource;
		return this;
	}

	public QueryExecutor names(String... paramsName) {
		innerModel.paramsName = paramsName;
		return this;
	}

	public QueryExecutor values(Object... paramsValue) {
		innerModel.paramsValue = paramsValue;
		innerModel.shardingParamsValue = paramsValue;
		return this;
	}

	/**
	 * @TODO 锁记录
	 * @param lockMode
	 * @return
	 */
	public QueryExecutor lock(LockMode lockMode) {
		innerModel.lockMode = lockMode;
		return this;
	}

	/**
	 * @TODO 设置返回结果的类型
	 * @param resultType
	 * @return
	 */
	public QueryExecutor resultType(Type resultType) {
		if (resultType == null) {
			logger.warn("请关注:查询语句sql={} 指定的resultType=null,将以ArrayList作为默认类型返回!", innerModel.sql);
		}
		innerModel.resultType = resultType;
		return this;
	}

	/**
	 * @TODO 设置分库策略
	 * @param strategy
	 * @param paramNames
	 * @return
	 */
	public QueryExecutor dbSharding(String strategy, String... paramNames) {
		ShardingStrategyConfig sharding = new ShardingStrategyConfig(0);
		sharding.setStrategy(strategy);
		sharding.setFields(paramNames);
		sharding.setAliasNames(paramNames);
		innerModel.dbSharding = sharding;
		return this;
	}

	/**
	 * @TODO 设置分表策略,再复杂场景则推荐用xml的sql中定义
	 * @param strategy
	 * @param tables
	 * @param paramNames 分表策略依赖的参数
	 * @return
	 */
	public QueryExecutor tableSharding(String strategy, String[] tables, String... paramNames) {
		ShardingStrategyConfig sharding = new ShardingStrategyConfig(1);
		sharding.setTables(tables);
		sharding.setStrategy(strategy);
		sharding.setFields(paramNames);
		sharding.setAliasNames(paramNames);
		innerModel.tableShardings.add(sharding);
		return this;
	}

	/**
	 * @TODO 设置jdbc参数，一般无需设置
	 * @param fetchSize
	 * @return
	 */
	public QueryExecutor fetchSize(int fetchSize) {
		innerModel.fetchSize = fetchSize;
		return this;
	}

	/**
	 * @TODO 设置最大提取记录数量(一般不用设置)
	 * @param maxRows
	 * @return
	 */
	public QueryExecutor maxRows(int maxRows) {
		innerModel.maxRows = maxRows;
		return this;
	}

	/**
	 * @TODO 针对resultType为Map.class 时，设定map的key是否转为骆驼命名法，默认true
	 * @param humpMapLabel
	 * @return
	 */
	public QueryExecutor humpMapLabel(boolean humpMapLabel) {
		innerModel.humpMapLabel = humpMapLabel;
		return this;
	}

	/**
	 * @TODO 设置条件过滤空白转null为false，默认true
	 * @return
	 */
	public QueryExecutor blankNotNull() {
		innerModel.blankToNull = false;
		return this;
	}

	/**
	 * @TODO 对sql语句指定缓存翻译
	 * @param translates
	 * @return
	 */
	public QueryExecutor translates(Translate... translates) {
		if (translates != null && translates.length > 0) {
			TranslateExtend extend;
			for (Translate trans : translates) {
				extend = trans.getExtend();
				if (StringUtil.isBlank(extend.cache) || StringUtil.isBlank(extend.column)) {
					throw new IllegalArgumentException(
							"给查询增加的缓存翻译时未定义具体的cacheName=[" + extend.cache + "] 或 对应的column=[" + extend.column + "]!");
				}
				innerModel.translates.put(extend.column, trans);
			}
		}
		return this;
	}

	@Deprecated
	public QueryExecutor rowCallbackHandler(RowCallbackHandler rowCallbackHandler) {
		innerModel.rowCallbackHandler = rowCallbackHandler;
		return this;
	}

	// jdk8 stream之后意义已经不大
	@Deprecated
	public QueryExecutor reflectPropertyHandler(ReflectPropertyHandler reflectPropertyHandler) {
		innerModel.reflectPropertyHandler = reflectPropertyHandler;
		return this;
	}

	/**
	 * @TODO 结果日期格式化
	 * @param format
	 * @param params
	 * @return
	 */
	public QueryExecutor dateFmt(String format, String... columns) {
		if (StringUtil.isNotBlank(format) && columns != null && columns.length > 0) {
			for (String column : columns) {
				FormatModel fmt = new FormatModel();
				fmt.setType(1);
				fmt.setColumn(column);
				fmt.setFormat(format);
				innerModel.colsFormat.put(column, fmt);
			}
		}
		return this;
	}

	/**
	 * @TODO 对结果的数字进行格式化
	 * @param format
	 * @param roundingMode
	 * @param params
	 * @return
	 */
	public QueryExecutor numFmt(String format, RoundingMode roundingMode, String... columns) {
		if (StringUtil.isNotBlank(format) && columns != null && columns.length > 0) {
			for (String column : columns) {
				FormatModel fmt = new FormatModel();
				fmt.setType(2);
				fmt.setColumn(column);
				fmt.setFormat(format);
				fmt.setRoundingMode(roundingMode);
				innerModel.colsFormat.put(column, fmt);
			}
		}
		return this;
	}

	/**
	 * @TODO 对结果字段进行安全脱敏
	 * @param maskType
	 * @param params
	 * @return
	 */
	public QueryExecutor secureMask(MaskType maskType, String... columns) {
		if (maskType != null && columns != null && columns.length > 0) {
			for (String column : columns) {
				SecureMask mask = new SecureMask();
				mask.setColumn(column);
				mask.setType(maskType.getValue());
				innerModel.secureMask.put(column, mask);
			}
		}
		return this;
	}

	/**
	 * @TODO 分页优化
	 * @param pageOptimize
	 * @return
	 */
	public QueryExecutor pageOptimize(PageOptimize pageOptimize) {
		if (pageOptimize != null) {
			innerModel.pageOptimize = pageOptimize;
		}
		return this;
	}

	// 分库分表在xml中应用,代码中暂时不支持(必要性不强，不建议将sql写在代码中，更不推荐调试完sql再转成jooq对象查询模式)
//	public QueryExecutor shardingDB(String strategory, String... columns) {
//		return this;
//	}
//
//	public QueryExecutor shardingTable(String strategory, String... columns) {
//		return this;
//	}

	public QueryExecutorExtend getInnerModel() {
		return innerModel;
	}
}
