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

// 在 BgDataService.java 文件开头的import部分添加：
import android.content.ComponentName;
import android.os.Bundle;

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
    
    /*
    // LocalBinder用于内部绑定
    public class LocalBinder extends Binder {
        public BgDataService getService() {
            return BgDataService.this;
        }
    }
    private final IBinder localBinder = new LocalBinder();
    */
    
    @Override
    public void onCreate() {
        super.onCreate();

        // 设置实例
        instance = this;
        
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
    
/*
@Override
public IBinder onBind(Intent intent) {
    logger.step("服务绑定", "开始 - 智能判断");
    
    if (intent == null) {
        logger.warn("绑定请求intent为null，返回AIDL binder");
        logger.debug("返回AIDL Binder类名: " + binder.getClass().getName());
        return binder;
    }
    
    String action = intent.getAction();
    String packageName = intent.getPackage();
    ComponentName component = intent.getComponent();
    
    logger.debug("=== 绑定请求详细分析 ===");
    logger.debug("Action: " + action);
    logger.debug("Package: " + packageName);
    logger.debug("Component: " + (component != null ? component.getClassName() : "null"));
    logger.debug("Extras: " + (intent.getExtras() != null ? 
               intent.getExtras().toString() : "none"));
    
    // 获取当前应用的包名
    String currentPackageName = getPackageName();
    logger.debug("当前应用包名: " + currentPackageName);
    
    // 判断是否是内部调用（来自xdrip应用自身）
    boolean isInternalCall = false;
    
    // 方法1：通过包名判断
    if (packageName != null && packageName.equals(currentPackageName)) {
        isInternalCall = true;
        logger.debug("✅ 通过包名判断为内部调用");
    }
    
    // 方法2：通过Component判断（如果没有设置包名）
    if (!isInternalCall && component != null) {
        String componentPackage = component.getPackageName();
        if (componentPackage != null && componentPackage.equals(currentPackageName)) {
            isInternalCall = true;
            logger.debug("✅ 通过Component包名判断为内部调用");
        }
    }
    
    // 方法3：通过Action判断（显式标记）
    if (action != null) {
        if ("local".equals(action) || "internal".equals(action) || "xdrip.internal".equals(action)) {
            isInternalCall = true;
            logger.debug("✅ 通过Action判断为内部调用: " + action);
        } else if ("aidl".equals(action) || "external".equals(action) || "aaps".equals(action)) {
            isInternalCall = false;
            logger.debug("✅ 通过Action判断为外部调用: " + action);
        }
    }
    
    // 方法4：通过调用栈判断（最准确）
    if (!isInternalCall) {
        try {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            logger.debug("调用栈分析（前10个）:");
            for (int i = 3; i < Math.min(stackTrace.length, 13); i++) {
                StackTraceElement element = stackTrace[i];
                String className = element.getClassName();
                String methodName = element.getMethodName();
                
                // 记录调用栈信息
                logger.debug("  [" + i + "] " + className + "." + methodName + "()");
                
                // 检查是否是xdrip内部类在调用
                if (className.contains("com.eveningoutpost.dexdrip") &&
                    (className.contains("xdrip") || 
                     className.contains("Xdrip") ||
                     className.contains("BroadcastService"))) {
                    isInternalCall = true;
                    logger.debug("✅ 通过调用栈判断为内部调用: " + className);
                    break;
                }
            }
        } catch (Exception e) {
            logger.warn("调用栈分析失败: " + e.getMessage());
        }
    }
    
    // 方法5：通过Extra判断
    if (intent.getExtras() != null) {
        Bundle extras = intent.getExtras();
        if (extras.containsKey("internal_call") || 
            extras.containsKey("caller") && extras.getString("caller", "").contains("xdrip")) {
            isInternalCall = true;
            logger.debug("✅ 通过Extra判断为内部调用");
        }
    }
    
    // 最终决定返回哪种Binder
    if (isInternalCall) {
        logger.success("✅ 判断为内部调用，返回LocalBinder");
        logger.debug("LocalBinder类名: " + localBinder.getClass().getName());
        logger.debug("LocalBinder是否是AIDL Stub: " + (localBinder instanceof IBgDataService.Stub));
        logger.debug("LocalBinder是否是Binder子类: " + (localBinder instanceof Binder));
        
        // 返回LocalBinder给xdrip内部调用
        return localBinder;
    } else {
        logger.success("✅ 判断为外部调用，返回AIDL Binder");
        logger.debug("AIDL Binder类名: " + binder.getClass().getName());
        logger.debug("AIDL Binder是否是IBgDataService.Stub: " + (binder instanceof IBgDataService.Stub));
        
        // 返回AIDL Binder给外部应用（如AAPS）
        return binder;
    }
}
*/
/*/////////
    @Override
public IBinder onBind(Intent intent) {
    logger.step("服务绑定", "开始 - 智能判断");

    // 【新增】快速路径1：如果包名非本应用，则强制为外部调用
    if (intent != null && intent.getPackage() != null && !intent.getPackage().equals(getPackageName())) {
        logger.success("✅ 包名非本应用，强制外部调用，返回AIDL Binder");
        return binder;
    }

    // 【新增】快速路径2：如果Intent包含特定标识，则强制为外部调用
    if (intent != null && intent.hasExtra("force_external")) {
        logger.success("✅ 强制外部调用，返回AIDL Binder");
        return binder;
    }

    if (intent == null) {
        logger.warn("绑定请求intent为null，返回AIDL binder");
        return binder;
    }

    String action = intent.getAction();
    String packageName = intent.getPackage();
    ComponentName component = intent.getComponent();

    logger.debug("=== 绑定请求详细分析 ===");
    logger.debug("Action: " + action);
    logger.debug("Package: " + packageName);
    logger.debug("Component: " + (component != null ? component.getClassName() : "null"));
    logger.debug("Extras: " + (intent.getExtras() != null ? 
               intent.getExtras().toString() : "none"));

    String currentPackageName = getPackageName();
    logger.debug("当前应用包名: " + currentPackageName);

    boolean isInternalCall = false;

    // 方法1：通过包名判断（已通过快速路径处理）
    
    // 方法2：通过Component判断
    if (!isInternalCall && component != null) {
        String componentPackage = component.getPackageName();
        if (componentPackage != null && componentPackage.equals(currentPackageName)) {
            isInternalCall = true;
            logger.debug("✅ 通过Component包名判断为内部调用");
        }
    }

    // 方法3：通过Action判断（增强版）
    if (action != null) {
        if ("local".equals(action) || "internal".equals(action) || "xdrip.internal".equals(action)) {
            isInternalCall = true;
            logger.debug("✅ 通过Action判断为内部调用: " + action);
        } else if ("aidl".equals(action) || "external".equals(action) || "aaps".equals(action)) {
            isInternalCall = false;
            logger.debug("✅ 通过Action判断为外部调用: " + action);
        } else {
            isInternalCall = false; // 默认视为外部调用（安全策略）
            logger.warn("⚠️ Action未知，默认视为外部调用: " + action);
        }
    }

    // 方法5：通过Extra判断
    if (intent.getExtras() != null) {
        Bundle extras = intent.getExtras();
        if (extras.containsKey("internal_call") || 
            extras.containsKey("caller") && extras.getString("caller", "").contains("xdrip")) {
            isInternalCall = true;
            logger.debug("✅ 通过Extra判断为内部调用");
        }
    }

    // 最终决定返回哪种Binder
    if (isInternalCall) {
        logger.success("✅ 判断为内部调用，返回LocalBinder");
        return localBinder;
    } else {
        logger.success("✅ 判断为外部调用，返回AIDL Binder");
        return binder;
    }
}
/////////*/ 

///////////
    @Override
public IBinder onBind(Intent intent) {
    logger.step("服务绑定", "AIDL服务绑定 - 统一返回AIDL接口");

    if (intent == null) {
        logger.warn("绑定请求intent为null，但仍返回AIDL binder以维持连接");
        return binder; // 即使intent为空，也返回AIDL
    }

    // 无需复杂的判断逻辑，直接返回AIDL Stub
    // 如果你想保留一点“安全阀”，可以打印一下调用方信息
    String packageName = intent.getPackage();
    String action = intent.getAction();
    logger.debug("外部绑定请求 - 包名: " + packageName + ", Action: " + action);

    return binder; 
}
///////////
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
    private static volatile BgDataService instance;
    
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
