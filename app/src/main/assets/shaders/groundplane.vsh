attribute mediump vec4 vertex;
varying highp vec3 pixelNormal;
varying highp vec2 texCoord;
uniform mat4 modelMatrix;
uniform mat4 projectionMatrix;
void main ()
{
  mediump vec4 r;
  r.w = 1.0;
  r.xyz = vertex.xyz;
  gl_Position = ((projectionMatrix * modelMatrix) * r);
  mediump vec4 y;
  y.w = 1.0;
  y.xyz = vertex.xyz;
  pixelNormal = y.xyz;
  texCoord = (vec2(0.5, 0.5) + (vertex.xy * 0.5));
}

