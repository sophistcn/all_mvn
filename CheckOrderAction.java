package com.nstc.ibs.action;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import com.nstc.framework.action.EventAction;
import com.nstc.framework.core.Caller;
import com.nstc.framework.core.Profile;
import com.nstc.framework.core.ServiceContext;
import com.nstc.framework.dto.BizEvent;
import com.nstc.framework.dto.BizEventResponse;
import com.nstc.framework.exception.AppException;
import com.nstc.framework.util.ThreadResources;
import com.nstc.ibs.constants.Constants;
import com.nstc.ibs.constants.OrderConstants;
import com.nstc.ibs.domain.Sign;
import com.nstc.ibs.factory.SignFactory;
import com.nstc.ibs.service.OrderService;
import com.nstc.util.CastUtil;

/**
 * 
 * <p>
 * Title:修改指令状态为复核通过
 * </p>
 *
 * <p>
 * Description:
 * </p>
 *
 * <p>
 * Company: 北京九恒星科技股份有限公司
 * </p>
 *
 * @author 凌敏 2009-7-20
 *
 * @since：2009-7-20 下午02:06:09
 *
 */
@Controller(Constants.IBS_CODE + ".CheckOrderAction")
public class CheckOrderAction implements EventAction {
    
	@Autowired
    private OrderService orderService;      
    
    public BizEventResponse execute(Caller caller, BizEvent event)
        throws AppException {
        Integer[] orderIds = event.getIntegerArray(OrderConstants.ORDER_ID);
        List<Sign> signList = SignFactory.build(event);
        String[] lastUpdateTimeStrings = event.getStringArray(OrderConstants.LAST_UPDATE_TIME_STRING);
        Hashtable<Integer,String> resultHashtable = new Hashtable<Integer, String>();

    	long a=System.currentTimeMillis();
    	System.out.println("[IBS-Service]开始批量执行 CheckOrderAction");
    	
    	//最大并发数量，最好设置成比连接池的数量少一点。
    	/*Integer MAX_THREADS = 100;
		ExecutorService cachedThreadPool = new ThreadPoolExecutor(0, MAX_THREADS,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>());
        */
    	ExecutorService cachedThreadPool = Executors.newCachedThreadPool();
		CountDownLatch cdl = new CountDownLatch(orderIds.length);
        for (int i = 0; i < orderIds.length; i++) {
            HashMap<String, Object> param = new HashMap<String,Object>();
            param.put(Constants.PROFILE, caller.getProfile());
            
            param.put(OrderConstants.ORDER_ID,	orderIds[i]);
            param.put(OrderConstants.SIGN, signList.get(i));
            param.put(OrderConstants.LAST_UPDATE_TIME_STRING, lastUpdateTimeStrings[i]);
			cachedThreadPool.execute(new CheckOrderTask(orderService,param,resultHashtable,cdl));
        }  
		
        try {
        	//等待所有子线程结束
        	cdl.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println("[IBS-Service] CheckOrderAction 执行结果" + resultHashtable);
        System.out.println( "\r<br>[IBS-Service] CheckOrderAction 执行耗时 : "+(System.currentTimeMillis()-a)/1000f+" 秒 ");
		return new BizEventResponse();
    }
}

class CheckOrderTask implements  Runnable {
	// 保存任务所需要的数据
	private HashMap<String, Object> threadPoolTaskParam;
	private OrderService orderService;
	private Hashtable<Integer,String> resultTable;
	private final CountDownLatch cdl;
	
	CheckOrderTask(OrderService orderService, HashMap<String, Object> param, Hashtable<Integer, String> resultHashtable, CountDownLatch cdl){
		this.threadPoolTaskParam = param;
		this.resultTable = resultHashtable;
		this.orderService = orderService;
		this.cdl = cdl;
	}
	
	public void run() {
		System.out.println("[IBS-Service] CheckOrderAction 线程名称: " + Thread.currentThread().getName());
		
		Integer orderId =  CastUtil.toInteger(threadPoolTaskParam.get(OrderConstants.ORDER_ID));
		Sign sign = (Sign) threadPoolTaskParam.get(OrderConstants.SIGN);
		String lastUpdateTimeString = CastUtil.toString(threadPoolTaskParam.get(OrderConstants.LAST_UPDATE_TIME_STRING),"");
		/**
		 * 在fwkw里面的 getProfile 会用到，否则报错,如下：
		 * 当前代码没有遵循NSTC框架标准，使用该API的代码可能正处于一个自定义的HTTP远程调用的生命周期中，
		 * 非框架内的HTTP远程调用生命周期，由于其技术特性，框架无法监控。
		 */
		Profile profile =(Profile) threadPoolTaskParam.get(Constants.PROFILE);
		Caller caller = new Caller();
		caller.setProfile(profile);
		//绑定线程资源,因为有的地方直接拿profile，有的拿caller
		ThreadResources.bindResource(Profile.class.getName(), profile);
		ThreadResources.bindResource(Caller.class.getName(), caller);
		
		System.out.println("[IBS-Service] CheckOrderAction 开始处理交易，交易编号：" + orderId);
		try {
			orderService.doCheckOrder(orderId, sign, lastUpdateTimeString);
			resultTable.put(orderId, "result@ " + orderId + "success");
		} catch (Exception e) {
			e.printStackTrace();
			resultTable.put(orderId, "result@ " + e.getMessage());
		}
		cdl.countDown();
		System.out.println("[IBS-Service] CheckOrderAction 处理交易结束，交易编号：" + orderId);
	}
}