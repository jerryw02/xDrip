package com.eveningoutpost.dexdrip;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.utilitymodels.SendXdripBroadcast;

/**
 * AIDL服务生命周期管理助手
 * 在xdrip.java中初始化和管理
 */
public class ServiceHelper {
    
    private static final String TAG = "ServiceHelper";
    private static BgDataService bgDataService;
    private static boolean isBound = false;
    
    // 服务连接
    private static ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                // 智能判断返回的Binder类型
                if (service instanceof BgDataService.LocalBinder) {
                    // 内部调用，获取LocalBinder
                    BgDataService.LocalBinder binder = (BgDataService.LocalBinder) service;
                    bgDataService = binder.getService();
                    UserError.Log.i(TAG, "✅ 获取LocalBinder，服务连接成功");
                } else if (service instanceof IBgDataService.Stub) {
                    // 外部调用，但我们不需要AIDL Stub
                    UserError.Log.w(TAG, "⚠️ 收到AIDL Stub，尝试重新绑定内部服务");
                    rebindWithInternalIntent();
                    return;
                }
                
                isBound = true;
                // 通知SendXdripBroadcast服务已就绪
                SendXdripBroadcast.onServiceReady(bgDataService);
                
            } catch (Exception e) {
                UserError.Log.e(TAG, "服务连接异常: " + e.getMessage());
            }
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            bgDataService = null;
            isBound = false;
            UserError.Log.w(TAG, "服务连接断开");
        }
    };
    
    /**
     * 初始化并绑定AIDL服务
     * 在xdrip.java的onCreate()中调用
     */
    public static void initialize(Context context) {
        try {
            UserError.Log.i(TAG, "初始化AIDL服务管理");
            
            // 确保服务已启动
            Intent serviceIntent = new Intent(context, BgDataService.class);
            serviceIntent.setPackage(context.getPackageName());
            serviceIntent.setAction("internal"); // 标记为内部调用
            
            // 启动服务
            context.startService(serviceIntent);
            
            // 绑定服务
            Intent bindIntent = new Intent(context, BgDataService.class);
            bindIntent.setPackage(context.getPackageName());
            bindIntent.setAction("internal"); // 标记为内部调用
            
            boolean bindResult = context.bindService(
                bindIntent, 
                serviceConnection, 
                Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT
            );
            
            UserError.Log.i(TAG, "服务绑定结果: " + (bindResult ? "成功" : "失败"));
            
        } catch (Exception e) {
            UserError.Log.e(TAG, "服务初始化失败: " + e.getMessage());
        }
    }
    
    /**
     * 清理服务资源
     * 在xdrip.java的onDestroy()中调用
     */
    public static void cleanup(Context context) {
        try {
            if (isBound && context != null) {
                context.unbindService(serviceConnection);
                isBound = false;
                bgDataService = null;
                UserError.Log.i(TAG, "服务清理完成");
            }
        } catch (Exception e) {
            UserError.Log.e(TAG, "服务清理异常: " + e.getMessage());
        }
    }
    
    /**
     * 重新绑定内部服务（当意外获取到AIDL Stub时使用）
     */
    private static void rebindWithInternalIntent(Context context) {
        try {
            // 先解绑
            if (isBound && context != null) {
                context.unbindService(serviceConnection);
            }
            
            // 使用显式内部标记重新绑定
            Intent internalIntent = new Intent(context, BgDataService.class);
            internalIntent.setPackage(context.getPackageName());
            internalIntent.setAction("xdrip.internal");
            internalIntent.putExtra("caller", "xdrip_main");
            
            context.bindService(
                internalIntent,
                serviceConnection,
                Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT
            );
            
            UserError.Log.i(TAG, "重新绑定内部服务");
            
        } catch (Exception e) {
            UserError.Log.e(TAG, "重新绑定失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取服务实例（供SendXdripBroadcast使用）
     */
    public static BgDataService getService() {
        return bgDataService;
    }
    
    /**
     * 检查服务是否绑定
     */
    public static boolean isServiceBound() {
        return isBound && bgDataService != null;
    }
}
