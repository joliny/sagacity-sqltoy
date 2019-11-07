/**
 * 
 */
package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.IFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sqltoy-orm
 * @description 不同数据库当前系统时间获取方式
 * @author renfei.chen <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version id:Now.java,Revision:v1.0,Date:2013-3-25
 */
public class Now extends IFunction {
	private static Pattern regex = Pattern.compile("(?i)\\W(((now|getdate|sysdate)\\()|(sysdate\\W))");

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.config.function.IFunction#dialects()
	 */
	@Override
	public String dialects() {
		return ALL;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.config.function.IFunction#regex()
	 */
	@Override
	public Pattern regex() {
		return regex;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.config.function.IFunction#wrap(int,
	 * java.lang.String[])
	 */
	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		if (dialect == DBType.MYSQL || dialect == DBType.MYSQL8 || dialect == DBType.POSTGRESQL
				|| dialect == DBType.POSTGRESQL11 || dialect == DBType.POSTGRESQL10) {
			return wrapArgs("now", args);
		} else if (dialect == DBType.ORACLE || dialect == DBType.ORACLE12) {
			return "sysdate";
		} else if (dialect == DBType.SQLSERVER || dialect == DBType.SQLSERVER2017 || dialect == DBType.SQLSERVER2014
				|| dialect == DBType.SQLSERVER2016 || dialect == DBType.SQLSERVER2019) {
			return wrapArgs("getdate", args);
		} else if (dialect == DBType.SYBASE_IQ) {
			if (hasArgs)
				return wrapArgs(functionName, args);
			else
				return "getdate()";
		}
		return super.IGNORE;
	}
}
