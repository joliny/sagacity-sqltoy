/**
 * 
 */
package org.sagacity.sqltoy.dialect.handler;

import org.sagacity.sqltoy.config.model.EntityMeta;

/**
 * @project sagacity-sqltoy4.0
 * @description 提供一个参数sql的反调函数，方便抽象统一的方言处理
 * @author zhongxuchen
 * @version v1.0,Date:2015年3月5日
 */
public abstract class AbstractGenerateSqlHandler {
	/**
	 * @todo 根据pojo的meta产生特定的诸如：insert、update等sql
	 * @param entityMeta
	 * @param forceUpdateFields
	 * @return
	 */
	public abstract String generateSql(EntityMeta entityMeta, String[] forceUpdateFields);
}
