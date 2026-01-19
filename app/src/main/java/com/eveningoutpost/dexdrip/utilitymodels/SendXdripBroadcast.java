package com.eveningoutpost.dexdrip.utilitymodels;

import android.content.Intent;
import android.content.Context;
import android.os.Bundle;

// === 新增：添加缺失的 import ===
import android.content.BroadcastReceiver;      // 添加这个
import android.content.IntentFilter;           // 添加这个
import android.content.ComponentName;          // 可能需要
import android.os.Handler;                     // 可能需要
import android.os.Looper;                      // 可能需要

import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.BgData;
import com.eveningoutpost.dexdrip.BgDataService;
import com.eveningoutpost.dexdrip.xdrip;
import android.os.RemoteException;
import android.app.ActivityManager;            // 可能需要

// === 新增：Java 集合类 ===
import java.util.Queue;                        // 添加这个
import java.util.LinkedList;                   // 添加这个
import java.util.List;                         // 可能需要

import static com.eveningoutpost.dexdrip.xdrip.getAppContext;

/**
 * jamorham
 *
 * Locally broadcast an xDrip intent for other apps, caller should check enabled() first
 * handles different and legacy configuration options for package/permission destination
 */

public class SendXdripBroadcast {
    
    private static final String TAG = "SendXdripBroadcast";

    public static void send(final Intent intent, final Bundle bundle) {
        // 原有的调试日志
        UserError.Log.uel("AIDL-DEBUG", "🔴 SendXdripBroadcast.send: " + (intent != null ? intent.getAction() : "null"));
        
        // 新增：通过AIDL服务注入数据（双备份）
        try {
            injectBgDataToAidlService(intent, bundle);
        } catch (Exception e) {
            UserError.Log.uel(TAG, "AIDL数据注入异常: " + e.getMessage());
            // AIDL注入失败不影响原有的广播发送
        }
        
        // 原有的广播发送逻辑
        if (bundle != null && intent != null) {
            intent.putExtras(bundle);
        }
        if (intent != null) {
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        }

        final String destination = Pref.getString("local_broadcast_specific_package_destination", "").trim();

        if (destination.length() > 3) {
            for (final String this_dest : destination.split(" ")) {
                if (this_dest != null && this_dest.length() > 3) {
                    // send to each package in space delimited list
                    if (intent != null) {
                        intent.setPackage(this_dest);
                        sendWithOrWithoutPermission(intent, bundle);
                    }
                }
            }
        } else {
            // no package specified
            sendWithOrWithoutPermission(intent, bundle);
        }
    }

    /**
     * 向AIDL服务注入血糖数据
     * 这个方法只负责调用服务方法，不管理服务生命周期
     */
    private static void injectBgDataToAidlService(Intent intent, Bundle bundle) {
        // 只处理血糖相关广播
        if (intent == null || !Intents.ACTION_NEW_BG_ESTIMATE.equals(intent.getAction())) {
            return;
        }
        
        try {
            // 调试：打印所有可用的Extra键名
            if (intent.getExtras() != null) {
                UserError.Log.uel(TAG, "Intent Extras Keys: " + intent.getExtras().keySet());
                for (String key : intent.getExtras().keySet()) {
                    Object value = intent.getExtras().get(key);
                    UserError.Log.uel(TAG, "  " + key + " = " + value + " (type: " + 
                                   (value != null ? value.getClass().getSimpleName() : "null") + ")");
                }
            }
            
            if (bundle != null) {
                UserError.Log.uel(TAG, "Bundle Keys: " + bundle.keySet());
                for (String key : bundle.keySet()) {
                    Object value = bundle.get(key);
                    UserError.Log.uel(TAG, "  " + key + " = " + value + " (type: " + 
                                   (value != null ? value.getClass().getSimpleName() : "null") + ")");
                }
            }
          
            // 提取血糖数据的正确方法
            double glucose = extractGlucoseValue(intent, bundle);
            long timestamp = extractTimestampValue(intent, bundle);
            //String direction = extractDirectionValue(intent, bundle);
            //double noise = extractNoiseValue(intent, bundle);

            // 验证提取的数据
            if (glucose == 0.0) {
                UserError.Log.uel(TAG, "⚠️ 警告：提取到血糖值为0.0，可能数据提取方式不正确");
                // 可以尝试再次从其他来源获取数据
            }
                                
            // 创建BgData对象
            BgData bgData = new BgData();
            bgData.setTimestamp(timestamp);
            bgData.setGlucoseValue(glucose);
            //bgData.setDirection(direction != null ? direction : "");
            //bgData.setNoise(noise);
            bgData.setSource("xDrip");

            UserError.Log.uel(TAG, "📊 提取的血糖数据 - Glucose: " + glucose + ", Time: " + timestamp);
            
            // 注入数据到AIDL服务
            injectToService(bgData);
            
            UserError.Log.uel(TAG, "✅ AIDL数据注入成功: " + glucose + " at " + timestamp);
            
        } catch (Exception e) {
            UserError.Log.uel(TAG, "❌ AIDL数据注入失败: " + e.getMessage());
        }
    }

    /**
     * 提取血糖值 - 尝试多种可能的键名
     */
    private static double extractGlucoseValue(Intent intent, Bundle bundle) {
        // 尝试多种可能的键名
        String[] possibleKeys = {
            "BgEstimate",                                       // 最可能的短键名
            "com.eveningoutpost.dexdrip.Extras.BgEstimate",     // 完整键名
            "com.eveningoutpost.dexdrip.BgEstimate",            // 另一种完整键名
            "glucose",                                          // 通用键名
            "GlucoseValue",                                     // 通用键名
            "value",                                            // 可能的值键名
            "EXTRA_BG_ESTIMATE"                                 // 常量名
        };
        
        for (String key : possibleKeys) {
            try {
                // 先从bundle尝试
                if (bundle != null && bundle.containsKey(key)) {
                    Object value = bundle.get(key);
                    if (value instanceof Double) {
                        UserError.Log.uel(TAG, "✅ 从Bundle找到血糖值: " + key + " = " + value);
                        return (Double) value;
                    } else if (value instanceof Float) {
                        UserError.Log.uel(TAG, "✅ 从Bundle找到血糖值(Float): " + key + " = " + value);
                        return (double) (Float) value;
                    } else if (value instanceof String) {
                        UserError.Log.uel(TAG, "✅ 从Bundle找到血糖值(String): " + key + " = " + value);
                        return Double.parseDouble((String) value);
                    }
                }
                
                // 再从intent尝试
                if (intent.hasExtra(key)) {
                    double value = intent.getDoubleExtra(key, 0.0);
                    if (value != 0.0) {
                        UserError.Log.uel(TAG, "✅ 从Intent找到血糖值: " + key + " = " + value);
                        return value;
                    }
                }
            } catch (Exception e) {
                UserError.Log.uel(TAG, "提取血糖值失败(key=" + key + "): " + e.getMessage());
            }
        }
        
        // 最后尝试直接检查所有值
        if (bundle != null) {
            for (String key : bundle.keySet()) {
                if (key != null && (key.toLowerCase().contains("bg") || 
                                    key.toLowerCase().contains("glucose") || 
                                    key.toLowerCase().contains("estimate"))) {
                    Object value = bundle.get(key);
                    UserError.Log.uel(TAG, "🔍 可能匹配的血糖键: " + key + " = " + value);
                }
            }
        }
        
        return 0.0;
    }
    
    /**
     * 提取时间戳
     */
    private static long extractTimestampValue(Intent intent, Bundle bundle) {
        // 尝试多种可能的键名
        String[] possibleKeys = {
            "BgTimestamp",
            "com.eveningoutpost.dexdrip.Extras.BgTimestamp",
            "timestamp",
            "time",
            "EXTRA_BG_TIMESTAMP",
            "EXTRA_TIMESTAMP",
        };
        
        for (String key : possibleKeys) {
            try {
                // 从bundle尝试
                if (bundle != null && bundle.containsKey(key)) {
                    Object value = bundle.get(key);
                    if (value instanceof Long) {
                        return (Long) value;
                    } else if (value instanceof String) {
                        return Long.parseLong((String) value);
                    }
                }
                
                // 从intent尝试
                if (intent.hasExtra(key)) {
                    long value = intent.getLongExtra(key, System.currentTimeMillis());
                    if (value > 0) {
                        return value;
                    }
                }
            } catch (Exception e) {
                // 忽略错误，尝试下一个键名
            }
        }
        
        return System.currentTimeMillis();
    }

    /**
     * 提取趋势方向
     */
    private static String extractDirectionValue(Intent intent, Bundle bundle) {
        // 尝试多种可能的键名
        String[] possibleKeys = {
            "BgSlopeName",
            "BgSlope",
            "com.eveningoutpost.dexdrip.Extras.BgSlopeName",
            "direction",
            "trend",
            "slope",
            "EXTRA_BG_SLOPE_NAME",            
        };
        
        for (String key : possibleKeys) {
            try {
                // 从bundle尝试
                if (bundle != null) {
                    String value = bundle.getString(key);
                    if (value != null && !value.isEmpty()) {
                        return value;
                    }
                }
                
                // 从intent尝试
                String value = intent.getStringExtra(key);
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            } catch (Exception e) {
                // 忽略错误，尝试下一个键名
            }
        }
        
        return "";
    }
    
    /**
     * 提取噪声值
     */
    private static double extractNoiseValue(Intent intent, Bundle bundle) {
        // 尝试多种可能的键名
        String[] possibleKeys = {
            "Noise",
            "com.eveningoutpost.dexdrip.Extras.Noise",
            "bg_noise",
            "EXTRA_NOISE",
        };
        
        for (String key : possibleKeys) {
            try {
                // 从bundle尝试
                if (bundle != null && bundle.containsKey(key)) {
                    Object value = bundle.get(key);
                    if (value instanceof Double) {
                        return (Double) value;
                    } else if (value instanceof Float) {
                        return (double) (Float) value;
                    }
                }
                
                // 从intent尝试
                if (intent.hasExtra(key)) {
                    return intent.getDoubleExtra(key, 0.0);
                }
            } catch (Exception e) {
                // 忽略错误，尝试下一个键名
            }
        }
        
        return 0.0;
    }
    
    /**
     * 通过静态方法注入数据到服务
     * 这里使用静态方法调用，让xdrip.java管理服务绑定
     */
    
    private static void injectToService(BgData bgData) {
        
        // 最大重试次数
        final int MAX_RETRY = 2;
        
        for (int retry = 0; retry <= MAX_RETRY; retry++) {
            try {

                UserError.Log.uel(TAG, "=== 开始注入数据到AIDL服务 ===");
                UserError.Log.uel(TAG, "数据: " + bgData.getGlucoseValue() + " @ " + bgData.getTimestamp());
                
                // 方法1：直接通过静态方法获取
                BgDataService service = BgDataService.getInstance();
                UserError.Log.uel(TAG, "方法1 - getInstance() 结果: " + (service != null ? "非空" : "NULL"));
                
                if (service != null) {
                    UserError.Log.uel(TAG, "服务类: " + service.getClass().getName());
                    UserError.Log.uel(TAG, "调用 injectBgData()...");
                    // 服务存在，注入数据
                    try {
                        service.injectBgData(bgData);
                        UserError.Log.uel(TAG, "✅ injectBgData 调用成功");
                        UserError.Log.uel(TAG, "✅ AIDL数据注入成功: " + bgData.getGlucoseValue() + " at " + bgData.getTimestamp());
                        return; // 成功，退出
                    } catch (Exception e) {
                        UserError.Log.uel(TAG, "❌ injectBgData 调用异常: " + e.getMessage());
                        e.printStackTrace();
                    } 
                                
                } else {
                    // 服务不存在
                    UserError.Log.uel(TAG, "⚠️ 服务不可用，数据暂存");
            
                    // 将数据加入暂存队列
                    synchronized (pendingDataQueue) {
                        pendingDataQueue.offer(bgData);
                        // 限制队列大小，防止内存泄漏
                        if (pendingDataQueue.size() > 10) {
                            pendingDataQueue.poll(); // 移除最旧的数据
                            UserError.Log.uel(TAG, "⚠️ 队列已满，移除最旧数据");
                        }
                    }
            
                    UserError.Log.uel(TAG, "当前暂存数据量: " + pendingDataQueue.size());
            
                    // 尝试启动服务
                    startBgDataService();
            
                    return; // 不继续尝试注入
                }
                
                ////////
                // 方法2：通过应用实例获取
                UserError.Log.uel(TAG, "尝试方法2：通过xdrip应用实例获取");
                try {
                    if (xdrip.getInstance() != null) {
                        // 检查xdrip.java中是否有绑定的服务
                        // 注意：这里需要xdrip.java暴露相应的方法
                        UserError.Log.uel(TAG, "xdrip应用实例存在");
            
                        // 如果xdrip.java有getBgDataService()方法
                        // BgDataService service2 = xdrip.getInstance().getBgDataService();
                    }
                } catch (Exception e) {
                    UserError.Log.uel(TAG, "获取xdrip应用实例失败: " + e.getMessage());
                }
    
                // 方法3：直接启动服务并绑定
                UserError.Log.uel(TAG, "尝试方法3：直接启动并绑定服务");
                startAndBindServiceDirectly(bgData);
                ////////
                
            } catch (Exception e) {
                UserError.Log.uel(TAG, "注入服务异常 (重试 " + retry + "): " + 
                e.getClass().getSimpleName() + ": " + e.getMessage());
            
                // 特别处理特定异常
                if (e instanceof NullPointerException) {
                    UserError.Log.uel(TAG, "⚠️ 空指针异常，可能是getInstance()返回null");
                }
                                
                if (retry < MAX_RETRY) {
                    try {
                        Thread.sleep(100 * (retry + 1)); // 递增等待时间
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    ////////
/**
 * 直接启动并绑定服务
 */
private static void startAndBindServiceDirectly(BgData bgData) {
    try {
        Context context = getAppContext();
        
        // 创建ServiceConnection
        ServiceConnection tempConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                UserError.Log.uel(TAG, "✅ 临时服务连接成功");
                
                try {
                    // 获取服务实例
                    BgDataService bgService = BgDataService.getInstance();
                    if (bgService != null) {
                        bgService.injectBgData(bgData);
                        UserError.Log.uel(TAG, "✅ 通过临时连接注入数据成功");
                    }
                } catch (Exception e) {
                    UserError.Log.uel(TAG, "❌ 临时连接注入失败: " + e.getMessage());
                }
                
                // 立即解绑，避免泄漏
                try {
                    context.unbindService(this);
                } catch (Exception e) {
                    // 忽略解绑异常
                }
            }
            
            @Override
            public void onServiceDisconnected(ComponentName name) {
                UserError.Log.uel(TAG, "临时服务连接断开");
            }
        };
        
        // 启动服务
        Intent serviceIntent = new Intent(context, BgDataService.class);
        serviceIntent.setPackage(context.getPackageName());
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
        
        // 绑定服务
        boolean bound = context.bindService(
            serviceIntent,
            tempConnection,
            Context.BIND_AUTO_CREATE | Context.BIND_ABOVE_CLIENT
        );
        
        UserError.Log.uel(TAG, "临时服务绑定结果: " + bound);
        
        // 等待服务连接（最多2秒）
        new Thread(() -> {
            try {
                Thread.sleep(2000);
                // 如果2秒后还没有注入成功，记录日志
                UserError.Log.uel(TAG, "临时连接超时检查");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
        
    } catch (Exception e) {
        UserError.Log.uel(TAG, "❌ 直接绑定服务失败: " + e.getMessage());
    }
}
    ////////

    /**
     * 检查服务是否真正可用（新增辅助方法）
     */
    private static boolean isServiceAvailable(BgDataService service) {
        if (service == null) {
            return false;
        }
    
        // 这里可以添加更多服务健康检查
        // 例如：检查服务是否被销毁、是否处于正确状态等
    
        return true;
    }

    /**
     * 启动BgDataService 
     */
    private static void startBgDataService() {
        try {
            Context context = getAppContext();
            
            // 先检查服务是否已经在运行
            if (isServiceRunning(context, BgDataService.class)) {
                UserError.Log.uel(TAG, "服务已经在运行，无需重复启动");

                // 即使服务在运行，也要确保实例已设置
                BgDataService service = BgDataService.getInstance();
                if (service == null) {
                    UserError.Log.uel(TAG, "⚠️ 服务运行但实例未设置，尝试通过应用实例获取");
                
                    // 尝试通过xdrip.java获取
                    if (xdrip.getInstance() != null && 
                        xdrip.getInstance().isBgDataServiceBound()) {
                        // xdrip.java中可能有连接好的服务
                        UserError.Log.uel(TAG, "✅ 通过xdrip应用实例获取服务");
                    }
                }
                return;
            }
            
            // 创建启动Intent
            Intent serviceIntent = new Intent(context, BgDataService.class);
            serviceIntent.setPackage(context.getPackageName());
            
            // ⚠️ 重要：现在不需要特殊标记，因为onBind总是返回AIDL Binder
            // serviceIntent.setAction("internal"); // 可以删除这行
            // serviceIntent.putExtra("caller", "SendXdripBroadcast"); // 可以删除

            serviceIntent.putExtra("timestamp", System.currentTimeMillis());            
            UserError.Log.uel(TAG, "🚀 正在启动BgDataService...");
            
            // 启动服务
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            
            UserError.Log.uel(TAG, "✅ BgDataService启动请求已发送");
            // 等待服务初始化
            Thread.sleep(200);
            
        } catch (Exception e) {
            UserError.Log.uel(TAG, "❌ 启动服务失败: " + e.getMessage());
        }
    }
    
    /**
     * 检查服务是否正在运行
     */
    private static boolean isServiceRunning(Context context, Class<?> serviceClass) {
        try {
            android.app.ActivityManager manager = 
                (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            
            for (android.app.ActivityManager.RunningServiceInfo service : 
                 manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        } catch (Exception e) {
            UserError.Log.uel(TAG, "检查服务运行状态失败: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * 通知xdrip.java启动AIDL服务
     * 这个方法会被xdrip.java调用，用于通知SendXdripBroadcast服务已就绪
     */
    public static void onServiceReady(BgDataService service) {
        UserError.Log.uel(TAG, "🎯 AIDL服务准备就绪");
        // 这里可以处理暂存的数据
        // TODO: 如果之前有暂存数据，现在可以注入
    }

    private static void sendWithOrWithoutPermission(final Intent intent, final Bundle bundle) {
        if (intent == null) return;

        if (Pref.getBooleanDefaultFalse("broadcast_data_through_intents_without_permission")) {
            getAppContext().sendBroadcast(intent);
        } else {
            getAppContext().sendBroadcast(intent, Intents.RECEIVER_PERMISSION);
        }
    }

    public static boolean enabled() {
        return Pref.getBooleanDefaultFalse("broadcast_data_through_intents");
    }
           
    /**
     * 新增：获取详细的AIDL服务状态信息
     */
    public static String getAidlServiceStatus() {
        StringBuilder status = new StringBuilder();
    
        try {
            // 1. 检查静态实例
            BgDataService instance = BgDataService.getInstance();
            status.append("静态实例: ").append(instance != null ? "存在" : "null").append("\n");
        
            // 2. 检查服务是否在运行
            if (getAppContext() != null) {
                boolean isRunning = isServiceRunning(getAppContext(), BgDataService.class);
                status.append("服务运行状态: ").append(isRunning ? "运行中" : "未运行").append("\n");
            }
        
            // 3. 检查xdrip应用实例（如果可用）
            if (xdrip.getInstance() != null) {
                boolean isBound = xdrip.getInstance().isBgDataServiceBound();
                status.append("应用绑定状态: ").append(isBound ? "已绑定" : "未绑定").append("\n");
            }
        
            // 4. 上次注入状态
            status.append("上次注入结果: ").append(lastInjectionStatus).append("\n");
        
        } catch (Exception e) {
            status.append("状态检查异常: ").append(e.getMessage());
        }
    
        return status.toString();
    }

    // 添加状态跟踪变量
    private static String lastInjectionStatus = "从未尝试";

////////
    // === 新增：服务状态广播接收器 ===
    private static final BroadcastReceiver serviceStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            UserError.Log.uel(TAG, "收到广播: " + action);
            
            if ("com.eveningoutpost.dexdrip.AIDL_SERVICE_READY".equals(action)) {
                long timestamp = intent.getLongExtra("timestamp", 0);
                int pid = intent.getIntExtra("service_pid", 0);
                
                UserError.Log.uel(TAG, "🎉 AIDL服务就绪！");
                UserError.Log.uel(TAG, "  时间戳: " + timestamp);
                UserError.Log.uel(TAG, "  服务PID: " + pid);
                
                // 立即检查服务实例是否可用
                BgDataService service = BgDataService.getInstance();
                UserError.Log.uel(TAG, "  服务实例: " + (service != null ? "可用" : "不可用"));
                
                // 如果有暂存数据，现在可以处理
                processPendingData();
            }
        }
    };
    
    // === 新增：数据暂存队列 ===
    private static final Queue<BgData> pendingDataQueue = new LinkedList<>();
    
    /**
     * 处理暂存的数据
     */
    private static synchronized void processPendingData() {
        if (pendingDataQueue.isEmpty()) {
            UserError.Log.uel(TAG, "没有暂存数据需要处理");
            return;
        }
        
        UserError.Log.uel(TAG, "开始处理 " + pendingDataQueue.size() + " 条暂存数据");
        
        while (!pendingDataQueue.isEmpty()) {
            BgData data = pendingDataQueue.poll();
            try {
                injectToService(data);
                UserError.Log.uel(TAG, "✅ 暂存数据处理成功: " + data.getGlucoseValue());
            } catch (Exception e) {
                UserError.Log.uel(TAG, "❌ 暂存数据处理失败: " + e.getMessage());
                // 可以重新加入队列或记录错误
            }
        }
    }
////////
    // === 静态初始化块：注册广播接收器 ===
    static {
        try {
            Context context = getAppContext();
            if (context != null) {
                IntentFilter filter = new IntentFilter();
                filter.addAction("com.eveningoutpost.dexdrip.AIDL_SERVICE_READY");
                filter.addAction("com.eveningoutpost.dexdrip.SERVICE_STATUS");
                
                // 动态注册接收器
                context.registerReceiver(serviceStatusReceiver, filter);
                
                UserError.Log.uel(TAG, "服务状态广播接收器已注册");
            }
        } catch (Exception e) {
            UserError.Log.uel(TAG, "注册广播接收器失败: " + e.getMessage());
        }
    }
    
    // === 新增：清理方法 ===
    public static void cleanup() {
        try {
            Context context = getAppContext();
            if (context != null) {
                context.unregisterReceiver(serviceStatusReceiver);
                UserError.Log.uel(TAG, "广播接收器已注销");
            }
        } catch (Exception e) {
            // 忽略异常，接收器可能未注册
        }
        
        // 清空暂存队列
        pendingDataQueue.clear();
    }
    
}
