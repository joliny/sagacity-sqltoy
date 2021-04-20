package org.sagacity.sqltoy.translate;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.config.model.Translate;
import org.sagacity.sqltoy.model.TranslateExtend;
import org.sagacity.sqltoy.translate.cache.AbstractTranslateCacheManager;
import org.sagacity.sqltoy.translate.cache.impl.TranslateEhcacheManager;
import org.sagacity.sqltoy.translate.model.CheckerConfigModel;
import org.sagacity.sqltoy.translate.model.DefaultConfig;
import org.sagacity.sqltoy.translate.model.TranslateConfigModel;
import org.sagacity.sqltoy.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @project sqltoy-orm
 * @description sqltoy 缓存翻译器(通过缓存存储常用数据，如数据字典、机构、员工等，从而在数据库查询时可以避免 关联查询)
 * @author zhongxuchen
 * @version v1.0,Date:2013年4月8日
 * @modify {Date:2017-12-8,提取缓存时增加分库策略判断,如果存在分库策略dataSource则按照分库逻辑提取}
 * @modify {Date:2018-1-5,增强缓存更新检测机制}
 */
public class TranslateManager {
	/**
	 * 定义全局日志
	 */
	protected final static Logger logger = LoggerFactory.getLogger(TranslateManager.class);

	/**
	 * 翻译缓存管理器，默认提供基于ehcache的实现，用户可以另行定义
	 */
	private AbstractTranslateCacheManager translateCacheManager;

	/**
	 * 字符集
	 */
	private String charset = "UTF-8";

	/**
	 * 翻译配置解析后的模型
	 */
	private HashMap<String, TranslateConfigModel> translateMap = new HashMap<String, TranslateConfigModel>();

	/**
	 * 更新检测器
	 */
	private List<CheckerConfigModel> updateCheckers = new ArrayList<CheckerConfigModel>();

	/**
	 * 是否初始化过
	 */
	private boolean initialized = false;

	/**
	 * 翻译器配置文件,默认配置文件放于classpath下面，名称为sqltoy-translate.xml
	 */
	private String translateConfig = "classpath:sqltoy-translate.xml";

	/**
	 * 缓存更新检测程序(后台线程)
	 */
	private CacheUpdateWatcher cacheCheck;

	private SqlToyContext sqlToyContext;

	/**
	 * @param translateConfig the translateConfig to set
	 */
	public void setTranslateConfig(String translateConfig) {
		this.translateConfig = translateConfig;
	}

	public synchronized void initialize(SqlToyContext sqlToyContext, AbstractTranslateCacheManager cacheManager,
			int delayCheckCacheSeconds) throws Exception {
		// 防止被多次调用
		if (initialized) {
			return;
		}
		try {
			this.sqlToyContext = sqlToyContext;
			initialized = true;
			logger.debug("开始加载sqltoy的translate缓存翻译配置文件:{}", translateConfig);
			// 加载和解析缓存翻译的配置
			DefaultConfig defaultConfig = TranslateConfigParse.parseTranslateConfig(sqlToyContext, translateMap,
					updateCheckers, translateConfig, charset);
			// 配置了缓存翻译
			if (!translateMap.isEmpty()) {
				//可以自定义缓存管理器,默认为ehcache实现
				if (cacheManager == null) {
					translateCacheManager = new TranslateEhcacheManager();
				} else {
					translateCacheManager = cacheManager;
				}
				// 设置默认存储路径
				if (!StringUtil.isBlank(defaultConfig.getDiskStorePath())
						&& translateCacheManager instanceof TranslateEhcacheManager) {
					((TranslateEhcacheManager) translateCacheManager)
							.setDiskStorePath(defaultConfig.getDiskStorePath());
				}
				// 设置装入具体缓存配置
				translateCacheManager.setTranslateMap(translateMap);
				boolean initSuccess = translateCacheManager.init();

				// 每隔1秒执行一次检查(检查各个任务时间间隔是否到达设定的区间,并不意味着一秒执行数据库或调用接口) 正常情况下,
				// 这种检查都是高效率的空转不影响性能
				if (initSuccess && !updateCheckers.isEmpty()) {
					cacheCheck = new CacheUpdateWatcher(sqlToyContext, translateCacheManager, updateCheckers,
							delayCheckCacheSeconds, defaultConfig.getDeviationSeconds());
					cacheCheck.start();
					logger.debug("sqltoy的translate缓存配置加载完成,已经启动:{} 个缓存更新检测!", updateCheckers.size());
				} else {
					logger.debug("sqltoy的translate缓存配置加载完成,您没有配置缓存更新检测机制或没有配置缓存,将不做缓存更新检测!");
				}
			} else {
				logger.warn("translateConfig={} 中未定义缓存,请正确定义,如不使用缓存翻译可忽视此提示!", translateConfig);
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("加载和解析xml过程发生异常!{}", e.getMessage(), e);
			throw e;
		}
	}

	/**
	 * @todo 根据sqltoy sql.xml中的翻译设置获取对应的缓存(多个translate对应的多个缓存结果)
	 * @param conn
	 * @param translates
	 * @return
	 * @throws Exception
	 */
	public HashMap<String, HashMap<String, Object[]>> getTranslates(Connection conn,
			HashMap<String, Translate> translates) {
		HashMap<String, HashMap<String, Object[]>> result = new HashMap<String, HashMap<String, Object[]>>();
		HashMap<String, Object[]> cache;
		TranslateConfigModel cacheModel;
		TranslateExtend extend;
		for (Map.Entry<String, Translate> entry : translates.entrySet()) {
			extend = entry.getValue().getExtend();
			if (translateMap.containsKey(extend.cache)) {
				cacheModel = translateMap.get(extend.cache);
				cache = getCacheData(cacheModel, extend.cacheType);
				if (cache != null) {
					result.put(extend.column, cache);
				} else {
					result.put(extend.column, new HashMap<String, Object[]>());
					if (logger.isWarnEnabled()) {
						logger.warn("sqltoy translate:cacheName={},cache-type={},column={}配置不正确,未获取对应cache数据!",
								cacheModel.getCache(), extend.cacheType, extend.column);
					} else {
						System.err.println("sqltoy translate:cacheName=" + cacheModel.getCache() + ",cache-type="
								+ extend.cacheType + ",column=" + extend.column + " 配置不正确,未获取对应cache数据!");
					}
				}
			} else {
				logger.error("cacheName:{} 没有配置,请检查sqltoy-translate.xml文件!", extend.cache);
			}
		}
		return result;
	}

	/**
	 * @todo 根据sqltoy sql.xml中的翻译设置获取对应的缓存
	 * @param cacheModel
	 * @param cacheType  一般为null,不为空时一般用于数据字典等同于dictType
	 * @return
	 * @throws Exception
	 */
	private HashMap<String, Object[]> getCacheData(TranslateConfigModel cacheModel, String cacheType) {
		// 从缓存中提取数据
		HashMap<String, Object[]> result = translateCacheManager.getCache(cacheModel.getCache(), cacheType);
		// 数据为空则执行调用逻辑提取数据放入缓存，否则直接返回
		if (result == null || result.isEmpty()) {
			result = TranslateFactory.getCacheData(sqlToyContext, cacheModel, cacheType);
			// 放入缓存
			if (result != null && !result.isEmpty()) {
				translateCacheManager.put(cacheModel, cacheModel.getCache(), cacheType, result);
			}
		}
		return result;
	}

	/**
	 * @see getCacheData(String cacheName, String cacheType) 剔除sqlToyContext参数
	 * @param sqlToyContext
	 * @param cacheName
	 * @param cacheType
	 * @return
	 */
	@Deprecated
	public HashMap<String, Object[]> getCacheData(SqlToyContext sqlToyContext, String cacheName, String cacheType) {
		return getCacheData(cacheName, cacheType);
	}

	/**
	 * @todo 提供对外的访问(如要做增量更新可以对这里的数据进行修改即可达到缓存的更新作用)
	 * @param cacheName
	 * @param cacheType (一般为null,不为空时一般用于数据字典等同于dictType)
	 * @return
	 * @throws Exception
	 */
	public HashMap<String, Object[]> getCacheData(String cacheName, String cacheType) {
		TranslateConfigModel cacheModel = translateMap.get(cacheName);
		if (cacheModel == null) {
			logger.error("cacheName:{} 没有配置,请检查sqltoy-translate.xml文件!", cacheName);
			return null;
		}
		return getCacheData(cacheModel, cacheType);
	}

	/**
	 * @todo 更新单个缓存的整体数据
	 * @param cacheName
	 * @param cacheType  (默认为null，针对诸如数据字典类型的，对应字典类型)
	 * @param cacheValue
	 */
	public void put(String cacheName, String cacheType, HashMap<String, Object[]> cacheValue) {
		if (translateCacheManager != null) {
			TranslateConfigModel cacheModel = translateMap.get(cacheName);
			if (cacheModel == null) {
				logger.error("cacheName:{} 没有配置,请检查sqltoy-translate.xml文件!", cacheName);
				return;
			}
			translateCacheManager.put(cacheModel, cacheName, cacheType, cacheValue);
		}
	}

	/**
	 * @todo 清空缓存
	 * @param cacheName
	 * @param cacheType (默认为null，针对诸如数据字典类型的，对应字典类型)
	 */
	public void clear(String cacheName, String cacheType) {
		if (translateCacheManager != null) {
			translateCacheManager.clear(cacheName, cacheType);
		}
	}

	/**
	 * @todo 判断cache是否存在
	 * @param cacheName
	 * @return
	 */
	public boolean existCache(String cacheName) {
		TranslateConfigModel cacheModel = translateMap.get(cacheName);
		if (cacheModel != null) {
			return true;
		}
		return false;
	}

	/**
	 * @todo 获取所有缓存的名称
	 * @return
	 */
	public Set<String> getCacheNames() {
		return translateMap.keySet();
	}

	/**
	 * @param charset the charset to set
	 */
	public void setCharset(String charset) {
		this.charset = charset;
	}

	/**
	 * @return the translateCacheManager
	 */
	public AbstractTranslateCacheManager getTranslateCacheManager() {
		return translateCacheManager;
	}

	public void destroy() {
		try {
			if (translateCacheManager != null) {
				translateCacheManager.destroy();
			}
			if (cacheCheck != null && !cacheCheck.isInterrupted()) {
				cacheCheck.interrupt();
			}
		} catch (Exception e) {

		}
	}
}
