/**
 * 
 */
package org.sagacity.sqltoy.config.model;

import java.io.Serializable;

import org.sagacity.sqltoy.SqlToyConstants;

/**
 * @project sagacity-sqltoy
 * @description 分页优化配置
 * @author zhongxuchen
 * @version v1.0, Date:2020-8-4
 * @modify 2020-8-4,修改说明
 */
public class PageOptimize implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4202934471963179375L;

	/**
	 * 开启并行查询
	 */
	private boolean parallel = false;

	/**
	 * 1000个不同条件查询
	 */
	private int aliveMax = 1000;

	/**
	 * 1.5分钟
	 */
	private int aliveSeconds = 90;

	/**
	 * 默认值为1800秒
	 */
	private long parallelMaxWaitSeconds = SqlToyConstants.PARALLEL_MAXWAIT_SECONDS;

	/**
	 * @return the aliveMax
	 */
	public int getAliveMax() {
		return aliveMax;
	}

	/**
	 * @param aliveMax the aliveMax to set
	 */
	public PageOptimize aliveMax(int aliveMax) {
		// 最大不超过10000
		if (aliveMax > 10000) {
			this.aliveMax = 10000;
		} else {
			this.aliveMax = aliveMax;
		}
		return this;
	}

	/**
	 * @return the aliveSeconds
	 */
	public int getAliveSeconds() {
		return aliveSeconds;
	}

	public boolean isParallel() {
		return parallel;
	}

	public PageOptimize parallel(boolean parallel) {
		this.parallel = parallel;
		return this;
	}

	/**
	 * @param aliveSeconds the aliveSeconds to set
	 */
	public PageOptimize aliveSeconds(int aliveSeconds) {
		// 最小保持30秒
		if (aliveSeconds < 30) {
			this.aliveSeconds = 30;
		}
		// 不超过24小时
		else if (aliveSeconds > 3600 * 24) {
			this.aliveSeconds = 1800;
		} else {
			this.aliveSeconds = aliveSeconds;
		}
		return this;
	}

	public long getParallelMaxWaitSeconds() {
		return parallelMaxWaitSeconds;
	}

	public PageOptimize parallelMaxWaitSeconds(long parallelMaxWaitSeconds) {
		if (parallelMaxWaitSeconds > 1) {
			this.parallelMaxWaitSeconds = parallelMaxWaitSeconds;
		}
		return this;
	}

}
