package com.eveningoutpost.dexdrip;

import android.os.Parcel;
import android.os.Parcelable;

public class BgData implements Parcelable {
    // 字段必须与 AAPS 完全一致
    public long timestamp;
    public double glucose;
    public double glucoseMmol;
    public double trend;
    public double trendMmol;
    public String direction;
    public String noise;
    public double filtered;
    public double unfiltered;
    public String source;
    public int sensorBatteryLevel;
    public int transmitterBatteryLevel;
    public String rawData;
    
    // 默认构造函数
    public BgData() {
        this.timestamp = System.currentTimeMillis();
        this.source = "xDrip";
        this.noise = "";
        this.direction = "Flat";
        this.rawData = "";
        this.sensorBatteryLevel = 0;
        this.transmitterBatteryLevel = 0;
    }
    
    // 便捷构造函数（兼容现有代码）
    public BgData(double glucoseValue, long timestamp, int trendValue, long sequenceNumber) {
        this();
        this.timestamp = timestamp;
        this.glucose = glucoseValue;
        this.glucoseMmol = glucoseValue / 18.0;  // mg/dL 转 mmol/L
        this.trend = trendValue;
        this.trendMmol = trendValue / 18.0;
        this.direction = convertTrendToString(trendValue);
        this.filtered = glucoseValue;
        this.unfiltered = glucoseValue;
        this.rawData = String.valueOf(sequenceNumber);
    }
    
    // 趋势值转换
    private String convertTrendToString(int trend) {
        // xDrip 趋势值映射
        // 0=无趋势, 1=45度上升, 2=单箭头上升, 3=双箭头上升
        // 4=45度下降, 5=单箭头下降, 6=双箭头下降
        switch(trend) {
            case 1: return "FortyFiveUp";
            case 2: return "SingleUp";
            case 3: return "DoubleUp";
            case 4: return "FortyFiveDown";
            case 5: return "SingleDown";
            case 6: return "DoubleDown";
            case 0:
            default: return "Flat";
        }
    }
    
    // Parcelable 实现 - 必须与 AAPS 完全一致！
    protected BgData(Parcel in) {
        timestamp = in.readLong();
        glucose = in.readDouble();
        glucoseMmol = in.readDouble();
        trend = in.readDouble();
        trendMmol = in.readDouble();
        direction = in.readString();
        noise = in.readString();
        filtered = in.readDouble();
        unfiltered = in.readDouble();
        source = in.readString();
        sensorBatteryLevel = in.readInt();
        transmitterBatteryLevel = in.readInt();
        rawData = in.readString();
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        // 顺序必须与 AAPS 的 read 顺序完全一致！
        dest.writeLong(timestamp);
        dest.writeDouble(glucose);
        dest.writeDouble(glucoseMmol);
        dest.writeDouble(trend);
        dest.writeDouble(trendMmol);
        dest.writeString(direction);
        dest.writeString(noise);
        dest.writeDouble(filtered);
        dest.writeDouble(unfiltered);
        dest.writeString(source);
        dest.writeInt(sensorBatteryLevel);
        dest.writeInt(transmitterBatteryLevel);
        dest.writeString(rawData);
    }
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    public static final Creator<BgData> CREATOR = new Creator<BgData>() {
        @Override
        public BgData createFromParcel(Parcel in) {
            return new BgData(in);
        }
        
        @Override
        public BgData[] newArray(int size) {
            return new BgData[size];
        }
    };
    
    // Getter 方法
    public double getGlucose() { return glucose; }
    public long getTimestamp() { return timestamp; }
    public String getDirection() { return direction; }
    public String getSource() { return source; }
    public String getRawData() { return rawData; }
    
    // 为了兼容原有调用，可以添加这些方法
    public double getGlucoseValue() { return glucose; }
    public int getTrend() { return (int)trend; }
    public long getSequenceNumber() { 
        try {
            return Long.parseLong(rawData);
        } catch (Exception e) {
            return 0;
        }
    }
    
    public double getDelta() { return 0.0; } // 兼容方法，AAPS 不需要 delta
    
    public boolean isReliable() { return true; } // 兼容方法
    
    @Override
    public String toString() {
        return String.format("BgData{glucose=%.1f, time=%d, direction=%s, source=%s}", 
            glucose, timestamp, direction, source);
    }
}
