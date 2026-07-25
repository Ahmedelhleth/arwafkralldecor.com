package com.arwafkral.ads;

import com.google.ads.googleads.lib.GoogleAdsClient;
import com.google.ads.googleads.v25.common.Consent;
import com.google.ads.googleads.v25.common.UserIdentifier;
import com.google.ads.googleads.v25.enums.ConsentStatusEnum.ConsentStatus;
import com.google.ads.googleads.v25.enums.UserIdentifierSourceEnum.UserIdentifierSource;
import com.google.ads.googleads.v25.errors.GoogleAdsError;
import com.google.ads.googleads.v25.errors.GoogleAdsException;
import com.google.ads.googleads.v25.services.ClickConversion;
import com.google.ads.googleads.v25.services.ClickConversionResult;
import com.google.ads.googleads.v25.services.ConversionUploadServiceClient;
import com.google.ads.googleads.v25.services.UploadClickConversionsRequest;
import com.google.ads.googleads.v25.services.UploadClickConversionsResponse;
import com.google.ads.googleads.v25.utils.ResourceNames;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * محمّل تحويلات Google Ads المحسّن
 * يدعم تحميل تحويلات متعددة دفعة واحدة مع معالجة أخطاء احترافية
 */
public class GoogleAdsConversionUploader {
  private static final Logger logger = Logger.getLogger(GoogleAdsConversionUploader.class.getName());
  private static final int BATCH_SIZE = 2000; // الحد الأقصى للتحويلات في request واحد
  
  private final GoogleAdsClient googleAdsClient;
  private final long customerId;
  private final long conversionActionId;

  /**
   * إنشاء محمّل التحويلات
   * 
   * @param googleAdsClient عميل Google Ads API
   * @param customerId معرّف حساب Google Ads
   * @param conversionActionId معرّف إجراء التحويل
   */
  public GoogleAdsConversionUploader(
      GoogleAdsClient googleAdsClient, long customerId, long conversionActionId) {
    this.googleAdsClient = googleAdsClient;
    this.customerId = customerId;
    this.conversionActionId = conversionActionId;
  }

  /**
   * تحميل قائمة من التحويلات دفعة واحدة
   * 
   * @param conversions قائمة بيانات التحويلات
   * @return نتائج التحميل
   */
  public ConversionUploadResult uploadConversions(List<ConversionData> conversions) {
    if (conversions == null || conversions.isEmpty()) {
      logger.warning("لا توجد تحويلات للتحميل");
      return new ConversionUploadResult(0, 0);
    }

    ConversionUploadResult totalResult = new ConversionUploadResult(0, 0);
    
    // تقسيم التحويلات إلى دفعات
    for (int i = 0; i < conversions.size(); i += BATCH_SIZE) {
      int end = Math.min(i + BATCH_SIZE, conversions.size());
      List<ConversionData> batch = conversions.subList(i, end);
      
      logger.info("تحميل دفعة: " + (i / BATCH_SIZE + 1) + " (" + batch.size() + " تحويل)");
      
      try {
        ConversionUploadResult batchResult = uploadBatch(batch);
        totalResult.addSuccessful(batchResult.getSuccessful());
        totalResult.addFailed(batchResult.getFailed());
      } catch (Exception e) {
        logger.log(Level.SEVERE, "خطأ في تحميل الدفعة", e);
        totalResult.addFailed(batch.size());
      }
    }

    return totalResult;
  }

  /**
   * تحميل دفعة واحدة من التحويلات
   */
  private ConversionUploadResult uploadBatch(List<ConversionData> conversions)
      throws UnsupportedEncodingException, NoSuchAlgorithmException {
    
    List<ClickConversion> clickConversions = new ArrayList<>();
    MessageDigest sha256Digest = MessageDigest.getInstance("SHA-256");

    // تحويل بيانات المستخدم إلى ClickConversion objects
    for (ConversionData data : conversions) {
      try {
        ClickConversion conversion = buildClickConversion(data, sha256Digest);
        clickConversions.add(conversion);
      } catch (Exception e) {
        logger.log(Level.WARNING, "خطأ في معالجة التحويل: " + data.getEmail(), e);
      }
    }

    if (clickConversions.isEmpty()) {
      return new ConversionUploadResult(0, conversions.size());
    }

    try (ConversionUploadServiceClient client = 
        googleAdsClient.getLatestVersion().createConversionUploadServiceClient()) {
      
      UploadClickConversionsResponse response = client.uploadClickConversions(
          UploadClickConversionsRequest.newBuilder()
              .setCustomerId(Long.toString(customerId))
              .addAllConversions(clickConversions)
              .setPartialFailure(true)
              .build());

      return processResponse(response, clickConversions.size());
      
    } catch (GoogleAdsException gae) {
      logger.log(Level.SEVERE, "خطأ Google Ads: " + gae.getRequestId(), gae);
      for (GoogleAdsError error : gae.getGoogleAdsFailure().getErrorsList()) {
        logger.log(Level.SEVERE, "الخطأ: " + error);
      }
      throw new RuntimeException("فشل تحميل التحويلات", gae);
    }
  }

  /**
   * بناء ClickConversion من بيانات التحويل
   */
  private ClickConversion buildClickConversion(ConversionData data, MessageDigest digest)
      throws UnsupportedEncodingException {
    
    ClickConversion.Builder builder = ClickConversion.newBuilder();

    // إضافة معرّفات المستخدم المشفّرة
    List<UserIdentifier> userIdentifiers = new ArrayList<>();

    // البريد الإلكتروني
    if (data.getEmail() != null && !data.getEmail().isEmpty()) {
      userIdentifiers.add(
          UserIdentifier.newBuilder()
              .setUserIdentifierSource(UserIdentifierSource.FIRST_PARTY)
              .setHashedEmail(normalizeAndHashEmail(digest, data.getEmail()))
              .build());
    }

    // رقم الهاتف
    if (data.getPhone() != null && !data.getPhone().isEmpty()) {
      userIdentifiers.add(
          UserIdentifier.newBuilder()
              .setUserIdentifierSource(UserIdentifierSource.FIRST_PARTY)
              .setHashedPhoneNumber(normalizeAndHash(digest, data.getPhone()))
              .build());
    }

    if (!userIdentifiers.isEmpty()) {
      builder.addAllUserIdentifiers(userIdentifiers);
    }

    // تفاصيل التحويل
    builder.setConversionAction(
        ResourceNames.conversionAction(customerId, conversionActionId));
    builder.setConversionDateTime(data.getConversionDateTime());
    builder.setConversionValue(data.getConversionValue());
    builder.setCurrencyCode(data.getCurrencyCode());

    // البيانات الاختيارية
    if (data.getOrderId() != null && !data.getOrderId().isEmpty()) {
      builder.setOrderId(data.getOrderId());
    }

    if (data.getGclid() != null && !data.getGclid().isEmpty()) {
      builder.setGclid(data.getGclid());
    }

    if (data.getAdUserDataConsent() != null) {
      builder.setConsent(
          Consent.newBuilder()
              .setAdUserData(data.getAdUserDataConsent()));
    }

    return builder.build();
  }

  /**
   * تطبيع وتجزئة البريد الإلكتروني
   */
  private String normalizeAndHashEmail(MessageDigest digest, String email)
      throws UnsupportedEncodingException {
    String normalized = email.toLowerCase().trim();
    String[] parts = normalized.split("@");
    
    if (parts.length == 2 && parts[1].matches("^(gmail|googlemail)\\.com\\s*")) {
      parts[0] = parts[0].replaceAll("\\.", "");
      normalized = parts[0] + "@" + parts[1];
    }
    
    return normalizeAndHash(digest, normalized);
  }

  /**
   * تطبيع وتجزئة النص
   */
  private String normalizeAndHash(MessageDigest digest, String text)
      throws UnsupportedEncodingException {
    String normalized = text.toLowerCase().replaceAll("\\s+", "");
    byte[] hash = digest.digest(normalized.getBytes("UTF-8"));
    
    StringBuilder result = new StringBuilder();
    for (byte b : hash) {
      result.append(String.format("%02x", b));
    }
    
    return result.toString();
  }

  /**
   * معالجة استجابة التحويلات
   */
  private ConversionUploadResult processResponse(UploadClickConversionsResponse response, 
      int totalCount) {
    
    int successful = 0;
    int failed = 0;

    if (response.hasPartialFailureError()) {
      logger.warning("خطأ جزئي: " + response.getPartialFailureError().getMessage());
    }

    for (ClickConversionResult result : response.getResultsList()) {
      if (result.hasConversionDateTime()) {
        successful++;
        logger.fine("تم تحميل التحويل: " + result.getConversionDateTime());
      } else {
        failed++;
      }
    }

    return new ConversionUploadResult(successful, failed);
  }

  /**
   * نتائج التحميل
   */
  public static class ConversionUploadResult {
    private int successful;
    private int failed;

    public ConversionUploadResult(int successful, int failed) {
      this.successful = successful;
      this.failed = failed;
    }

    public void addSuccessful(int count) {
      this.successful += count;
    }

    public void addFailed(int count) {
      this.failed += count;
    }

    public int getSuccessful() {
      return successful;
    }

    public int getFailed() {
      return failed;
    }

    public int getTotal() {
      return successful + failed;
    }

    @Override
    public String toString() {
      return String.format("النتائج: %d نجح، %d فشل، الإجمالي: %d", 
          successful, failed, getTotal());
    }
  }
}
