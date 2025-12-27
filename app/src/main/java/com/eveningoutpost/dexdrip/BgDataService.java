package com.eveningoutpost.dexdrip; // 请替换为 xDrip 的实际包名

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;

import java.util.List;

public class BgDataService extends Service {

    private static final String TAG = "xDrip_BgService";
    private final IBgDataService.Stub mBinder = new IBgDataService.Stub() {
        @Override
        public void updateBgData(BgData data) throws RemoteException {
            Log.d(TAG, "收到 updateBgData 请求，推送数据到 AAPS: " + data.value);
            // 遍历所有已注册的回调，通知它们数据更新了
            final int N = mCallbacks.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    mCallbacks.getBroadcastItem(i).onNewBgData(data);
                } catch (RemoteException e) {
                    // 客户端死亡，从列表中移除
                    Log.e(TAG, "回调失败，客户端可能已死亡", e);
                }
            }
            mCallbacks.finishBroadcast();
        }

        @Override
        public void registerCallback(IBgDataCallback callback) throws RemoteException {
            Log.d(TAG, "AAPS 注册回调");
            if (callback != null) {
                mCallbacks.register(callback);
            }
        }

        @Override
        public void unregisterCallback(IBgDataCallback callback) throws RemoteException {
            Log.d(TAG, "AAPS 注销回调");
            if (callback != null) {
                mCallbacks.unregister(callback);
            }
        }
    };

    // 使用 RemoteCallbackList 管理回调列表，自动处理客户端死亡
    private final RemoteCallbackList<IBgDataCallback> mCallbacks = new RemoteCallbackList<>();

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "AAPS 绑定 xDrip 服务");
        return mBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 服务销毁时，注销所有回调
        mCallbacks.kill();
    }
}
