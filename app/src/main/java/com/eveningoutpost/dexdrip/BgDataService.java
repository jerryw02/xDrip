package com.eveningoutpost.dexdrip;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;

import androidx.core.app.NotificationCompat;

import com.eveningoutpost.dexdrip.utils.AIDLLogger;

import java.util.concurrent.atomic.AtomicLong;

public class BgDataService extends Service {
    private static final String TAG = "BgDataService";
    private static final String CHANNEL_ID = "xDrip_BgData_Service";
    private static final int NOTIFICATION_ID = 1001;
    
    // 日志工具
    private AIDLLogger logger;
    
    // 当前血糖数据
    private volatile BgData currentBgData = null;
    
    // 回调列表
    private final RemoteCallbackList<IBgDataCallback> callbacks = new RemoteCallbackList<>();
    
    // 序列号生成器（用于去重）
    private final AtomicLong sequenceGenerator = new AtomicLong(0);
    
    // AIDL接口实现
    private final IBgDataService.Stub binder = new IBgDataService.Stub() {
        @Override
        public BgData getLatestBgData() throws RemoteException {
            logger.debug("AAPS请求最新数据");
            return currentBgData;
        }
        
        @Override
        public void registerCallback(IBgDataCallback callback) throws RemoteException {
            if (callback != null) {
                callbacks.register(callback);
                int count = callbacks.getRegisteredCallbackCount();
                logger.success(String.format("AAPS回调注册成功，当前客户端数: %d", count));
                
                // 立即发送当前数据给新注册的客户端
                if (currentBgData != null) {
                    callback.onNewBgData(currentBgData);
                    logger.debug("向新客户端发送当前数据");
                }
            }
        }
        
        @Override
        public void unregisterCallback(IBgDataCallback callback) throws RemoteException {
            if (callback != null) {
                callbacks.unregister(callback);
                int count = callbacks.getRegisteredCallbackCount();
                logger.debug(String.format("AAPS回调注销，剩余客户端数: %d", count));
            }
        }
    };
    
    // LocalBinder用于内部绑定
    public class LocalBinder extends Binder {
        public BgDataService getService() {
            return BgDataService.this;
        }
    }
    
    private final IBinder localBinder = new LocalBinder();
    
    @Override
    public void onCreate() {
        super.onCreate();
        logger = AIDLLogger.getInstance();
        
        logger.logServiceStatus("BgDataService", "创建");
        logger.step("初始化", "开始");
        
        try {
            // 创建前台服务通知
            createNotificationChannel();
            startForeground(NOTIFICATION_ID, createNotification());
            
            logger.success("BgDataService启动成功");
            logger.step("初始化", "完成");
            
        } catch (Exception e) {
            logger.error("BgDataService启动失败: " + e.getMessage());
        }
    }
    
    @Override
public IBinder onBind(Intent intent) {
    logger.step("服务绑定", "开始");
    
    if (intent == null) {
        logger.warn("绑定请求intent为null，返回AIDL binder");
        return binder;
    }
    
    String action = intent.getAction();
    String packageName = intent.getPackage();
    String component = intent.getComponent() != null ? 
                       intent.getComponent().getClassName() : "null";
    
    logger.debug("绑定请求详情:");
    logger.debug("  Action: " + action);
    logger.debug("  Package: " + packageName);
    logger.debug("  Component: " + component);
    logger.debug("  Extras: " + (intent.getExtras() != null ? 
               intent.getExtras().toString() : "none"));
    
    // 检查是否是AAPS客户端
    boolean isAAPS = false;
    boolean isInternal = false;
    
    if (packageName != null) {
        isAAPS = packageName.contains("androidaps") || 
                 packageName.contains("aaps") ||
                 packageName.equals("com.eveningoutpost.dexdrip");
        isInternal = packageName.equals(getPackageName());
        
        logger.debug("包名分析:");
        logger.debug("  是否AAPS: " + isAAPS);
        logger.debug("  是否内部: " + isInternal);
    }
    
    // 关键修复：给外部应用（包括AAPS）返回AIDL binder
    if (isInternal && action != null && "local".equals(action)) {
        logger.success("内部绑定，返回LocalBinder");
        return localBinder;
    } else {
        // 默认返回AIDL binder给所有外部调用
        logger.success("外部绑定，返回AIDL Binder");
        logger.debug("Binder类型: " + binder.getClass().getName());
        return binder;
    }
}
    
    @Override
    public boolean onUnbind(Intent intent) {
        logger.debug("所有客户端解除绑定");
        return true;
    }
    
    @Override
    public void onDestroy() {
        logger.logServiceStatus("BgDataService", "销毁");
        
        // 清理回调列表
        callbacks.kill();
        
        super.onDestroy();
    }
    
    /**
     * 核心方法：注入新的血糖数据
     */
    public void injectBgData(BgData newData) {
        if (newData == null) {
            logger.error("注入数据为空");
            return;
        }
        
        logger.step("数据注入", "开始", 
            "BG:" + newData.getGlucoseValue(),
            "时间:" + newData.getTimestamp());
        
        // 设置序列号（用于去重）
        if (newData.getSequenceNumber() == 0) {
            newData.setSequenceNumber(sequenceGenerator.incrementAndGet());
        }
        
        // 检查是否重复数据
        if (isDuplicateData(newData)) {
            logger.warn("重复数据，跳过注入");
            return;
        }
        
        // 更新当前数据
        currentBgData = newData;
        
        // 通知所有客户端
        notifyClients(newData);
        
        logger.success("数据注入完成");
        logger.debug("当前数据: " + newData.toString());
    }
    
    /**
     * 检查是否重复数据
     */
    private boolean isDuplicateData(BgData newData) {
        if (currentBgData == null) {
            return false;
        }
        
        // 相同时间戳视为重复
        if (currentBgData.getTimestamp() == newData.getTimestamp()) {
            return true;
        }
        
        // 旧数据不能覆盖新数据
        if (newData.getTimestamp() < currentBgData.getTimestamp()) {
            logger.warn("收到旧数据，拒绝更新");
            return true;
        }
        
        return false;
    }
    
    /**
     * 通知所有客户端
     */
    private void notifyClients(BgData data) {
        final int count = callbacks.beginBroadcast();
        logger.step("通知客户端", "开始", "客户端数:" + count);
        
        if (count == 0) {
            logger.warn("没有注册的客户端");
            callbacks.finishBroadcast();
            return;
        }
        
        int successCount = 0;
        int failCount = 0;
        
        for (int i = 0; i < count; i++) {
            try {
                IBgDataCallback callback = callbacks.getBroadcastItem(i);
                callback.onNewBgData(data);
                successCount++;
                logger.debug("通知客户端成功: " + i);
            } catch (RemoteException e) {
                failCount++;
                logger.error("通知客户端失败: " + i + ", " + e.getMessage());
            }
        }
        
        callbacks.finishBroadcast();
        
        logger.step("通知完成", "结果", 
            "成功:" + successCount, "失败:" + failCount);
    }
    
    /**
     * 创建通知渠道
     */
    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "xDrip AIDL服务",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("为AAPS提供血糖数据");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
                logger.debug("创建通知渠道");
            }
        }
    }
    
    /**
     * 创建前台服务通知
     */
    private Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("xDrip AIDL服务运行中")
            .setContentText("为AAPS提供实时血糖数据")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW);
        
        logger.debug("创建前台服务通知");
        return builder.build();
    }
    
    /**
     * 获取服务实例（静态方法）
     */
    private static BgDataService instance;
    
    public static BgDataService getInstance() {
        return instance;
    }
    
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        logger.logServiceStatus("BgDataService", "任务移除，重新启动");
        // 确保服务不被系统清理
        Intent restartService = new Intent(getApplicationContext(), BgDataService.class);
        restartService.setPackage(getPackageName());
        startService(restartService);
        super.onTaskRemoved(rootIntent);
    }
}
