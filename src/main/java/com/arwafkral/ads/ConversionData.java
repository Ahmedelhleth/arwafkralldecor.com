package com.arwafkral.ads;

import com.google.ads.googleads.v25.enums.ConsentStatusEnum.ConsentStatus;

/**
 * نموذج بيانات التحويل
 * يحتوي على جميع البيانات المطلوبة لتحميل تحويل إلى Google Ads
 */
public class ConversionData {
  private String email;
  private String phone;
  private String conversionDateTime;
  private Double conversionValue;
  private String currencyCode;
  private String orderId;
  private String gclid;
  private ConsentStatus adUserDataConsent;

  // البنّاء الفارغ
  public ConversionData() {
    this.currencyCode = "USD"; // القيمة الافتراضية
  }

  // البنّاء مع المعاملات الأساسية
  public ConversionData(String email, String phone, String conversionDateTime, 
      Double conversionValue) {
    this();
    this.email = email;
    this.phone = phone;
    this.conversionDateTime = conversionDateTime;
    this.conversionValue = conversionValue;
  }

  // Getters و Setters
  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getConversionDateTime() {
    return conversionDateTime;
  }

  public void setConversionDateTime(String conversionDateTime) {
    this.conversionDateTime = conversionDateTime;
  }

  public Double getConversionValue() {
    return conversionValue;
  }

  public void setConversionValue(Double conversionValue) {
    this.conversionValue = conversionValue;
  }

  public String getCurrencyCode() {
    return currencyCode;
  }

  public void setCurrencyCode(String currencyCode) {
    this.currencyCode = currencyCode;
  }

  public String getOrderId() {
    return orderId;
  }

  public void setOrderId(String orderId) {
    this.orderId = orderId;
  }

  public String getGclid() {
    return gclid;
  }

  public void setGclid(String gclid) {
    this.gclid = gclid;
  }

  public ConsentStatus getAdUserDataConsent() {
    return adUserDataConsent;
  }

  public void setAdUserDataConsent(ConsentStatus adUserDataConsent) {
    this.adUserDataConsent = adUserDataConsent;
  }

  @Override
  public String toString() {
    return "ConversionData{" +
        "email='" + email + '\'' +
        ", phone='" + phone + '\'' +
        ", conversionDateTime='" + conversionDateTime + '\'' +
        ", conversionValue=" + conversionValue +
        ", orderId='" + orderId + '\'' +
        '}';
  }
}
