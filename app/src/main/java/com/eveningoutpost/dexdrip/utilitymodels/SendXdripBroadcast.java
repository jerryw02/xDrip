package com.eveningoutpost.dexdrip.utilitymodels;

import android.content.Intent;
import android.os.Bundle;

import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.BgData;
import com.eveningoutpost.dexdrip.BgDataService;
import android.os.RemoteException;

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
            
            // 从Intent中提取数据
            //long timestamp = intent.getLongExtra(Intents.EXTRA_TIMESTAMP, System.currentTimeMillis());
            //double glucose = intent.getDoubleExtra(Intents.EXTRA_BG_ESTIMATE, 0.0);
            //String direction = intent.getStringExtra(Intents.EXTRA_BG_SLOPE_NAME);
            //double noise = intent.getDoubleExtra(Intents.EXTRA_NOISE, 0.0);
            
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
            "BgEstimate",                            // 最可能的短键名
            "com.eveningoutpost.dexdrip.Extras.BgEstimate", // 完整键名
            "com.eveningoutpost.dexdrip.BgEstimate",        // 另一种完整键名
            "glucose",                              // 通用键名
            "GlucoseValue",                              // 通用键名
            "value",                                // 可能的值键名
            "EXTRA_BG_ESTIMATE"                     // 常量名
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
     * 通过静态方法注入数据到服务
     * 这里使用静态方法调用，让xdrip.java管理服务绑定
     */
    
    private static void injectToService(BgData bgData) {
        // 最大重试次数
        final int MAX_RETRY = 2;
        
        for (int retry = 0; retry <= MAX_RETRY; retry++) {
            try {
                // 尝试获取服务实例
                BgDataService service = BgDataService.getInstance();
                
                if (service != null) {
                    // 服务存在，注入数据
                    service.injectBgData(bgData);
                    UserError.Log.d(TAG, "✅ AIDL数据注入成功: " + bgData.getGlucose() + " at " + bgData.getTimestamp());
                    return; // 成功，退出
                } else {
                    // 服务不存在
                    if (retry == 0) {
                        UserError.Log.w(TAG, "⚠️ AIDL服务未就绪，尝试启动服务...");
                        startBgDataService();
                        
                        // 等待服务启动
                        Thread.sleep(300);
                    } else {
                        UserError.Log.w(TAG, "⚠️ AIDL服务未就绪，数据暂存 (重试 " + retry + "/" + MAX_RETRY + ")");
                        
                        // 只在第一次重试时等待，后续快速失败
                        if (retry < MAX_RETRY) {
                            Thread.sleep(100);
                        }
                    }
                }
            } catch (Exception e) {
                UserError.Log.e(TAG, "注入服务异常 (重试 " + retry + "): " + e.getMessage());
                if (retry < MAX_RETRY) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
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
     * 新增：检查AIDL服务是否可用
     */
    public static boolean isAidlServiceAvailable() {
        try {
            return BgDataService.getInstance() != null;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 新增：获取当前服务的状态信息（用于调试）
     */
    public static String getAidlServiceStatus() {
        try {
            if (BgDataService.getInstance() != null) {
                return "AIDL服务运行中";
            }
            return "AIDL服务未启动";
        } catch (Exception e) {
            return "AIDL服务状态未知: " + e.getMessage();
        }
    }
}
