package com.ziquid.uslce;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.Scanner;
import java.util.UUID;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import android.content.res.Configuration;
import android.graphics.Point;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;

import android.net.Uri;
import android.provider.Settings.System;
import android.telephony.TelephonyManager;
import android.transition.TransitionManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.ziquid.iap.BillingService;
import com.ziquid.iap.PurchaseObserver;
import com.ziquid.iap.ResponseHandler;
import com.ziquid.iap.BillingService.RequestPurchase;
import com.ziquid.iap.BillingService.RestoreTransactions;
import com.ziquid.iap.Consts.PurchaseState;
import com.ziquid.iap.Consts.ResponseCode;

public class FullscreenActivity extends Activity {
    
  private WebView engine;
  private String androidID, usableWidth = "320", usableHeight = "480",
    billingSupportString = "; GoogleIAP", authKey = "",
    authKeySupportString = "", screenOrientation = "";
  public static String TAG = "uslce";

  private static final Random RNG = new Random();

  private PackageInfo pInfo = null;

  private MyPurchaseObserver mMyPurchaseObserver;
  private Handler mHandler;
  private BillingService mBillingService;
    
  public Context mContext;
  public File musicDlDir;
  private LinearLayout splashScreenLayout;
  private Runnable removeSplash;
  private Handler splashHandler;
  private Point screenSize = new Point();
  private static MediaPlayer mPlayer;

  @SuppressLint("SetJavaScriptEnabled")
	@Override
  public void onCreate(Bundle savedInstanceState) {
    mContext = getBaseContext();
    setTheme(R.style.FullscreenTheme);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_fullscreen);
    splashScreenLayout = findViewById(R.id.splashScreenLayout);

    // Hide the status and action bars.
    final View decorView = getWindow().getDecorView();
    int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN
      | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
      | View.SYSTEM_UI_FLAG_IMMERSIVE;
//        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
    decorView.setSystemUiVisibility(uiOptions);
//      ActionBar actionBar = getActionBar();
//      actionBar.hide();

    musicDlDir = mContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
    Log.d(TAG, "music DL dir is " + musicDlDir.toString());
    if (!musicDlDir.exists()) {
      musicDlDir.mkdir();
    }

    engine = (WebView) findViewById(R.id.browser);
    engine.clearCache(true); // start by clearing the cache

    androidID = System.getString(this.getContentResolver(), System.ANDROID_ID);
    Log.i(TAG, "androidID is " + androidID);

    if (android.os.Build.MODEL.equalsIgnoreCase("NookColor")) {
        androidID = "nkc+" + androidID;
        Log.i(TAG, "because this is a nook color and I can't change " +
          "the UserAgent, androidID is now " + androidID);
    }

    // Sometimes androidID can't be compared, so wrap in try/catch.
    try {
      if (androidID.equals("null") || androidID.equals("9774d56d682e549c")) {
        Log.i(TAG, "invalid androidID!  Trying IMEI...");
        androidID = getIMEI(); // hack for sdk and broken phones
        Log.i(TAG, "because my androidID is not valid, I am now using " +
          "an IMEI of " + androidID);
      }
    }
    catch (Exception e) {
      // Lame java workaround for hack for sdk and broken phones.
      androidID = getIMEI();
      Log.i(TAG, "because my androidID is not valid, I am now using " +
          "an IMEI of " + androidID);
    }

    authKey = getAuthKey();
    authKeySupportString = "; authKey=" + authKey;

    // Set up IAPs.
    mHandler = new Handler();
    mMyPurchaseObserver = new MyPurchaseObserver(this, mHandler);
    mBillingService = new BillingService();
    mBillingService.setContext(this);

    // Check if billing is supported.
    ResponseHandler.register(mMyPurchaseObserver);

    if (!mBillingService.checkBillingSupported()) {
      billingSupportString = "";
      Log.i(TAG, "Uhoh!  Billing isn't supported!");
    }
    else {
      Log.i(TAG, "Billing supported.");
    }

    // Set up the Web Engine.
    engine.getSettings().setJavaScriptEnabled(true);

    // Get usable width -- normal is 320.
    Display display = ((WindowManager)
      getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
    display.getSize(screenSize);
    int screenWidth = screenSize.x;
    int screenHeight = screenSize.y;

    Log.d(TAG, "screen width is " + screenWidth +
      " and height is " + screenHeight);

    // Android tries to auto-adjust images for a specific screen density.
    // We don't want that to happen, so we give it a width we know it will
    // turn into the correct width we want.
    DisplayMetrics metrics = new DisplayMetrics();
    display.getMetrics(metrics);
    float screenDensity = metrics.density;

    String density = Float.toString(screenDensity);
    Log.d(TAG, "screen density is " + density);

    usableWidth = Integer.toString((int) (screenWidth / screenDensity));
    usableHeight = Integer.toString((int) (screenHeight / screenDensity));
    Log.d(TAG, "usable width and height are " + usableWidth + ", " + usableHeight);

    if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
      screenOrientation = "landscape";
    }
    else {
      screenOrientation = "portrait";
    }
    Log.d(TAG, "screen orientation is " + screenOrientation);

    try {
      pInfo = getPackageManager().getPackageInfo("com.ziquid.uslce",
      PackageManager.GET_META_DATA);
      engine.getSettings().setUserAgentString(
        engine.getSettings().getUserAgentString() + " (" +
        pInfo.packageName + "; Android/" + pInfo.versionName + '/' +
        pInfo.versionCode + "; width=" + usableWidth + "; height=" +
        usableHeight + "; orientation=" + screenOrientation +
        billingSupportString + authKeySupportString + ')'
      );
    }
    catch (Exception e) {
      engine.getSettings().setUserAgentString(
        engine.getSettings().getUserAgentString() +
        " (com.ziquid.uslce; Android/Unknown/Unknown; " +
        billingSupportString + ')');
    }
    Log.d(TAG, "User agent set to " + engine.getSettings().getUserAgentString());

    engine.setWebViewClient(new MyWebViewClient());
    if (screenOrientation == "landscape") {
      engine.loadUrl(getString(R.string.url_begin_landscape) + androidID);
    }
    else {
      engine.loadUrl(getString(R.string.url_begin) + androidID);
    }

    // Remove the splashscreen.
    removeSplash = new Runnable() {
      public void run() {
        ViewGroup fullscreenViewGroup = (ViewGroup) decorView.findViewById(R.id.fullscreenLayout);
        TransitionManager.beginDelayedTransition(fullscreenViewGroup);
        splashScreenLayout.setVisibility(View.GONE);
      }
    };

    splashHandler = new Handler();
    splashHandler.postDelayed(removeSplash, 3000);
  }

  @Override
  protected void onStart() {
    super.onStart();
    ResponseHandler.register(mMyPurchaseObserver);
	}

	@Override
	protected void onStop() {
    super.onStop();
    ResponseHandler.unregister(mMyPurchaseObserver);
	}
	
	@Override
	protected void onDestroy() {
    super.onDestroy();
    mBillingService.unbind();
	}
    
  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {

    if ((keyCode == KeyEvent.KEYCODE_BACK) && engine.canGoBack() &&
    !engine.getUrl().equals(getString(R.string.url_home) + androidID)) {
      engine.clearHistory();
      engine.loadUrl(getString(R.string.url_home) + androidID);
      return true;
    }

    return super.onKeyDown(keyCode, event);
  }
    
  public String getIMEI() {
    	
    	String imei = "000000000000000";
    	String fakeIMEIPrefix = "sdk+";

    	SharedPreferences settings;
        SharedPreferences.Editor settings_editor;
    	        	
        try {
        	
        	TelephonyManager manager = (TelephonyManager)
        		this.getSystemService(Context.TELEPHONY_SERVICE);
        	imei = manager.getDeviceId();
        	
        } catch (SecurityException e) {
        		
        	Log.i(TAG, "uhoh!  This tablet/device doesn't have an IMEI " +
        		"or the user did not give permission to access it!");
        	fakeIMEIPrefix = "tab+";

        } catch (Exception e) {

          Log.i(TAG, "uhoh!  This tablet/device doesn't have an IMEI " +
            "and LAMELY deleted all references to TelephonyManager " +
            "in the ABI!");
          fakeIMEIPrefix = "tab+";

        }

    	if (imei == null) { // some generic tablets return null for IMEI
		
    		Log.i(TAG, "uhoh!  This tablet/device doesn't have an IMEI " +
    			"and returns null!");
    		fakeIMEIPrefix = "tab+";
    			
    	}
        
        if ((imei == null) || imei.equals("000000000000000")) { 
// no imei?  generate fake one
        
        	settings = getSharedPreferences(TAG, MODE_PRIVATE);
            imei = settings.getString("fake-imei", "000000000000000");
            
            if (imei.equals("000000000000000")) {
            
            	Log.d(TAG, "No saved fake IMEI, so I'm going " +
            		"to have to generate a fake one ...");
            	
            	imei = fakeIMEIPrefix + String.valueOf(RNG.nextLong());
            	settings_editor = settings.edit();
            	settings_editor.putString("fake-imei", imei);
            	settings_editor.commit();
            	Log.i(TAG, "created fake imei of " + imei);
           
            } else {
            
            	Log.i(TAG, "found existing fake imei of " + imei);
            
            }
        	
        }
        
        Log.i(TAG, "imei is " + imei);
        return imei;

    }

  public String getAuthKey() {
    	
    String authKey;
    SharedPreferences settings;
    SharedPreferences.Editor settings_editor;

    settings = getSharedPreferences(TAG, MODE_PRIVATE);
    authKey = settings.getString("auth-key", "000000000000000");

    if (authKey.equals("000000000000000")) {

      Log.d(TAG, "No saved authKey; generating one ...");

      authKey = UUID.randomUUID().toString();
      settings_editor = settings.edit();
      settings_editor.putString("auth-key", authKey);
      settings_editor.commit();
      Log.i(TAG, "created authKey of " + authKey);
    }
    else {
      Log.i(TAG, "found existing authKey of " + authKey);
    }

    return authKey;
  }

  private DialogInterface.OnClickListener okListener(DialogInterface dialog,
    int which) {
    return null;
  }

  @SuppressWarnings("unused")
  private void showPopUp(String Text) {
    new AlertDialog.Builder(this)
    .setMessage(Text)
    .setPositiveButton(getString(R.string.msg_ok), okListener(null, 0))
    .show();
  }

  private void showPopUp(Integer Text) {
    this.showPopUp(Text.toString());
  }

  private void playMusic(String url) {
    Log.d(TAG, "Attempting to play " + url);
    String musicFilename = getDlFileName(musicDlDir.getPath(), url);
    Log.d(TAG, "Music filename is " + musicFilename);
    File musicFile = new File(musicFilename);
    if (musicFile.canRead()) {
      playLocalMusic(musicFilename);
    }
    else {
      new DownloadFileAsync().execute(musicDlDir.getPath(), url);
    }

//    mPlayer = new MediaPlayer();
//    mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
//
//    try {
//      mPlayer.setDataSource(url);
//    } catch (IllegalArgumentException | SecurityException | IllegalStateException e) {
//      Log.e(TAG, "Invalid music URL? " + url);
//    } catch (IOException e) {
//      Log.e(TAG, "Could not download music URL " + url);
//    }
//
//    try {
//      mPlayer.prepareAsync();
//    } catch (IllegalStateException e) {
//      Log.e(TAG, "Invalid music URL? " + url);
//    }
//
//    mPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
//      @Override
//      public void onPrepared(MediaPlayer mp) {
//        Log.d(TAG, "Starting playback!");
//        mp.start();
//      }
//    });
//
//    mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
//      @Override
//      public void onCompletion(MediaPlayer mp) {
//        Log.d(TAG, "Stopping playback!");
//        mp.stop();
//        mp.release();
//      }
//    });
  }

  private static void playLocalMusic(String url) {
    Log.d(TAG, "Attempting to play local file " + url);

    mPlayer = new MediaPlayer();
    mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

    try {
      mPlayer.setDataSource(url);
    }
    catch (IllegalArgumentException | SecurityException | IllegalStateException e) {
      Log.e(TAG, "Invalid music file? " + url);
    }
    catch (IOException e) {
      Log.e(TAG, "Could not access music file " + url);
    }

    try {
      mPlayer.prepare();
    }
    catch (IllegalStateException e) {
      Log.e(TAG, "music player in illegal state!");
      e.printStackTrace();
    }
    catch (IOException e) {
      Log.e(TAG, "cannot access music file!");
      e.printStackTrace();
    }

    Log.d(TAG, "Starting playback!");
    mPlayer.start();

    mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
      @Override
      public void onCompletion(MediaPlayer mp) {
        Log.d(TAG, "Stopping playback!");
        mp.stop();
        mp.release();
      }
    });
  }
    
  private void buyLuck(String sku) {
    if (mBillingService.requestPurchase(sku, androidID)) {
//        	showPopUp("you just bought luck!");
//        	String response = downloadString(getString(R.string.url_purchase) +
//    			androidID +	'/' + sku);
      engine.loadUrl(getString(R.string.url_home) + androidID);
    }
    else {
      showPopUp(R.string.billing_not_supported_message);
    }
  }

  static class DownloadFileAsync extends AsyncTask<String, String, String> {

    @Override
    protected String doInBackground(String... params) {
      int count;

      String dlDirname = params[0];
      String outputFilename = getDlFileName(dlDirname, params[1]);

      try {
        URL url = new URL(params[1]);
        URLConnection conexion = url.openConnection();
        conexion.connect();
        int lengthOfFile = conexion.getContentLength();
        Log.d(TAG, "Length of file: " + lengthOfFile);

        InputStream input = new BufferedInputStream(url.openStream());
        OutputStream output = new FileOutputStream(outputFilename);
        byte data[] = new byte[1024];

        while ((count = input.read(data)) != -1) {
          output.write(data, 0, count);
        }

        output.flush();
        output.close();
        input.close();
        Log.d(TAG, "download finished");
      }
      catch (Exception e) {
        e.printStackTrace();
        Log.e(TAG, "download ERROR");
        File outputFile = new File(outputFilename);
        outputFile.delete();
        return null;
      }

      return outputFilename;
    }

    @Override
    protected void onPostExecute(String result) {
      Log.d(TAG, "onPostExecute: " + result);
      playLocalMusic(result);
    }

  }

  private static String getDlFileName(String dlDirname, String url) {
    String outputFilename = dlDirname + File.separator +
      getBaseName(url).replaceAll("%20", "-")
      .replaceAll(" ", "-");
    Log.d(TAG, "outputFilename is " + outputFilename);
    return outputFilename;
  }

  private String downloadAsString(String fileUrl) {

    URL myFileUrl = null;
    String newString = "";

    try {
      myFileUrl = new URL(fileUrl);
    }
    catch (MalformedURLException e) {
      e.printStackTrace();
    }

    try {
      HttpURLConnection conn =
        (HttpURLConnection) myFileUrl.openConnection();
      conn.setDoInput(true);
      conn.setRequestProperty("User-Agent",
        "com.ziquid.uslce luckloader");
      conn.connect();
//      int length = conn.getContentLength();
      InputStream is = conn.getInputStream();
      newString = new Scanner(is).useDelimiter("\\A").next();
    }
    catch (IOException e) {
      e.printStackTrace();
    }

    return newString;
  }

  public static String getBaseName(Uri uri) {
    try {
      String path = uri.getLastPathSegment();
      return path != null ? path.substring(path.lastIndexOf("/") + 1) : "unknown";
    }
    catch (Exception e) {
      e.printStackTrace();
    }

    return "unknown";
  }

  public static String getBaseName(String uri) {
    return getBaseName(Uri.parse(uri));
  }
    
  private class MyWebViewClient extends WebViewClient {

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
      Log.d(TAG, "url is " + url);
      if (url.startsWith("iap://")) {
//        		showPopUp("in app purchase!");
        buyLuck(url.substring(6));
        return true;
      }
      else if (url.startsWith("external://")) {
        String fixedUrl = url.replace("external://", "http://");
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(fixedUrl));
        startActivity(i);
        return true;
      }
      else {
        view.loadUrl(url);
        return true;
      }
    }

    @Override
    public void onPageFinished(WebView view, String url) {
      Log.d(TAG, "page has finished loading for URL " + url);
      super.onPageFinished(view, url);
      view.evaluateJavascript("if (typeof Drupal == 'object') {" +
          "(function(ds) { " +
          "if (typeof ds && typeof ds.zg.sound) {" +
          "  return ds.zg.sound;" +
          "} })(Drupal.settings);" +
        "}",
        new ValueCallback<String>() {
        @Override
        public void onReceiveValue(String s) {
          if (s.equals("null")) { return; }
          Log.d(TAG, "Url to play: " + s);
          s = StringTrimmer.trim(s, '"');
          s = s.replaceAll(" ", "%20");
          playMusic(s);
        }
        });
    }

  }
}

/**
 * A {@link PurchaseObserver} is used to get callbacks when Android Market sends
 * messages to this application so that we can update the UI.
 */
class MyPurchaseObserver extends PurchaseObserver {
	
	String TAG = "MyPurchaseObserver";
	Activity myActivity;
	
	public MyPurchaseObserver(Activity activity, Handler handler) {
		
		super(activity, handler);
		myActivity = activity;
		Log.i(TAG, "MyPurchaseObserver init");
		
	}

	@Override
	public void onBillingSupported(boolean supported) {
		
		Log.i(TAG, "supported: " + supported);
		
	}

	@Override
	public void onPurchaseStateChange(PurchaseState purchaseState, String itemId,
		int quantity, long purchaseTime, String developerPayload) {
	     
		Log.i(TAG, "onPurchaseStateChange() itemId: " + itemId +
			 " " + purchaseState);
	    
	}

	@Override
	public void onRequestPurchaseResponse(RequestPurchase request,
		ResponseCode responseCode) {
	    
	    Log.d(TAG, request.mProductId + ": " + responseCode);
	
	    if (responseCode == ResponseCode.RESULT_OK) {
	
	    	Log.i(TAG, "purchase was successfully sent to server");
	        
	    } else if (responseCode == ResponseCode.RESULT_USER_CANCELED) {
	    	
	        Log.i(TAG, "user canceled purchase");
	        
	    } else {
	    
	    	Log.i(TAG, "purchase failed");
	        
	    }
	    
	}

	
	@Override
	public void onRestoreTransactionsResponse(RestoreTransactions request,
		ResponseCode responseCode) {
	    
		if (responseCode == ResponseCode.RESULT_OK) {
	    
			Log.d(TAG, "completed RestoreTransactions request");
	        
		} else {
	    
			Log.d(TAG, "RestoreTransactions error: " + responseCode);
	        
		}
	
	}
	
}

/**
 * Trim strings.
 *
 * @see https://stackoverflow.com/questions/25691415/java-trim-leading-or-trailing-characters-from-a-string
 */
class StringTrimmer {
  public static String trim(String string, char ch) {
    return trim(string, ch, ch);
  }

  public static String trim(String string, char leadingChar, char trailingChar) {
    return string.replaceAll("^["+leadingChar+"]+|["+trailingChar+"]+$", "");
  }

  public static String trim(String string, String regex) {
    return trim(string, regex, regex);
  }

  public static String trim(String string, String leadingRegex, String trailingRegex) {
    return string.replaceAll("^("+leadingRegex+")+|("+trailingRegex+")+$", "");
  }
}

