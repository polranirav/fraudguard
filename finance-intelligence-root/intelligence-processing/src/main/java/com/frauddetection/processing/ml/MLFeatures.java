package com.frauddetection.processing.ml;

/**
 * Features extracted from transaction for ML model inference.
 * 
 * These features match the training data structure and are used
 * to call the ML inference service.
 */
public class MLFeatures {
    private double transactionAmount;
    private int transactionCount1Min;
    private int transactionCount1Hour;
    private double totalAmount1Min;
    private double totalAmount1Hour;
    private double distanceFromHomeKm;
    private double timeSinceLastTxnMinutes;
    private double velocityKmh;
    private double merchantReputationScore;
    private int customerAgeDays;
    private boolean isKnownDevice;
    private boolean isVpnDetected;
    private int deviceUsageCount;
    private int hourOfDay;
    private int dayOfWeek;
    private int merchantCategoryRisk;
    
    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private MLFeatures features = new MLFeatures();
        
        public Builder transactionAmount(double amount) {
            features.transactionAmount = amount;
            return this;
        }
        
        public Builder transactionCount1Min(int count) {
            features.transactionCount1Min = count;
            return this;
        }
        
        public Builder transactionCount1Hour(int count) {
            features.transactionCount1Hour = count;
            return this;
        }
        
        public Builder totalAmount1Min(double amount) {
            features.totalAmount1Min = amount;
            return this;
        }
        
        public Builder totalAmount1Hour(double amount) {
            features.totalAmount1Hour = amount;
            return this;
        }
        
        public Builder distanceFromHomeKm(double distance) {
            features.distanceFromHomeKm = distance;
            return this;
        }
        
        public Builder timeSinceLastTxnMinutes(double minutes) {
            features.timeSinceLastTxnMinutes = minutes;
            return this;
        }
        
        public Builder velocityKmh(double velocity) {
            features.velocityKmh = velocity;
            return this;
        }
        
        public Builder merchantReputationScore(double score) {
            features.merchantReputationScore = score;
            return this;
        }
        
        public Builder customerAgeDays(int days) {
            features.customerAgeDays = days;
            return this;
        }
        
        public Builder isKnownDevice(boolean known) {
            features.isKnownDevice = known;
            return this;
        }
        
        public Builder isVpnDetected(boolean vpn) {
            features.isVpnDetected = vpn;
            return this;
        }
        
        public Builder deviceUsageCount(int count) {
            features.deviceUsageCount = count;
            return this;
        }
        
        public Builder hourOfDay(int hour) {
            features.hourOfDay = hour;
            return this;
        }
        
        public Builder dayOfWeek(int day) {
            features.dayOfWeek = day;
            return this;
        }
        
        public Builder merchantCategoryRisk(int risk) {
            features.merchantCategoryRisk = risk;
            return this;
        }
        
        public MLFeatures build() {
            return features;
        }
    }
    
    // Getters
    public double getTransactionAmount() { return transactionAmount; }
    public int getTransactionCount1Min() { return transactionCount1Min; }
    public int getTransactionCount1Hour() { return transactionCount1Hour; }
    public double getTotalAmount1Min() { return totalAmount1Min; }
    public double getTotalAmount1Hour() { return totalAmount1Hour; }
    public double getDistanceFromHomeKm() { return distanceFromHomeKm; }
    public double getTimeSinceLastTxnMinutes() { return timeSinceLastTxnMinutes; }
    public double getVelocityKmh() { return velocityKmh; }
    public double getMerchantReputationScore() { return merchantReputationScore; }
    public int getCustomerAgeDays() { return customerAgeDays; }
    public boolean getIsKnownDevice() { return isKnownDevice; }
    public boolean getIsVpnDetected() { return isVpnDetected; }
    public int getDeviceUsageCount() { return deviceUsageCount; }
    public int getHourOfDay() { return hourOfDay; }
    public int getDayOfWeek() { return dayOfWeek; }
    public int getMerchantCategoryRisk() { return merchantCategoryRisk; }
}
