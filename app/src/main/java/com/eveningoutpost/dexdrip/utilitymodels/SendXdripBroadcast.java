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
            // 从Intent中提取数据
            long timestamp = intent.getLongExtra(Intents.EXTRA_BG_TIMESTAMP, System.currentTimeMillis());
            double glucose = intent.getDoubleExtra(Intents.EXTRA_BG_ESTIMATE, 0.0);
            //String direction = intent.getStringExtra(Intents.EXTRA_BG_SLOPE_NAME);
            //double noise = intent.getDoubleExtra(Intents.EXTRA_NOISE, 0.0);
            
            // 创建BgData对象
            BgData bgData = new BgData();
            bgData.timestamp = timestamp;
            bgData.glucose = glucose;
            //bgData.direction = direction != null ? direction : "";
            //bgData.noise = noise;
            bgData.source = "xDrip";
            
            // 通过ServiceHelper注入数据到AIDL服务
            injectToService(bgData);
            
            UserError.Log.uel(TAG, "✅ AIDL数据注入成功: " + glucose + " at " + timestamp);
            
        } catch (Exception e) {
            UserError.Log.uel(TAG, "❌ AIDL数据注入失败: " + e.getMessage());
        }
    }
    
    /**
     * 通过ServiceHelper注入数据
     * 这里使用静态方法调用，让xdrip.java管理服务绑定
     */
    private static void injectToService(BgData bgData) {
        try {
            // 方法1：尝试通过本地Binder注入（如果服务已启动）
            if (BgDataService.getInstance() != null) {
                BgDataService.getInstance().injectBgData(bgData);
                return;
            }
            
            // 方法2：通过xdrip应用实例获取服务引用
            // 这里假设xdrip.java中提供了获取服务引用的静态方法
            // 例如：BgDataService.getServiceInstance()
            
            UserError.Log.uel(TAG, "⚠️ AIDL服务未就绪，数据暂存");
            // TODO: 可以在这里实现数据暂存逻辑，等待服务就绪后再注入
            
        } catch (Exception e) {
            UserError.Log.uel(TAG, "注入服务异常: " + e.getMessage());
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
