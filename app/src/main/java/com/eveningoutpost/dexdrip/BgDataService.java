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
import com.eveningoutpost.dexdrip.models.UserError;

// === 修改：删除不必要的import ===
// import android.content.ComponentName;
// import android.os.Bundle;

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
    
    @Override
    public void onCreate() {
        super.onCreate();

        UserError.Log.uel(TAG, "Thread: " + Thread.currentThread().getName());
        UserError.Log.uel(TAG, "Process: " + android.os.Process.myPid());
        UserError.Log.uel(TAG, "Application: " + getApplication());
        
        UserError.Log.uel(TAG, "=== BgDataService.onCreate() 被调用 ===");
        
        // === 关键：设置实例 ===
        instance = this;
        UserError.Log.uel(TAG, "✅ 实例已设置: " + (instance != null));
        
        logger = AIDLLogger.getInstance();        
        logger.logServiceStatus("BgDataService", "创建");
        logger.step("初始化", "开始");
        
        try {
            // 创建前台服务通知
            createNotificationChannel();
            startForeground(NOTIFICATION_ID, createNotification());
            logger.success("BgDataService启动成功");     
            logger.step("初始化", "完成");
            
            // === 新增：发送服务就绪广播 ===
            sendServiceReadyBroadcast();
            
        } catch (Exception e) {
            logger.error("BgDataService启动失败: " + e.getMessage());
            instance = null; // 启动失败时清除实例
        }
    }

    // 发送服务就绪广播
    private void sendServiceReadyBroadcast() {
        Intent intent = new Intent("com.eveningoutpost.dexdrip.AIDL_SERVICE_READY");
        intent.putExtra("timestamp", System.currentTimeMillis());
        sendBroadcast(intent);
        UserError.Log.uel(TAG, "✅ 服务就绪广播已发送");
    }
 
//////////    
    // === 修改：简化onBind方法（关键修改）===
    @Override
    public IBinder onBind(Intent intent) {
        logger.step("服务绑定", "开始");
        
        // 记录调用者信息
        int callingUid = Binder.getCallingUid();
        try {
            String[] packages = getPackageManager().getPackagesForUid(callingUid);
            if (packages != null && packages.length > 0) {
                String callingPackage = packages[0];
                logger.debug("调用者包名: " + callingPackage);
                logger.debug("调用者UID: " + callingUid);
                
                // 区分调用者用于日志，但不影响返回的Binder
                if (callingPackage.equals(getPackageName())) {
                    logger.debug("调用者: xdrip自身");
                } else {
                    logger.debug("调用者: 外部应用 - " + callingPackage);
                }
            }
        } catch (Exception e) {
            logger.warn("获取调用者信息失败: " + e.getMessage());
        }
        
        // === 核心修改：总是返回AIDL Binder ===
        logger.success("返回AIDL Binder");
        logger.debug("Binder类型: " + binder.getClass().getName());
        logger.debug("实现接口: IBgDataService.Stub");
        
        return binder;
    }
////////// 

    @Override
    public boolean onUnbind(Intent intent) {
        logger.debug("所有客户端解除绑定");
        return true;
    }
    
    @Override
    public void onDestroy() {

        // 清理1：清除静态引用，防止内存泄漏
        instance = null;  // ✅ 必须做，让GC可以回收这个服务实例
        
        logger.logServiceStatus("BgDataService", "销毁");
        
        // 清理2：释放客户端回调资源
        callbacks.kill(); // ✅ 必须做，清理RemoteCallbackList内部资源
        
        super.onDestroy();
    }
    
    /**
     * 核心方法：注入新的血糖数据
     */
    public void injectBgData(BgData newData) { // ✅ public方法
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
        logger.step("通知完成", "结果", "成功:" + successCount, "失败:" + failCount);
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
    // === 关键修改：确保实例被正确设置 ===
    private static volatile BgDataService instance;
    
    // 双重检查锁定获取实例
    public static BgDataService getInstance() {
        if (instance == null) {
            UserError.Log.uel(TAG, "⚠️ getInstance() 返回 null，服务可能未启动");
            
            // 尝试通过其他方式获取服务
            try {
                // 检查服务是否在运行
                Context context = xdrip.getAppContext();
                if (context != null && isServiceRunning(context, BgDataService.class)) {
                    UserError.Log.uel(TAG, "服务在运行但实例为null，尝试启动绑定");
                    
                    // 发送广播通知需要启动服务
                    Intent intent = new Intent("com.eveningoutpost.dexdrip.START_SERVICE");
                    context.sendBroadcast(intent);
                }
            } catch (Exception e) {
                UserError.Log.uel(TAG, "获取实例失败: " + e.getMessage());
            }
        }
        return instance;
    }

    // 检查服务是否在运行
    private static boolean isServiceRunning(Context context, Class<?> serviceClass) {
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        } catch (Exception e) {
            UserError.Log.uel(TAG, "检查服务运行状态失败: " + e.getMessage());
        }
        return false;
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

    // === 修改：添加简单的测试方法 ===
    public void sendTestData() {
        BgData testData = new BgData();
        testData.setGlucoseValue(120.0);
        testData.setTimestamp(System.currentTimeMillis());
        //testData.setTrend("→");
        testData.setSequenceNumber(sequenceGenerator.incrementAndGet());
        
        injectBgData(testData);
        logger.debug("发送测试数据完成");
    }
    
}
