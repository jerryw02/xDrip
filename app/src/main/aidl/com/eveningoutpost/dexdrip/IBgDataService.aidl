package com.eveningoutpost.dexdrip;

import com.eveningoutpost.dexdrip.BgData;
import com.eveningoutpost.dexdrip.IBgDataCallback;

interface IBgDataService {
    BgData getLatestBgData();
    void registerCallback(IBgDataCallback callback);
    void unregisterCallback(IBgDataCallback callback);
}
