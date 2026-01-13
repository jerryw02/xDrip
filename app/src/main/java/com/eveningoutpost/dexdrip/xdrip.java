package com.eveningoutpost.dexdrip;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import androidx.annotation.StringRes;
import android.util.Log;

import com.eveningoutpost.dexdrip.models.AlertType;
import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.models.Reminder;
import com.eveningoutpost.dexdrip.alert.Poller;
import com.eveningoutpost.dexdrip.services.ActivityRecognizedService;
import com.eveningoutpost.dexdrip.services.BluetoothGlucoseMeter;
import com.eveningoutpost.dexdrip.services.MissedReadingService;
import com.eveningoutpost.dexdrip.services.PlusSyncService;
import com.eveningoutpost.dexdrip.utilitymodels.CollectionServiceStarter;
import com.eveningoutpost.dexdrip.utilitymodels.ColorCache;
import com.eveningoutpost.dexdrip.utilitymodels.IdempotentMigrations;
import com.eveningoutpost.dexdrip.utilitymodels.PlusAsyncExecutor;
import com.eveningoutpost.dexdrip.utilitymodels.Pref;
import com.eveningoutpost.dexdrip.utilitymodels.VersionTracker;
import com.eveningoutpost.dexdrip.calibrations.PluggableCalibration;
import com.eveningoutpost.dexdrip.utils.SentryCrashReporting;
import com.eveningoutpost.dexdrip.utils.jobs.DailyJob;
import com.eveningoutpost.dexdrip.utils.jobs.XDripJobCreator;
import com.eveningoutpost.dexdrip.watch.lefun.LeFunEntry;
import com.eveningoutpost.dexdrip.watch.miband.MiBandEntry;
import com.eveningoutpost.dexdrip.watch.thinjam.BlueJayEntry;
import com.eveningoutpost.dexdrip.services.broadcastservice.BroadcastEntry;
import com.eveningoutpost.dexdrip.webservices.XdripWebService;
import com.evernote.android.job.JobManager;

import net.danlew.android.joda.JodaTimeAndroid;

import java.util.Locale;



/**
 * Created by Emma Black on 3/21/15.
 */

public class xdrip extends Application {

    private static final String TAG = "xdrip.java";
    @SuppressLint("StaticFieldLeak")
    private static volatile Context context;
    private static boolean fabricInited = false;
    private static boolean bfInited = false;
    private static Locale LOCALE;
    public static PlusAsyncExecutor executor;
    public static boolean useBF = false;
    private static Boolean isRunningTestCache;

    // =============== AIDL 服务管理方法（新增） ===============
    
    private IBgDataService bgAidlService = null;
    private ServiceConnection bgServiceConnection = null;
    private BgDataService bgDataService = null;
    private boolean isBgDataServiceBound = false;
    private com.eveningoutpost.dexdrip.utils.AIDLLogger aidlLogger = null;
    
    // =============== AIDL 服务管理方法（新增） ===============
    
    /**
     * 获取应用单例实例
     */
    public static xdrip getInstance() {
        return instance;
    }
    
    /**
     * 初始化AIDL服务
     */
    private void initAIDLService() {
        try {
            aidlLogger = com.eveningoutpost.dexdrip.utils.AIDLLogger.getInstance();
            if (aidlLogger != null) {
                aidlLogger.logServiceStatus("xDrip应用", "启动");
                aidlLogger.step("AIDL服务", "初始化");
            }
            
            // 初始化服务连接
            initBgDataServiceConnection();
            
            // 启动并绑定BgDataService
            startBgDataService();
            
            if (aidlLogger != null) {
                aidlLogger.success("AIDL服务初始化完成");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "AIDL服务初始化失败: " + e.getMessage(), e);
            if (aidlLogger != null) {
                aidlLogger.error("AIDL服务初始化失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 初始化BgDataService连接（兼容AIDL和LocalBinder）
     */
    private void initBgDataServiceConnection() {
        if (aidlLogger != null) {
            aidlLogger.step("连接初始化", "开始 - AIDL兼容版本");
        }
        
        bgServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.i(TAG, "BgDataService连接成功");
                if (aidlLogger != null) {
                    aidlLogger.success("BgDataService连接成功");
                    aidlLogger.debug("接收到的Binder类型: " + service.getClass().getName());
                }
                
                isBgDataServiceBound = true;
                
                try {
                    // ✅ 修复：优先尝试AIDL接口（服务现在返回的是AIDL Binder）
                    bgAidlService = IBgDataService.Stub.asInterface(service);
                    
                    if (bgAidlService != null) {
                        // 成功获取AIDL接口
                        Log.d(TAG, "成功获取AIDL接口");
                        if (aidlLogger != null) {
                            aidlLogger.success("使用AIDL接口成功");
                        }
                        
                        // 测试AIDL连接
                        testAIDLConnection();
                        
                    } else {
                        // AIDL转换失败，尝试LocalBinder（向后兼容）
                        try {
                            BgDataService.LocalBinder binder = (BgDataService.LocalBinder) service;
                            bgDataService = binder.getService();
                            
                            Log.d(TAG, "获取LocalBinder成功: " + (bgDataService != null));
                            if (aidlLogger != null) {
                                aidlLogger.success("使用LocalBinder成功（兼容模式）");
                            }
                            
                        } catch (ClassCastException e) {
                            Log.e(TAG, "无法转换为LocalBinder: " + e.getMessage());
                            if (aidlLogger != null) {
                                aidlLogger.error("Binder类型不匹配: " + e.getMessage());
                            }
                            isBgDataServiceBound = false;
                        }
                    }
                    
                } catch (Exception e) {
                    Log.e(TAG, "处理BgDataService连接失败: " + e.getMessage());
                    if (aidlLogger != null) {
                        aidlLogger.error("处理连接失败: " + e.getMessage());
                    }
                    isBgDataServiceBound = false;
                }
            }
            
            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.w(TAG, "BgDataService连接断开");
                if (aidlLogger != null) {
                    aidlLogger.warn("BgDataService连接断开");
                }
                
                isBgDataServiceBound = false;
                bgDataService = null;
                bgAidlService = null;
                
                // 尝试重新连接
                scheduleReconnection();
            }
        };
        
        if (aidlLogger != null) {
            aidlLogger.step("连接初始化", "完成");
        }
    }
    
    /**
     * 测试AIDL连接
     */
    private void testAIDLConnection() {
        if (bgAidlService != null) {
            try {
                // 简单测试：获取最新数据
                BgData latest = bgAidlService.getLatestBgData();
                Log.d(TAG, "AIDL连接测试成功，最新数据: " + 
                      (latest != null ? latest.getGlucoseValue() : "null"));
                
                if (aidlLogger != null) {
                    aidlLogger.success("AIDL连接测试成功");
                }
                
                // 注册回调，接收数据更新通知
                registerAIDLCallback();
                
            } catch (Exception e) {
                Log.e(TAG, "AIDL连接测试失败: " + e.getMessage());
                if (aidlLogger != null) {
                    aidlLogger.error("AIDL连接测试失败: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * 注册AIDL回调
     */
    private void registerAIDLCallback() {
        if (bgAidlService != null) {
            try {
                bgAidlService.registerCallback(new IBgDataCallback.Stub() {
                    @Override
                    public void onNewBgData(BgData bgData) throws RemoteException {
                        // 当有新的血糖数据时，这里会被调用
                        Log.d(TAG, "AIDL回调收到新数据: " + bgData.getGlucoseValue() + 
                              " @ " + new java.util.Date(bgData.getTimestamp()));
                        
                        if (aidlLogger != null) {
                            aidlLogger.debug("AIDL回调: BG=" + bgData.getGlucoseValue());
                        }
                        
                        // 可以在这里处理数据，比如转发给其他组件或记录日志
                        processIncomingBgDataViaAIDL(bgData);
                    }
                });
                
                Log.d(TAG, "AIDL回调注册成功");
                if (aidlLogger != null) {
                    aidlLogger.success("AIDL回调注册成功");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "注册AIDL回调失败: " + e.getMessage());
                if (aidlLogger != null) {
                    aidlLogger.error("注册AIDL回调失败: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * 处理通过AIDL收到的血糖数据
     */
    private void processIncomingBgDataViaAIDL(BgData bgData) {
        // 这里可以处理接收到的数据，比如：
        // 1. 更新本地缓存
        // 2. 通知UI更新
        // 3. 记录到数据库
        // 4. 转发给其他组件
        
        Log.d(TAG, "处理AIDL血糖数据: " + bgData.getGlucoseValue());
        
        // 示例：将数据注入到BroadcastService的数据流中
        // 这样BroadcastService就不需要自己绑定服务了
        if (bgDataService != null) {
            // 如果有LocalBinder连接，也可以直接使用
            bgDataService.injectBgData(bgData);
        }
    }
    
    /**
     * 启动并绑定BgDataService
     */
    private void startBgDataService() {
        if (aidlLogger != null) {
            aidlLogger.step("启动服务", "开始");
        }
        
        try {
            Intent serviceIntent = new Intent(this, BgDataService.class);
            serviceIntent.putExtra("startup_priority", "high");
            serviceIntent.putExtra("started_by", "xdrip_application");
            // 明确指定AIDL模式，让服务返回AIDL Binder
            serviceIntent.setAction("aidl");
            
            // 启动服务
            Log.d(TAG, "启动BgDataService (AIDL模式)");
            if (aidlLogger != null) {
                aidlLogger.debug("启动BgDataService (AIDL模式)");
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            
            // 绑定服务
            int bindFlags = Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT;
            boolean bound = bindService(serviceIntent, bgServiceConnection, bindFlags);
            
            if (bound) {
                Log.i(TAG, "BgDataService绑定已启动");
                if (aidlLogger != null) {
                    aidlLogger.success("BgDataService绑定已启动");
                }
            } else {
                Log.e(TAG, "BgDataService绑定失败");
                if (aidlLogger != null) {
                    aidlLogger.error("BgDataService绑定失败");
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "启动BgDataService异常: " + e.getMessage(), e);
            if (aidlLogger != null) {
                aidlLogger.error("启动BgDataService异常: " + e.getMessage());
            }
        }
    }
    
    /**
     * 计划重新连接
     */
    private void scheduleReconnection() {
        Log.d(TAG, "计划5秒后重新连接");
        if (aidlLogger != null) {
            aidlLogger.debug("计划5秒后重新连接");
        }
        
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (!isBgDataServiceBound) {
                Log.i(TAG, "尝试重新连接BgDataService");
                if (aidlLogger != null) {
                    aidlLogger.info("尝试重新连接BgDataService");
                }
                startBgDataService();
            }
        }, 5000);
    }
    
    /**
     * 获取BgDataService实例（供BroadcastService使用 - 向后兼容）
     */
    public BgDataService getBgDataService() {
        return bgDataService;
    }
    
    /**
     * 获取AIDL服务实例（新增）
     */
    public IBgDataService getBgAidlService() {
        return bgAidlService;
    }
    
    /**
     * 通过AIDL注入血糖数据（新增）
     */
    public void injectBgDataViaAIDL(BgData data) {
        if (bgAidlService != null && data != null) {
            try {
                // 通过AIDL服务注入数据
                // 注意：IBgDataService接口需要添加injectBgData方法
                // 或者通过BgDataService的静态方法
                Log.d(TAG, "准备通过AIDL注入数据: " + data.getGlucoseValue());
                
                // 如果IBgDataService接口有inject方法：
                // bgAidlService.injectBgData(data);
                
                // 或者通过静态方法（如果实现了的话）：
                BgDataService.injectData(data);
                
            } catch (Exception e) {
                Log.e(TAG, "通过AIDL注入数据失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 检查BgDataService是否已绑定
     */
    public boolean isBgDataServiceBound() {
        return isBgDataServiceBound;
    }
    
    /**
     * 应用终止时清理资源
     */
    @Override
    public void onTerminate() {
        // 清理AIDL连接
        if (isBgDataServiceBound && bgServiceConnection != null) {
            try {
                // 注销AIDL回调
                if (bgAidlService != null) {
                    try {
                        // 需要传递一个callback实例，这里使用null或创建一个临时实例
                        bgAidlService.unregisterCallback(null);
                    } catch (Exception e) {
                        // 忽略注销错误
                    }
                }
                
                unbindService(bgServiceConnection);
                Log.d(TAG, "成功解绑BgDataService");
                if (aidlLogger != null) {
                    aidlLogger.debug("成功解绑BgDataService");
                }
            } catch (Exception e) {
                Log.e(TAG, "解绑BgDataService异常: " + e.getMessage());
                if (aidlLogger != null) {
                    aidlLogger.error("解绑BgDataService异常: " + e.getMessage());
                }
            }
        }
        
        super.onTerminate();
    }
    
    // 新增字段（需要在类的字段定义区域添加）
    private IBgDataService bgAidlService = null;
    // =============== AIDL 服务管理方法结束 ===============
    

    public static synchronized boolean isRunningTest() {
        if (null == isRunningTestCache) {
            boolean test_framework;
            if ("robolectric".equals(Build.FINGERPRINT)) {
                isRunningTestCache = true;
            } else {
                try {
                    Class.forName("android.support.test.espresso.Espresso");
                    test_framework = true;
                } catch (ClassNotFoundException e) {
                    test_framework = false;
                }
                isRunningTestCache = test_framework;
            }
        }
        return isRunningTestCache;
    }

    public synchronized static void initBF() {
        try {
            if (PreferenceManager.getDefaultSharedPreferences(xdrip.context).getBoolean("enable_bugfender", false)) {
                new Thread() {
                    @Override
                    public void run() {
                        String app_id = PreferenceManager.getDefaultSharedPreferences(xdrip.context).getString("bugfender_appid", "").trim();
                        if (!useBF && (app_id.length() > 10)) {
                            if (!bfInited) {
                                //Bugfender.init(xdrip.context, app_id, BuildConfig.DEBUG);
                                bfInited = true;
                            }
                            useBF = true;
                        }
                    }
                }.start();
            } else {
                useBF = false;
            }
        } catch (Exception e) {
            //
        }
    }


    public static Context getAppContext() {
        return xdrip.context;
    }

    public static boolean checkAppContext(Context context) {
        if (getAppContext() == null) {
            xdrip.context = context;
            return false;
        } else {
            return true;
        }
    }

    public static void checkForcedEnglish(Context context) {
        if (Pref.getBoolean("force_english", false)) {
            final String forced_language = Pref.getString("forced_language", "en");
            final String current_language = Locale.getDefault().getLanguage();
            if (!current_language.equals(forced_language)) {
                Log.i(TAG, "Forcing locale: " + forced_language + " was: " + current_language);
                LOCALE = new Locale(forced_language, "", "");
                Locale.setDefault(LOCALE);
                final Configuration config = context.getResources().getConfiguration();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    config.setLocale(LOCALE);
                } else {
                    config.locale = LOCALE;
                }
                try {
                    ((Application) context).getBaseContext().getResources().updateConfiguration(config, ((Application) context).getBaseContext().getResources().getDisplayMetrics());
                } catch (ClassCastException e) {
                    //
                }
                try {
                    context.getResources().updateConfiguration(config, context.getResources().getDisplayMetrics());
                } catch (ClassCastException e) {
                    //

                }
            }
            Log.d(TAG, "Already set to locale: " + forced_language);
        }
    }

    // force language on oreo activities
    public static Context getLangContext(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && Pref.getBooleanDefaultFalse("force_english")) {
                final String forced_language = Pref.getString("forced_language", "en");
                final Configuration config = context.getResources().getConfiguration();

                if (LOCALE == null) LOCALE = new Locale(forced_language);
                Locale.setDefault(LOCALE);
                config.setLocale(LOCALE);
                context = context.createConfigurationContext(config);
                //Log.d(TAG, "Sending language context for: " + LOCALE);
                return new ContextWrapper(context);
            } else {
                return context;
            }
        } catch (Exception e) {
            Log.e(TAG, "Got exception in getLangContext: " + e);
            return context;
        }
    }


    public static String gs(@StringRes final int id) {
        return getAppContext().getString(id);
    }

    public static String gs(@StringRes final int id, String... args) {
        return getAppContext().getString(id, (Object[]) args);
    }

}
