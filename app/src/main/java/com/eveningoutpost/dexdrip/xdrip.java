

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
    
    // =============== AIDL 服务管理变量（新增） ===============
    private static xdrip instance;
    private BgDataService bgDataService;
    private boolean isBgDataServiceBound = false;
    private ServiceConnection bgServiceConnection;
    private com.eveningoutpost.dexdrip.utils.AIDLLogger aidlLogger;
    // ====================== AIDL 结束 ======================

    public static void setContext(final Context context) {
        if (context == null) return;
        if (xdrip.context == null) {
            xdrip.context = context.getApplicationContext();
        }
    }

    public static void setContextAlways(final Context context) {
        if (context == null) return;
        Log.d(TAG, "Set context: " + context.getResources().getConfiguration().getLocales().get(0).getLanguage()
                + " was: " + xdrip.context.getResources().getConfiguration().getLocales().get(0).getLanguage());
        xdrip.context = context;
    }


    @Override
    public void onCreate() {
        xdrip.context = getApplicationContext();
        instance = this;
        super.onCreate();
        
        // =============== AIDL 服务初始化（新增） ===============
        // ✅ 已经有的代码 - 这是正确的
        initAIDLService();  // 这会调用 startBgDataService()
        // ====================== AIDL 结束 =====================
        
        JodaTimeAndroid.init(this);
        try {
            if (PreferenceManager.getDefaultSharedPreferences(xdrip.context).getBoolean("enable_crashlytics", true)) {
                SentryCrashReporting.start(this);
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
        executor = new PlusAsyncExecutor();

        IdempotentMigrations.migrateOOP2CalibrationPreferences(); // needs to run before preferences get defaults

        PreferenceManager.setDefaultValues(this, R.xml.pref_general, true);
        PreferenceManager.setDefaultValues(this, R.xml.pref_data_sync, true);
        PreferenceManager.setDefaultValues(this, R.xml.pref_advanced_settings, true);
        PreferenceManager.setDefaultValues(this, R.xml.pref_notifications, true);
        PreferenceManager.setDefaultValues(this, R.xml.pref_data_source, true);
        PreferenceManager.setDefaultValues(this, R.xml.xdrip_plus_defaults, true);
        PreferenceManager.setDefaultValues(this, R.xml.xdrip_plus_prefs, true);
        ColorCache.setDefaultsLoaded();

        checkForcedEnglish(xdrip.context);

        JoH.ratelimit("policy-never", 3600); // don't on first load
        new IdempotentMigrations(getApplicationContext()).performAll();


        JobManager.create(this).addJobCreator(new XDripJobCreator());
        DailyJob.schedule();
        //SyncService.startSyncServiceSoon();

        if (!isRunningTest()) {
            MissedReadingService.delayedLaunch();
            NFCReaderX.handleHomeScreenScanPreference(getApplicationContext());
            AlertType.fromSettings(getApplicationContext());
            //new CollectionServiceStarter(getApplicationContext()).start(getApplicationContext());
            CollectionServiceStarter.restartCollectionServiceBackground();
            PlusSyncService.startSyncService(context, "xdrip.java");
            if (Pref.getBoolean("motion_tracking_enabled", false)) {
                ActivityRecognizedService.startActivityRecogniser(getApplicationContext());
            }
            BluetoothGlucoseMeter.startIfEnabled();
            LeFunEntry.initialStartIfEnabled();
            MiBandEntry.initialStartIfEnabled();
            BroadcastEntry.initialStartIfEnabled();
            BlueJayEntry.initialStartIfEnabled();
            XdripWebService.immortality();
            VersionTracker.updateDevice();

        } else {
            Log.d(TAG, "Detected running test mode, holding back on background processes");
        }
        Reminder.firstInit(xdrip.getAppContext());
        PluggableCalibration.invalidateCache();
        Poller.init();
    }
    
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
            
            // === 关键改进：立即启动服务，不等待 ===
            // 原来可能有的延迟启动逻辑改为立即启动
        
            // 初始化服务连接
            initBgDataServiceConnection();
            
            // 启动并绑定BgDataService
            // ✅ 立即启动并绑定服务（不要延迟）
            startBgDataServiceImmediately();
            
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

/////////////////
/**
 * 初始化BgDataService连接
 */
private void initBgDataServiceConnection() {
    if (aidlLogger != null) {
        aidlLogger.step("连接初始化", "开始");
    }
    
    bgServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "BgDataService连接成功");
            if (aidlLogger != null) {
                aidlLogger.success("BgDataService连接成功");
            }
            
            isBgDataServiceBound = true;
            
            try {
                // ============ 关键修改：移除LocalBinder强制转换 ============
                // 原来错误代码：
                // BgDataService.LocalBinder binder = (BgDataService.LocalBinder) service;
                // bgDataService = binder.getService();
                
                // 新代码：直接使用BgDataService.getInstance()
                bgDataService = BgDataService.getInstance();
                
                Log.d(TAG, "获取BgDataService实例: " + (bgDataService != null));
                if (aidlLogger != null) {
                    aidlLogger.debug("获取BgDataService实例: " + (bgDataService != null));
                }
                
                // 可选：验证服务是否已启动
                if (bgDataService != null) {
                    if (aidlLogger != null) {
                        aidlLogger.success("BgDataService已成功获取");
                    }
                    
                    // 发送测试数据验证
                    bgDataService.sendTestData();
                } else {
                    if (aidlLogger != null) {
                        aidlLogger.warn("BgDataService实例为null，可能需要重新绑定");
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "获取BgDataService失败: " + e.getMessage(), e);
                if (aidlLogger != null) {
                    aidlLogger.error("获取BgDataService失败: " + e.getMessage());
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
            
            // 尝试重新连接
            scheduleReconnection();
        }
    };
    
    if (aidlLogger != null) {
        aidlLogger.step("连接初始化", "完成");
    }
}
/////////////////  

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
        
        // ============ 关键修改：移除不必要的标记 ============
        // 现在onBind总是返回AIDL Binder，不需要特殊标记
        // serviceIntent.setAction("local"); // 删除这行
        // serviceIntent.putExtra("internal_call", true); // 删除这行
        // serviceIntent.putExtra("caller", "xdrip_main_app"); // 删除这行
        
        // 只需要设置包名
        serviceIntent.setPackage(getPackageName());
        
        Log.d(TAG, "启动BgDataService");
        if (aidlLogger != null) {
            aidlLogger.debug("启动BgDataService");
        }
        
        // 启动服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        
        // ============ 重要：简化绑定标志 ============
        // 不需要复杂标志，只需自动创建和重要绑定
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
    
/////////////////

/////////////////
/**
 * 立即启动并绑定服务（改进版）
 */
private void startBgDataServiceImmediately() {
    if (aidlLogger != null) {
        aidlLogger.step("立即启动服务", "开始");
    }
    
    try {
        // 创建明确的启动Intent
        Intent serviceIntent = new Intent(this, BgDataService.class);
        serviceIntent.setPackage(getPackageName());
        
        // ⚠️ 关键：添加明确的启动标志
        serviceIntent.putExtra("startup_priority", "highest");
        serviceIntent.putExtra("startup_time", System.currentTimeMillis());
        serviceIntent.putExtra("started_by", "xdrip_onCreate");
        
        UserError.Log.uel(TAG, "立即启动BgDataService，优先级：highest");
        
        // 1. 先启动服务（确保实例创建）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        
        // 2. 立即绑定（确保获得实例引用）
        int bindFlags = Context.BIND_AUTO_CREATE | 
                       Context.BIND_IMPORTANT | 
                       Context.BIND_ABOVE_CLIENT;
        
        boolean bound = bindService(serviceIntent, bgServiceConnection, bindFlags);
        
        if (bound) {
            Log.i(TAG, "BgDataService立即绑定成功");
            if (aidlLogger != null) {
                aidlLogger.success("BgDataService立即绑定成功");
            }
        } else {
            Log.e(TAG, "BgDataService立即绑定失败");
            if (aidlLogger != null) {
                aidlLogger.error("BgDataService立即绑定失败");
            }
            
            // 3. 失败后重试
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                UserError.Log.uel(TAG, "重试绑定BgDataService");
                bindService(serviceIntent, bgServiceConnection, bindFlags);
            }, 1000);
        }
        
    } catch (Exception e) {
        Log.e(TAG, "立即启动服务异常: " + e.getMessage(), e);
        if (aidlLogger != null) {
            aidlLogger.error("立即启动服务异常: " + e.getMessage());
        }
    }
}
/////////////////    
  
    
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

/////////////////
/**
 * 检查并确保服务运行
 */
public void ensureBgDataServiceRunning() {
    if (!isBgDataServiceBound || bgDataService == null) {
        Log.w(TAG, "BgDataService未运行，重新启动");
        if (aidlLogger != null) {
            aidlLogger.warn("BgDataService未运行，重新启动");
        }
        startBgDataService();
    }
}

/**
 * 手动注入测试数据（用于调试）
 */
public void injectTestBgData() {
    ensureBgDataServiceRunning();
    
    if (bgDataService != null) {
        try {
            bgDataService.sendTestData();
            Log.d(TAG, "测试数据注入成功");
            if (aidlLogger != null) {
                aidlLogger.success("测试数据注入成功");
            }
        } catch (Exception e) {
            Log.e(TAG, "测试数据注入失败: " + e.getMessage());
            if (aidlLogger != null) {
                aidlLogger.error("测试数据注入失败: " + e.getMessage());
            }
        }
    } else {
        Log.e(TAG, "无法注入测试数据：bgDataService为null");
        if (aidlLogger != null) {
            aidlLogger.error("无法注入测试数据：bgDataService为null");
        }
    }
}

/**
 * 获取服务实例的公共方法（供其他模块使用）
 */
public BgDataService getBgDataService() {
    ensureBgDataServiceRunning();
    return bgDataService;
}
/////////////////    
    
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
