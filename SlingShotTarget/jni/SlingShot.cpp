/*==============================================================================
            Copyright (c) 2010-2011 QUALCOMM Incorporated.
            All Rights Reserved.
            Qualcomm Confidential and Proprietary
            
@file 
    SlingShot.cpp

@brief
    Sample for SlingShot

==============================================================================*/


#include <jni.h>
#include <android/log.h>
#include <stdio.h>
#include <string.h>
#include <assert.h>

#ifdef USE_OPENGL_ES_1_1
#include <GLES/gl.h>
#include <GLES/glext.h>
#else
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#endif

#include <QCAR/QCAR.h>
#include <QCAR/UpdateCallback.h>
#include <QCAR/CameraDevice.h>
#include <QCAR/Renderer.h>
#include <QCAR/VideoBackgroundConfig.h>
#include <QCAR/Trackable.h>
#include <QCAR/Tool.h>
#include <QCAR/Tracker.h>
#include <QCAR/CameraCalibration.h>
#include <QCAR/ImageTarget.h>
#include <QCAR/VirtualButton.h>
#include <QCAR/Rectangle.h>

#include "SampleUtils.h"
#include "Texture.h"
#include "CubeShaders.h"
#include "LineShaders.h"
#include "Teapot.h"

#ifdef __cplusplus
extern "C"
{
#endif

// Textures:
int textureCount                = 0;
Texture** textures              = 0;

// OpenGL ES 2.0 specific:
#ifdef USE_OPENGL_ES_2_0
unsigned int shaderProgramID    = 0;
GLint vertexHandle              = 0;
GLint normalHandle              = 0;
GLint textureCoordHandle        = 0;
GLint mvpMatrixHandle           = 0;
#endif

// Screen dimensions:
unsigned int screenWidth        = 0;
unsigned int screenHeight       = 0;

// Indicates whether screen is in portrait (true) or landscape (false) mode
bool isActivityInPortraitMode   = false;

// The projection matrix used for rendering virtual objects:
QCAR::Matrix44F projectionMatrix;

// Constants:

// The Goal button name
static const char* BUTTON_NAME = "goal";

//Whether to render the button borders
static const bool RENDER_BUTTON_BORDERS = true;

static const bool RENDER_OBJECT = true;

static const float IMAGE_TARGET_WIDTH = 247.0f;
static const float IMAGE_TARGET_HEIGHT = 172.0f;

// OpenGL ES 2.0 specific for rendering borders
unsigned int vbShaderProgramID  = 0;
GLint vbVertexHandle            = 0;

//Whether the player has scored or not
bool mScored = false;

bool mCanScore = false;

//The offset of the currently showing button grid
float mButtonOffset[] = {20.0, 20.0};

//The area of the individual buttons
float mButtonArea[] = {20.0, 16.0};

bool mUpdateButtons = false;
bool mHasButtons    = false;

int mCols = 4;
int mRows = 3;

float mAvailableArea[] = {
                            IMAGE_TARGET_WIDTH - (mButtonArea[0] * mCols),
                            IMAGE_TARGET_HEIGHT - (mButtonArea[1] * mRows)
                            };

float kObjectScale = 1.0f;



JNIEXPORT int JNICALL
Java_orbotix_slingshot_SlingShotActivity_getOpenGlEsVersionNative(JNIEnv *, jobject)
{
#ifdef USE_OPENGL_ES_1_1        
    return 1;
#else
    return 2;
#endif
}


JNIEXPORT void JNICALL
Java_orbotix_slingshot_SlingShotActivity_setActivityPortraitMode(JNIEnv *, jobject, jboolean isPortrait)
{
    isActivityInPortraitMode = isPortrait;
}


JNIEXPORT void JNICALL
Java_orbotix_slingshot_SlingShotActivity_onQCARInitializedNative(JNIEnv *, jobject)
{
    // Comment in to enable tracking of up to 2 targets simultaneously and
    // split the work over multiple frames:
    //QCAR::setHint(QCAR::HINT_MAX_SIMULTANEOUS_IMAGE_TARGETS, 2);
    // QCAR::setHint(QCAR::HINT_IMAGE_TARGET_MULTI_FRAME_ENABLED, 1);
}

JNIEXPORT void JNICALL
Java_orbotix_slingshot_SlingShotActivity_setTargetObjectSize(JNIEnv *, jobject, jfloat size){
    kObjectScale = size;
}

JNIEXPORT void JNICALL
Java_orbotix_slingshot_SlingShotActivity_setCanScore(JNIEnv *, jobject, jboolean val){
    mCanScore = val;
}

JNIEXPORT void JNICALL
Java_orbotix_slingshot_SlingShotActivity_setNumCols(JNIEnv *, jobject, jint cols){
    mCols = cols;
}

JNIEXPORT void JNICALL
Java_orbotix_slingshot_SlingShotActivity_setNumRows(JNIEnv *, jobject, jint rows){
    mRows = rows;
}

QCAR::ImageTarget* getTarget(int target_index){

    LOG("SlingShot::getTarget getting target.");

    QCAR::Trackable* trackable = QCAR::Tracker::getInstance().getTrackable(target_index);

    assert(trackable->getType() == QCAR::Trackable::IMAGE_TARGET);
    QCAR::ImageTarget* imageTarget = static_cast<QCAR::ImageTarget*>(trackable);

    if(imageTarget != NULL){
        LOG("Aquired target at index %d", target_index);
    }else{
        LOG("Failed to get target at index %d", target_index);
    }
    return imageTarget;
}

float getTargetWidth(int target_index){

    QCAR::ImageTarget* imageTarget = getTarget(target_index);

    QCAR::Vec2F vec = imageTarget->getSize();

    LOG("SlingShot::getTargetWidth target width: %f", vec.data[0]);
    return vec.data[0];
}

float getTargetHeight(int target_index){

    QCAR::ImageTarget* imageTarget = getTarget(target_index);

    QCAR::Vec2F vec = imageTarget->getSize();
    return vec.data[1];
}

JNIEXPORT float JNICALL
Java_orbotix_slingshot_SlingShotActivity_getAvailableWidth(JNIEnv *, jobject){

    return IMAGE_TARGET_WIDTH - (mButtonArea[0] * mCols);
}

JNIEXPORT float JNICALL
Java_orbotix_slingshot_SlingShotActivity_getWidth(JNIEnv *, jobject){
    return IMAGE_TARGET_WIDTH;
}

JNIEXPORT float JNICALL
Java_orbotix_slingshot_SlingShotActivity_getAvailableHeight(JNIEnv *, jobject){

    return IMAGE_TARGET_HEIGHT - (mButtonArea[1] * mRows);
}

JNIEXPORT float JNICALL
Java_orbotix_slingshot_SlingShotActivity_getHeight(JNIEnv *, jobject){
    return IMAGE_TARGET_HEIGHT;
}

JNIEXPORT float JNICALL
Java_orbotix_slingshot_SlingShotActivity_getTargetWidth(JNIEnv *, jobject){
    return getTargetWidth(0);
}

JNIEXPORT float JNICALL
Java_orbotix_slingshot_SlingShotActivity_getTargetHeight(JNIEnv *, jobject){
    return getTargetHeight(0);
}



JNIEXPORT void JNICALL
Java_orbotix_slingshot_SlingShotActivity_setButtonArea(JNIEnv *, jobject, jfloat w, jfloat h){
    mButtonArea[0] = w;
    mButtonArea[1] = h;
}

JNIEXPORT void JNICALL
Java_orbotix_slingshot_SlingShotActivity_showButtons(JNIEnv *, jobject, jfloat x, jfloat y){

    LOG("Java_orbotix_slingshot_SlingShotActivity_showButtons");
    LOG("Button Offset is %f, %f. Setting it to %f, %f", mButtonOffset[0], mButtonOffset[1], x, y);
    mButtonOffset[0] = x;
    mButtonOffset[1] = y;
    mUpdateButtons = true;
    LOG("Button Offset is now %f, %f.", mButtonOffset[0], mButtonOffset[1]);
}

JNIEXPORT void JNICALL
Java_orbotix_slingshot_SlingShotActivity_removeButtons(JNIEnv *, jobject){

    mUpdateButtons = true;
}

/**
    Indicates whether or not the user has scored
*/
JNIEXPORT bool JNICALL
Java_orbotix_slingshot_SlingShotActivity_getScored(JNIEnv *, jobject){

    return mScored;
}

/*
    Resets the 'scored' value to false
*/
JNIEXPORT void JNICALL
Java_orbotix_slingshot_SlingShotActivity_resetScored(JNIEnv *, jobject){

    mScored = false;
}

JNIEXPORT void JNICALL
Java_orbotix_slingshot_SlingShotRenderer_renderFrame(JNIEnv *, jobject)
{
    //LOG("Java_orbotix_slingshot_GLRenderer_renderFrame");

    // Clear color and depth buffer 
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

    // Render video background:
    QCAR::State state = QCAR::Renderer::getInstance().begin();
        
#ifdef USE_OPENGL_ES_1_1
    // Set GL11 flags:
    glEnableClientState(GL_VERTEX_ARRAY);
    glEnableClientState(GL_NORMAL_ARRAY);
    glEnableClientState(GL_TEXTURE_COORD_ARRAY);

    glEnable(GL_TEXTURE_2D);
    glDisable(GL_LIGHTING);
        
#endif

    glEnable(GL_DEPTH_TEST);
    glEnable(GL_CULL_FACE);

    // Did we find any trackables this frame?
    for(int tIdx = 0; tIdx < state.getNumActiveTrackables(); tIdx++)
    {

        // Get the trackable:
        const QCAR::Trackable* trackable = state.getActiveTrackable(tIdx);
        QCAR::Matrix44F modelViewMatrix =
            QCAR::Tool::convertPose2GLMatrix(trackable->getPose());        

        // Choose the texture based on the target name:
        int textureIndex = (!strcmp(trackable->getName(), "stones")) ? 0 : 1;

        //Determine if the button is pressed
        // The image target:
        assert(trackable->getType() == QCAR::Trackable::IMAGE_TARGET);
        const QCAR::ImageTarget* target =
            static_cast<const QCAR::ImageTarget*>(trackable);

        unsigned int num_buttons = target->getNumVirtualButtons();

        //Vertices
        GLfloat verts[num_buttons*24];
        int counter = 0;



        //The buttons
        if(target->getNumVirtualButtons() > 0){

            LOG("Found %d buttons.", target->getNumVirtualButtons());

            bool pressed = false;

            for(int i=0;i<num_buttons;i++){

                LOG("Evaluating button %d", i+1);

                const QCAR::VirtualButton* button = target->getVirtualButton(i);

                if(mCanScore && !pressed && button->isPressed()){
                    pressed = true;
                    mScored = true;
                }

                //If pressed, and we're not rendering buttons, then we're done here
                if(pressed && !RENDER_BUTTON_BORDERS){
                    break;
                }

                if(RENDER_BUTTON_BORDERS){

                    LOG("Rendering button %d.", i+1);

                    //Get button area
                    const QCAR::Area* vbArea = &button->getArea();
                    assert(vbArea->getType() == QCAR::Area::RECTANGLE);
                    const QCAR::Rectangle* rect = static_cast<const QCAR::Rectangle*>(vbArea);

                    //Fill vertices
                    //top edge
                    verts[counter]    = rect->getLeftTopX();
                    verts[counter+1]  = rect->getLeftTopY();
                    verts[counter+2]  = 0.0f;
                    verts[counter+3]  = rect->getRightBottomX();
                    verts[counter+4]  = rect->getLeftTopY();
                    verts[counter+5]  = 0.0f;
                    //right edge
                    verts[counter+6]  = rect->getRightBottomX();
                    verts[counter+7]  = rect->getLeftTopY();
                    verts[counter+8]  = 0.0f;

                    verts[counter+9]  = rect->getRightBottomX();
                    verts[counter+10] = rect->getRightBottomY();
                    verts[counter+11] = 0.0f;
                    //bottom edge
                    verts[counter+12] = rect->getRightBottomX();
                    verts[counter+13] = rect->getRightBottomY();
                    verts[counter+14] = 0.0f;
                    verts[counter+15] = rect->getLeftTopX();
                    verts[counter+16] = rect->getRightBottomY();
                    verts[counter+17] = 0.0f;
                    //left edge
                    verts[counter+18] = rect->getLeftTopX();
                    verts[counter+19] = rect->getRightBottomY();
                    verts[counter+20] = 0.0f;
                    verts[counter+21] = rect->getLeftTopX();
                    verts[counter+22] = rect->getLeftTopY();
                    verts[counter+23] = 0.0f;

                    counter += 24;
                }
            }

            if(mScored){

                //If button is pressed, swap the textures
                textureIndex = 2;
            }
        }

        const Texture* const thisTexture = textures[textureIndex];

#ifdef USE_OPENGL_ES_1_1
        // Load projection matrix:
        glMatrixMode(GL_PROJECTION);
        glLoadMatrixf(projectionMatrix.data);

        // Load model view matrix:
        glMatrixMode(GL_MODELVIEW);
        glLoadMatrixf(modelViewMatrix.data);
        glTranslatef(0.f, 0.f, kObjectScale);
        glScalef(kObjectScale, kObjectScale, kObjectScale);

        // Draw object:
        glBindTexture(GL_TEXTURE_2D, thisTexture->mTextureID);
        glTexCoordPointer(2, GL_FLOAT, 0, (const GLvoid*) &teapotTexCoords[0]);
        glVertexPointer(3, GL_FLOAT, 0, (const GLvoid*) &teapotVertices[0]);
        glNormalPointer(GL_FLOAT, 0,  (const GLvoid*) &teapotNormals[0]);
        glDrawElements(GL_TRIANGLES, NUM_TEAPOT_OBJECT_INDEX, GL_UNSIGNED_SHORT,
                       (const GLvoid*) &teapotIndices[0]);
#else

        QCAR::Matrix44F modelViewProjection;

        SampleUtils::multiplyMatrix(&projectionMatrix.data[0],
                                            &modelViewMatrix.data[0],
                                            &modelViewProjection.data[0]);


        //Draw button borders, if needed
        if(RENDER_BUTTON_BORDERS && counter > 0){

            // Render frame around button
            glUseProgram(vbShaderProgramID);

            glVertexAttribPointer(vbVertexHandle, 3, GL_FLOAT, GL_FALSE, 0,
                (const GLvoid*) &verts[0]);

            glEnableVertexAttribArray(vbVertexHandle);

            glUniformMatrix4fv(mvpMatrixHandle, 1, GL_FALSE,
                (GLfloat*)&modelViewProjection.data[0] );

            // We multiply by 8 because that's the number of vertices per button
            // The reason is that GL_LINES considers only pairs. So some vertices
            // must be repeated.
            glDrawArrays(GL_LINES, 0, target->getNumVirtualButtons()*8);

            SampleUtils::checkGlError("VirtualButtons drawButton");

            glDisableVertexAttribArray(vbVertexHandle);
        }

        //Draw teapot

        if(target->getNumVirtualButtons() > 0 && RENDER_OBJECT){

            float teapot_pos[] = {mButtonOffset[0] + (mButtonArea[0] * (mCols / 2)), mButtonOffset[1] + (mButtonArea[1] * (mRows / 2))};

            //SampleUtils::translatePoseMatrix(0.0f, 0.0f, kObjectScale, &modelViewMatrix.data[0]);
            SampleUtils::translatePoseMatrix(teapot_pos[0], teapot_pos[1], kObjectScale, &modelViewMatrix.data[0]);

            SampleUtils::scalePoseMatrix(kObjectScale, kObjectScale, kObjectScale,
                                         &modelViewMatrix.data[0]);
            SampleUtils::multiplyMatrix(&projectionMatrix.data[0],
                                        &modelViewMatrix.data[0] ,
                                        &modelViewProjection.data[0]);


            glUseProgram(shaderProgramID);

            glVertexAttribPointer(vertexHandle, 3, GL_FLOAT, GL_FALSE, 0,
                                  (const GLvoid*) &teapotVertices[0]);
            glVertexAttribPointer(normalHandle, 3, GL_FLOAT, GL_FALSE, 0,
                                  (const GLvoid*) &teapotNormals[0]);
            glVertexAttribPointer(textureCoordHandle, 2, GL_FLOAT, GL_FALSE, 0,
                                  (const GLvoid*) &teapotTexCoords[0]);

            glEnableVertexAttribArray(vertexHandle);
            glEnableVertexAttribArray(normalHandle);
            glEnableVertexAttribArray(textureCoordHandle);

            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, thisTexture->mTextureID);
            glUniformMatrix4fv(mvpMatrixHandle, 1, GL_FALSE,
                               (GLfloat*)&modelViewProjection.data[0] );
            glDrawElements(GL_TRIANGLES, NUM_TEAPOT_OBJECT_INDEX, GL_UNSIGNED_SHORT,
                           (const GLvoid*) &teapotIndices[0]);
        }



        SampleUtils::checkGlError("SlingShot renderFrame");
#endif

    }

    glDisable(GL_DEPTH_TEST);

#ifdef USE_OPENGL_ES_1_1        
    glDisable(GL_TEXTURE_2D);
    glDisableClientState(GL_VERTEX_ARRAY);
    glDisableClientState(GL_NORMAL_ARRAY);
    glDisableClientState(GL_TEXTURE_COORD_ARRAY);
#else
    glDisableVertexAttribArray(vertexHandle);
    glDisableVertexAttribArray(normalHandle);
    glDisableVertexAttribArray(textureCoordHandle);
#endif

    //Get the goal button, if there is one


    QCAR::Renderer::getInstance().end();
}



void
configureVideoBackground()
{
    // Get the default video mode:
    QCAR::CameraDevice& cameraDevice = QCAR::CameraDevice::getInstance();
    //QCAR::VideoMode videoMode = cameraDevice.getVideoMode(QCAR::CameraDevice::MODE_DEFAULT);

    QCAR::VideoMode videoMode = cameraDevice.getVideoMode(QCAR::CameraDevice::MODE_OPTIMIZE_QUALITY);

    //QCAR::VideoMode videoMode = cameraDevice.getVideoMode(QCAR::CameraDevice::MODE_OPTIMIZE_SPEED);

    // Configure the video background
    QCAR::VideoBackgroundConfig config;
    config.mEnabled = true;
    config.mSynchronous = true;
    config.mPosition.data[0] = 0.0f;
    config.mPosition.data[1] = 0.0f;
    
    if (isActivityInPortraitMode)
    {
        //LOG("configureVideoBackground PORTRAIT");
        config.mSize.data[0] = videoMode.mHeight
                                * (screenHeight / (float)videoMode.mWidth);
        config.mSize.data[1] = screenHeight;
    }
    else
    {
        //LOG("configureVideoBackground LANDSCAPE");
        config.mSize.data[0] = screenWidth;
        config.mSize.data[1] = videoMode.mHeight
                            * (screenWidth / (float)videoMode.mWidth);
    }

    // Set the config:
    QCAR::Renderer::getInstance().setVideoBackgroundConfig(config);
}

void removeVirtualButtons(QCAR::ImageTarget* imageTarget){

    int num_buttons = imageTarget->getNumVirtualButtons();
    for(unsigned short x=0;x<num_buttons;x++){
        QCAR::VirtualButton* virtualButton = imageTarget->getVirtualButton(0);

        if(virtualButton != NULL){
            LOG("Destroying Virtual Button %s", virtualButton->getName());
            imageTarget->destroyVirtualButton(virtualButton);
        }
    }
}

void createVirtualButtons(QCAR::ImageTarget* imageTarget){

    int i = 0;
    LOG("Offset is %f, %f", mButtonOffset[0], mButtonOffset[1]);
    for(int col=0;col<mCols;col++){
        for(int row=0;row<mRows;row++){

            float left   = mButtonOffset[0] + ((col) * mButtonArea[0]);
            float top    = mButtonOffset[1] + ((row) * mButtonArea[1]);
            float right  = left + mButtonArea[0];
            float bottom = top + mButtonArea[1];

            char numstr[20];
            sprintf(numstr, "goal_%d_%d", col, row);
            const char* name = numstr;

            QCAR::Rectangle vbRectangle(left, top, right, bottom);
            QCAR::VirtualButton* virtualButton = imageTarget->createVirtualButton(name, vbRectangle);

            LOG("Created Virtual Button");

            virtualButton->setEnabled(true);
            virtualButton->setSensitivity(QCAR::VirtualButton::HIGH);

            LOG("Configured Virtual Button #%d, '%s', at (%f, %f), %fx%f.", i+1, name, left, top, mButtonArea[0], mButtonArea[1]);
            i++;
        }
    }
}

// Object to receive update callbacks from QCAR SDK
class VirtualButton_UpdateCallback : public QCAR::UpdateCallback
{
    virtual void QCAR_onUpdate(QCAR::State& /*state*/)
    {
        if (mUpdateButtons)
        {
            // Update runs in the tracking thread therefore it is guaranteed that the tracker is
            // not doing anything at this point. => Reconfiguration is possible.

            unsigned short num_trackables = QCAR::Tracker::getInstance().getNumTrackables();
            assert(num_trackables > 0);

            for(int i=0;i<num_trackables;i++){

                QCAR::Trackable* trackable = QCAR::Tracker::getInstance().getTrackable(i);

                assert(trackable);
                assert(trackable->getType() == QCAR::Trackable::IMAGE_TARGET);
                QCAR::ImageTarget* imageTarget = static_cast<QCAR::ImageTarget*>(trackable);


                if (imageTarget->getNumVirtualButtons() > 0){
                    LOG("Removing All Buttons from trackable %d", i+1);

                    removeVirtualButtons(imageTarget);

                    mHasButtons = false;
                }else{

                    LOG("Adding All Buttons to trackable %d", i+1);

                    createVirtualButtons(imageTarget);

                    mHasButtons = true;
                }
            }


            mUpdateButtons = false;

            LOG("Finished updating buttons.");
        }
    }
} qcarUpdate;


JNIEXPORT void JNICALL
Java_orbotix_slingshot_SlingShotActivity_initApplicationNative(
                            JNIEnv* env, jobject obj, jint width, jint height)
{
    LOG("Java_orbotix_slingshot_SlingShotActivity_initApplicationNative");
    
    // Store screen dimensions
    screenWidth = width;
    screenHeight = height;
        
    // Handle to the activity class:
    jclass activityClass = env->GetObjectClass(obj);

    //Register callback to handle adding and removing buttons
    QCAR::registerCallback(&qcarUpdate);

    jmethodID getTextureCountMethodID = env->GetMethodID(activityClass,
                                                    "getTextureCount", "()I");
    if (getTextureCountMethodID == 0)
    {
        LOG("Function getTextureCount() not found.");
        return;
    }

    textureCount = env->CallIntMethod(obj, getTextureCountMethodID);    
    if (!textureCount)
    {
        LOG("getTextureCount() returned zero.");
        return;
    }

    textures = new Texture*[textureCount];

    jmethodID getTextureMethodID = env->GetMethodID(activityClass,
        "getTexture", "(I)Lorbotix/slingshot/Texture;");

    if (getTextureMethodID == 0)
    {
        LOG("Function getTexture() not found.");
        return;
    }

    // Register the textures
    for (int i = 0; i < textureCount; ++i)
    {

        jobject textureObject = env->CallObjectMethod(obj, getTextureMethodID, i); 
        if (textureObject == NULL)
        {
            LOG("GetTexture() returned zero pointer");
            return;
        }

        textures[i] = Texture::create(env, textureObject);
    }
}


JNIEXPORT void JNICALL
Java_orbotix_slingshot_SlingShotActivity_deinitApplicationNative(
                                                        JNIEnv* env, jobject obj)
{
    LOG("Java_orbotix_slingshot_SlingShotActivity_deinitApplicationNative");

    // Release texture resources
    if (textures != 0)
    {    
        for (int i = 0; i < textureCount; ++i)
        {
            delete textures[i];
            textures[i] = NULL;
        }
    
        delete[]textures;
        textures = NULL;
        
        textureCount = 0;
    }
}


JNIEXPORT void JNICALL
Java_orbotix_slingshot_SlingShotActivity_startCamera(JNIEnv *,
                                                                         jobject)
{
    LOG("Java_orbotix_slingshot_SlingShotActivity_startCamera");

    // Initialize the camera:
    if (!QCAR::CameraDevice::getInstance().init())
        return;

    // Configure the video background
    configureVideoBackground();

    // Select the default mode:
    if (!QCAR::CameraDevice::getInstance().selectVideoMode(
                                QCAR::CameraDevice::MODE_DEFAULT))
        return;

    // Start the camera:
    if (!QCAR::CameraDevice::getInstance().start())
        return;

    // Uncomment to enable flash
    //if(QCAR::CameraDevice::getInstance().setFlashTorchMode(true))
    //	LOG("IMAGE TARGETS : enabled torch");

    // Uncomment to enable infinity focus mode, or any other supported focus mode
    // See CameraDevice.h for supported focus modes
    QCAR::CameraDevice::getInstance().setFocusMode(QCAR::CameraDevice::FOCUS_MODE_AUTO);

    // Start the tracker:
    QCAR::Tracker::getInstance().start();
 
    // Cache the projection matrix:
    const QCAR::Tracker& tracker = QCAR::Tracker::getInstance();
    const QCAR::CameraCalibration& cameraCalibration =
                                    tracker.getCameraCalibration();
    projectionMatrix = QCAR::Tool::getProjectionGL(cameraCalibration, 2.0f,
                                            2000.0f);
}


JNIEXPORT void JNICALL
Java_orbotix_slingshot_SlingShotActivity_stopCamera(JNIEnv *,
                                                                   jobject)
{
    LOG("Java_orbotix_slingshot_SlingShotActivity_stopCamera");

    QCAR::Tracker::getInstance().stop();

    QCAR::CameraDevice::getInstance().stop();
    QCAR::CameraDevice::getInstance().deinit();
}

JNIEXPORT jboolean JNICALL
Java_orbotix_slingshot_SlingShotActivity_toggleFlash(JNIEnv*, jobject, jboolean flash)
{
    return QCAR::CameraDevice::getInstance().setFlashTorchMode((flash==JNI_TRUE)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_orbotix_slingshot_SlingShotActivity_autofocus(JNIEnv*, jobject)
{
    return QCAR::CameraDevice::getInstance().startAutoFocus()?JNI_TRUE:JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_orbotix_slingshot_SlingShotActivity_setFocusMode(JNIEnv*, jobject, jint mode)
{
    return QCAR::CameraDevice::getInstance().setFocusMode(mode)?JNI_TRUE:JNI_FALSE;
}


JNIEXPORT void JNICALL
Java_orbotix_slingshot_SlingShotRenderer_initRendering(
                                                    JNIEnv* env, jobject obj)
{
    LOG("Java_orbotix_slingshot_SlingShotRenderer_initRendering");

    // Define clear color
    glClearColor(0.0f, 0.0f, 0.0f, QCAR::requiresAlpha() ? 0.0f : 1.0f);
    
    // Now generate the OpenGL texture objects and add settings
    for (int i = 0; i < textureCount; ++i)
    {
        glGenTextures(1, &(textures[i]->mTextureID));
        glBindTexture(GL_TEXTURE_2D, textures[i]->mTextureID);
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, textures[i]->mWidth,
                textures[i]->mHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE,
                (GLvoid*)  textures[i]->mData);
    }
#ifndef USE_OPENGL_ES_1_1
  
    shaderProgramID     = SampleUtils::createProgramFromBuffer(cubeMeshVertexShader,
                                                            cubeFragmentShader);

    vertexHandle        = glGetAttribLocation(shaderProgramID,
                                                "vertexPosition");
    normalHandle        = glGetAttribLocation(shaderProgramID,
                                                "vertexNormal");
    textureCoordHandle  = glGetAttribLocation(shaderProgramID,
                                                "vertexTexCoord");
    mvpMatrixHandle     = glGetUniformLocation(shaderProgramID,
                                                "modelViewProjectionMatrix");

    // OpenGL setup for Virtual Buttons
    vbShaderProgramID   = SampleUtils::createProgramFromBuffer(lineMeshVertexShader,
                                                               lineFragmentShader);

    vbVertexHandle      = glGetAttribLocation(vbShaderProgramID, "vertexPosition");

#endif

}


JNIEXPORT void JNICALL
Java_orbotix_slingshot_SlingShotRenderer_updateRendering(
                        JNIEnv* env, jobject obj, jint width, jint height)
{
    LOG("Java_orbotix_slingshot_SlingShotRenderer_updateRendering");
    
    // Update screen dimensions
    screenWidth = width;
    screenHeight = height;

    // Reconfigure the video background
    configureVideoBackground();
}







#ifdef __cplusplus
}
#endif
