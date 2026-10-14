uniform sampler2D tex;
uniform sampler2D normalMap;
uniform sampler2D outline;
uniform sampler2D envMap;
varying highp vec2 texCoord;
varying highp vec3 colorMul;
varying highp mat3 normalMatrix;
varying highp vec3 toViewScreenSpace;
uniform mediump float specularPow;
uniform mediump float specularMul;
uniform mediump float mirrorMul;
uniform mediump float mirrorPow;
void main ()
{
  mediump vec3 r;
  mediump vec3 y;
  mediump vec3 g;
  highp vec3 x;
  mediump vec4 b;
  mediump vec4 z;
  lowp vec4 a;
  a = texture2D (tex, texCoord);
  z = a;
  lowp vec4 _;
  _ = texture2D (outline, texCoord);
  b = _;
  lowp vec3 A;
  A = texture2D (normalMap, texCoord).xyz;
  x = A;
  highp vec3 Y;
  Y.x = (x.x - 0.5);
  Y.y = (0.5 - x.y);
  Y.z = (x.z - 0.5);
  x = (normalMatrix * normalize(Y));
  highp float R;
  R = dot (x, vec3(-0.3481553, 0.3481553, 0.8703883));
  highp float X;
  X = dot (x, vec3(0.912871, -0.3651484, 0.1825742));
  highp vec3 W;
  W = (vec3(((0.7 + 
    ((X * X) * 0.3)
  ) + (
    (R * R)
   * 0.3))) * colorMul);
  g = W;
  highp vec3 Z;
  Z = (toViewScreenSpace - (2.0 * (
    dot (x, toViewScreenSpace)
   * x)));
  y = Z;
  mediump vec2 o;
  o = (vec2(0.5, 0.5) + (y.xy * 0.5));
  lowp vec3 O;
  O = texture2D (envMap, o).xyz;
  r = O;
  highp float I;
  highp vec3 i;
  i = y;
  highp float l;
  l = dot (i, vec3(-0.3481553, 0.3481553, 0.8703883));
  highp float S;
  S = dot (i, vec3(0.912871, -0.3651484, 0.1825742));
  I = ((0.7 + (
    (S * S)
   * 0.3)) + ((l * l) * 0.3));
  mediump vec4 G;
  G.w = 1.0;
  G.xyz = (((
    (z.xyz * b.x)
   * 
    clamp (g, 0.0, 1.0)
  ) + (
    pow (r, vec3(mirrorPow))
   * mirrorMul)) + pow (vec3(clamp (
    (I * specularMul)
  , 0.0, 100.0)), vec3(specularPow)));
  gl_FragColor = G;
}

