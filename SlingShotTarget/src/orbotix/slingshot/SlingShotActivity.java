/*==============================================================================
            Copyright (c) 2010-2011 QUALCOMM Incorporated.
            All Rights Reserved.
            Qualcomm Confidential and Proprietary
            
@file 
    orbotix.slingshot.ImageTargets.java

@brief
    Sample for orbotix.slingshot.ImageTargets

==============================================================================*/


package orbotix.slingshot;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.*;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import com.qualcomm.QCAR.QCAR;
import orbotix.robot.app.StartupActivity;
import orbotix.robot.base.RGBLEDOutputCommand;
import orbotix.robot.base.Robot;
import orbotix.robot.base.RobotProvider;
import orbotix.robot.widgets.ControllerActivity;
import orbotix.robot.widgets.calibration.CalibrationView;
import orbotix.robot.widgets.slingshot.SlingShotView;

import java.util.Random;
import java.util.Vector;



public class SlingShotActivity extends ControllerActivity
{
    // Application status constants:
    private static final int APPSTATUS_UNINITED         = -1;
    private static final int APPSTATUS_INIT_APP         = 0;
    private static final int APPSTATUS_INIT_QCAR        = 1;
    private static final int APPSTATUS_INIT_APP_AR      = 2;
    private static final int APPSTATUS_INIT_TRACKER     = 3;
    private static final int APPSTATUS_INITED           = 4;
    private static final int APPSTATUS_CAMERA_STOPPED   = 5;
    private static final int APPSTATUS_CAMERA_RUNNING   = 6;
    
    //ID for starting StartupActivity to connect to Sphero
    private static final int STARTUP_ACTIVITY = 0;
    
    // Name of the native dynamic libraries to load:
    private static final String NATIVE_LIB_SAMPLE = "SlingShot";
    private static final String NATIVE_LIB_QCAR = "QCAR"; 

    // Our OpenGL view:
    private QCARSampleGLView mGlView;
    
    // The view to display the sample splash screen:
    private ImageView mSplashScreenView;
    
    // The minimum time the splash screen should be visible:
    private static final long MIN_SPLASH_SCREEN_TIME = 2000;    
    
    // The time when the splash screen has become visible:
    long mSplashScreenStartTime = 0;
    
    // Our renderer:
    private SlingShotRenderer mRenderer;
    
    // Display size of the device
    private int mScreenWidth = 0;
    private int mScreenHeight = 0;
    
    // The current application status
    private int mAppStatus = APPSTATUS_UNINITED;
    
    // The async tasks to initialize the QCAR SDK 
    private InitQCARTask mInitQCARTask;
    private LoadTrackerTask mLoadTrackerTask;

    // QCAR initialization flags
    private int mQCARFlags = 0;
    
    // The textures we will use for rendering:
    private Vector<Texture> mTextures;
    private int mSplashScreenImageResource = 0;
    
    //Slingshot and calibration views for controlling the robot
    private RelativeLayout mSlingShotParent;
    private SlingShotView mSlingShotView;
    private CalibrationView mCalibrationView;
    
    //Sphero robot to control
    private Robot mRobot;
    
    //Indicates whether the game is in a "just scored" state
    private volatile boolean mScored = false;
    
    //Handler for timed reset of the score
    private final Handler mHandler = new Handler();

    //Congratulatory message View
    private CongratsView mCongratsView;
    
    private boolean mStartedButtons = false;

    private MenuItem checked;
    private boolean mFlash = false;

    
    //Callback for rendering each frame
    private final Runnable mOnFrameRenderCallback = new Runnable() {
        @Override
        public void run() {
            
            if(!mScored && getScored()){
                score();
            }
        }
    };
    
    /** Static initializer block to load native libraries on start-up. */
    static
    {
        loadLibrary(NATIVE_LIB_QCAR);
        loadLibrary(NATIVE_LIB_SAMPLE);
    }
    
    
    /** An async task to initialize QCAR asynchronously. */
    private class InitQCARTask extends AsyncTask<Void, Integer, Boolean>
    {   
        // Initialize with invalid value
        private int mProgressValue = -1;
        
        protected Boolean doInBackground(Void... params)
        {
            QCAR.setInitParameters(SlingShotActivity.this, mQCARFlags);
            
            do
            {
                // QCAR.init() blocks until an initialization step is complete,
                // then it proceeds to the next step and reports progress in
                // percents (0 ... 100%)
                // If QCAR.init() returns -1, it indicates an error.
                // Initialization is done when progress has reached 100%.
                mProgressValue = QCAR.init();
                
                // Publish the progress value:
                publishProgress(mProgressValue);
                
                // We check whether the task has been canceled in the meantime
                // (by calling AsyncTask.cancel(true))
                // and bail out if it has, thus stopping this thread.
                // This is necessary as the AsyncTask will run to completion
                // regardless of the status of the component that started is.
            } while (!isCancelled() && mProgressValue >= 0 && mProgressValue < 100);
            
            return (mProgressValue > 0);
        }

        
        protected void onProgressUpdate(Integer... values)
        {
            // Do something with the progress value "values[0]", e.g. update
            // splash screen, progress bar, etc.
        }

        
        protected void onPostExecute(Boolean result)
        {
            // Done initializing QCAR, proceed to next application
            // initialization status:
            if (result)
            {
                DebugLog.LOGD("InitQCARTask::onPostExecute: QCAR initialization" +
                                                            " successful");

                updateApplicationStatus(APPSTATUS_INIT_APP_AR);
            }
            else
            {
                // Create dialog box for display error:
                AlertDialog dialogError = new AlertDialog.Builder(SlingShotActivity.this).create();
                dialogError.setButton(
                    "Close",
                    new DialogInterface.OnClickListener()
                    {
                        public void onClick(DialogInterface dialog, int which)
                        {
                            // Exiting application
                            System.exit(1);
                        }
                    }
                ); 
                
                String logMessage;

                // NOTE: Check if initialization failed because the device is
                // not supported. At this point the user should be informed
                // with a message.
                if (mProgressValue == QCAR.INIT_DEVICE_NOT_SUPPORTED)
                {
                    logMessage = "Failed to initialize QCAR because this " +
                        "device is not supported.";
                }
                else if (mProgressValue ==
                            QCAR.INIT_CANNOT_DOWNLOAD_DEVICE_SETTINGS)
                {
                    logMessage = 
                        "Network connection required to initialize camera " +
                        "settings. Please check your connection and restart " +
                        "the application. If you are still experiencing " +
                        "problems, then your device may not be currently " +
                        "supported.";
                }
                else
                {
                    logMessage = "Failed to initialize QCAR.";
                }
                
                // Log error:
                DebugLog.LOGE("InitQCARTask::onPostExecute: " + logMessage +
                                " Exiting.");
                
                // Show dialog box with error message:
                dialogError.setMessage(logMessage);  
                dialogError.show();
            }
        }
    }
    
    
    /** An async task to load the tracker data asynchronously. */
    private class LoadTrackerTask extends AsyncTask<Void, Integer, Boolean>
    {
        protected Boolean doInBackground(Void... params)
        {
            // Initialize with invalid value
            int progressValue = -1;

            do
            {
                progressValue = QCAR.load();
                publishProgress(progressValue);
                
            } while (!isCancelled() && progressValue >= 0 &&
                        progressValue < 100);
            
            return (progressValue > 0);
        }
        
        
        protected void onProgressUpdate(Integer... values)
        {
            // Do something with the progress value "values[0]", e.g. update
            // splash screen, progress bar, etc.
        }
        
        
        protected void onPostExecute(Boolean result)
        {
            DebugLog.LOGD("LoadTrackerTask::onPostExecute: execution " +
                        (result ? "successful" : "failed"));

            // Done loading the tracker, update application status: 
            updateApplicationStatus(APPSTATUS_INITED);
        }
    }

    
    /** Called when the activity first starts or the user navigates back
     * to an activity. */
    protected void onCreate(Bundle savedInstanceState)
    {
        DebugLog.LOGD("SlingShot::onCreate");
        super.onCreate(savedInstanceState);
        
        // Set the splash screen image to display during initialization:
        mSplashScreenImageResource = R.drawable.splash_screen_image_targets;
        
        // Load any sample specific textures:  
        mTextures = new Vector<Texture>();
        loadTextures();
        
        // Query the QCAR initialization flags:
        mQCARFlags = getInitializationFlags();
        
        // Update the application status to start initializing application
        updateApplicationStatus(APPSTATUS_INIT_APP);

        mSlingShotParent = new RelativeLayout(this);
        mSlingShotView = new SlingShotView(this, null);
        mSlingShotView.setReturnBall(true);
        mSlingShotView.setMaxVelocity(0.6f);

        mSlingShotView.setOnShotCallback(new Runnable() {
            @Override
            public void run() {
                setCanScore(true);
            }
        });

        mSlingShotView.setOnStopCallback(new Runnable() {
            @Override
            public void run() {
                setCanScore(false);
            }
        });

        mCalibrationView = new CalibrationView(this, null);
        mCalibrationView.setShowGlow(true);

        //Place the SlingShotView into the parent, for proper placement
        RelativeLayout.LayoutParams ss_lp = new RelativeLayout.LayoutParams(LayoutParams.FILL_PARENT, 400);
        ss_lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);

        mSlingShotParent.addView(mSlingShotView, ss_lp);

        mSlingShotView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        mCalibrationView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        addController(mSlingShotView);
        addController(mCalibrationView);

        //Create and place the CongratsView
        mCongratsView = new CongratsView(this, null);
        //mCongratsView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        setButtonArea(15f, 13f);
        setNumRows(4);
        setNumCols(5);
        setTargetObjectSize(1.5f);
        showButtons();
        setCanScore(false);

        if(mRobot == null){

            Intent i = new Intent(this, StartupActivity.class);
            startActivityForResult(i, STARTUP_ACTIVITY);
        }
    }

    /**
     * Show the buttons in the next location
     */
    private void showButtons(){

        final Random rng = new Random();

        mStartedButtons = true;

        //Decide button location by available area
        final float aw = getAvailableWidth();
        final float ah = getAvailableHeight();
        final float w = getWidth();
        final float h = getHeight();

        final float x = (rng.nextFloat()*aw) - (w /2);
        final float y = (rng.nextFloat()*ah) - (h /2);

        showButtons(x, y);
    }

    /**
     * Remove the buttons
     */
    private void deleteButtons(){
        
        if(mStartedButtons){
            //mStartedButtons = false;
            removeButtons();
        }
    }

    
    /** We want to load specific textures from the APK, which we will later
    use for rendering. */
    private void loadTextures()
    {
        mTextures.add(Texture.loadTextureFromApk("TextureTeapotBrass.png",
                                                 getAssets()));
        mTextures.add(Texture.loadTextureFromApk("TextureTeapotBlue.png",
                                                 getAssets()));
        mTextures.add(Texture.loadTextureFromApk("TextureTeapotRed.png",
                getAssets()));
    }
    
    
    /** Configure QCAR with the desired version of OpenGL ES. */
    private int getInitializationFlags()
    {
        int flags = 0;
        
        // Query the native code:
        if (getOpenGlEsVersionNative() == 1)
        {
            flags = QCAR.GL_11;
        }
        else
        {
            flags = QCAR.GL_20;
        }
        
        return flags;
    }    
    
    


   /** Called when the activity will start interacting with the user.*/
    protected void onResume()
    {
        DebugLog.LOGD("onResume");
        super.onResume();
        
        // QCAR-specific resume operation
        QCAR.onResume();
        
        // We may start the camera only if the QCAR SDK has already been 
        // initialized
        if (mAppStatus == APPSTATUS_CAMERA_STOPPED)
            updateApplicationStatus(APPSTATUS_CAMERA_RUNNING);
        
        // Resume the GL view:
        if (mGlView != null)
        {
            mGlView.setVisibility(View.VISIBLE);
            mGlView.onResume();
        }
    }

    /** Called when the system is about to start resuming a previous activity.*/
    protected void onPause()
    {
        DebugLog.LOGD("onPause");
        super.onPause();

        /*((ViewGroup)mSlingShotParent.getParent()).removeView(mSlingShotParent);
        ((ViewGroup)mCalibrationView.getParent()).removeView(mCalibrationView);
        ((ViewGroup)mCongratsView.getParent()).removeView(mCongratsView);*/

        if (mGlView != null)
        {
            mGlView.setVisibility(View.INVISIBLE);
            mGlView.onPause();
        }
        
        // QCAR-specific pause operation
        QCAR.onPause();
        
        if (mAppStatus == APPSTATUS_CAMERA_RUNNING)
        {
            updateApplicationStatus(APPSTATUS_CAMERA_STOPPED);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        RobotProvider.getDefaultProvider().disconnectControlledRobots();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if(resultCode == RESULT_OK){
            if(requestCode == STARTUP_ACTIVITY){
                
                final String robot_id = data.getStringExtra(StartupActivity.EXTRA_ROBOT_ID);
                
                if(robot_id != null && !robot_id.equals("")){
                    mRobot = RobotProvider.getDefaultProvider().findRobot(robot_id);

                    mSlingShotView.setRobot(mRobot);
                    mCalibrationView.setRobot(mRobot);

                    RGBLEDOutputCommand.sendCommand(mRobot, 255, 128, 0);
                }
            }
        }
    }

    /** Native function to deinitialize the application.*/
    private native void deinitApplicationNative();

    
    /** The final call you receive before your activity is destroyed.*/
    protected void onDestroy()
    {
        DebugLog.LOGD("onDestroy");
        super.onDestroy();
        
        // Cancel potentially running tasks
        if (mInitQCARTask != null &&
            mInitQCARTask.getStatus() != InitQCARTask.Status.FINISHED)
        {
            mInitQCARTask.cancel(true);
            mInitQCARTask = null;
        }
        
        if (mLoadTrackerTask != null &&
            mLoadTrackerTask.getStatus() != LoadTrackerTask.Status.FINISHED)
        {
            mLoadTrackerTask.cancel(true);
            mLoadTrackerTask = null;
        }
        
        // Do application deinitialization in native code
        deinitApplicationNative();
        
        // Unload texture
        mTextures.clear();
        mTextures = null;
        
        // Deinitialize QCAR SDK
        QCAR.deinit();
        
        System.gc();
    }

    
    /** NOTE: this method is synchronized because of a potential concurrent
     * access by orbotix.slingshot.SlingShotActivity::onResume() and InitQCARTask::onPostExecute(). */
    private synchronized void updateApplicationStatus(int appStatus)
    {
        // Exit if there is no change in status
        if (mAppStatus == appStatus)
            return;

        // Store new status value      
        mAppStatus = appStatus;

        // Execute application state-specific actions
        switch (mAppStatus)
        {
            case APPSTATUS_INIT_APP:
                // Initialize application elements that do not rely on QCAR
                // initialization  
                initApplication();
                
                // Proceed to next application initialization status
                updateApplicationStatus(APPSTATUS_INIT_QCAR);
                break;

            case APPSTATUS_INIT_QCAR:
                // Initialize QCAR SDK asynchronously to avoid blocking the
                // main (UI) thread.
                // This task instance must be created and invoked on the UI
                // thread and it can be executed only once!
                try
                {
                    mInitQCARTask = new InitQCARTask();
                    mInitQCARTask.execute();
                }
                catch (Exception e)
                {
                    DebugLog.LOGE("Initializing QCAR SDK failed");
                }
                break;
                
            case APPSTATUS_INIT_APP_AR:
                // Initialize Augmented Reality-specific application elements
                // that may rely on the fact that the QCAR SDK has been
                // already initialized
                initApplicationAR();
                
                // Proceed to next application initialization status
                updateApplicationStatus(APPSTATUS_INIT_TRACKER);
                break;
                
            case APPSTATUS_INIT_TRACKER:
                // Load the tracking data set
                //
                // This task instance must be created and invoked on the UI
                // thread and it can be executed only once!
                try
                {
                    mLoadTrackerTask = new LoadTrackerTask();
                    mLoadTrackerTask.execute();
                }
                catch (Exception e)
                {
                    DebugLog.LOGE("Loading tracking data set failed");
                }
                break;
                
            case APPSTATUS_INITED:
                // Hint to the virtual machine that it would be a good time to
                // run the garbage collector.
                //
                // NOTE: This is only a hint. There is no guarantee that the
                // garbage collector will actually be run.
                System.gc();

                // Native post initialization:
                onQCARInitializedNative();
                
                // The elapsed time since the splash screen was visible:
                long splashScreenTime = System.currentTimeMillis() -
                                            mSplashScreenStartTime;
                long newSplashScreenTime = 0;
                if (splashScreenTime < MIN_SPLASH_SCREEN_TIME)
                {
                    newSplashScreenTime = MIN_SPLASH_SCREEN_TIME -
                                            splashScreenTime;   
                }

                /*// Activate the renderer
                mRenderer.mIsActive = true;

                // Now add the GL surface view. It is important
                // that the OpenGL ES surface view gets added
                // BEFORE the camera is started and video
                // background is configured.
                addContentView(mGlView, new LayoutParams(
                        LayoutParams.FILL_PARENT,
                        LayoutParams.FILL_PARENT));


                // Start the camera:
                updateApplicationStatus(APPSTATUS_CAMERA_RUNNING);*/
                
                // Request a callback function after a given timeout to dismiss
                // the splash screen:
                Handler handler = new Handler();
                handler.postDelayed(
                    new Runnable() {
                        public void run()
                        {
                            
                            // Activate the renderer
                            mRenderer.mIsActive = true;

                            // Now add the GL surface view. It is important
                            // that the OpenGL ES surface view gets added
                            // BEFORE the camera is started and video
                            // background is configured.
                            addContentView(mGlView, new LayoutParams(
                                            LayoutParams.FILL_PARENT,
                                            LayoutParams.FILL_PARENT));

                            ViewGroup.LayoutParams lp =
                                    new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);

                            addContentView(mCongratsView, lp);
                            addContentView(mSlingShotParent, lp);
                            addContentView(mCalibrationView, lp);

                            // Start the camera:
                            updateApplicationStatus(APPSTATUS_CAMERA_RUNNING);
                        }
                    }
                    , newSplashScreenTime);
        
                break;
                
            case APPSTATUS_CAMERA_STOPPED:
                // Call the native function to stop the camera
                stopCamera();
                break;
                
            case APPSTATUS_CAMERA_RUNNING:
                // Call the native function to start the camera
                startCamera(); 
                break;
                
            default:
                throw new RuntimeException("Invalid application state");
        }
    }
    
    

    
    
    /** Initialize application GUI elements that are not related to AR. */
    private void initApplication()
    {
        // Set the screen orientation
        //
        // NOTE: It is recommended to set this because of the following reasons:
        //
        //    1.) Before Android 2.2 there is no reliable way to query the
        //        absolute screen orientation from an activity, therefore using 
        //        an undefined orientation is not recommended. Screen 
        //        orientation matching orientation sensor measurements is also
        //        not recommended as every screen orientation change triggers
        //        deinitialization / (re)initialization steps in internal QCAR 
        //        SDK components resulting in unnecessary overhead during 
        //        application run-time.
        //
        //    2.) Android camera drivers seem to always deliver landscape images
        //        thus QCAR SDK components (e.g. camera capturing) need to know 
        //        when we are in portrait mode. Before Android 2.2 there is no 
        //        standard, device-independent way to let the camera driver know 
        //        that we are in portrait mode as each device seems to require a
        //        different combination of settings to rotate camera preview 
        //        frames images to match portrait mode views. Because of this,
        //        we suggest that the activity using the QCAR SDK be locked
        //        to landscape mode if you plan to support Android 2.1 devices
        //        as well. Froyo is fine with both orientations.
        int screenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        
        // Apply screen orientation
        setRequestedOrientation(screenOrientation);
        
        // Pass on screen orientation info to native code
        setActivityPortraitMode(screenOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        
        // Query display dimensions
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenWidth = metrics.widthPixels;
        mScreenHeight = metrics.heightPixels;

        // As long as this window is visible to the user, keep the device's
        // screen turned on and bright.
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
              
        // Create and add the splash screen view
        /*mSplashScreenView = new ImageView(this);
        mSplashScreenView.setImageResource(mSplashScreenImageResource);
        addContentView(mSplashScreenView, new LayoutParams(
                        LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
        */
        mSplashScreenStartTime = System.currentTimeMillis();

    }
    
    



    /** Initializes AR application components. */
    private void initApplicationAR()
    {        
        // Do application initialization in native code (e.g. registering
        // callbacks, etc.)
        initApplicationNative(mScreenWidth, mScreenHeight);

        // Create OpenGL ES view:
        int depthSize = 16;
        int stencilSize = 0;
        boolean translucent = QCAR.requiresAlpha();
        
        mGlView = new QCARSampleGLView(this);
        mGlView.init(mQCARFlags, translucent, depthSize, stencilSize);
        
        //mGlView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        
        mRenderer = new SlingShotRenderer();
        mRenderer.setOnFrameRenderCallback(mOnFrameRenderCallback);
        mGlView.setRenderer(mRenderer);
    }

    /** Invoked the first time when the options menu is displayed to give
     *  the Activity a chance to populate its Menu with menu items. */
    public boolean onCreateOptionsMenu(Menu menu)
    {
        super.onCreateOptionsMenu(menu);
        
        menu.add("Toggle flash");
        menu.add("Autofocus");
        
        SubMenu focusModes = menu.addSubMenu("Focus Modes");
        focusModes.add("Auto Focus").setCheckable(true);
        focusModes.add("Fixed Focus").setCheckable(true);
        focusModes.add("Infinity").setCheckable(true);
        focusModes.add("Macro Mode").setCheckable(true);
        
        return true;
    }
    
    /** Invoked when the user selects an item from the Menu */
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if(item.getTitle().equals("Toggle flash"))
        {
            mFlash = !mFlash;
            boolean result = toggleFlash(mFlash);
            DebugLog.LOGI("Toggle flash "+(mFlash?"ON":"OFF")+" "+(result?"WORKED":"FAILED")+"!!");
        }
        else if(item.getTitle().equals("Autofocus"))
        {
            boolean result = autofocus();
            DebugLog.LOGI("Autofocus requested"+(result?" successfully.":".  Not supported in current mode or on this device."));
        }
        else 
        {
            int arg = -1;
            if(item.getTitle().equals("Auto Focus"))
                arg = 0;
            if(item.getTitle().equals("Fixed Focus"))
                arg = 1;
            if(item.getTitle().equals("Infinity"))
                arg = 2;
            if(item.getTitle().equals("Macro Mode"))
                arg = 3;
            
            if(arg != -1)
            {
                item.setChecked(true);
                if(checked!= null)
                    checked.setChecked(false);
                checked = item;
                
                boolean result = setFocusMode(arg);
                
                DebugLog.LOGI("Requested Focus mode "+item.getTitle()+(result?" successfully.":".  Not supported on this device."));
            }
        }
        
        return true;
    }
    

    
    /** Returns the number of registered textures. */
    public int getTextureCount()
    {
        return mTextures.size();
    }

    
    /** Returns the texture object at the specified index. */
    public Texture getTexture(int i)
    {
        return mTextures.elementAt(i);
    }

    
    /** A helper for loading native libraries stored in "libs/armeabi*". */
    public static boolean loadLibrary(String nLibName)
    {
        try
        {
            System.loadLibrary(nLibName);
            DebugLog.LOGI("Native library lib" + nLibName + ".so loaded");
            return true;
        }
        catch (UnsatisfiedLinkError ulee)
        {
            DebugLog.LOGE("The library lib" + nLibName +
                            ".so could not be loaded");
        }
        catch (SecurityException se)
        {
            DebugLog.LOGE("The library lib" + nLibName +
                            ".so was not allowed to be loaded");
        }
        
        return false;
    }    
    
    private void score(){
        mScored = true;

        mHandler.post(new Runnable() {
            @Override
            public void run() {

                mCongratsView.show();

                removeButtons();
            }
        });

        //reset the scored state after a delay
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {

                mCongratsView.hide();
                mScored = false;
                mRenderer.mIsActive = true;
                resetScored();
                showButtons();
            }
        }, 5000);
    }

    /** native method for querying the OpenGL ES version.
     * Returns 1 for OpenGl ES 1.1, returns 2 for OpenGl ES 2.0. */
    public native int getOpenGlEsVersionNative();

    /** Native sample initialization. */
    public native void onQCARInitializedNative();

    /** Native function to initialize the application. */
    private native void initApplicationNative(int width, int height);

    /** Tells native code whether we are in portait or landscape mode */
    private native void setActivityPortraitMode(boolean isPortrait);

    private native void startCamera();
    private native void stopCamera();
    private native boolean toggleFlash(boolean flash);
    private native boolean autofocus();
    private native boolean setFocusMode(int mode);
    private native void setButtonArea(float w, float h);
    private native void showButtons(float x, float y);
    private native void setNumCols(int cols);
    private native void setNumRows(int rows);
    private native void setTargetObjectSize(float size);
    private native float getAvailableWidth();
    private native float getAvailableHeight();
    private native float getWidth();
    private native float getHeight();
    private native void removeButtons();
    private native boolean getScored();
    private native void resetScored();
    private native void setCanScore(boolean val);
}
