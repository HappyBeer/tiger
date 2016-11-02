/**
 * 
 */
package com.dianping.tiger.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import com.dianping.tiger.engine.dispatch.DispatchHandler;
import com.dianping.tiger.engine.event.EventConfig;
import com.dianping.tiger.engine.event.EventExecutorManager;
import com.dianping.tiger.engine.groovy.GroovyBeanFactory;
import com.dianping.tiger.engine.utils.EventConfigUtil;
import com.dianping.tiger.engine.utils.ScheduleConstants;
import com.dianping.tiger.engine.zk.ScheduleZkManager;

/**
 * @author yuantengkai 执行引擎构造器
 */
public class ScheduleManagerFactory {

	private static final Logger logger = LoggerFactory
			.getLogger(ScheduleManagerFactory.class);

	private static ApplicationContext appCtx;

	private ScheduleZkManager scheduleZkManager = new ScheduleZkManager();

	private int sleepInteverval = 30 * 1000;

	private Thread eventStarterThread;

	private AtomicBoolean initFlag = new AtomicBoolean(false);

	/**
	 * @param sleepTimePerFetch
	 *            每次轮训捞取任务的间隔时间(ms)
	 */
	public ScheduleManagerFactory(int sleepTimeMsPerFetch) {
		if (sleepTimeMsPerFetch > 10) {
			this.sleepInteverval = sleepTimeMsPerFetch;
		}
	}

	public enum ZookeeperKeys {
		zkConnectAddress, rootPath, userName, password, zkSessionTimeout;
	}

	public enum ScheduleKeys {
		scheduleFlag, taskStrategy, enableNavigate, enableBackFetch, enableGroovyCode, handlerGroup,handlers, coreSize, maxSize, virtualNodeNum, divideType;
	}

	public enum MonitorKeys {
		enableMonitor, monitorIP;
	}

	/**
	 * 入口:zk调度初始化
	 * 
	 * @param config
	 *            :必填：zkConnectString,handlerGroup,handlers
	 * @throws IllegalArgumentException
	 * @throws Exception
	 */
	@SuppressWarnings("deprecation")
	public void initSchedule(Properties config)
			throws IllegalArgumentException, Exception {
		if (!initFlag.compareAndSet(false, true)) {
			return;
		}
		String zkAddress = config.getProperty(ZookeeperKeys.zkConnectAddress
				.name());
		String handlers = config.getProperty(ScheduleKeys.handlers.name());
		if (StringUtils.isBlank(zkAddress) || StringUtils.isBlank(handlers)) {
			throw new IllegalArgumentException(
					"zkAddress or handlers is empty.");
		}
		ScheduleServer.getInstance().setZkAddress(zkAddress);
		String[] handlerArray = handlers.split(",");
		List<Integer> nodeList = ScheduleServer.getInstance().getNodeList();
		List<String> handlerList = new ArrayList<String>();
		for (int i = 0; i < handlerArray.length; i++) {
			ScheduleServer.getInstance().addHandler(handlerArray[i], nodeList);
			handlerList.add(handlerArray[i]);
		}
		ScheduleServer.getInstance().setHandlerIdentifyCode(
				handlerList.hashCode());
		String rootPath = config.getProperty(ZookeeperKeys.rootPath.name(),
				"/TIGERZK");
		String handlerGroup = config.getProperty(ScheduleKeys.handlerGroup.name(),"TigerDefaultGroup");
		String visualNode = config.getProperty(
				ScheduleKeys.virtualNodeNum.name(), "100");
		String divideType = config.getProperty(ScheduleKeys.divideType.name(),
				ScheduleConstants.NodeDivideMode.DIVIDE_RANGE_MODE.getValue() + "");
		String zkSessionTimeout = config.getProperty(
				ZookeeperKeys.zkSessionTimeout.name(), "60000");
		String scheduleFlag = config.getProperty(
				ScheduleKeys.scheduleFlag.name(), "true");
		String enableNavigate = config.getProperty(
				ScheduleKeys.enableNavigate.name(), "true");
		String enableBackFetch = config.getProperty(
				ScheduleKeys.enableBackFetch.name(), "false");
		String enableGroovyCode = config.getProperty(
				ScheduleKeys.enableGroovyCode.name(), "false");
		String enableMonitor = config.getProperty(
				MonitorKeys.enableMonitor.name(), "false");
		String monitorurl = config.getProperty(MonitorKeys.monitorIP.name());
		String coreSize = config.getProperty(ScheduleKeys.coreSize.name());
		String maxSize = config.getProperty(ScheduleKeys.maxSize.name());
		String taskStrategy = config.getProperty(ScheduleKeys.taskStrategy.name());

		if(!StringUtils.isBlank(handlerGroup)){
			ScheduleServer.getInstance().setHandlerGroup(handlerGroup);
		}else if (!StringUtils.isBlank(rootPath)) {
			ScheduleServer.getInstance().setRootPath(rootPath);
		}
		if (StringUtils.isNumeric(visualNode)) {
			ScheduleServer.getInstance().setNumOfVisualNode(
					Integer.valueOf(visualNode));
		}
		if (StringUtils.isNumeric(divideType)) {
			ScheduleServer.getInstance().setDivideType(
					Integer.valueOf(divideType));
		}
		if (StringUtils.isNumeric(zkSessionTimeout)) {
			ScheduleServer.getInstance().setZkSessionTimeout(
					Integer.valueOf(zkSessionTimeout));
		}
		if (!StringUtils.isBlank(scheduleFlag)) {
			this.setScheduleFlag("true".equals(scheduleFlag));
		}
		if (!StringUtils.isBlank(enableNavigate)) {
			this.setNavigateFlag("true".equals(enableNavigate));
		}
		if (!StringUtils.isBlank(enableBackFetch)) {
			this.setBackFetchFlag("true".equals(enableBackFetch));
		}
		if (!StringUtils.isBlank(enableGroovyCode)) {
			this.setGroovyCodeFlag("true".equals(enableGroovyCode));
		}
		if (!StringUtils.isBlank(coreSize) && StringUtils.isNumeric(coreSize)) {
			ScheduleServer.getInstance().setHandlerCoreSize(
					Integer.valueOf(coreSize));
		}
		if (!StringUtils.isBlank(maxSize) && StringUtils.isNumeric(maxSize)) {
			ScheduleServer.getInstance().setHandlerMaxSize(
					Integer.valueOf(maxSize));
		}
		if(!StringUtils.isBlank(taskStrategy) && StringUtils.isNumeric(taskStrategy)){
			ScheduleServer.getInstance().setTaskStrategy(Integer.valueOf(taskStrategy));
		}
		// ==========监控相关============
		if (!StringUtils.isBlank(enableMonitor)) {
			this.setMonitorFlag("true".equals(enableMonitor));
		}
		if (!StringUtils.isBlank(monitorurl)) {
			ScheduleServer.getInstance().setMonitorIP(monitorurl);
		}

		ScheduleServer.getInstance().initOk();

		startZk();

		startSchedule();

	}

	/**
	 * 重新设置调度执行器
	 * 
	 * @param handlers
	 */
	public void reSchedule(List<String> handlers) {
		if (handlers == null || !initFlag.get()) {
			return;
		}
		List<Integer> nodeList = ScheduleServer.getInstance().getNodeList();
		ScheduleServer.getInstance().clearAllHandler();
		for (String handler : handlers) {
			ScheduleServer.getInstance().addHandler(handler,
					new ArrayList<Integer>(nodeList));
		}
		ScheduleServer.getInstance()
				.setHandlerIdentifyCode(handlers.hashCode());
	}
	
	/**
	 * 增加一个handler的个性化设置
	 * @param handler
	 * @param configMap:
	 * 	 subkey:switchername, for example {@link ScheduleManagerFactory.ScheduleKeys.enableNavigate.name()}
	 *   subvalue:boolean
	 * 
	 */
	public void setHandlerConfig(String handler, HashMap<String,Boolean> configMap){
		if(StringUtils.isBlank(handler) || configMap == null || configMap.isEmpty()){
			return;
		}
		ScheduleServer.getInstance().addHandlerConfig(handler, configMap);
	}
	
	/**
	 * 批量设置handlers的个性化设置
	 * @param handlerConfigMap
	 * key: handler
	 * value:
	 *   subkey:switchername, for example {@link ScheduleManagerFactory.ScheduleKeys.enableNavigate.name()}
	 *   subvalue:boolean
	 */
	public void setMultiHandlersConfig(Map<String,HashMap<String,Boolean>> handlerConfigMap){
		if(handlerConfigMap == null || handlerConfigMap.isEmpty()){
			return;
		}
		for(Entry<String, HashMap<String,Boolean>> e:handlerConfigMap.entrySet()){
			this.setHandlerConfig(e.getKey(), e.getValue());
		}
	}

	/**
	 * 设置调度总开关
	 * 
	 * @param flag
	 */
	public void setScheduleFlag(boolean flag) {
		ScheduleServer.getInstance().setScheduleSwitcher(flag);
	}

	/**
	 * 设置是否启用巡航模式
	 * 
	 * @param flag
	 */
	public void setNavigateFlag(boolean flag) {
		ScheduleServer.getInstance().setEnableNavigate(flag);
	}

	/**
	 * 设置是否反压
	 * 
	 * @param flag
	 */
	public void setBackFetchFlag(boolean flag) {
		ScheduleServer.getInstance().setEnableBackFetch(flag);
	}
	
	/**
	 * 设置是否使用groovy动态加载handler
	 * 
	 * @param flag
	 */
	public void setGroovyCodeFlag(boolean flag) {
		ScheduleServer.getInstance().setEnableGroovyCode(flag);
	}

	/**
	 * 设置监控开关
	 * 
	 * @param flag
	 */
	public void setMonitorFlag(boolean flag) {
		ScheduleServer.getInstance().setEnableMonitor(flag);
	}

	private void startSchedule() {
		eventStarterThread = new Thread(new Runnable() {

			@Override
			public void run() {
				while (true) {
					try {
						if (ScheduleServer.getInstance().canScheduler()) {//需要等zk节点分配完成
							if (!EventExecutorManager.getInstance().hasInited()) {
								List<EventConfig> eventConfigs = EventConfigUtil
										.syncEventConfigs();
								EventExecutorManager.getInstance().init(
										eventConfigs);
								logger.warn("######"
										+ ScheduleServer.getInstance()
												.getServerName()
										+ " start scheduling,task config:"
										+ eventConfigs);
							}
							EventExecutorScheduler.getInstance().execute();
						}
						if (EventExecutorManager.getInstance().hasInited()) {
							Thread.sleep(sleepInteverval);
						} else {//先sleep 1s 等待zk节点分配完成
							Thread.sleep(1000);
							continue;
						}
						if (EventExecutorManager.getInstance()
								.getCurrentExecutorVersion() != ScheduleServer
								.getInstance().getHandlerIdentifyCode()) {
							Thread.sleep(3 * 1000);// 等待现有任务执行完
							List<EventConfig> eventConfigs = EventConfigUtil
									.syncEventConfigs();
							EventExecutorManager.getInstance().init(
									eventConfigs);
							logger.warn("######"
									+ ScheduleServer.getInstance()
											.getServerName()
									+ " task config changed:" + eventConfigs);
						}
					} catch (InterruptedException e) {
						logger.error("eventExecutorScheduler sleep exception.",
								e);
					} catch (Throwable t) {
						logger.error(
								"eventExecutorScheduler happens unknow exception.",
								t);
					}
				}
			}
		});
		eventStarterThread.setDaemon(true);
		eventStarterThread.setName("Event-Executor-Frame");
		eventStarterThread.start();
	}

	private void startZk() throws Exception {
		scheduleZkManager.start();
	}

	public void setAppCtx(ApplicationContext applicationContext) {
		appCtx = applicationContext;
	}

	public static Object getBean(String beanName) {
		if (appCtx == null || StringUtils.isBlank(beanName)) {
			return null;
		}
		try {
			return appCtx.getBean(beanName);
		} catch (Exception e) {
			logger.error("get bean exception,beanName="+beanName,e);
			return null;
		}
	}
	
	public static Object getHandlerBean(String handlerName){
		if(ScheduleServer.getInstance().enableGroovyCode() && 
				GroovyBeanFactory.getInstance().isGroovyHandler(handlerName)){
			return GroovyBeanFactory.getInstance().getHandlerByName(handlerName);
		}else{
			return getBean(handlerName);
		}
	}
	
	@SuppressWarnings("unchecked")
	public static Class<DispatchHandler> getHandlerClazz(String handlerName){
		if(ScheduleServer.getInstance().enableGroovyCode() &&
				GroovyBeanFactory.getInstance().isGroovyHandler(handlerName)){
			return GroovyBeanFactory.getInstance().getClazzByHandlerName(handlerName);
		}else{
			DispatchHandler h =  (DispatchHandler) getBean(handlerName);
			if(h == null){
				return null;
			}
			return (Class<DispatchHandler>) h.getClass();
		}
	}

}
