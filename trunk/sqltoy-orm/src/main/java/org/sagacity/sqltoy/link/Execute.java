/**
 * 
 */
package org.sagacity.sqltoy.link;

import java.io.Serializable;

import javax.sql.DataSource;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.AbstractReflectPropertyHandler;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.utils.BeanUtil;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description 执行sql
 * @author zhongxuchen
 * @version v1.0,Date:2017年10月24日
 */
public class Execute extends BaseLink {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6336692505147861983L;

	/**
	 * 具体执行的sql
	 */
	private String sql;

	/**
	 * 作为参数传递的实体对象(属性跟sql中的参数名称对应)
	 */
	private Serializable entity;

	/**
	 * 参数反调设置器(特殊情况下使用)
	 */
	private AbstractReflectPropertyHandler reflectPropertyHandler;

	/**
	 * 是否自动提交
	 */
	private Boolean autoCommit = false;

	/**
	 * 参数名称
	 */
	private String[] paramsNamed;

	/**
	 * 参数值
	 */
	private Object[] paramsValue;

	/**
	 * @param sqlToyContext
	 * @param dataSource
	 */
	public Execute(SqlToyContext sqlToyContext, DataSource dataSource) {
		super(sqlToyContext, dataSource);
	}

	public Execute dataSource(DataSource dataSource) {
		this.dataSource = dataSource;
		this.defaultDataSource = false;
		return this;
	}

	public Execute entity(Serializable entity) {
		this.entity = entity;
		return this;
	}

	public Execute names(String... paramsNamed) {
		this.paramsNamed = paramsNamed;
		return this;
	}

	public Execute values(Object... paramsValue) {
		this.paramsValue = paramsValue;
		return this;
	}

	public Execute autoCommit(Boolean autoCommit) {
		this.autoCommit = autoCommit;
		return this;
	}

	public Execute reflectHandler(AbstractReflectPropertyHandler reflectPropertyHandler) {
		this.reflectPropertyHandler = reflectPropertyHandler;
		return this;
	}

	public Execute sql(String sql) {
		this.sql = sql;
		return this;
	}

	/**
	 * @todo 执行并返回修改的记录数量
	 * @return
	 */
	public Long submit() {
		if (StringUtil.isBlank(sql)) {
			throw new IllegalArgumentException("execute operate sql is null!");
		}
		SqlToyConfig sqlToyConfig = sqlToyContext.getSqlToyConfig(sql, SqlType.update, super.getDialect());
		// 根据sql中的变量从entity对象中提取参数值
		Object[] values = paramsValue;
		String[] names = paramsNamed;
		if (entity != null) {
			names = sqlToyConfig.getParamsName();
			values = BeanUtil.reflectBeanToAry(entity, names, null, reflectPropertyHandler);
		}
		return dialectFactory.executeSql(sqlToyContext, sqlToyConfig, names, values, null, autoCommit,
				getDataSource(sqlToyConfig));
	}
}
