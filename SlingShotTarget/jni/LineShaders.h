/*==============================================================================
            Copyright (c) 2010-2011 QUALCOMM Incorporated.
            All Rights Reserved.
            Qualcomm Confidential and Proprietary
            
@file 
    LineShaders.h

@brief
    Defines OpenGL shaders as char* strings.

==============================================================================*/

#ifndef _QCAR_LINE_SHADERS_H_
#define _QCAR_LINE_SHADERS_H_

#ifndef USE_OPENGL_ES_1_1

static const char* lineMeshVertexShader = " \
  \
attribute vec4 vertexPosition; \
 \
uniform mat4 modelViewProjectionMatrix; \
 \
void main() \
{ \
   gl_Position = modelViewProjectionMatrix * vertexPosition; \
} \
";


static const char* lineFragmentShader = " \
 \
precision mediump float; \
 \
void main() \
{ \
   gl_FragColor = vec4(1.0, 1.0, 0.0, 1.0); \
} \
";

#endif

#endif // _QCAR_LINE_SHADERS_H_
