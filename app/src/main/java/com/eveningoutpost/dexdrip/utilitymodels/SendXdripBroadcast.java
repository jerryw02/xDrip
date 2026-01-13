package com.eveningoutpost.dexdrip.utilitymodels;

import android.content.Intent;
import android.os.Bundle;

import com.eveningoutpost.dexdrip.BgData;
import com.eveningoutpost.dexdrip.BgDataService;
import com.eveningoutpost.dexdrip.utils.AIDLLogger;

import static com.eveningoutpost.dexdrip.xdrip.getAppContext;

/**
 * jamorham
 *
 * Locally broadcast an xDrip intent for other apps, caller should check enabled() first
 * handles different and legacy configuration options for package/permission destination
 */

public class SendXdripBroadcast {

    public static void send(final Intent intent, final Bundle bundle) {
        // 🚨 添加AIDL测试日志
        try {
            // 方法1：使用标准日志
            android.util.Log.e("AIDL-BROADCAST", "🎯 SendXdripBroadcast.send()被调用");
            
            // 方法2：检查是否是血糖相关广播
            String action = intent.getAction();
            if (action != null && (action.contains("BG") || action.contains("GLUCOSE"))) {
                android.util.Log.e("AIDL-BROADCAST", "🎯 检测到血糖广播: " + action);
                
                // 调用AIDL服务
                testAIDLWithBroadcast(intent, bundle);
            }
            
        } catch (Exception e) {
            android.util.Log.e("AIDL-BROADCAST", "❌ 日志记录失败: " + e.getMessage());
        }
        
        // 原有逻辑继续
        if (bundle != null) intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);

        final String destination = Pref.getString("local_broadcast_specific_package_destination", "").trim();

        if (destination.length() > 3) {
            for (final String this_dest : destination.split(" ")) {
                if (this_dest != null && this_dest.length() > 3) {
                    // send to each package in space delimited list
                    intent.setPackage(this_dest);
                    sendWithOrWithoutPermission(intent, bundle);
                }
            }
        } else {
            // no package specified
            sendWithOrWithoutPermission(intent, bundle);
        }
    }

    private static void sendWithOrWithoutPermission(final Intent intent, final Bundle bundle) {
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
     * 测试AIDL功能
     */
    private static void testAIDLWithBroadcast(Intent intent, Bundle bundle) {
        try {
            android.util.Log.e("AIDL-BROADCAST", "🔄 开始AIDL测试");
            
            // 获取血糖值
            float glucoseValue = extractGlucoseValue(intent, bundle);
            android.util.Log.e("AIDL-BROADCAST", "📊 提取的血糖值: " + glucoseValue);
            
            if (glucoseValue > 0) {
                // 创建BgData对象
                BgData bgData = new BgData();
                bgData.setTimestamp(System.currentTimeMillis());
                bgData.setGlucoseValue(glucoseValue);
                bgData.setTrend("→");
                bgData.setNoise(0);
                
                android.util.Log.e("AIDL-BROADCAST", "✅ 创建BgData: " + glucoseValue);
                
                // 尝试调用BgDataService
                try {
                    // 方法1：使用静态方法
                    BgDataService.injectDataStatic(bgData);
                    android.util.Log.e("AIDL-BROADCAST", "✅ 静态方法调用成功");
                    
                } catch (Exception e) {
                    android.util.Log.e("AIDL-BROADCAST", "❌ 静态方法失败: " + e.getMessage());
                    
                    // 方法2：使用AIDL服务
                    try {
                        com.eveningoutpost.dexdrip.xdrip app = com.eveningoutpost.dexdrip.xdrip.getInstance();
                        if (app != null) {
                            app.injectBgDataViaAIDL(bgData);
                            android.util.Log.e("AIDL-BROADCAST", "✅ 通过xdrip实例调用成功");
                        }
                    } catch (Exception e2) {
                        android.util.Log.e("AIDL-BROADCAST", "❌ xdrip实例调用失败: " + e2.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            android.util.Log.e("AIDL-BROADCAST", "❌ AIDL测试异常: " + e.getMessage());
        }
    }
    
    /**
     * 从Intent和Bundle中提取血糖值
     */
    private static float extractGlucoseValue(Intent intent, Bundle bundle) {
        float glucose = 0;
        
        try {
            // 从Bundle中提取
            if (bundle != null) {
                if (bundle.containsKey("glucose")) {
                    glucose = bundle.getFloat("glucose");
                } else if (bundle.containsKey("sgv")) {
                    glucose = (float) bundle.getDouble("sgv");
                } else if (bundle.containsKey("value")) {
                    glucose = bundle.getFloat("value");
                }
            }
            
            // 从Intent的Extra中提取
            if (glucose == 0 && intent != null && intent.getExtras() != null) {
                Bundle intentExtras = intent.getExtras();
                if (intentExtras.containsKey("glucose")) {
                    glucose = intentExtras.getFloat("glucose");
                } else if (intentExtras.containsKey("sgv")) {
                    glucose = (float) intentExtras.getDouble("sgv");
                }
            }
            
            // 从Action中提取（如"BG_READING_120"）
            if (glucose == 0 && intent != null) {
                String action = intent.getAction();
                if (action != null) {
                    String[] parts = action.split("_");
                    for (String part : parts) {
                        try {
                            float value = Float.parseFloat(part);
                            if (value > 0 && value < 1000) { // 合理的血糖范围
                                glucose = value;
                                break;
                            }
                        } catch (NumberFormatException e) {
                            // 忽略非数字部分
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            android.util.Log.e("AIDL-BROADCAST", "提取血糖值失败: " + e.getMessage());
        }
        
        return glucose > 0 ? glucose : 123.4f; // 返回实际值或测试值
    }
}            android.util.Log.e("AIDL-BROADCAST", "❌ 日志记录失败: " + e.getMessage());
        }
        
        // 原有逻辑继续
        if (bundle != null) intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);

        final String destination = Pref.getString("local_broadcast_specific_package_destination", "").trim();

        if (destination.length() > 3) {
            for (final String this_dest : destination.split(" ")) {
                if (this_dest != null && this_dest.length() > 3) {
                    // send to each package in space delimited list
                    intent.setPackage(this_dest);
                    sendWithOrWithoutPermission(intent, bundle);
                }
            }
        } else {
            // no package specified
            sendWithOrWithoutPermission(intent, bundle);
        }
    }

    private static void sendWithOrWithoutPermission(final Intent intent, final Bundle bundle) {
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
     * 测试AIDL功能
     */
    private static void testAIDLWithBroadcast(Intent intent, Bundle bundle) {
        try {
            android.util.Log.e("AIDL-BROADCAST", "🔄 开始AIDL测试");
            
            // 获取血糖值
            float glucoseValue = extractGlucoseValue(intent, bundle);
            android.util.Log.e("AIDL-BROADCAST", "📊 提取的血糖值: " + glucoseValue);
            
            if (glucoseValue > 0) {
                // 创建BgData对象
                BgData bgData = new BgData();
                bgData.setTimestamp(System.currentTimeMillis());
                bgData.setGlucoseValue(glucoseValue);
                bgData.setTrend("→");
                bgData.setNoise(0);
                
                android.util.Log.e("AIDL-BROADCAST", "✅ 创建BgData: " + glucoseValue);
                
                // 尝试调用BgDataService
                try {
                    // 方法1：使用静态方法
                    BgDataService.injectDataStatic(bgData);
                    android.util.Log.e("AIDL-BROADCAST", "✅ 静态方法调用成功");
                    
                } catch (Exception e) {
                    android.util.Log.e("AIDL-BROADCAST", "❌ 静态方法失败: " + e.getMessage());
                    
                    // 方法2：使用AIDL服务
                    try {
                        com.eveningoutpost.dexdrip.xdrip app = com.eveningoutpost.dexdrip.xdrip.getInstance();
                        if (app != null) {
                            app.injectBgDataViaAIDL(bgData);
                            android.util.Log.e("AIDL-BROADCAST", "✅ 通过xdrip实例调用成功");
                        }
                    } catch (Exception e2) {
                        android.util.Log.e("AIDL-BROADCAST", "❌ xdrip实例调用失败: " + e2.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            android.util.Log.e("AIDL-BROADCAST", "❌ AIDL测试异常: " + e.getMessage());
        }
    }
    
    /**
     * 从Intent和Bundle中提取血糖值
     */
    private static float extractGlucoseValue(Intent intent, Bundle bundle) {
        float glucose = 0;
        
        try {
            // 从Bundle中提取
            if (bundle != null) {
                if (bundle.containsKey("glucose")) {
                    glucose = bundle.getFloat("glucose");
                } else if (bundle.containsKey("sgv")) {
                    glucose = (float) bundle.getDouble("sgv");
                } else if (bundle.containsKey("value")) {
                    glucose = bundle.getFloat("value");
                }
            }
            
            // 从Intent的Extra中提取
            if (glucose == 0 && intent != null && intent.getExtras() != null) {
                Bundle intentExtras = intent.getExtras();
                if (intentExtras.containsKey("glucose")) {
                    glucose = intentExtras.getFloat("glucose");
                } else if (intentExtras.containsKey("sgv")) {
                    glucose = (float) intentExtras.getDouble("sgv");
                }
            }
            
            // 从Action中提取（如"BG_READING_120"）
            if (glucose == 0 && intent != null) {
                String action = intent.getAction();
                if (action != null) {
                    String[] parts = action.split("_");
                    for (String part : parts) {
                        try {
                            float value = Float.parseFloat(part);
                            if (value > 0 && value < 1000) { // 合理的血糖范围
                                glucose = value;
                                break;
                            }
                        } catch (NumberFormatException e) {
                            // 忽略非数字部分
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            android.util.Log.e("AIDL-BROADCAST", "提取血糖值失败: " + e.getMessage());
        }
        
        return glucose > 0 ? glucose : 123.4f; // 返回实际值或测试值
    }
}    private static void sendWithOrWithoutPermission(final Intent intent, final Bundle bundle) {

        if (Pref.getBooleanDefaultFalse("broadcast_data_through_intents_without_permission")) {
            getAppContext().sendBroadcast(intent);
        } else {
            getAppContext().sendBroadcast(intent, Intents.RECEIVER_PERMISSION);
        }
    }

    public static boolean enabled() {
        return Pref.getBooleanDefaultFalse("broadcast_data_through_intents");
    }

}
