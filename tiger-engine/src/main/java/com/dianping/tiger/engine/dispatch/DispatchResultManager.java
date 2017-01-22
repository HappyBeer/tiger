/**
 * 
 */
package com.dianping.tiger.engine.dispatch;

import java.util.Calendar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dianping.tiger.api.dispatch.DispatchTaskEntity;
import com.dianping.tiger.api.dispatch.DispatchTaskService;
import com.dianping.tiger.api.dispatch.TaskAttribute;
import com.dianping.tiger.engine.ScheduleManagerFactory;
import com.dianping.tiger.engine.ScheduleServer;
import com.dianping.tiger.engine.utils.ScheduleConstants;

/**
 * @author yuantengkai
 *
 */
public class DispatchResultManager {

	private static final Logger logger = LoggerFactory
			.getLogger(DispatchResultManager.class);
	
	private static final DispatchResultManager instance = new DispatchResultManager();

	private static final int MAX_FAIL_TIMES = 60;

	private DispatchTaskService dispatchTaskService;
	
	private DispatchResultManager(){
		
	}
	
	public static DispatchResultManager getInstance() {
		return instance;
	}

	/**
	 * 执行完后续处理，成功－更新状态；失败－增加重试次数；丢弃－状态更新状态
	 * 
	 * @param result
	 * @param task
	 */
	public void processDispatchResult(DispatchResult result,
			DispatchTaskEntity task) {
		if (result == null || task == null) {
			return;
		}
		if (dispatchTaskService == null) {
			dispatchTaskService = (DispatchTaskService) ScheduleManagerFactory
					.getBean("dispatchTaskService");
		}
		TaskAttribute attr = new TaskAttribute(ScheduleServer.getInstance().getServerName(), 
													task.getTtid());
		attr.setNode(task.getNode());
		if (DispatchResult.SUCCESS.equals(result)) {
			boolean flag = dispatchTaskService.updateTaskStatus(task.getId(),
					ScheduleConstants.TaskType.SUCCESS.getValue(),attr);

			if (!flag) {
				logger.error("task execute success, update status failed,"
						+ task);
			}
			return;
		}
		if (DispatchResult.FAIL2RETRY.equals(result)) {
			if (task.getRetryTimes() < MAX_FAIL_TIMES) {
				Calendar c = Calendar.getInstance();
				c.add(Calendar.MINUTE, task.getRetryTimes() + 1);// 每多重试一次，就多延迟1分钟后执行
				boolean flag = dispatchTaskService.addRetryTimesByFail(
						task.getId(), c.getTime(), attr);
				if (!flag) {
					logger.warn("task execute failed, update retryTimes failed,"
							+ task);
				}
				return;
			}
		}
		if (DispatchResult.NEXT.equals(result)) {
			return;
		}
		boolean flag = dispatchTaskService.updateTaskStatus(task.getId(),
				ScheduleConstants.TaskType.FAIL.getValue(), attr);
		if (!flag) {
			logger.error("task execute discard, update status failed," + task);
		}

	}

}
