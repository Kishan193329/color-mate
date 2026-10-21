#extension GL_OES_EGL_image_external : require
precision mediump float;

uniform samplerExternalOES outline;
varying vec2 texCoord;

void main() {
    //gl_FragColor = vec4(texCoord.xy,0,1);//
    gl_FragColor = texture2D(outline,texCoord);
}

