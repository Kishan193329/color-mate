attribute mediump vec4 vertex;
varying highp vec3 pixelNormal;
uniform mat4 projectionMatrix;
uniform mat4 lightMatrix;
void main ()
{
  mediump vec4 r;
  r.w = 1.0;
  r.xyz = (vertex.xyz * 4.0);
  gl_Position = ((projectionMatrix * lightMatrix) * r);
  mediump vec3 y;
  y = normalize(vertex.xyz);
  pixelNormal = y;
}

