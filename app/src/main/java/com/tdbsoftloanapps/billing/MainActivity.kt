 package com.tdbsoftloanapps.billing

 import android.content.SharedPreferences
 import android.content.SharedPreferences.Editor
 import android.os.Bundle
 import android.view.View
 import android.widget.Button
 import android.widget.TextView
 import android.widget.Toast
 import androidx.appcompat.app.AppCompatActivity
 import com.android.billingclient.api.*
 import com.android.billingclient.api.BillingClient.SkuType
 import java.io.IOException
 import java.util.*

 class MainActivity : AppCompatActivity(), PurchasesUpdatedListener {
     var consumeButton: Button? = null
     var consumeCount: TextView? = null
     private var billingClient: BillingClient? = null
     override fun onCreate(savedInstanceState: Bundle?) {
         super.onCreate(savedInstanceState)
         setContentView(R.layout.activity_main)
         consumeButton = findViewById<View>(R.id.consume_button) as Button
         consumeCount = findViewById<View>(R.id.consume_count) as TextView

         // Establish connection to billing client
         //check purchase status from google play store cache on every app start
         billingClient = BillingClient.newBuilder(this)
             .enablePendingPurchases().setListener(this).build()
         billingClient!!.startConnection(object : BillingClientStateListener {
             override fun onBillingSetupFinished(billingResult: BillingResult) {
                 if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                     val queryPurchase = billingClient!!.queryPurchases(SkuType.INAPP)
                     val queryPurchases = queryPurchase.purchasesList
                     if (queryPurchases != null && queryPurchases.size > 0) {
                         handlePurchases(queryPurchases)
                     }
                 }
             }

             override fun onBillingServiceDisconnected() {}
         })
         consumeCount!!.text = "Payment done $purchaseCountValueFromPref Time(s)"
     }

     private val preferenceObject: SharedPreferences
         private get() = applicationContext.getSharedPreferences(PREF_FILE, 0)
     private val preferenceEditObject: Editor
         private get() {
             val pref = applicationContext.getSharedPreferences(PREF_FILE, 0)
             return pref.edit()
         }
     private val purchaseCountValueFromPref: Int
         private get() = preferenceObject.getInt(PURCHASE_KEY, 0)

     private fun savePurchaseCountValueToPref(value: Int) {
         preferenceEditObject.putInt(PURCHASE_KEY, value).commit()
     }

     //initiate purchase on consume button click
     fun consume(view: View?) {
         //check if service is already connected
         if (billingClient!!.isReady) {
             initiatePurchase()
         } else {
             billingClient = BillingClient.newBuilder(this).enablePendingPurchases().setListener(this).build()
             billingClient!!.startConnection(object : BillingClientStateListener {
                 override fun onBillingSetupFinished(billingResult: BillingResult) {
                     if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                         initiatePurchase()
                     } else {
                         Toast.makeText(applicationContext, "Error " + billingResult.debugMessage, Toast.LENGTH_SHORT).show()
                     }
                 }

                 override fun onBillingServiceDisconnected() {}
             })
         }
     }

     private fun initiatePurchase() {
         val skuList: MutableList<String> = ArrayList()
         skuList.add(PRODUCT_ID)
         val params = SkuDetailsParams.newBuilder()
         params.setSkusList(skuList).setType(SkuType.INAPP)
         billingClient!!.querySkuDetailsAsync(params.build()
         )
         {
                 billingResult, skuDetailsList ->
             if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                 if (skuDetailsList != null && skuDetailsList.size > 0) {
                     val flowParams = BillingFlowParams.newBuilder()
                         .setSkuDetails(skuDetailsList[0])
                         .build()
                     billingClient!!.launchBillingFlow(this@MainActivity, flowParams)
                 }
                 else {
                     //try to add item/product id "consumable" inside managed product in google play console
                     Toast.makeText(applicationContext, "Payment Info not Found", Toast.LENGTH_SHORT).show()
                 }
             }
             else {
                 Toast.makeText(applicationContext,
                     " Error " + billingResult.debugMessage, Toast.LENGTH_SHORT).show()
             }
         }
     }

     override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
         //if item newly purchased
         if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
             handlePurchases(purchases)
         } else if (billingResult.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
             val queryAlreadyPurchasesResult = billingClient!!.queryPurchases(SkuType.INAPP)
             val alreadyPurchases = queryAlreadyPurchasesResult.purchasesList
             alreadyPurchases?.let { handlePurchases(it) }
         } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
             Toast.makeText(applicationContext, "Payment cancelled Canceled", Toast.LENGTH_SHORT).show()
         } else {
             Toast.makeText(applicationContext, "Error " + billingResult.debugMessage, Toast.LENGTH_SHORT).show()
         }
     }

     fun handlePurchases(purchases: List<Purchase>) {
         for (purchase in purchases) {
             //if item is purchased
             if (PRODUCT_ID == purchase.sku && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                 if (!verifyValidSignature(purchase.originalJson, purchase.signature)) {
                     // Invalid purchase
                     // show error to user
                     Toast.makeText(applicationContext, "Error : Invalid Payment", Toast.LENGTH_SHORT).show()
                     return
                 }
                 // else purchase is valid
                 //if item is purchased and not consumed
                 if (!purchase.isAcknowledged) {
                     val consumeParams = ConsumeParams.newBuilder()
                         .setPurchaseToken(purchase.purchaseToken)
                         .build()
                     billingClient!!.consumeAsync(consumeParams, consumeListener)
                 }
             } else if (PRODUCT_ID == purchase.sku && purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                 Toast.makeText(applicationContext,
                     "Payment is Pending. Please complete Transaction", Toast.LENGTH_SHORT).show()
             } else if (PRODUCT_ID == purchase.sku && purchase.purchaseState == Purchase.PurchaseState.UNSPECIFIED_STATE) {
                 Toast.makeText(applicationContext, "Payment Status Unknown", Toast.LENGTH_SHORT).show()
             }
         }
     }

     var consumeListener = ConsumeResponseListener { billingResult, purchaseToken ->
         if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
             val consumeCountValue = purchaseCountValueFromPref + 1
             savePurchaseCountValueToPref(consumeCountValue)
             Toast.makeText(applicationContext, "Registration fee payment successful!", Toast.LENGTH_SHORT).show()
             consumeCount!!.text = "Payment  done $purchaseCountValueFromPref Time(s)"
         }
     }

     /**
      * Verifies that the purchase was signed correctly for this developer's public key.
      *
      * Note: It's strongly recommended to perform such check on your backend since hackers can
      * replace this method with "constant true" if they decompile/rebuild your app.
      *
      */
     private fun verifyValidSignature(signedData: String, signature: String): Boolean {
         return try {
             //for old playconsole
             // To get key go to Developer Console > Select your app > Development Tools > Services & APIs.
             //for new play console
             //To get key go to Developer Console > Select your app > Monetize > Monetization setup
             val base64Key = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAoFO+NsF1zirwQD4s9lWtf2yt1UpObxMJ2NTKEDiMRH7qOLvv6pBC4yc7aooTJ6upb1qefi7YECUeTCEY2IbgdVKDc+aXDgLD5wGFO5M1vQtFjm9GsMl5xMKbT/bGT7Glmrk/vPBPZgonA+sckbK2L4jSb9sN7xtfEqOcW1TcdRVThQrsGRqGk3wWBT4bugXmsl8lzbgBaaYepB9fjMNtu6fG+yZhRtU3e8NlhdqAl+ChizZcXj3/YTLUN32hy6rWhRi3EfXmBXaHYcOgry0clpdfYvaLxQGumB1aCZgFXIuqmCPqcmZ81UWTSzDUFvWr/sW6Y8ydvIU4YLki9sVFmwIDAQAB";
             Security.verifyPurchase(base64Key, signedData, signature)
         } catch (e: IOException) {
             false
         }
     }

     override fun onDestroy() {
         super.onDestroy()
         if (billingClient != null) {
             billingClient!!.endConnection()
         }
     }

     companion object {
         const val PREF_FILE = "MyPref"
         const val PURCHASE_KEY = "registeration_fee"
         const val PRODUCT_ID = "registeration_fee"
     }
 }