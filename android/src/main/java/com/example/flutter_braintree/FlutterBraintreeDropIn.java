package com.example.flutter_braintree;

import android.app.Activity;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.braintreepayments.api.DropInRequest;
import com.braintreepayments.api.DropInResult;
import com.braintreepayments.api.GooglePayRequest;
import com.braintreepayments.api.PayPalCheckoutRequest;
import com.braintreepayments.api.PaymentMethodNonce;
import com.braintreepayments.api.ThreeDSecureAdditionalInformation;
import com.braintreepayments.api.ThreeDSecurePostalAddress;
import com.braintreepayments.api.ThreeDSecureRequest;
import com.google.android.gms.wallet.TransactionInfo;
import com.google.android.gms.wallet.WalletConstants;

import java.io.Serializable;
import java.util.HashMap;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener;

public class FlutterBraintreeDropIn implements FlutterPlugin, ActivityAware, MethodCallHandler, ActivityResultListener, Serializable {
  private static final int DROP_IN_REQUEST_CODE = 0x1337;

  private Activity activity;
  private Result activeResult;
  private MethodChannel channel;
  private ActivityPluginBinding activityBinding;

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
    channel = new MethodChannel(binding.getBinaryMessenger(), "flutter_braintree.drop_in");
    channel.setMethodCallHandler(this);
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    if (channel != null) {
      channel.setMethodCallHandler(null);
    }
  }

  @Override
  public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
    activity = binding.getActivity();
    activityBinding = binding;
    binding.addActivityResultListener(this);
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
    onDetachedFromActivity();
  }

  @Override
  public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
    onAttachedToActivity(binding);
  }

  @Override
  public void onDetachedFromActivity() {
    if (activityBinding != null) {
      activityBinding.removeActivityResultListener(this);
      activityBinding = null;
    }
    activity = null;
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    if (call.method.equals("start")) {
      String clientToken = call.argument("clientToken");
      String tokenizationKey = call.argument("tokenizationKey");
      ThreeDSecureRequest threeDSecureRequest = new ThreeDSecureRequest();
      String token = "";
      if (clientToken != null)
        token = clientToken;
      else if (tokenizationKey != null)
        token = tokenizationKey;

      HashMap<String, String> billingAddress = call.argument("billingAddress");
      if(billingAddress != null){
        ThreeDSecurePostalAddress address = new ThreeDSecurePostalAddress();
        address.setGivenName(billingAddress.get("givenName"));
        address.setSurname(billingAddress.get("surname"));
        address.setPhoneNumber(billingAddress.get("phoneNumber"));
        address.setStreetAddress(billingAddress.get("streetAddress"));
        address.setExtendedAddress(billingAddress.get("extendedAddress"));
        address.setLocality(billingAddress.get("locality"));
        address.setRegion(billingAddress.get("region"));
        address.setPostalCode(billingAddress.get("postalCode"));
        address.setCountryCodeAlpha2(billingAddress.get("countryCodeAlpha2"));

        ThreeDSecureAdditionalInformation additionalInformation = new ThreeDSecureAdditionalInformation();
        additionalInformation.setShippingAddress(address);
        threeDSecureRequest.setBillingAddress(address);
        threeDSecureRequest.setAdditionalInformation(additionalInformation);
      }

      threeDSecureRequest.setAmount((String) call.argument("amount"));
      String email = call.argument("email");
      if(email != null){
        threeDSecureRequest.setEmail(email);
      }
      threeDSecureRequest.setVersionRequested(ThreeDSecureRequest.VERSION_2);

      DropInRequest dropInRequest = new DropInRequest();
      dropInRequest.setVaultManagerEnabled((Boolean) call.argument("vaultManagerEnabled"));
      dropInRequest.setThreeDSecureRequest(threeDSecureRequest);
      dropInRequest.setMaskCardNumber((Boolean) call.argument("maskCardNumber"));

      readGooglePaymentParameters(dropInRequest, call);
      readPayPalParameters(dropInRequest, call);
      if (!((Boolean) call.argument("venmoEnabled")))
        dropInRequest.setVenmoDisabled(true);
      if (!((Boolean) call.argument("cardEnabled")))
        dropInRequest.setCardDisabled(true);
      if (!((Boolean) call.argument("paypalEnabled")))
        dropInRequest.setPayPalDisabled(true);

      if (this.activeResult != null) {
        result.error("drop_in_already_running", "Cannot launch another Drop-in activity while one is already running.", null);
        return;
      }
      this.activeResult = result;
      Intent intent = new Intent(activity, DropInActivity.class);
      intent.putExtra("token", token);
      intent.putExtra("dropInRequest", dropInRequest);
      this.activity.startActivityForResult(intent, DROP_IN_REQUEST_CODE);
    } else {
      result.notImplemented();
    }
  }

  private static void readGooglePaymentParameters(DropInRequest dropInRequest, MethodCall call) {
    HashMap<String, Object> arg = call.argument("googlePaymentRequest");
    if (arg == null) {
      dropInRequest.setGooglePayDisabled(true);
      return;
    }
    GooglePayRequest googlePayRequest = new GooglePayRequest();
    googlePayRequest.setTransactionInfo(TransactionInfo.newBuilder()
            .setTotalPrice((String) arg.get("totalPrice"))
            .setTotalPriceStatus(WalletConstants.TOTAL_PRICE_STATUS_FINAL)
            .setCurrencyCode((String) arg.get("currencyCode"))
            .build());
    googlePayRequest.setBillingAddressRequired(true);
    dropInRequest.setGooglePayRequest(googlePayRequest);
  }

  private static void readPayPalParameters(DropInRequest dropInRequest, MethodCall call) {
    HashMap<String, Object> arg = call.argument("paypalRequest");
    if (arg == null) {
      dropInRequest.setPayPalDisabled(true);
      return;
    }
    String amount = (String) arg.get("amount");
    PayPalCheckoutRequest paypalRequest = new PayPalCheckoutRequest(amount);
    paypalRequest.setCurrencyCode((String) arg.get("currencyCode"));
    paypalRequest.setDisplayName((String) arg.get("displayName"));
    paypalRequest.setBillingAgreementDescription((String) arg.get("billingAgreementDescription"));
    dropInRequest.setPayPalRequest(paypalRequest);
  }

  @Override
  public boolean onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (this.activeResult == null)
      return false;

    switch (requestCode) {
      case DROP_IN_REQUEST_CODE:
        if (resultCode == Activity.RESULT_OK) {
          DropInResult dropInResult = data.getParcelableExtra("dropInResult");
          PaymentMethodNonce paymentMethodNonce = dropInResult.getPaymentMethodNonce();
          HashMap<String, Object> result = new HashMap<String, Object>();

          HashMap<String, Object> nonceResult = new HashMap<String, Object>();
          nonceResult.put("nonce", paymentMethodNonce.getString());
          nonceResult.put("typeLabel", dropInResult.getPaymentMethodType().name());
          nonceResult.put("description", dropInResult.getPaymentDescription());
          nonceResult.put("isDefault", paymentMethodNonce.isDefault());

          result.put("paymentMethodNonce", nonceResult);
          result.put("deviceData", dropInResult.getDeviceData());
          this.activeResult.success(result);
        } else if (resultCode == Activity.RESULT_CANCELED) {
          activeResult.success(null);
        } else {
          String error = data.getStringExtra("error");
          activeResult.error("braintree_error", error, null);
        }
        activeResult = null;
        return true;
      default:
        return false;
    }
  }
}