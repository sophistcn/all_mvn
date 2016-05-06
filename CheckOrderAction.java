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
 * Title:�޸�ָ��״̬Ϊ����ͨ��
 * </p>
 *
 * <p>
 * Description:
 * </p>
 *
 * <p>
 * Company: �����ź��ǿƼ��ɷ����޹�˾
 * </p>
 *
 * @author ���� 2009-7-20
 *
 * @since��2009-7-20 ����02:06:09
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
    	System.out.println("[IBS-Service]��ʼ����ִ�� CheckOrderAction");
    	
    	//��󲢷�������������óɱ����ӳص�������һ�㡣
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
        	//�ȴ��������߳̽���
        	cdl.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println("[IBS-Service] CheckOrderAction ִ�н��" + resultHashtable);
        System.out.println( "\r<br>[IBS-Service] CheckOrderAction ִ�к�ʱ : "+(System.currentTimeMillis()-a)/1000f+" �� ");
		return new BizEventResponse();
    }
}

class CheckOrderTask implements  Runnable {
	// ������������Ҫ������
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
		System.out.println("[IBS-Service] CheckOrderAction �߳�����: " + Thread.currentThread().getName());
		
		Integer orderId =  CastUtil.toInteger(threadPoolTaskParam.get(OrderConstants.ORDER_ID));
		Sign sign = (Sign) threadPoolTaskParam.get(OrderConstants.SIGN);
		String lastUpdateTimeString = CastUtil.toString(threadPoolTaskParam.get(OrderConstants.LAST_UPDATE_TIME_STRING),"");
		/**
		 * ��fwkw����� getProfile ���õ������򱨴�,���£�
		 * ��ǰ����û����ѭNSTC��ܱ�׼��ʹ�ø�API�Ĵ������������һ���Զ����HTTPԶ�̵��õ����������У�
		 * �ǿ���ڵ�HTTPԶ�̵����������ڣ������似�����ԣ�����޷���ء�
		 */
		Profile profile =(Profile) threadPoolTaskParam.get(Constants.PROFILE);
		Caller caller = new Caller();
		caller.setProfile(profile);
		//���߳���Դ,��Ϊ�еĵط�ֱ����profile���е���caller
		ThreadResources.bindResource(Profile.class.getName(), profile);
		ThreadResources.bindResource(Caller.class.getName(), caller);
		
		System.out.println("[IBS-Service] CheckOrderAction ��ʼ�����ף����ױ�ţ�" + orderId);
		try {
			orderService.doCheckOrder(orderId, sign, lastUpdateTimeString);
			resultTable.put(orderId, "result@ " + orderId + "success");
		} catch (Exception e) {
			e.printStackTrace();
			resultTable.put(orderId, "result@ " + e.getMessage());
		}
		cdl.countDown();
		System.out.println("[IBS-Service] CheckOrderAction �����׽��������ױ�ţ�" + orderId);
	}
}