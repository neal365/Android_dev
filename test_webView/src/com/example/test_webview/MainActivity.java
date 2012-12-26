package com.example.test_webview;

import android.os.Bundle;
import android.os.Handler;
import android.app.Activity;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class MainActivity extends Activity {        
    private WebView mWebView;       
    private Handler mHandler = new Handler();       
       
    public void onCreate(Bundle icicle) {       
        super.onCreate(icicle);       
        setContentView(R.layout.activity_main);       
        mWebView = (WebView) findViewById(R.id.webview);       
        WebSettings webSettings = mWebView.getSettings();       
        webSettings.setJavaScriptEnabled(true);  
        
        webSettings.setSupportZoom(true); 
        webSettings.setUseWideViewPort(true);
        webSettings.setLightTouchEnabled(true);
        mWebView.setInitialScale((int) (720 / 700 * 100)); 
        
        mWebView.addJavascriptInterface(new Object() {       
            public void clickOnAndroid() {       
                mHandler.post(new Runnable() {       
                    public void run() {       
                        mWebView.loadUrl("javascript:wave()");       
                    }       
                });       
            }       
        }, "demo");       
        mWebView.loadUrl("file:///android_asset/index.htm");       
    }       
}   
