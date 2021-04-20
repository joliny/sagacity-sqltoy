package org.sagacity.sqltoy.utils;

import java.lang.reflect.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.sagacity.sqltoy.SqlExecuteStat;
import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.callback.AbstractRowCallbackHandler;
import org.sagacity.sqltoy.callback.UpdateRowHandler;
import org.sagacity.sqltoy.config.SqlConfigParseUtils;
import org.sagacity.sqltoy.config.model.ColsChainRelativeModel;
import org.sagacity.sqltoy.config.model.FormatModel;
import org.sagacity.sqltoy.config.model.LinkModel;
import org.sagacity.sqltoy.config.model.PivotModel;
import org.sagacity.sqltoy.config.model.ReverseModel;
import org.sagacity.sqltoy.config.model.RowsChainRelativeModel;
import org.sagacity.sqltoy.config.model.SecureMask;
import org.sagacity.sqltoy.config.model.SqlToyConfig;
import org.sagacity.sqltoy.config.model.SqlToyResult;
import org.sagacity.sqltoy.config.model.SqlType;
import org.sagacity.sqltoy.config.model.SummaryModel;
import org.sagacity.sqltoy.config.model.Translate;
import org.sagacity.sqltoy.config.model.UnpivotModel;
import org.sagacity.sqltoy.dialect.utils.DialectUtils;
import org.sagacity.sqltoy.exception.DataAccessException;
import org.sagacity.sqltoy.executor.QueryExecutor;
import org.sagacity.sqltoy.model.DataSetResult;
import org.sagacity.sqltoy.model.LabelIndexModel;
import org.sagacity.sqltoy.model.QueryExecutorExtend;
import org.sagacity.sqltoy.model.QueryResult;
import org.sagacity.sqltoy.model.TranslateExtend;
import org.sagacity.sqltoy.plugins.calculator.ColsChainRelative;
import org.sagacity.sqltoy.plugins.calculator.GroupSummary;
import org.sagacity.sqltoy.plugins.calculator.ReverseList;
import org.sagacity.sqltoy.plugins.calculator.RowsChainRelative;
import org.sagacity.sqltoy.plugins.calculator.UnpivotList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sagacity-sqltoy
 * @description 提供查询结果的缓存key-value提取以及结果分组link功能
 * @author zhongxuchen
 * @version v1.0,Date:2013-4-18
 * @modify Date:2016-12-13 {对行转列分类参照集合进行了排序}
 * @modify Date:2020-05-29 {将脱敏和格式化转到calculate中,便于elastic和mongo查询提供同样的功能}
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class ResultUtils {
	/**
	 * 定义日志
	 */
	private final static Logger logger = LoggerFactory.getLogger(ResultUtils.class);

	private ResultUtils() {
	}

	/**
	 * @todo 处理sql查询时的结果集,当没有反调或voClass反射处理时以数组方式返回resultSet的数据
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param conn
	 * @param rs
	 * @param rowCallbackHandler
	 * @param updateRowHandler
	 * @param startColIndex
	 * @return
	 * @throws Exception
	 */
	public static QueryResult processResultSet(final SqlToyContext sqlToyContext, final SqlToyConfig sqlToyConfig,
                                               Connection conn, ResultSet rs, AbstractRowCallbackHandler rowCallbackHandler, UpdateRowHandler updateRowHandler,
                                               int startColIndex) throws Exception {
		QueryResult result = new QueryResult();
		// 记录行记数器
		int index = 0;
		if (rowCallbackHandler != null) {
			while (rs.next()) {
				rowCallbackHandler.processRow(rs, index);
				index++;
			}
			result.setRows(rowCallbackHandler.getResult());
		} else {
			// 取得字段列数,在没有rowCallbackHandler時用数组返回
			int rowCnt = rs.getMetaData().getColumnCount();
			String[] labelNames = new String[rowCnt - startColIndex];
			String[] labelTypes = new String[rowCnt - startColIndex];
			HashMap<String, Integer> labelIndexMap = new HashMap<String, Integer>();
			for (int i = startColIndex; i < rowCnt; i++) {
				labelNames[index] = rs.getMetaData().getColumnLabel(i + 1);
				labelIndexMap.put(labelNames[index].toLowerCase(), index);
				labelTypes[index] = rs.getMetaData().getColumnTypeName(i + 1);
				index++;
			}
			result.setLabelNames(labelNames);
			result.setLabelTypes(labelTypes);
			// 返回结果为非VO class时才可以应用旋转和汇总合计功能
			try {
				result.setRows(getResultSet(sqlToyConfig, sqlToyContext, conn, rs, updateRowHandler, rowCnt,
						labelIndexMap, labelNames, startColIndex));
			} // update 2019-09-11 此处增加数组溢出异常是因为经常有开发设置缓存cache-indexs时写错误，为了增加错误提示信息的友好性增加此处理
			catch (ArrayIndexOutOfBoundsException oie) {
				oie.printStackTrace();
				logger.error("sql={} 的缓存翻译数组越界:{},请检查其<translate cache-indexs 配置是否正确,index值必须跟缓存数据的列对应!",
						sqlToyConfig.getId(), oie.getMessage());
				throw oie;
			} catch (Exception e) {
				throw e;
			}
		}
		// 填充记录数
		if (result.getRows() != null) {
			result.setRecordCount(Long.valueOf(result.getRows().size()));
		}
		return result;
	}

	/**
	 * @todo 对字段进行安全脱敏
	 * @param rows
	 * @param masks
	 * @param labelIndexMap
	 */
	private static void secureMask(List<List> rows, Iterator<SecureMask> masks, LabelIndexModel labelIndexMap) {
		Integer index;
		Object value;
		SecureMask mask;
		int columnIndex;
		while (masks.hasNext()) {
			mask = masks.next();
			index = labelIndexMap.get(mask.getColumn());
			if (index != null) {
				columnIndex = index.intValue();
				for (List row : rows) {
					value = row.get(columnIndex);
					if (value != null) {
						row.set(columnIndex, maskStr(mask, value));
					}
				}
			}
		}
	}

	/**
	 * @todo 对字段进行格式化
	 * @param rows
	 * @param formats
	 * @param labelIndexMap
	 */
	private static void formatColumn(List<List> rows, Iterator<FormatModel> formats, LabelIndexModel labelIndexMap) {
		Integer index;
		Object value;
		FormatModel fmt;
		int columnIndex;
		while (formats.hasNext()) {
			fmt = formats.next();
			index = labelIndexMap.get(fmt.getColumn());
			if (index != null) {
				columnIndex = index.intValue();
				for (List row : rows) {
					value = row.get(columnIndex);
					if (value != null) {
						// 日期格式
						if (fmt.getType() == 1) {
							row.set(columnIndex, DateUtil.formatDate(value, fmt.getFormat()));
						}
						// 数字格式化
						else {
							row.set(columnIndex, NumberUtil.format(value, fmt.getFormat()));
						}
					}
				}
			}
		}
	}

	/**
	 * @todo 对字符串脱敏
	 * @param mask
	 * @param value
	 * @return
	 */
	private static String maskStr(SecureMask mask, Object value) {
		String type = mask.getType();
		String realStr = value.toString();
		int size = realStr.length();
		// 单字符无需脱敏
		if (size == 1) {
			return realStr;
		}
		String maskCode = mask.getMaskCode();
		int headSize = mask.getHeadSize();
		int tailSize = mask.getTailSize();
		// 自定义剪切长度
		if (headSize > 0 || tailSize > 0) {
			return StringUtil.secureMask(realStr, (headSize > 0) ? headSize : 0, (tailSize > 0) ? tailSize : 0,
					maskCode);
		}
		// 按类别处理
		// 电话
		if ("tel".equals(type)) {
			if (size >= 11) {
				return StringUtil.secureMask(realStr, 3, 4, maskCode);
			} else {
				return StringUtil.secureMask(realStr, 4, 0, maskCode);
			}
		}
		// 邮件
		if ("email".equals(type)) {
			return realStr.substring(0, 1).concat(maskCode).concat(realStr.substring(realStr.indexOf("@")));
		}
		// 身份证
		if ("id-card".equals(type)) {
			return StringUtil.secureMask(realStr, 0, 4, maskCode);
		}
		// 银行卡
		if ("bank-card".equals(type)) {
			return StringUtil.secureMask(realStr, 6, 4, maskCode);
		}
		// 姓名
		if ("name".equals(type)) {
			if (size >= 4) {
				return StringUtil.secureMask(realStr, 2, 0, maskCode);
			} else {
				return StringUtil.secureMask(realStr, 1, 0, maskCode);
			}
		}
		// 地址
		if ("address".equals(type)) {
			if (size >= 12) {
				return StringUtil.secureMask(realStr, 6, 0, maskCode);
			} else if (size >= 8) {
				return StringUtil.secureMask(realStr, 4, 0, maskCode);
			} else {
				return StringUtil.secureMask(realStr, 2, 0, maskCode);
			}
		}
		// 对公银行账号
		if ("public-account".equals(type)) {
			return StringUtil.secureMask(realStr, 2, 0, maskCode);
		}

		// 按比例模糊(百分比)
		if (mask.getMaskRate() > 0) {
			int maskSize = Double.valueOf(size * mask.getMaskRate() * 1.00 / 100).intValue();
			if (maskSize < 1) {
				maskSize = 1;
			} else if (maskSize >= size) {
				maskSize = size - 1;
			}
			tailSize = (size - maskSize) / 2;
			headSize = size - maskSize - tailSize;
			if (maskCode == null) {
				maskCode = "*";
				if (maskSize > 3) {
					maskCode = "***";
				} else if (maskSize == 2) {
					maskCode = "**";
				}
			}
		}
		return StringUtil.secureMask(realStr, headSize, tailSize, maskCode);
	}

	private static List getResultSet(SqlToyConfig sqlToyConfig, SqlToyContext sqlToyContext, Connection conn,
			ResultSet rs, UpdateRowHandler updateRowHandler, int rowCnt, HashMap<String, Integer> labelIndexMap,
			String[] labelNames, int startColIndex) throws Exception {
		// 字段连接(多行数据拼接成一个数据,以一行显示)
		LinkModel linkModel = sqlToyConfig.getLinkModel();
		// update 2020-09-13 存在多列link(独立出去编写,避免对单列产生影响)
		if (linkModel != null && linkModel.getColumns().length > 1) {
			return getMoreLinkResultSet(sqlToyConfig, sqlToyContext, conn, rs, rowCnt, labelIndexMap, labelNames,
					startColIndex);
		}

		List<List> items = new ArrayList();
		// 判断是否有缓存翻译器定义
		Boolean hasTranslate = (sqlToyConfig.getTranslateMap().isEmpty()) ? false : true;
		HashMap<String, Translate> translateMap = sqlToyConfig.getTranslateMap();
		HashMap<String, HashMap<String, Object[]>> translateCache = null;
		if (hasTranslate) {
			translateCache = sqlToyContext.getTranslateManager().getTranslates(conn, translateMap);
			if (translateCache == null || translateCache.isEmpty()) {
				hasTranslate = false;
				logger.debug("通过缓存配置未获取到缓存数据,请正确配置TranslateManager!");
			}
		}

		// link 目前只支持单个字段运算
		int columnSize = labelNames.length;
		int index = 0;

		// 警告阀值
		int warnThresholds = SqlToyConstants.getWarnThresholds();
		boolean warnLimit = false;
		// 最大阀值
		long maxThresholds = SqlToyConstants.getMaxThresholds();
		boolean maxLimit = false;
		// 是否判断全部为null的行记录
		boolean ignoreAllEmpty = sqlToyConfig.isIgnoreEmpty();
		// 最大值要大于等于警告阀值
		if (maxThresholds > 1 && maxThresholds <= warnThresholds) {
			maxThresholds = warnThresholds;
		}
		List rowTemp;
		if (linkModel != null) {
			Object identity = null;
			String linkColumn = linkModel.getColumns()[0];
			if (!labelIndexMap.containsKey(linkColumn.toLowerCase())) {
				throw new DataAccessException("做link操作时,查询结果字段中没有字段:" + linkColumn + ",请检查sql或link 配置的正确性!");
			}
			int linkIndex = labelIndexMap.get(linkColumn.toLowerCase());
			StringBuilder linkBuffer = new StringBuilder();
			boolean hasDecorate = (linkModel.getDecorateAppendChar() == null) ? false : true;
			boolean isLeft = true;
			if (hasDecorate) {
				isLeft = "left".equals(linkModel.getDecorateAlign()) ? true : false;
			}
			Object preIdentity = null;
			Object linkValue;
			String linkStr;
			boolean translateLink = hasTranslate ? translateMap.containsKey(linkColumn.toLowerCase()) : false;
			HashMap<String, Object[]> linkTranslateMap = null;
			int linkTranslateIndex = 1;
			TranslateExtend extend = null;
			if (translateLink) {
				extend = translateMap.get(linkColumn.toLowerCase()).getExtend();
				linkTranslateIndex = extend.index;
				linkTranslateMap = translateCache.get(extend.column);
			}
			Object[] cacheValues;
			// 判断link拼接是否重新开始
			boolean isLastProcess = false;
			while (rs.next()) {
				isLastProcess = false;
				linkValue = rs.getObject(linkColumn);
				if (linkValue == null) {
					linkStr = "";
				} else {
					if (translateLink) {
						cacheValues = linkTranslateMap.get(linkValue.toString());
						if (cacheValues == null) {
							linkStr = "[" + linkValue + "]未匹配";
							logger.debug("translate cache:{},cacheType:{}, 对应的key:{} 没有设置相应的value!", extend.cache,
									extend.cacheType, linkValue);
						} else {
							linkStr = (cacheValues[linkTranslateIndex] == null) ? ""
									: cacheValues[linkTranslateIndex].toString();
						}
					} else {
						linkStr = linkValue.toString();
					}
				}
				identity = (linkModel.getIdColumn() == null) ? "default" : rs.getObject(linkModel.getIdColumn());
				// 不相等
				if (!identity.equals(preIdentity)) {
					if (index != 0) {
						items.get(items.size() - 1).set(linkIndex, linkBuffer.toString());
						linkBuffer.delete(0, linkBuffer.length());
					}
					linkBuffer.append(linkStr);
					if (hasTranslate) {
						rowTemp = processResultRowWithTranslate(translateMap, translateCache, labelNames, rs,
								columnSize, ignoreAllEmpty);
					} else {
						rowTemp = processResultRow(rs, startColIndex, rowCnt, ignoreAllEmpty);
					}
					if (rowTemp != null) {
						items.add(rowTemp);
					}
					preIdentity = identity;
				} else {
					isLastProcess = true;
					if (linkBuffer.length() > 0) {
						linkBuffer.append(linkModel.getSign());
					}
					linkBuffer
							.append(hasDecorate
									? StringUtil.appendStr(linkStr, linkModel.getDecorateAppendChar(),
											linkModel.getDecorateSize(), isLeft)
									: linkStr);
				}
				index++;
				// 存在超出25000条数据的查询
				if (index == warnThresholds) {
					warnLimit = true;
				}
				// 提取数据超过上限(-1表示不限制)
				if (index == maxThresholds) {
					maxLimit = true;
					break;
				}
			}
			// 对最后一条写入循环值
			if (isLastProcess) {
				items.get(items.size() - 1).set(linkIndex, linkBuffer.toString());
			}
		} else {
			// 修改操作不支持link操作
			boolean isUpdate = false;
			if (updateRowHandler != null) {
				isUpdate = true;
			}
			// 循环通过java reflection将rs中的值映射到VO中
			if (hasTranslate) {
				while (rs.next()) {
					// 先修改后再获取最终值
					if (isUpdate) {
						updateRowHandler.updateRow(rs, index);
						rs.updateRow();
					}
					rowTemp = processResultRowWithTranslate(translateMap, translateCache, labelNames, rs, columnSize,
							ignoreAllEmpty);
					if (rowTemp != null) {
						items.add(rowTemp);
					}
					index++;
					// 存在超出25000条数据的查询(具体数据规模可以通过参数进行定义)
					if (index == warnThresholds) {
						warnLimit = true;
					}
					// 超出最大提取数据阀值,直接终止数据提取
					if (index == maxThresholds) {
						maxLimit = true;
						break;
					}
				}
			} else {
				while (rs.next()) {
					if (isUpdate) {
						updateRowHandler.updateRow(rs, index);
						rs.updateRow();
					}
					rowTemp = processResultRow(rs, startColIndex, rowCnt, ignoreAllEmpty);
					if (rowTemp != null) {
						items.add(rowTemp);
					}
					index++;
					// 存在超出警告规模级的数据查询
					if (index == warnThresholds) {
						warnLimit = true;
					}
					// 提取数据超过上限(-1表示不限制)
					if (index == maxThresholds) {
						maxLimit = true;
						break;
					}
				}
			}
		}
		// 超出警告阀值
		if (warnLimit) {
			warnLog(sqlToyConfig, index);
		}
		// 超过最大提取数据阀值
		if (maxLimit) {
			logger.error("MaxLargeResult:执行sql提取数据超出最大阀值限制{},sqlId={},具体语句={}", index, sqlToyConfig.getId(),
					sqlToyConfig.getSql(null));
		}
		return items;
	}

	/**
	 * @TODO 实现多列link
	 * @param sqlToyConfig
	 * @param sqlToyContext
	 * @param conn
	 * @param rs
	 * @param rowCnt
	 * @param labelIndexMap
	 * @param labelNames
	 * @param startColIndex
	 * @return
	 * @throws Exception
	 */
	private static List getMoreLinkResultSet(SqlToyConfig sqlToyConfig, SqlToyContext sqlToyContext, Connection conn,
			ResultSet rs, int rowCnt, HashMap<String, Integer> labelIndexMap, String[] labelNames, int startColIndex)
			throws Exception {
		// 字段连接(多行数据拼接成一个数据,以一行显示)
		LinkModel linkModel = sqlToyConfig.getLinkModel();
		List<List> items = new ArrayList();
		// 判断是否有缓存翻译器定义
		Boolean hasTranslate = (sqlToyConfig.getTranslateMap().isEmpty()) ? false : true;
		HashMap<String, Translate> translateMap = sqlToyConfig.getTranslateMap();
		HashMap<String, HashMap<String, Object[]>> translateCache = null;
		if (hasTranslate) {
			translateCache = sqlToyContext.getTranslateManager().getTranslates(conn, translateMap);
			if (translateCache == null || translateCache.isEmpty()) {
				hasTranslate = false;
				logger.debug("通过缓存配置未获取到缓存数据,请正确配置TranslateManager!");
			}
		}
		// link 目前只支持单个字段运算
		int columnSize = labelNames.length;
		int index = 0;
		// 警告阀值
		int warnThresholds = SqlToyConstants.getWarnThresholds();
		boolean warnLimit = false;
		// 最大阀值
		long maxThresholds = SqlToyConstants.getMaxThresholds();
		boolean maxLimit = false;
		// 是否判断全部为null的行记录
		boolean ignoreAllEmpty = sqlToyConfig.isIgnoreEmpty();
		// 最大值要大于等于警告阀值
		if (maxThresholds > 1 && maxThresholds <= warnThresholds) {
			maxThresholds = warnThresholds;
		}
		int linkCols = linkModel.getColumns().length;
		String[] linkColumns = linkModel.getColumns();
		int[] linkIndexs = new int[linkCols];
		StringBuilder[] linkBuffers = new StringBuilder[linkCols];
		boolean[] translateLinks = new boolean[linkCols];
		TranslateExtend[] transExtends = new TranslateExtend[linkCols];
		String linkColumn;
		for (int i = 0; i < linkCols; i++) {
			linkBuffers[i] = new StringBuilder();
			linkColumn = linkColumns[i];
			if (!labelIndexMap.containsKey(linkColumn.toLowerCase())) {
				throw new DataAccessException("做link操作时,查询结果字段中没有字段:" + linkColumn + ",请检查sql或link 配置的正确性!");
			}
			linkIndexs[i] = labelIndexMap.get(linkColumn.toLowerCase());
			if (hasTranslate) {
				translateLinks[i] = translateMap.containsKey(linkColumn.toLowerCase());
				if (translateLinks[i]) {
					transExtends[i] = translateMap.get(linkColumn.toLowerCase()).getExtend();
				}
			}
		}
		// link是否有修饰器
		boolean hasDecorate = (linkModel.getDecorateAppendChar() == null) ? false : true;
		boolean isLeft = true;
		if (hasDecorate) {
			isLeft = "left".equals(linkModel.getDecorateAlign()) ? true : false;
		}
		Object preIdentity = null;
		Object[] linkValues = new Object[linkCols];
		String[] linkStrs = new String[linkCols];
		TranslateExtend extend = null;
		Object[] cacheValues;
		List rowTemp;
		Object identity = null;
		// 判断link拼接是否重新开始
		boolean isLastProcess = false;
		while (rs.next()) {
			isLastProcess = false;
			// 对多个link字段取值并进行翻译转义
			for (int i = 0; i < linkCols; i++) {
				linkValues[i] = rs.getObject(linkColumns[i]);
				if (linkValues[i] == null) {
					linkStrs[i] = "";
				} else {
					if (translateLinks[i]) {
						extend = transExtends[i];
						cacheValues = translateCache.get(extend.column).get(linkValues[i].toString());
						if (cacheValues == null) {
							linkStrs[i] = "[" + linkValues[i] + "]未匹配";
							logger.debug("translate cache:{},cacheType:{}, 对应的key:{} 没有设置相应的value!", extend.cache,
									extend.cacheType, linkValues[i]);
						} else {
							linkStrs[i] = (cacheValues[extend.index] == null) ? ""
									: cacheValues[extend.index].toString();
						}
					} else {
						linkStrs[i] = linkValues[i].toString();
					}
				}
			}
			// 取分组列的值
			identity = (linkModel.getIdColumn() == null) ? "default" : rs.getObject(linkModel.getIdColumn());
			// 不相等
			if (!identity.equals(preIdentity)) {
				// 不相等时先对最后一条记录修改，写入拼接后的字符串
				if (index != 0) {
					rowTemp = items.get(items.size() - 1);
					for (int i = 0; i < linkCols; i++) {
						rowTemp.set(linkIndexs[i], linkBuffers[i].toString());
						linkBuffers[i].delete(0, linkBuffers[i].length());
					}
				}
				// 再写入新的拼接串
				for (int i = 0; i < linkCols; i++) {
					linkBuffers[i].append(linkStrs[i]);
				}
				// 提取result中的数据(identity相等时不需要提取)
				if (hasTranslate) {
					rowTemp = processResultRowWithTranslate(translateMap, translateCache, labelNames, rs, columnSize,
							ignoreAllEmpty);
				} else {
					rowTemp = processResultRow(rs, startColIndex, rowCnt, ignoreAllEmpty);
				}
				if (rowTemp != null) {
					items.add(rowTemp);
				}
				preIdentity = identity;
			} else {
				isLastProcess = true;
				// identity相同，表示还在同一组内，直接拼接link字符
				for (int i = 0; i < linkCols; i++) {
					if (linkBuffers[i].length() > 0) {
						linkBuffers[i].append(linkModel.getSign());
					}
					linkBuffers[i].append(hasDecorate ? StringUtil.appendStr(linkStrs[i],
							linkModel.getDecorateAppendChar(), linkModel.getDecorateSize(), isLeft) : linkStrs[i]);
				}
			}
			index++;
			// 存在超出25000条数据的查询
			if (index == warnThresholds) {
				warnLimit = true;
			}
			// 提取数据超过上限(-1表示不限制)
			if (index == maxThresholds) {
				maxLimit = true;
				break;
			}
		}
		// 数据集合不为空,对最后一条记录写入循环值
		if (isLastProcess) {
			rowTemp = items.get(items.size() - 1);
			for (int i = 0; i < linkCols; i++) {
				rowTemp.set(linkIndexs[i], linkBuffers[i].toString());
			}
		}
		// 超出警告阀值
		if (warnLimit) {
			warnLog(sqlToyConfig, index);
		}
		// 超过最大提取数据阀值
		if (maxLimit) {
			logger.error("MaxLargeResult:执行sql提取数据超出最大阀值限制{},sqlId={},具体语句={}", index, sqlToyConfig.getId(),
					sqlToyConfig.getSql(null));
		}
		return items;
	}

	/**
	 * @todo 对结果进行数据旋转
	 * @param pivotModel
	 * @param labelIndexMap
	 * @param result
	 * @param pivotCategorySet
	 * @return
	 * @throws Exception
	 */
	private static List pivotResult(PivotModel pivotModel, LabelIndexModel labelIndexMap, List result,
			List pivotCategorySet) {
		if (result == null || result.isEmpty()) {
			return result;
		}
		// 行列转换
		if (pivotModel.getGroupCols() == null || pivotModel.getCategoryCols().length == 0) {
			return CollectionUtil.convertColToRow(result, null);
		}
		// 参照列，如按年份进行旋转
		Integer[] categoryCols = mappingLabelIndex(pivotModel.getCategoryCols(), labelIndexMap);
		// 旋转列，如按年份进行旋转，则旋转列为：年份下面的合格数量、不合格数量等子分类数据
		Integer[] pivotCols = mappingLabelIndex(pivotModel.getPivotCols(), labelIndexMap);
		// 分组主键列（以哪几列为基准）
		Integer[] groupCols = mappingLabelIndex(pivotModel.getGroupCols(), labelIndexMap);
		// update 2016-12-13 提取category后进行了排序
		List categoryList = (pivotCategorySet == null) ? extractCategory(result, categoryCols) : pivotCategorySet;
		return CollectionUtil.pivotList(result, categoryList, null, groupCols, categoryCols, pivotCols[0],
				pivotCols[pivotCols.length - 1], pivotModel.getDefaultValue());
	}

	/**
	 * @todo 将label别名换成对应的列编号(select name,sex from xxxTable，name别名对应的列则为0)
	 * @param columnLabels
	 * @param labelIndexMap
	 * @return
	 */
	private static Integer[] mappingLabelIndex(String[] columnLabels, LabelIndexModel labelIndexMap) {
		Integer[] result = new Integer[columnLabels.length];
		for (int i = 0; i < result.length; i++) {
			if (NumberUtil.isInteger(columnLabels[i])) {
				result[i] = Integer.parseInt(columnLabels[i]);
			} else {
				result[i] = labelIndexMap.get(columnLabels[i].toLowerCase());
			}
		}
		return result;
	}

	/**
	 * @todo 提取出选择的横向分类信息
	 * @param items
	 * @param categoryCols
	 * @return
	 */
	private static List extractCategory(List items, Integer[] categoryCols) {
		List categoryList = new ArrayList();
		HashMap identityMap = new HashMap();
		String tmpStr;
		int categorySize = categoryCols.length;
		Object obj;
		List categoryRow;
		List row;
		for (int i = 0, size = items.size(); i < size; i++) {
			row = (List) items.get(i);
			tmpStr = "";
			categoryRow = new ArrayList();
			for (int j = 0; j < categorySize; j++) {
				obj = row.get(categoryCols[j]);
				categoryRow.add(obj);
				tmpStr = tmpStr.concat(obj == null ? "null" : obj.toString());
			}
			// 不存在
			if (!identityMap.containsKey(tmpStr)) {
				categoryList.add(categoryRow);
				identityMap.put(tmpStr, "");
			}
		}
		// 分组排序输出
		if (categoryCols.length > 1) {
			categoryList = sortList(categoryList, 0, 0, categoryList.size() - 1, true);
			for (int i = 1; i < categoryCols.length; i++) {
				categoryList = sortGroupList(categoryList, i - 1, i, true);
			}
		}
		return CollectionUtil.convertColToRow(categoryList, null);
	}

	/**
	 * @todo 分组排序
	 * @param sortList
	 * @param groupCol
	 * @param orderCol
	 * @param ascend
	 * @return
	 */
	private static List sortGroupList(List<List> sortList, int groupCol, int orderCol, boolean ascend) {
		int length = sortList.size();
		// 1:string,2:数字;3:日期
		int start = 0;
		int end;
		Object compareValue = null;
		Object tempObj;
		for (int i = 0; i < length; i++) {
			tempObj = sortList.get(i).get(groupCol);
			if (!tempObj.equals(compareValue)) {
				end = i - 1;
				sortList(sortList, orderCol, start, end, ascend);
				start = i;
				compareValue = tempObj;
			}
			if (i == length - 1) {
				sortList(sortList, orderCol, start, i, ascend);
			}
		}
		return sortList;
	}

	/**
	 * @todo 对二维数据进行排序
	 * @param sortList
	 * @param orderCol
	 * @param dataType
	 * @param ascend
	 * @return
	 */
	private static List sortList(List<List> sortList, int orderCol, int start, int end, boolean ascend) {
		if (end <= start) {
			return sortList;
		}
		Object iData;
		Object jData;
		// 1:string,2:数字;3:日期
		boolean lessThen = false;
		for (int i = start; i < end; i++) {
			for (int j = i + 1; j < end + 1; j++) {
				iData = sortList.get(i).get(orderCol);
				jData = sortList.get(j).get(orderCol);
				if ((iData == null && jData == null) || (iData != null && jData == null)) {
					lessThen = false;
				} else if (iData == null && jData != null) {
					lessThen = true;
				} else {
					lessThen = (iData.toString()).compareTo(jData.toString()) < 0;
				}

				if ((ascend && !lessThen) || (!ascend && lessThen)) {
					List tempList = sortList.get(i);
					sortList.set(i, sortList.get(j));
					sortList.set(j, tempList);
				}
			}
		}
		return sortList;
	}

	/**
	 * @todo 处理ResultSet的单行数据
	 * @param rs
	 * @param startColIndex
	 * @param rowCnt
	 * @param ignoreAllEmptySet
	 * @return
	 * @throws Exception
	 */
	private static List processResultRow(ResultSet rs, int startColIndex, int rowCnt, boolean ignoreAllEmptySet)
			throws Exception {
		List rowData = new ArrayList();
		Object fieldValue;
		// 单行所有字段结果为null
		boolean allNull = true;
		for (int i = startColIndex; i < rowCnt; i++) {
			fieldValue = rs.getObject(i + 1);
			if (null != fieldValue) {
				if (fieldValue instanceof java.sql.Clob) {
					fieldValue = SqlUtil.clobToString((java.sql.Clob) fieldValue);
				}
				// 有一个非null
				allNull = false;
			}
			rowData.add(fieldValue);
		}
		// 全null返回null结果，外围判断结果为null则不加入结果集合
		if (allNull && ignoreAllEmptySet) {
			return null;
		}
		return rowData;
	}

	/**
	 * @todo 存在缓存翻译的结果处理
	 * @param translateMap
	 * @param translateCaches
	 * @param labelNames
	 * @param rs
	 * @param size
	 * @param ignoreAllEmptySet
	 * @return
	 * @throws Exception
	 */
	private static List processResultRowWithTranslate(HashMap<String, Translate> translateMap,
			HashMap<String, HashMap<String, Object[]>> translateCaches, String[] labelNames, ResultSet rs, int size,
			boolean ignoreAllEmptySet) throws Exception {
		List rowData = new ArrayList();
		Object fieldValue;
		TranslateExtend extend;
		String label;
		String keyIndex;
		boolean allNull = true;
		for (int i = 0; i < size; i++) {
			label = labelNames[i];
			fieldValue = rs.getObject(label);
			label = label.toLowerCase();
			keyIndex = Integer.toString(i);
			if (null != fieldValue) {
				allNull = false;
				if (fieldValue instanceof java.sql.Clob) {
					fieldValue = SqlUtil.clobToString((java.sql.Clob) fieldValue);
				}
				if (translateMap.containsKey(label) || translateMap.containsKey(keyIndex)) {
					extend = translateMap.get(label).getExtend();
					if (extend == null) {
						extend = translateMap.get(keyIndex).getExtend();
					}
					fieldValue = translateKey(extend, translateCaches.get(extend.column), fieldValue);
				}
			}
			rowData.add(fieldValue);
		}
		if (allNull && ignoreAllEmptySet) {
			return null;
		}
		return rowData;
	}

	/**
	 * @date 2018-5-26 优化缓存翻译，提供keyCode1,keyCode2,keyCode3 形式的多代码翻译
	 * @todo 统一对key进行缓存翻译
	 * @param translateExtend
	 * @param translateKeyMap
	 * @param fieldValue
	 * @return
	 */
	private static Object translateKey(TranslateExtend extend, HashMap<String, Object[]> translateKeyMap,
			Object fieldValue) {
		String fieldStr = fieldValue.toString();
		// 单值翻译
		if (extend.splitRegex == null) {
			if (extend.keyTemplate != null) {
				// keyTemplate已经提前做了规整,将${key},${},${0} 统一成了{}
				fieldStr = extend.keyTemplate.replace("{}", fieldStr);
			}
			// 根据key获取缓存值
			Object[] cacheValues = translateKeyMap.get(fieldStr);
			// 未匹配到
			if (cacheValues == null || cacheValues.length == 0) {
				if (extend.uncached != null) {
					fieldValue = extend.uncached.replace("${value}", fieldStr);
				} else {
					fieldValue = fieldValue.toString();
				}
				logger.warn("translate cache:{},cacheType:{}, 对应的key:{}没有设置相应的value!", extend.cache, extend.cacheType,
						fieldValue);
			} else {
				fieldValue = cacheValues[extend.index];
			}
			return fieldValue;
		}
		// 将字符串用分隔符切分开进行逐个翻译
		String[] keys = null;
		String splitReg = extend.splitRegex.trim();
		if (",".equals(splitReg)) {
			keys = fieldStr.split("\\,");
		} else if (";".equals(splitReg)) {
			keys = fieldStr.split("\\;");
		} else if (":".equals(splitReg)) {
			keys = fieldStr.split("\\:");
		} else if ("".equals(splitReg)) {
			keys = fieldStr.split("\\s+");
		} else {
			keys = fieldStr.split(extend.splitRegex);
		}

		String linkSign = extend.linkSign;
		StringBuilder result = new StringBuilder();
		int index = 0;
		Object[] cacheValues;
		for (String key : keys) {
			if (index > 0) {
				result.append(linkSign);
			}
			cacheValues = translateKeyMap.get(key.trim());
			if (cacheValues == null || cacheValues.length == 0) {
				if (extend.uncached != null) {
					result.append(extend.uncached.replace("${value}", key));
				} else {
					result.append(key);
				}
				logger.warn("translate cache:{},cacheType:{}, 对应的key:{}没有设置相应的value!", extend.cache, extend.cacheType,
						key);
			} else {
				result.append(cacheValues[extend.index]);
			}
			index++;
		}
		return result.toString();
	}

	/**
	 * @todo 提取数据旋转对应的sql查询结果
	 * @param sqlToyContext
	 * @param sqlToyConfig
	 * @param queryExecutor
	 * @param conn
	 * @param dbType
	 * @param dialect
	 * @return
	 * @throws Exception
	 */
	public static List getPivotCategory(SqlToyContext sqlToyContext, SqlToyConfig sqlToyConfig,
			QueryExecutor queryExecutor, Connection conn, final Integer dbType, String dialect) throws Exception {
		if (sqlToyConfig.getResultProcessor() != null && !sqlToyConfig.getResultProcessor().isEmpty()) {
			List resultProcessors = sqlToyConfig.getResultProcessor();
			Object processor;
			QueryExecutorExtend extend = queryExecutor.getInnerModel();
			for (int i = 0; i < resultProcessors.size(); i++) {
				processor = resultProcessors.get(i);
				// 数据旋转只能存在一个
				if (processor instanceof PivotModel) {
					PivotModel pivotModel = (PivotModel) processor;
					if (pivotModel.getCategorySql() != null) {
						SqlToyConfig pivotSqlConfig = DialectUtils.getUnifyParamsNamedConfig(sqlToyContext,
								sqlToyContext.getSqlToyConfig(pivotModel.getCategorySql(), SqlType.search, ""),
								queryExecutor, dialect, false);
						SqlToyResult pivotSqlToyResult = SqlConfigParseUtils.processSql(pivotSqlConfig.getSql(dialect),
								extend.getParamsName(pivotSqlConfig),
								extend.getParamsValue(sqlToyContext, pivotSqlConfig));
						List pivotCategory = SqlUtil.findByJdbcQuery(sqlToyContext.getTypeHandler(),
								pivotSqlToyResult.getSql(), pivotSqlToyResult.getParamsValue(), null, null, conn,
								dbType, sqlToyConfig.isIgnoreEmpty(), null);
						// 行转列返回
						return CollectionUtil.convertColToRow(pivotCategory, null);
					}
				}
			}
		}
		return null;
	}

	/**
	 * @todo 对查询结果进行计算处理:字段脱敏、格式化、数据旋转、同步环比、分组汇总等
	 * @param sqlToyConfig
	 * @param dataSetResult
	 * @param pivotCategorySet
	 * @param extend
	 */
	public static boolean calculate(SqlToyConfig sqlToyConfig, DataSetResult dataSetResult, List pivotCategorySet,
			QueryExecutorExtend extend) {
		List items = dataSetResult.getRows();
		// 数据为空直接跳出处理
		if (items == null || items.isEmpty()) {
			return false;
		}

		boolean changedCols = false;
		List<SecureMask> secureMasks = sqlToyConfig.getSecureMasks();
		List<FormatModel> formatModels = sqlToyConfig.getFormatModels();
		List resultProcessors = sqlToyConfig.getResultProcessor();
		// 整理列名称跟index的对照map
		LabelIndexModel labelIndexMap = null;
		if (!secureMasks.isEmpty() || !formatModels.isEmpty()
				|| (extend != null && (!extend.secureMask.isEmpty() || !extend.colsFormat.isEmpty()))
				|| !resultProcessors.isEmpty()) {
			labelIndexMap = wrapLabelIndexMap(dataSetResult.getLabelNames());
		}
		// 字段脱敏
		if (!secureMasks.isEmpty()) {
			secureMask(items, secureMasks.iterator(), labelIndexMap);
		}

		// 自动格式化
		if (!formatModels.isEmpty()) {
			formatColumn(items, formatModels.iterator(), labelIndexMap);
		}
		// 扩展脱敏和格式化处理
		if (extend != null) {
			if (!extend.secureMask.isEmpty()) {
				secureMask(items, extend.secureMask.values().iterator(), labelIndexMap);
			}
			if (!extend.colsFormat.isEmpty()) {
				formatColumn(items, extend.colsFormat.values().iterator(), labelIndexMap);
			}
		}
		// 计算
		if (!resultProcessors.isEmpty()) {
			Object processor;
			for (int i = 0; i < resultProcessors.size(); i++) {
				processor = resultProcessors.get(i);
				// 数据旋转(行转列)
				if (processor instanceof PivotModel) {
					items = pivotResult((PivotModel) processor, labelIndexMap, items, pivotCategorySet);
					changedCols = true;
				} // 列转行
				else if (processor instanceof UnpivotModel) {
					items = UnpivotList.process((UnpivotModel) processor, dataSetResult, labelIndexMap, items);
					//changedCols = true;
				} else if (processor instanceof SummaryModel) {
					// 数据汇总合计
					GroupSummary.process((SummaryModel) processor, labelIndexMap, items);
				} else if (processor instanceof ColsChainRelativeModel) {
					// 列数据环比
					ColsChainRelative.process((ColsChainRelativeModel) processor, labelIndexMap, items);
					changedCols = true;
				} else if (processor instanceof RowsChainRelativeModel) {
					// 行数据环比
					RowsChainRelative.process((RowsChainRelativeModel) processor, labelIndexMap, items);
				} else if (processor instanceof ReverseModel) {
					// 数据反序
					ReverseList.process((ReverseModel) processor, labelIndexMap, items);
				}
			}
			dataSetResult.setRows(items);
		}
		return changedCols;
	}

	/**
	 * @TODO 建立列名称跟列index的对应关系
	 * @param fields
	 * @return
	 */
	private static LabelIndexModel wrapLabelIndexMap(String[] fields) {
		LabelIndexModel result = new LabelIndexModel();
		if (fields != null && fields.length > 0) {
			String realLabelName;
			int index;
			for (int i = 0, n = fields.length; i < n; i++) {
				realLabelName = fields[i].toLowerCase();
				index = realLabelName.indexOf(":");
				if (index != -1) {
					realLabelName = realLabelName.substring(index + 1).trim();
				}
				result.put(realLabelName, i);
			}
		}
		return result;
	}

	/**
	 * @todo 根据查询结果的类型，构造相应对象集合(增加map形式的结果返回机制)
	 * @param sqlToyContext
	 * @param queryResultRows
	 * @param labelNames
	 * @param resultType
	 * @param changedCols
	 * @param humpMapLabel
	 * @return
	 * @throws Exception
	 */
	public static List wrapQueryResult(SqlToyContext sqlToyContext, List queryResultRows, String[] labelNames,
			Class resultType, boolean changedCols, boolean humpMapLabel) throws Exception {
		// 类型为null就默认返回二维List
		if (queryResultRows == null || queryResultRows.isEmpty() || resultType == null || resultType.equals(List.class)
				|| resultType.equals(ArrayList.class) || resultType.equals(Collection.class)) {
			return queryResultRows;
		}
		// 返回数组类型
		if (Array.class.equals(resultType)) {
			return CollectionUtil.innerListToArray(queryResultRows);
		}
		// 已经存在pivot、unpivot、列环比计算等
		if (changedCols) {
			logger.warn("查询中存在类似pivot、列同比环比计算导致结果'列'数不固定，因此不支持转map或VO对象!");
			SqlExecuteStat.debug("映射结果类型错误", "查询中存在类似pivot、列同比环比计算导致结果'列'数不固定，因此不支持转map或VO对象!");
		}
		Class superClass = resultType.getSuperclass();
		// 如果结果类型是hashMap
		if (resultType.equals(HashMap.class) || resultType.equals(ConcurrentHashMap.class)
				|| resultType.equals(Map.class) || resultType.equals(ConcurrentMap.class)
				|| HashMap.class.equals(superClass) || LinkedHashMap.class.equals(superClass)
				|| ConcurrentHashMap.class.equals(superClass) || Map.class.equals(superClass)) {
			int width = labelNames.length;
			String[] realLabel = labelNames;
			// 驼峰处理
			if (humpMapLabel) {
				realLabel = humpFieldNames(labelNames, null);
			}
			List result = new ArrayList();
			List rowList;
			boolean isMap = resultType.equals(Map.class);
			boolean isConMap = resultType.equals(ConcurrentMap.class);
			for (int i = 0, n = queryResultRows.size(); i < n; i++) {
				rowList = (List) queryResultRows.get(i);
				Map rowMap;
				if (isMap) {
					rowMap = new HashMap();
				} else if (isConMap) {
					rowMap = new ConcurrentHashMap();
				} else {
					rowMap = (Map) resultType.getDeclaredConstructor().newInstance();
				}
				for (int j = 0; j < width; j++) {
					rowMap.put(realLabel[j], rowList.get(j));
				}
				result.add(rowMap);
			}
			return result;
		}
		HashMap<String, String> columnFieldMap = null;
		if (sqlToyContext.isEntity(resultType)) {
			columnFieldMap = sqlToyContext.getEntityMeta(resultType).getColumnFieldMap();
		}
		// 封装成VO对象形式
		return BeanUtil.reflectListToBean(sqlToyContext.getTypeHandler(), queryResultRows,
				convertRealProps(labelNames, columnFieldMap), resultType);
	}

	private static String[] convertRealProps(String[] labelNames, HashMap<String, String> colFieldMap) {
		if (colFieldMap != null && !colFieldMap.isEmpty()) {
			String key;
			for (int i = 0; i < labelNames.length; i++) {
				key = labelNames[i].toLowerCase();
				if (colFieldMap.containsKey(key)) {
					labelNames[i] = colFieldMap.get(key);
				}
			}
		}
		return labelNames;
	}

	/**
	 * @todo 加工字段名称，将数据库sql查询的columnName转成对应对象的属性名称(去除下划线)
	 * @param labelNames
	 * @param colFieldMap
	 * @return
	 */
	public static String[] humpFieldNames(String[] labelNames, HashMap<String, String> colFieldMap) {
		if (labelNames == null) {
			return null;
		}
		String[] result = new String[labelNames.length];
		if (colFieldMap == null) {
			for (int i = 0, n = labelNames.length; i < n; i++) {
				result[i] = StringUtil.toHumpStr(labelNames[i], false);
			}
		} else {
			for (int i = 0, n = labelNames.length; i < n; i++) {
				result[i] = colFieldMap.get(labelNames[i].toLowerCase());
				if (result[i] == null) {
					result[i] = StringUtil.toHumpStr(labelNames[i], false);
				}
			}
		}
		return result;
	}

	/**
	 * @todo 警告性日志记录,凡是单次获取超过一定规模数据的操作记录日志
	 * @param sqlToyConfig
	 * @param totalCount
	 */
	private static void warnLog(SqlToyConfig sqlToyConfig, int totalCount) {
		logger.warn("Large Result:totalCount={},sqlId={},sql={}", totalCount, sqlToyConfig.getId(),
				sqlToyConfig.getSql(null));
	}
}
