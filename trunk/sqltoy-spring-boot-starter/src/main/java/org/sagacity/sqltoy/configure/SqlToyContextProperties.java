package org.sagacity.sqltoy.configure;

import java.io.Serializable;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author zhongxuchen
 * @version v1.0,Date:2020年2月20日
 */
@ConfigurationProperties(prefix = "spring.sqltoy")
public class SqlToyContextProperties implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -8313800149129731930L;

	/**
	 * 指定sql.xml 文件路径,多个路径用逗号分隔
	 */
	private String sqlResourcesDir;

	/**
	 * 缓存翻译的配置文件
	 */
	private String translateConfig;

	/**
	 * 针对不同数据库函数进行转换,非必须属性,close 表示关闭
	 */
	private Object functionConverts;

	/**
	 * 数据库方言，一般无需设置
	 */
	private String dialect;

	/**
	 * Sqltoy实体Entity包路径,非必须属性
	 */
	private String[] packagesToScan;

	/**
	 * 额外注解class类，已经没有必要
	 */
	private String[] annotatedClasses;

	/**
	 * 具体的sql.xml 文件资源
	 */
	private String[] sqlResources;

	/**
	 * es的配置
	 */
	private Elastic elastic;

	/**
	 * 是否开启debug模式(默认为false)
	 */
	private Boolean debug;

	/**
	 * 批量操作，每批次数量,默认200
	 */
	private Integer batchSize;

	/**
	 * 分页最大单页数据量(默认是5万)
	 */
	private Integer pageFetchSizeLimit;

	/**
	 * 超时打印sql(毫秒,默认30秒)
	 */
	private Integer printSqlTimeoutMillis;

	/**
	 * sql打印策略,分debug\error两种模式,默认error时打印
	 */
	private String printSqlStrategy = "error";

	private Integer scriptCheckIntervalSeconds;

	private Integer delayCheckSeconds;

	private String encoding;

	/**
	 * 分页页号超出总页时转第一页，否则返回空集合
	 */
	private boolean pageOverToFirst = true;

	/**
	 * 统一字段处理器
	 */
	private String unifyFieldsHandler;

	/**
	 * 数据库方言参数配置
	 */
	private Map<String, String> dialectConfig;

	/**
	 * sqltoy默认数据库
	 */
	private String defaultDataSource;

	/**
	 * 数据库保留字,用逗号分隔
	 */
	private String reservedWords;

	/**
	 * 自定义获取DataSource的策略类
	 */
	private String obtainDataSource;

	/**
	 * 缓存管理器
	 */
	private String translateCacheManager;

	/**
	 * 字段类型转换器
	 */
	private String typeHandler;

	/**
	 * 自定义数据源选择器
	 */
	private String dataSourceSelector;

	/**
	 * 缓存类型，默认ehcache，可选caffeine
	 */
	private String cacheType = "ehcache";

	/**
	 * @return the sqlResourcesDir
	 */
	public String getSqlResourcesDir() {
		return sqlResourcesDir;
	}

	/**
	 * @param sqlResourcesDir the sqlResourcesDir to set
	 */
	public void setSqlResourcesDir(String sqlResourcesDir) {
		this.sqlResourcesDir = sqlResourcesDir;
	}

	/**
	 * @return the translateConfig
	 */
	public String getTranslateConfig() {
		return translateConfig;
	}

	/**
	 * @param translateConfig the translateConfig to set
	 */
	public void setTranslateConfig(String translateConfig) {
		this.translateConfig = translateConfig;
	}

	public Boolean getDebug() {
		return debug;
	}

	public void setDebug(Boolean debug) {
		this.debug = debug;
	}

	/**
	 * @return the batchSize
	 */
	public Integer getBatchSize() {
		return batchSize;
	}

	/**
	 * @param batchSize the batchSize to set
	 */
	public void setBatchSize(Integer batchSize) {
		this.batchSize = batchSize;
	}

	public Object getFunctionConverts() {
		return functionConverts;
	}

	/**
	 * functionConverts=close 表示关闭
	 * 
	 * @param functionConverts
	 */
	public void setFunctionConverts(Object functionConverts) {
		this.functionConverts = functionConverts;
	}

	/**
	 * @return the packagesToScan
	 */
	public String[] getPackagesToScan() {
		return packagesToScan;
	}

	/**
	 * @param packagesToScan the packagesToScan to set
	 */
	public void setPackagesToScan(String[] packagesToScan) {
		this.packagesToScan = packagesToScan;
	}

	/**
	 * @return the unifyFieldsHandler
	 */
	public String getUnifyFieldsHandler() {
		return this.unifyFieldsHandler;
	}

	/**
	 * @param unifyFieldsHandler the unifyFieldsHandler to set
	 */
	public void setUnifyFieldsHandler(String unifyFieldsHandler) {
		this.unifyFieldsHandler = unifyFieldsHandler;
	}

	public Elastic getElastic() {
		return elastic;
	}

	public void setElastic(Elastic elastic) {
		this.elastic = elastic;
	}

	public String getDialect() {
		return dialect;
	}

	public void setDialect(String dialect) {
		this.dialect = dialect;
	}

	public Map<String, String> getDialectConfig() {
		return dialectConfig;
	}

	public void setDialectConfig(Map<String, String> dialectConfig) {
		this.dialectConfig = dialectConfig;
	}

	public Integer getPageFetchSizeLimit() {
		return pageFetchSizeLimit;
	}

	public void setPageFetchSizeLimit(Integer pageFetchSizeLimit) {
		this.pageFetchSizeLimit = pageFetchSizeLimit;
	}

	public String[] getAnnotatedClasses() {
		return annotatedClasses;
	}

	public void setAnnotatedClasses(String[] annotatedClasses) {
		this.annotatedClasses = annotatedClasses;
	}

	public String getEncoding() {
		return encoding;
	}

	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	public Integer getPrintSqlTimeoutMillis() {
		return printSqlTimeoutMillis;
	}

	public void setPrintSqlTimeoutMillis(Integer printSqlTimeoutMillis) {
		this.printSqlTimeoutMillis = printSqlTimeoutMillis;
	}

	public String getPrintSqlStrategy() {
		return printSqlStrategy;
	}

	public void setPrintSqlStrategy(String printSqlStrategy) {
		this.printSqlStrategy = printSqlStrategy;
	}

	public Integer getScriptCheckIntervalSeconds() {
		return scriptCheckIntervalSeconds;
	}

	public void setScriptCheckIntervalSeconds(Integer scriptCheckIntervalSeconds) {
		this.scriptCheckIntervalSeconds = scriptCheckIntervalSeconds;
	}

	public Integer getDelayCheckSeconds() {
		return delayCheckSeconds;
	}

	public void setDelayCheckSeconds(Integer delayCheckSeconds) {
		this.delayCheckSeconds = delayCheckSeconds;
	}

	public String[] getSqlResources() {
		return sqlResources;
	}

	public void setSqlResources(String[] sqlResources) {
		this.sqlResources = sqlResources;
	}

	public String getDefaultDataSource() {
		return defaultDataSource;
	}

	public void setDefaultDataSource(String defaultDataSource) {
		this.defaultDataSource = defaultDataSource;
	}

	/**
	 * @return the reservedWords
	 */
	public String getReservedWords() {
		return reservedWords;
	}

	/**
	 * @param reservedWords the reservedWords to set
	 */
	public void setReservedWords(String reservedWords) {
		this.reservedWords = reservedWords;
	}

	public String getObtainDataSource() {
		return obtainDataSource;
	}

	public void setObtainDataSource(String obtainDataSource) {
		this.obtainDataSource = obtainDataSource;
	}

	/**
	 * @return the translateCacheManager
	 */
	public String getTranslateCacheManager() {
		return translateCacheManager;
	}

	/**
	 * @param translateCacheManager the translateCacheManager to set
	 */
	public void setTranslateCacheManager(String translateCacheManager) {
		this.translateCacheManager = translateCacheManager;
	}

	/**
	 * @return the typeHandler
	 */
	public String getTypeHandler() {
		return typeHandler;
	}

	/**
	 * @param typeHandler the typeHandler to set
	 */
	public void setTypeHandler(String typeHandler) {
		this.typeHandler = typeHandler;
	}

	/**
	 * @return the cacheType
	 */
	public String getCacheType() {
		return cacheType;
	}

	/**
	 * @param cacheType the cacheType to set
	 */
	public void setCacheType(String cacheType) {
		this.cacheType = cacheType;
	}

	public boolean isPageOverToFirst() {
		return pageOverToFirst;
	}

	public void setPageOverToFirst(boolean pageOverToFirst) {
		this.pageOverToFirst = pageOverToFirst;
	}

	/**
	 * @return the dataSourceSelector
	 */
	public String getDataSourceSelector() {
		return dataSourceSelector;
	}

	/**
	 * @param dataSourceSelector the dataSourceSelector to set
	 */
	public void setDataSourceSelector(String dataSourceSelector) {
		this.dataSourceSelector = dataSourceSelector;
	}

}
