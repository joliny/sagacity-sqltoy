package org.sagacity.sqltoy.plugins.function.impl;

import java.util.regex.Pattern;

import org.sagacity.sqltoy.plugins.function.AbstractFunction;
import org.sagacity.sqltoy.utils.DataSourceUtils.DBType;

/**
 * @project sqltoy-orm
 * @description 将在mysql中使用的if函数转换成case when 通用模式
 * @author renfei.chen <a href="mailto:zhongxuchen@gmail.com">联系作者</a>
 * @version v1.0,Date:2019-10-21
 */
public class If extends AbstractFunction {
	private static Pattern regex = Pattern.compile("(?i)\\Wif\\(");

	@Override
	public String dialects() {
		return ALL;
	}

	@Override
	public Pattern regex() {
		return regex;
	}

	@Override
	public String wrap(int dialect, String functionName, boolean hasArgs, String... args) {
		if (dialect == DBType.MYSQL || dialect == DBType.TIDB || dialect == DBType.MYSQL57) {
			return super.IGNORE;
		}
		if (args == null || args.length < 3) {
			return super.IGNORE;
		}
		return " case when " + args[0] + " then " + args[1] + " else " + args[2] + " end ";
	}

}
