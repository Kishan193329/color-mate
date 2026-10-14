uniform mat4 u_mvpMatrix;

attribute vec4 a_vertexPos;
attribute vec2 a_texCoord;

varying vec2 v_texCoord;
uniform float scaleFactor;

void main()
{
    gl_PointSize = scaleFactor;
    v_texCoord = a_texCoord;
    gl_Position = u_mvpMatrix * a_vertexPos;
}