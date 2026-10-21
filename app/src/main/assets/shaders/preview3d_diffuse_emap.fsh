uniform sampler2D tex;
uniform sampler2D normalMap;
uniform sampler2D controlMap;
uniform sampler2D outline;
uniform sampler2D envMap;
uniform sampler2D lightMap;
varying highp vec2 texCoord;
varying highp vec4 worldpos;
varying highp vec3 colorMul;
varying highp mat3 normalMatrix;
varying highp vec3 toViewScreenSpace;
uniform mediump float specularPow;
uniform mediump float specularMul;
uniform mediump float mirrorMul;
uniform mediump float mirrorPow;
void main ()
{
  highp vec3 r;
  mediump vec3 y;
  mediump vec3 g;
  mediump vec3 x;
  highp vec3 b;
  mediump vec4 z;
  mediump vec4 a;
  mediump vec4 _;
  lowp vec4 A;
  A = texture2D (controlMap, texCoord);
  _ = A;
  lowp vec4 Y;
  Y = texture2D (tex, texCoord);
  a = Y;
  lowp vec4 R;
  R = texture2D (outline, texCoord);
  z = R;
  lowp vec3 X;
  X = texture2D (normalMap, texCoord).xyz;
  b = X;
  highp vec3 W;
  W.x = (b.x - 0.5);
  W.y = (0.5 - b.y);
  W.z = (b.z - 0.5);
  b = (normalMatrix * normalize(W));
  lowp vec4 Z;
  highp vec2 o;
  o = ((b.xy * 0.5) + vec2(0.5, 0.5));
  Z = texture2D (lightMap, o);
  highp vec3 O;
  O = (((Z.xyz * colorMul) * _.x) + _.zzz);
  x = O;
  highp vec3 I;
  I = (toViewScreenSpace - (2.0 * (
    dot (b, toViewScreenSpace)
   * b)));
  g = I;
  highp vec2 i;
  i = ((worldpos.xy + vec2(0.5, 0.5)) + (g.xy * 0.5));
  lowp vec3 l;
  l = texture2D (envMap, i).xyz;
  y = l;
  lowp vec4 S;
  mediump vec2 G;
  G = ((g.xy * 0.5) + vec2(0.5, 0.5));
  S = texture2D (lightMap, G);
  mediump vec3 B;
  B = (((
    (a.xyz * z.x)
   * 
    clamp (x, 0.0, 1.0)
  ) + (
    (pow (y, vec3(mirrorPow)) * _.y)
   * mirrorMul)) + (pow (
    (S.xyz * specularMul)
  , vec3(specularPow)) * _.y));
  r = B;
  highp vec4 c;
  c.w = 1.0;
  c.xyz = pow (r, vec3(0.75, 0.75, 0.75));
  gl_FragColor = c;
}

