attribute highp vec3 vertex;
attribute highp vec3 normal;
attribute highp vec2 uv;
attribute highp vec4 ao;
varying highp vec2 texCoord;
varying highp vec3 pixelNormal;
varying highp vec4 worldpos;
varying highp vec3 colorMul;
varying highp mat3 normalMatrix;
varying highp vec3 toViewScreenSpace;
varying highp vec3 toView;
uniform mat4 modelMatrix;
uniform mat4 cameraMatrix;
uniform mat4 projectionMatrix;
uniform mat4 lightMatrix;
void main ()
{
  highp mat4 r;
  r = modelMatrix;
  highp vec4 y;
  y.w = 1.0;
  y.xyz = vertex;
  worldpos = (modelMatrix * y);
  highp vec4 g;
  g.w = 1.0;
  g.xyz = vertex;
  highp vec4 x;
  x = ((projectionMatrix * r) * g);
  gl_Position = x;
  highp vec3 b;
  b.z = -1.0;
  b.xy = (x / x.w).xy;
  toViewScreenSpace.z = b.z;
  toViewScreenSpace.x = (b.x / projectionMatrix[0].x);
  toViewScreenSpace.y = (b.y / projectionMatrix[1].y);
  mat3 z;
  z[0] = cameraMatrix[0].xyz;
  z[1] = cameraMatrix[1].xyz;
  z[2] = cameraMatrix[2].xyz;
  toView = (z * normalize(toViewScreenSpace));
  pixelNormal = normal;
  highp vec4 a;
  a.w = 0.0;
  a.xyz = normal;
  highp vec3 _;
  _ = ((lightMatrix * r) * a).xyz;
  normalMatrix[2] = _;
  highp vec3 A;
  A.y = 0.0;
  A.x = _.z;
  A.z = -(_.x);
  normalMatrix[0] = normalize(A);
  highp vec3 Y;
  Y = normalMatrix[0];
  highp vec3 R;
  R = normalMatrix[2];
  normalMatrix[1] = -(normalize((
    (Y.yzx * R.zxy)
   - 
    (Y.zxy * R.yzx)
  )));
  texCoord = uv;
  colorMul = ao.xyz;
}

