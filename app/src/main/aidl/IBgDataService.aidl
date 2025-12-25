// IBgDataService.aidl
package com.your.xdrip.package; // 请替换为 xDrip 的实际包名

import com.your.xdrip.package.IBgDataCallback; // 导入回调接口

interface IBgDataService {
    void updateBgData(in BgData data); // xDrip 调用此方法推送数据给 AAPS
    void registerCallback(IBgDataCallback callback); // AAPS 注册回调
    void unregisterCallback(IBgDataCallback callback); // AAPS 注销回调
}
