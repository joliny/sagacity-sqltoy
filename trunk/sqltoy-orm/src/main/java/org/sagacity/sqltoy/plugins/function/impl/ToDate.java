/**
 * 
 */
package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.AbstractFunction;

/**
 * @project sqltoy-orm
 * @description 转换to_date函数
 * @author zhongxuchen
 * @version v1.0,Date:2013-1-2
 */
public class ToDate extends AbstractFunction {
	private static Pattern regex = Pattern.compile("(?i)\\Wto\\_date\\(");

	@Override
    public String dialects() {
		return "oracle,dm";
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
	 * @see org.sagacity.sqltoy.config.function.IFunction#wrap(java.lang.String [])
	 */
	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		return super.IGNORE;
	}
}
