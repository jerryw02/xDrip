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

    // 提供一个接受单个 String 参数的 start 方法，以匹配调用它的代码
    public static void start(String action_text) {
        // Do nothing
    }

    // 也保留原始的 3 参数方法，以防有其他地方调用
    public static void start(String action_text, String alert_name, int snooze_time) {
        // Do nothing
    }
}
