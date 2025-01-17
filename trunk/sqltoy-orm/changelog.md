﻿# v4.18.3 2021-2-26
* 1、级联操作进行优化，精简级联配置，增加OneToOne类型的支持

```java
	// 可以自行定义oneToMany 和oneToOne
	// fields:表示当前表字段(当单字段关联且是主键时可不填)
	// mappedFields:表示对应关联表的字段
	// delete:表示是否执行级联删除，考虑安全默认为false(有实际外键时quickvo生成会是true)
	@OneToOne(fields = { "transDate", "transCode" }, mappedFields = { "transDate", "transId" }, delete = true)
	private ComplexpkItemVO complexpkItemVO;
```
* 2、修复xml定义sql中number-format和date-format多个参数换行没有trim的缺陷
* 3、优化cache-arg 反向通过名称匹配key，将之前字符串包含变为类似数据库like模式，可以实现：中国 苏州 带空格的模式匹配
* 4、quickvo 进行级联优化适配升级，版本4.18.3

# v4.13.11 2020-7-31
* 1、修复EntityManager中对OneToMany解析bug(4.13.10 版本已经调整)。

```java
for (int i = 0; i < idSize; i++) {
	// update 2020-7-30 修复取值错误,原:var = oneToMany.mappedFields()[i];
	var = oneToMany.fields()[i];
	for (int j = 0; j < idSize; j++) {
		idFieldName = idList.get(j);
		if (var.equalsIgnoreCase(idFieldName)) {
			// 原mappedFields[j] = var;
			mappedFields[j] = oneToMany.mappedFields()[i];
			mappedColumns[j] = oneToMany.mappedColumns()[i];
			break;
		}
	}
}
```
* 2、修复loadCascade时Class... cascadeTypes 模式传参判空处理错误，导致无法实际进行加载(4.13.7 版本修改导致)

```java
protected <T extends Serializable> T loadCascade(T entity, LockMode lockMode, Class... cascadeTypes) {
	if (entity == null) {
		return null;
	}
	Class[] cascades = cascadeTypes;
	// 当没有指定级联子类默认全部级联加载(update 2020-7-31 缺失了cascades.length == 0 判断)
	if (cascades == null || cascades.length == 0) {
		cascades = sqlToyContext.getEntityMeta(entity.getClass()).getCascadeTypes();
	}
	return dialectFactory.load(sqlToyContext, entity, cascades, lockMode, this.getDataSource(null));
}
```

* 3、全部修复update级联时，在mysql、postgresql子表采用原生sql进行saveOrUpdateAll的bug，分解为:先update后saveIgnoreExist模式
* 4、提供代码中动态查询增加filters,便于今后文本块应用sql直接写于代码中情况下可以动态调用缓存翻译、filters等功能

```java
@Test
public void findEntityByVO() {
	List<StaffInfoVO> staffVOs = sqlToyLazyDao.findEntity(StaffInfoVO.class,
			EntityQuery.create().where("#[staffId=:staffId]#[and staffName like :staffName] #[ and status=:status]")
					.values(new StaffInfoVO().setStatus(-1).setStaffName("陈").setStaffId("S0005"))
					.filters(new ParamsFilter("status").eq(-1)).filters(new ParamsFilter("staffName").rlike())
					.filters(new ParamsFilter("staffId").primary()));
	System.err.println(JSON.toJSONString(staffVOs));
}
```

# v4.8.2 2019-10-12
* 1、修复#[@if(:param==null) and t.name=:name] 其中:param为null则剔除整个#[] 之间sql的不合理规则
* 2、开放TranslateCacheManager的扩展注入，便于可以替换目前的默认实现
* 3、增加了缓存更新检测的集群节点时间差异参数[sqltoy-translate.xml配置]，保障集群环境下缓存更新检测的时效：
   <cache-update-checkers cluster-time-deviation="1">

# v4.8.1 2019-09-24
* 1、优化将查询部分PreparedStatement 原: ResultSet.TYPE_SCROLL_INSENSITIVE 改为ResultSet.TYPE_FORWARD_ONLY，提升效率。
* 2、修复根据方言提取sql部分缺陷。

# v4.8.0 2019-09-17
* 1、调整了plugin包为plugins，下面的类目录做了适当调整，便于今后扩展
* 2、大幅优化改进跨数据库函数替换处理模式，将之前执行时函数替换改进为缓存模式，相同数据库类型的存在则直接提取，不存在则进行函数替换并放入缓存中，大幅提升效率
* 3、优化了IUnifyFieldsHandler接口，增加public IgnoreCaseSet forceUpdateFields()方法，便于提供统一公共字段强制修改策略，如最后修改时间

```java     
    /* (non-Javadoc)
	 * @see org.sagacity.sqltoy.plugins.IUnifyFieldsHandler#forceUpdateFields()
	 */
	@Override
	public IgnoreCaseSet forceUpdateFields() {
		//强制updateTime 和 systemTime 进行修改
		IgnoreCaseSet forceUpdates=new IgnoreCaseSet();
		forceUpdates.add("updateTime");
		forceUpdates.add("systemTime");
		return forceUpdates;
	}
```
* 4、修复函数替换sysdate 无括号匹配处理的缺陷
* 5、其他一些优化

# v4.6.4 2019-09-10
* 1、改进缓存更新检测机制由timer定时改为线程内循环检测。
* 2、改进shardingDataSource检测机制由Timer定时改为线程内循环检测
* 3、修复DTO 中当同时有 isName 和 Name （isXXXX 和 XXXX 字段）时映射错误,强化isXXXX形式是boolean类型判断。
* 4、增强函数to_char和date_format 跨数据库时自动替换，实现mysql和oracle等情况下sql的通用性
* 5、升级fastjson依赖版本
* 6、其他一些优化

# v4.6.2 2019-08-26
* 1、优化SQL文件发生变更重新加载的机制，变成独立的进程进行文件变更监测，代替原来根据sqlId获取sql内容时检测机制。
* 2、增强了缓存翻译缓存初始化文件策略
* 3、增加多数据源连接信息debug输出，便于开发阶段识别
* 4、增加了容器销毁时自动销毁定时检测任务和缓存管理器

```xml
<bean id="sqlToyContext" class="org.sagacity.sqltoy.SqlToyContext" init-method="initialize" destroy-method="destroy">
</bean>
```
	
# v4.6.0 2019-08-12
* 1、全面支持jdk8 的LocalDate、LocalDateTime、LocalTime 日期类型；
* 2、优化EntityManager 解析对象的代码，避免子类中定义父类中属性导致被覆盖问题；
* 3、quickvo也同步进行了修改，默认类型为jdk8的日期类型
* 4、提交sqltoy-showcase 范例代码，请参见src\test 下面的用例 和 src\main\resources 下面的配置生成vo请参见sqltoy-showcase\tools\quickvo 下面的配置

# v4.5.3 2019-07-19
* 1、优化一些注释
* 2、增强泛型支持，避免：(VO)load(xxxVO) 这种前面需要强转换的操作
* 3、增强with as 语法几种模式的解析，如：with t1 (p1,p2) as () select * from t1 模式
* 4、sql文件变更解析改成同步机制，避免应用启动阶段因个别任务执行sql导致重复加载
* 5、sql模型增加是否有union语法的判断变量并优化部分机制，避免执行过程中去解析判断是否有union 和with as语法，从而提升效率。

# v4.5.2 2019-07-16
* 1、将统一接口层面的异常抛出去除，统一内部抛出RuntimeException，避免开发者强制要进行异常捕获处理（关键更新，因此版本号从4.3升级到4.5，但不影响之前的使用）
* 2、cache-arg 缓存条件转换二级过滤逻辑bug修复。
* 3、sql查询filter中to-date 过滤处理器里面增加了first_week_day，last_week_day 类型，取指定日期所在周的第一和最后一天
* 4、部分注释增强和少量代码优化
* 5、针对jdk11 进行了适应性代码改造，将已经废弃的写法重新依据新标准改写，因此sqltoy完全可以在jdk8和以后的版本中应用
* 6、增强对update xx set #[, field=？ ] 场景语句set 和 后续语句连接存在多余的逗号问题的兼容处理

# v4.3.12 2019-06-27
* 1、修复select * from table where instr(a.name,'xxx?x') and a.type=:type 形式查询问号导致的问题
* 2、放开参数条件名称不能是单个字母的限制，如 where a.name like :n ，单个字母参数命名一般不严谨，不建议如此简化，基本无法表达意思，考虑有些开发思维不够严谨，特此兼容
* 3、优化sql执行时长超过阀值的打印日志格式，便于从日志中快速找到性能慢的sql
* 4、优化部分代码，明确Exception的类型，避免部分条件检查直接抛出Exception，而是明确具体的条件非法或其他的准确的异常，避免事务控制时无法判断RunException 还是checkException

# v4.3.10 2019-05-08
* 1、支持elasticsearch6.x版本的restclient
* 2、强化sql查询固定参数中的问号处理
* 3、注释优化

# v4.3.7.2 2019-04-12
* 1、修复sql文件加载剔除非相同方言的sql文件的bug

# v4.3.7 2019-04-02
* 1、增加数字格式化精度设置(四舍五入、去位、进位等)
* 2、增加基于springsecurity安全框架下通用字段的赋值实现(可选,根据项目情况选择使用，不影响项目)

# v4.3.6.1 2019-02-25
* 1、修复查询语句中to-date('2019-01-01 12:20:30') 给定固定值情况下将:dd 作为:paramNamed 变量参数条件问题
* 2、强化分页查询对分组和统计型语句的判断，解决因为子查询中存在统计函数，将sql误判为统计型查询，count语句没有最优化
* 3、优化缓存条件过滤，未匹配情况下的赋值问题

# v4.3.6 2019-02-22
* 1、修复sql中别名参数正则表达式，避免to-date('2019-02-01 12:21:30','yyyy-MM-dd HH:mm:ss') 直接将具体数值写入sql时未能区分出:paramNamed 格式
* 2、优化缓存条件筛选过滤机制，当未匹配上时返回指定值

# v4.3.5 2019-01-28
* 1、优化redis全局业务主键生成格式，变成sqltoy_global_id:tableName:xxxxx 树形格式，之前是sqltoy_global_id_tablename_xxx 导致redis里面结构不清晰

# v4.3.4 2019-01-22
* 1、优化缓存检测更新时间，按照:yyyy-MM-dd HH:mm:ss 格式，避免检测存在毫秒级判断误差

# v4.3.3 2019-01-15
* 1、增加从缓存中模糊查询获取sql条件中的key值，直接作为条件参与查询，避免sql中关联模糊查询，从而简化sql并提升查询性能

# v4.2.22 2018-12-16
* 1、filter equals 判断当 时，当参数是日期类型对比异常，增加容错性。
* 2、将中文金额的圆统一成元，2004年后国家统一标准
* 3、修复缓存翻译定时检测参数处理问题

# v4.2.20 2018-11-27
* 1、mysql8 锁记录语法优化为 for update [of table] skip locked
* 2、对代码进行注释加强

# v4.2.17 2018-11-10
* 1、sql剔除末尾的分号和逗号，增强容错性，分号开发者会经常从客户端copy过来容易忘记剔除
* 2、在mysql中带有sum等统计函数的查询，结果集存在全是nulll的情况(一般结果就一条)，sqltoy通过sql中增加注释--#ignore_all_null_set# 方式告诉框架进行剔除，避免无效记录

# v4.2.16 2018-10-18
* 1、修复当设置缓存翻译配置文件为空白时，文件加载错误的bug
* 2、优化部分代码性能和规范一些正则表达式的统一定义

# v4.2.15 2018-9-30
* 1、修复不使用缓存翻译时，未对文件是否存在进行判断。
* 2、修复sql语句中进行注释时，先剔除-- xx -- 行注释，导致注释模式被剔除中间部分导致失效

# v4.2.14 2018-9-17
* 1、支持多个sql配置路径

```xml
<property name="sqlResourcesDir" value="classpath:com/sagframe;classpath:sqltoy" />
```

# v4.2.13 2018-9-6
* 1、修复业务主键跟数据库主键属于同一个字段时的长度控制问题
* 2、修复业务主键跟数据库主键同一个字段批量保存时的生成参数未指定正确问题

# v4.2.10 2018-8-24
* 1、修复分页查询组合count sql时：select a,from_days() days from table 取from位置bug

# v4.2.8 2018-6-5
* 1、增加日期格式化和数字格式化

```xml
<sql id="companyTrans">
    <!-- 敏感数据安全脱敏 -->
	<secure-mask columns="account_code" type="public-account"/>
	<secure-mask columns="link_tel" type="tel"/>
	<!-- 日期格式化 -->
	<date-format columns="trans_date" format="yyyy-MM-dd"/>
	<!-- 数字格式化,分:#,###.00 、capital、capital-rmb 等形式 -->
	<number-format columns="total_amt" format="capital-rmb"/>
</sql>
```

# v4.2.7 2018-5-28
* 1、业务主键策略可以根据多个字段组合形成。quickvo业务主键配置：  1),signature 增加${}引用related-columns 设置相关的列的值，@case() 进行类似oracle的decode函数处理，@df(${xxx},fmt) 对日期进行格式化，第一个参数缺省表示当天，第二个参数缺省为:yyMMdd。
    2)，related-columns可以维护多个数据库字段，用逗号分隔。

```xml
   <business-primary-key >
       <table name="OD_CONTRACT_INFO" column="CONTRACT_ID" 
          signature="${periodType}@case(${orderType},P,PO,S,SO,BN)${tradeType}@df(yyMMdd)" 
         related-columns="periodType,orderType,tradeType" length="12" generator="redis" />
   </business-primary-key>
```
* 2、缓存翻译可以一组代码进行同时翻译。如:某个字段结构是A,B,C这种格式，翻译结果为:A名称,B名称,C名称：

```xml
     <translate cache="dictKeyNameCache" columns="SEX_TYPE" split-regex="," link-sign=","/>
```

# v4.2.6 2018-5-19 
* 1、修复mysql、postgresql 执行saveOrUpdate时报：发生SQL 错误 [1048] [23000]: Column 'NAME' cannot be null

# v4.2.5 2018-5-12
* 1、修复mysql8.0 树形表设置节点路径时报sql错误的问题。

# v4.2.4 2018-5-3
* 1、修复@if（:param=='value' && :param1=='-1'）带单双引号后面紧跟+_符号的逻辑处理。
* 2、优化原生elasticsearch json语法解析错误提醒。
* 3、修复 elastic suggest 场景查询无法处理的问题
* 4、修复分页查询count语句优化处理时，在select from 之间有order by语句时处理异常问题。

# v4.2.3 2018-4-12
* 1、优化pom依赖,避免每次依赖oracle和其它一下特定需求情况下的依赖。
* 2、优化查询传参数验证提醒。
* 3、优化分页查询取count记录数时sql判断order by 并剔除的判断逻辑，确保剔除的精准。

# v4.2.2 2018-3-31
* 1、缓存翻译全部改为ehcache3.5.2版本，无需再定义cacheManager和ehcache.xml等，大幅减少配置。
* 2、缓存翻译采用了新的xml schema，支持sql、rest、service等策略。
* 3、缓存翻译增加了主动侦测数据是否发生变化，然后清空缓存的功能，且配置灵活，支持不同时间不同频率。
## bug修复：
* 1、sql语句@if(a== 'xxx' )逻辑判断，等号后面对比数据有空格时判断错误问题。
* 2、修复elasticsearch Sql查询时select count(*) count from xxxxx 没有group 时没有判断为聚合查询的bug。

# v4.1.0 2018-2-20
* 1、正式支持elasticsearch（两种模式:1、通过elasticsearch-sql模式和json原生模式）,已经经过项目应用。
* 2、正式支持redis集中式主键策略，已经正式项目应用通过。
* 3、正式支持redis缓存翻译，已经可以同时支持ehcache和redis
* 4、修复sql参数过滤的一个bug，将默认blank处理作为第一处理顺序。
* 5、对schema xsd文件进行了调整优化
* 6、quickvo 支持swagger api

# v4.0.9 2018-2-3
* 1、支持elasticsearch以及elasticsearch-sql插件

# v3.2.2 (2017-2-28)
* 1、优化取总记录数查询sql分析,排除统计性查询用分页形式查询产生的记录数量错误
* 2、更新依赖包

# v3.2.1 2016-12-1
* 1、修复pivot行转列的参照category列排序问题

# v3.2版本(2016年11月25日发布)
* 1、增加unpivot 列转行功能
* 2、修改了存储过程调用模式，剔除掉存储过程分页查询，修复oracle存储过程返回结果的执行错误
* 3、删除StoreUtils类
* 4、sql语句中增加#[@blank(:paramNamed) sql] 控制特性，便于组织sql
* 5、增加分页优化功能，避免每次都查询2次，在查询条件一致的情况下不再查询分页总记录数

```xml
<page-optimize alive-max="100" alive-seconds="900"/>
```