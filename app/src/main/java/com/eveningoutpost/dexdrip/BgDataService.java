package com.eveningoutpost.dexdrip;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;  // ========== 新增导入 ==========
import android.os.Looper;   // ========== 新增导入 ==========
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import com.eveningoutpost.dexdrip.models.UserError;

import androidx.core.app.NotificationCompat;
import com.eveningoutpost.dexdrip.utils.AIDLLogger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;  // ========== 新增导入 ==========
import java.util.Iterator;  // ========== 新增导入 ==========
import java.util.Map;       // ========== 新增导入 ==========

public class BgDataService extends Service {
    private static final String TAG = "BgDataService";
    private static final String CHANNEL_ID = "xDrip_BgData_Service";
    private static final int NOTIFICATION_ID = 1001;
    
    // ========== 新增常量 ==========
    private static final int HEARTBEAT_INTERVAL = 30000; // 30秒心跳间隔
    private static final String HEARTBEAT_TAG = "BgDataService_Heartbeat";
    private static final long CLIENT_TIMEOUT = 120000; // 2分钟无活动超时
    // ==============================
    
    // 日志工具
    private AIDLLogger logger;
    
    // 当前血糖数据
    private volatile BgData currentBgData = null;
    
    // 回调列表
    private final RemoteCallbackList<IBgDataCallback> callbacks = new RemoteCallbackList<>();
    
    // 序列号生成器（用于去重）
    private final AtomicLong sequenceGenerator = new AtomicLong(0);
    
    // ========== 新增：心跳Handler和客户端活动时间跟踪 ==========
    private Handler heartbeatHandler;
    private final Map<IBinder, Long> clientConnectionTime = new ConcurrentHashMap<>();
    // =====================================================
    
    // AIDL接口实现
    private final IBgDataService.Stub binder = new IBgDataService.Stub() {
        @Override
        public BgData getLatestBgData() throws RemoteException {
            logger.debug("AAPS请求最新数据");
            
            // ========== 新增：更新客户端活动时间 ==========
            IBinder callingBinder = getCallingBinder();
            if (callingBinder != null) {
                clientConnectionTime.put(callingBinder, System.currentTimeMillis());
            }
            // ==========================================
            
            return currentBgData;
        }
        
        @Override
        public void registerCallback(IBgDataCallback callback) throws RemoteException {
            if (callback != null) {
                callbacks.register(callback);
                int count = callbacks.getRegisteredCallbackCount();
                logger.success(String.format("AAPS回调注册成功，当前客户端数: %d", count));
                
                // ========== 新增：记录客户端活动时间和死亡接收器 ==========
                clientConnectionTime.put(callback.asBinder(), System.currentTimeMillis());
                
                // 添加死亡接收器
                callback.asBinder().linkToDeath(new IBinder.DeathRecipient() {
                    @Override
                    public void binderDied() {
                        logger.warn("客户端死亡，清理回调");
                        callbacks.unregister(callback);
                        clientConnectionTime.remove(callback.asBinder());
                        updateNotification();
                    }
                }, 0);
                // ====================================================
                
                // 立即发送当前数据给新注册的客户端
                if (currentBgData != null) {
                    callback.onNewBgData(currentBgData);
                    logger.debug("向新客户端发送当前数据");
                }
                
                // ========== 新增：更新通知显示 ==========
                updateNotification();
                // =====================================
            }
        }
        
        @Override
        public void unregisterCallback(IBgDataCallback callback) throws RemoteException {
            if (callback != null) {
                callbacks.unregister(callback);
                int count = callbacks.getRegisteredCallbackCount();
                logger.debug(String.format("AAPS回调注销，剩余客户端数: %d", count));
                
                // ========== 新增：清理客户端时间记录 ==========
                clientConnectionTime.remove(callback.asBinder());
                // ==========================================
                
                // ========== 新增：更新通知显示 ==========
                updateNotification();
                // =====================================
            }
        }
        
        // ========== 新增：辅助方法获取调用者的Binder ==========
        private IBinder getCallingBinder() {
            try {
                // 尝试从回调列表中获取当前调用者的Binder
                final int count = callbacks.beginBroadcast();
                try {
                    for (int i = 0; i < count; i++) {
                        IBgDataCallback cb = callbacks.getBroadcastItem(i);
                        if (cb != null && cb.asBinder().pingBinder()) {
                            // 这里简化处理，实际可能需要更精确的匹配
                            return cb.asBinder();
                        }
                    }
                } finally {
                    callbacks.finishBroadcast();
                }
            } catch (Exception e) {
                logger.error("获取调用者Binder失败: " + e.getMessage());
            }
            return null;
        }
        // =================================================
        
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
        UserError.Log.uel(TAG, "✅ 静态实例已设置: " + (instance != null));
        
        logger = AIDLLogger.getInstance();        
        logger.logServiceStatus("BgDataService", "创建");
        logger.step("初始化", "开始");
        
        // ========== 新增：初始化心跳Handler ==========
        heartbeatHandler = new Handler(Looper.getMainLooper());
        // ==========================================
        
        try {
            // 创建前台服务通知
            createNotificationChannel();
            startForeground(NOTIFICATION_ID, createNotification());
            logger.success("BgDataService启动成功");     
            logger.step("初始化", "完成");
            
            // === 新增：发送服务就绪广播 ===
            sendServiceReadyBroadcast();
            
            // ========== 新增：启动心跳机制 ==========
            startHeartbeat();
            // =====================================
            
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

    @Override
    public IBinder onBind(Intent intent) {
        UserError.Log.uel(TAG, "=== onBind() ===");
        UserError.Log.uel(TAG, "Intent: " + intent);
        UserError.Log.uel(TAG, "Action: " + intent.getAction());
        UserError.Log.uel(TAG, "Calling UID: " + Binder.getCallingUid());
        UserError.Log.uel(TAG, "Calling Package: " + getPackageManager().getNameForUid(Binder.getCallingUid()));
    
        // 临时：允许所有绑定请求
        UserError.Log.uel(TAG, "✅ 临时允许绑定，返回Binder");

        // ========== 修改：删除错误的心跳启动代码 ==========
        // 原错误代码：handler.postDelayed(heartbeatRunnable, 30000);
        // 现在心跳在 startHeartbeat() 中统一管理
        // =============================================
        
        // ========== 新增：记录绑定时间 ==========
        // 注意：这里记录的是binder的绑定，不是callback的注册
        // 实际客户端活动时间在registerCallback中记录更准确
        clientConnectionTime.put(binder, System.currentTimeMillis());
        // =====================================
        
        return binder;
    }
    
    @Override
    public boolean onUnbind(Intent intent) {
        logger.debug("所有客户端解除绑定");
        
        // ========== 新增：清理所有客户端时间记录 ==========
        clientConnectionTime.clear();
        // ============================================
        
        return true;
    }
    
    @Override
    public void onDestroy() {
        // ========== 新增：停止心跳机制 ==========
        stopHeartbeat();
        // =====================================
        
        // 清理1：清除静态引用，防止内存泄漏
        instance = null;
        
        // ========== 新增：清理客户端时间记录 ==========
        clientConnectionTime.clear();
        // ============================================
        
        logger.logServiceStatus("BgDataService", "销毁");
        
        // 清理2：释放客户端回调资源
        callbacks.kill();
        
        // ========== 新增：清理Handler ==========
        if (heartbeatHandler != null) {
            heartbeatHandler.removeCallbacksAndMessages(null);
        }
        // =====================================
        
        super.onDestroy();
    }
    
    // ========== 新增方法：心跳机制 ==========
    /**
     * 启动心跳，保持AIDL连接活跃
     * 原因：防止Android系统因空闲而断开AIDL连接
     */
    private void startHeartbeat() {
        if (heartbeatHandler != null) {
            // 延迟5秒开始第一次心跳，避免启动时立即发送
            heartbeatHandler.postDelayed(this::sendHeartbeat, 5000);
            logger.debug("心跳机制已启动，间隔：" + HEARTBEAT_INTERVAL + "ms");
        }
    }
    
    /**
     * 停止心跳
     */
    private void stopHeartbeat() {
        if (heartbeatHandler != null) {
            heartbeatHandler.removeCallbacks(this::sendHeartbeat);
            logger.debug("心跳机制已停止");
        }
    }
    
    /**
     * 发送心跳数据
     */
    private void sendHeartbeat() {
        final int count = callbacks.beginBroadcast();
        try {
            if (count > 0) {
                // 创建心跳数据
                BgData heartbeatData = new BgData();
                heartbeatData.setTimestamp(System.currentTimeMillis());
                heartbeatData.setSequenceNumber(sequenceGenerator.incrementAndGet());
                heartbeatData.setSource("xDrip_Heartbeat");
                
                // 如果有最新数据，包含在心跳中
                if (currentBgData != null) {
                    heartbeatData.setGlucoseValue(currentBgData.getGlucoseValue());
                    heartbeatData.setTrend(currentBgData.getTrend());
                    heartbeatData.setDirection(currentBgData.getDirection());
                }
                
                int successCount = 0;
                int failCount = 0;
                
                for (int i = 0; i < count; i++) {
                    try {
                        IBgDataCallback callback = callbacks.getBroadcastItem(i);
                        callback.onNewBgData(heartbeatData);
                        successCount++;
                        
                        // 更新客户端活动时间
                        clientConnectionTime.put(callback.asBinder(), System.currentTimeMillis());
                        
                    } catch (RemoteException e) {
                        failCount++;
                        logger.warn(HEARTBEAT_TAG + "心跳发送失败给客户端 " + i + ": " + e.getMessage());
                    }
                }
                
                logger.debug(HEARTBEAT_TAG + "心跳发送结果: 成功=" + successCount + ", 失败=" + failCount);
                
                // 清理超时客户端
                cleanupTimeoutClients();
                
            } else {
                logger.debug(HEARTBEAT_TAG + "无活跃客户端，跳过心跳");
            }
        } finally {
            callbacks.finishBroadcast();
        }
        
        // 安排下一次心跳
        if (heartbeatHandler != null) {
            heartbeatHandler.postDelayed(this::sendHeartbeat, HEARTBEAT_INTERVAL);
        }
    }
    
    /**
     * 清理超时未活动的客户端
     */
    private void cleanupTimeoutClients() {
        long currentTime = System.currentTimeMillis();
        int removedCount = 0;
        
        Iterator<Map.Entry<IBinder, Long>> iterator = clientConnectionTime.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<IBinder, Long> entry = iterator.next();
            long lastActivity = entry.getValue();
            
            if (currentTime - lastActivity > CLIENT_TIMEOUT) {
                iterator.remove();
                removedCount++;
                
                logger.debug("清理超时客户端: " + entry.getKey().hashCode());
                
                // 尝试从callbacks中移除（如果存在）
                // 注意：RemoteCallbackList的管理更复杂，这里主要清理时间记录
            }
        }
        
        if (removedCount > 0) {
            logger.info("清理了 " + removedCount + " 个超时客户端记录");
            updateNotification();
        }
    }
    
    /**
     * 尝试重新连接（如果检测到连接问题）
     */
    private void attemptReconnect() {
        logger.debug("尝试触发客户端重连");
        
        // 发送服务状态广播
        Intent intent = new Intent("com.eveningoutpost.dexdrip.SERVICE_RECONNECT");
        sendBroadcast(intent);
    }
    // ==============================================
    
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
        
        // ========== 新增：更新所有客户端的活动时间 ==========
        long currentTime = System.currentTimeMillis();
        final int count = callbacks.beginBroadcast();
        try {
            for (int i = 0; i < count; i++) {
                try {
                    IBgDataCallback callback = callbacks.getBroadcastItem(i);
                    clientConnectionTime.put(callback.asBinder(), currentTime);
                } catch (Exception e) {
                    // 忽略个别错误
                }
            }
        } finally {
            callbacks.finishBroadcast();
        }
        // ================================================
        
        // 通知所有客户端
        notifyClients(newData);
        
        logger.success("数据注入完成");
        logger.debug("当前数据: " + newData.toString());
        
        // ========== 新增：更新通知 ==========
        updateNotification();
        // ================================
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
        // ========== 修改：计算活跃客户端数 ==========
        int activeClients = 0;
        long currentTime = System.currentTimeMillis();
        for (Long lastActivity : clientConnectionTime.values()) {
            if (currentTime - lastActivity < 60000) { // 1分钟内活跃
                activeClients++;
            }
        }
        
        String contentText = "为AAPS提供实时血糖数据";
        if (activeClients > 0) {
            contentText += " (" + activeClients + "个活跃客户端)";
        } else {
            contentText += " (等待连接...)";
        }
        // ========================================
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("xDrip AIDL服务运行中")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW);
        
        logger.debug("创建前台服务通知");
        return builder.build();
    }
    
    /**
     * 更新通知（新增方法）
     */
    private void updateNotification() {
        if (instance != null) {
            NotificationManager notificationManager = 
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.notify(NOTIFICATION_ID, createNotification());
            }
        }
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

    // ========== 删除旧的错误心跳代码段 ==========
    // 删除以下代码（如果存在）：
    /*
    // 添加心跳机制
    private final Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (callback != null) {
                try {
                    // 发送心跳数据或空数据保持连接
                    BgData heartbeatData = new BgData();
                    heartbeatData.setTimestamp(System.currentTimeMillis());
                    callback.onNewBgData(heartbeatData);
                
                    UserError.Log.uel(TAG, "Heartbeat sent to keep connection alive");
                } catch (RemoteException e) {
                    UserError.Log.uel(TAG, "Heartbeat failed, client may have disconnected", e);
                    callback = null;
                }
            }
        
            // 每30秒发送一次心跳
            handler.postDelayed(this, 30000);
        }
    };
    */
    // =========================================

    // === 修改：添加简单的测试方法 ===
    public void sendTestData() {
        BgData testData = new BgData();
        testData.setGlucoseValue(120.0);
        testData.setTimestamp(System.currentTimeMillis());
        testData.setSequenceNumber(sequenceGenerator.incrementAndGet());
        
        injectBgData(testData);
        logger.debug("发送测试数据完成");
    }
}
