attribute mediump vec4 vertex;
varying highp vec3 pixelNormal;
uniform mat4 projectionMatrix;
uniform mat4 lightMatrix;
void main ()
{
  mediump vec4 r;
  r.w = 1.0;
  mediump vec3 y;
  y = (vertex.xyz * 4.0);
  r.xyz = y;
  gl_Position = ((projectionMatrix * lightMatrix) * r);
  mediump vec4 g;
  g.w = 1.0;
  g.xyz = y;
  pixelNormal = g.xyz;
}

