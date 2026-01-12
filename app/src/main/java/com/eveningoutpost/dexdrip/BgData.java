package com.eveningoutpost.dexdrip;

import android.os.Parcel;
import android.os.Parcelable;

public class BgData implements Parcelable {
    private double glucoseValue;
    private long timestamp;
    private int trend;
    private double delta;
    private String source;
    private boolean isReliable;
    
    // 用于去重的序列号
    private long sequenceNumber;
    
    public BgData() {
        this.timestamp = System.currentTimeMillis();
        this.isReliable = true;
    }
    
    public BgData(double glucoseValue, long timestamp, int trend, double delta) {
        this.glucoseValue = glucoseValue;
        this.timestamp = timestamp;
        this.trend = trend;
        this.delta = delta;
        this.isReliable = true;
    }
    
    protected BgData(Parcel in) {
        glucoseValue = in.readDouble();
        timestamp = in.readLong();
        trend = in.readInt();
        delta = in.readDouble();
        source = in.readString();
        isReliable = in.readByte() != 0;
        sequenceNumber = in.readLong();
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
