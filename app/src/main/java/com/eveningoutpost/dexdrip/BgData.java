package com.eveningoutpost.dexdrip;  // 保持包名不变

import android.os.Parcel;
import android.os.Parcelable;

public class BgData implements Parcelable {
    // 字段名称和类型必须与 AAPS 完全一致
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
        // xDrip 使用的趋势值映射
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
    
    // Getter 方法（可选，但建议添加）
    public double getGlucose() { return glucose; }
    public long getTimestamp() { return timestamp; }
    public String getDirection() { return direction; }
    public String getSource() { return source; }
    
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
    
    @Override
    public String toString() {
        return String.format("BgData{glucose=%.1f, time=%d, direction=%s, source=%s}", 
            glucose, timestamp, direction, source);
    }
}        sequenceNumber = in.readLong();
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeDouble(glucoseValue);
        dest.writeLong(timestamp);
        dest.writeInt(trend);
        dest.writeDouble(delta);
        dest.writeString(source);
        dest.writeByte((byte) (isReliable ? 1 : 0));
        dest.writeLong(sequenceNumber);
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
    
    // Getters and Setters
    public double getGlucoseValue() { return glucoseValue; }
    public void setGlucoseValue(double glucoseValue) { this.glucoseValue = glucoseValue; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public int getTrend() { return trend; }
    public void setTrend(int trend) { this.trend = trend; }
    
    public double getDelta() { return delta; }
    public void setDelta(double delta) { this.delta = delta; }
    
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    
    public boolean isReliable() { return isReliable; }
    public void setReliable(boolean reliable) { isReliable = reliable; }
    
    public long getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(long sequenceNumber) { this.sequenceNumber = sequenceNumber; }
    
    @Override
    public String toString() {
        return String.format("BgData{glucose=%.1f, time=%d, trend=%d, seq=%d}", 
            glucoseValue, timestamp, trend, sequenceNumber);
    }
}
