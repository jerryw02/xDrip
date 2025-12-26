package com.eveningoutpost.dexdrip.wearintegration;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * 一个空的 Amazfitservice 类，用于满足其他类的 import 需求，
 * 但不提供任何实际的 Amazfit 通信功能。
 */
public class Amazfitservice extends Service {

    @Override
    public IBinder onBind(Intent intent) {
        // 返回 null，因为这个服务不提供任何绑定接口
        return null;
    }

    // 可以添加一个静态方法来满足其他类可能的调用，但不执行任何操作
    public static void start(String action_text, String alert_name, int snooze_time) {
        // Do nothing
    }
}
