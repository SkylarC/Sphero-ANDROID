/*==============================================================================
            Copyright (c) 2010-2011 QUALCOMM Incorporated.
            All Rights Reserved.
            Qualcomm Confidential and Proprietary
            
@file 
    ImageTargetsRenderer.java

@brief
    Sample for com.qualcomm.QCARSamples.ImageTargets

==============================================================================*/


package orbotix.slingshot;

import android.opengl.GLSurfaceView;
import com.qualcomm.QCAR.QCAR;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;



/** The renderer class for the com.qualcomm.QCARSamples.SlingShotActivity sample. */
public class SlingShotRenderer implements GLSurfaceView.Renderer
{
    public boolean mIsActive = false;
    
    /** Native function for initializing the renderer. */
    public native void initRendering();
    
    
    /** Native function to update the renderer. */
    public native void updateRendering(int width, int height);

    private native void setButtonArea(float w, float h);
    private native void showButtons(float x, float y);
    private native void removeButtons();

    /**
     * Callback to run on each frame render
     */
    private Runnable mOnFrameRenderCallback;
    
    private Runnable mOnCreatedCallback;

    
    /** Called when the surface is created or recreated. */
    public void onSurfaceCreated(GL10 gl, EGLConfig config)
    {
        DebugLog.LOGD("GLRenderer::onSurfaceCreated");

        // Call native function to initialize rendering:
        initRendering();
        
        // Call QCAR function to (re)initialize rendering after first use
        // or after OpenGL ES context was lost (e.g. after onPause/onResume):
        QCAR.onSurfaceCreated();

        if(mOnCreatedCallback != null){
            mOnCreatedCallback.run();
        }
    }
    
    
    /** Called when the surface changed size. */
    public void onSurfaceChanged(GL10 gl, int width, int height)
    {
        DebugLog.LOGD("GLRenderer::onSurfaceChanged");
        
        // Call native function to update rendering when render surface parameters have changed:
        updateRendering(width, height);

        // Call QCAR function to handle render surface size changes:
        QCAR.onSurfaceChanged(width, height);
    }

    public void setOnFrameRenderCallback(Runnable callback){
        mOnFrameRenderCallback = callback;
    }

    public void setmOnCreatedCallback(Runnable callback){
        mOnCreatedCallback = callback;
    }
    
    
    /** The native render function. */    
    public native void renderFrame();
    
    
    /** Called to draw the current frame. */
    public void onDrawFrame(GL10 gl)
    {
        if (!mIsActive)
            return;

        // Call our native function to render content
        renderFrame();

        //Run callback
        if(mOnFrameRenderCallback != null){
            mOnFrameRenderCallback.run();
        }
    }
}
