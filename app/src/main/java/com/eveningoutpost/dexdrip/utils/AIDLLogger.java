package com.eveningoutpost.dexdrip.utils;

import com.eveningoutpost.dexdrip.BgData;
import com.eveningoutpost.dexdrip.models.UserError;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 简化版AIDL日志工具
 * 只输出到xDrip事件日志
 */
public class AIDLLogger {
    private static final String TAG = "AIDL-Logger";
    private static final String EVENT_LOG_TAG = "AIDL";
    private static final SimpleDateFormat TIME_FORMAT = 
        new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    
    private static AIDLLogger instance;
    private long sequence = 0;
    
    public static synchronized AIDLLogger getInstance() {
        if (instance == null) {
            instance = new AIDLLogger();
        }
        return instance;
    }
    
    private AIDLLogger() {
        // 初始化
    }
    
    /**
     * 生成带时间戳和序列号的消息
     */
    private String formatMessage(String level, String message) {
        String timestamp = TIME_FORMAT.format(new Date());
        long seq = ++sequence;
        return String.format(Locale.US, "[%s] [%s] [SEQ:%d] %s", 
            timestamp, level, seq, message);
    }
    
    /**
     * 记录INFO级别日志
     */
    public void info(String message) {
        String formatted = formatMessage("INFO", message);
        UserError.Log.uel(EVENT_LOG_TAG, formatted);
    }
    
    /**
     * 记录DEBUG级别日志
     */
    public void debug(String message) {
        String formatted = formatMessage("DEBUG", message);
        UserError.Log.uel(EVENT_LOG_TAG, formatted);
    }
    
    /**
     * 记录WARN级别日志
     */
    public void warn(String message) {
        String formatted = formatMessage("WARN", message);
        UserError.Log.uel(EVENT_LOG_TAG, formatted);
    }
    
    /**
     * 记录ERROR级别日志
     */
    public void error(String message) {
        String formatted = formatMessage("ERROR", message);
        UserError.Log.uel(EVENT_LOG_TAG, formatted);
    }
    
    /**
     * 记录SUCCESS级别日志
     */
    public void success(String message) {
        String formatted = formatMessage("SUCCESS", message);
        UserError.Log.uel(EVENT_LOG_TAG, formatted); // 使用高亮日志
    }
    
    /**
     * 记录关键步骤
     */
    public void step(String stepName, String status, Object... details) {
        StringBuilder detailStr = new StringBuilder();
        if (details != null && details.length > 0) {
            detailStr.append(" (");
            for (int i = 0; i < details.length; i++) {
                if (i > 0) detailStr.append(", ");
                detailStr.append(String.valueOf(details[i]));
            }
            detailStr.append(")");
        }
        
        String message = String.format("%s: %s%s", stepName, status, detailStr.toString());
        info(message);
    }
    
    /**
     * 记录数据发送
     */
    public void logDataSend(String method, BgData data, boolean success) {
        if (data == null) {
            error("尝试发送空数据 via " + method);
            return;
        }
        
        String status = success ? "成功" : "失败";
        String message = String.format(Locale.US, 
            "发送数据 %s | BG:%.1f 时间:%d 序列:%d via %s",
            status, data.getGlucoseValue(), data.getTimestamp(), 
            data.getSequenceNumber(), method);
        
        if (success) {
            success(message);
        } else {
            error(message);
        }
    }
    
    /**
     * 记录服务状态
     */
    public void logServiceStatus(String serviceName, String status) {
        info(String.format("服务状态: %s - %s", serviceName, status));
    }
}
