struct Light {
  highp vec3 dir;
  highp vec3 color;
  highp vec3 attrib;
};
uniform sampler2D tex;
uniform sampler2D normalMap;
uniform sampler2D controlMap;
uniform sampler2D envMap;
uniform sampler2D outline;
varying highp vec2 texCoord;
varying highp vec3 colorMul;
varying highp mat3 normalMatrix;
varying highp vec3 toViewScreenSpace;
uniform highp vec4 ambientLight;
uniform Light light1;
uniform Light light2;
uniform Light light3;
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
  mediump vec4 a;
  lowp vec4 _;
  _ = texture2D (controlMap, texCoord);
  a = _;
  lowp vec4 A;
  A = texture2D (tex, texCoord);
  z = A;
  lowp vec4 Y;
  Y = texture2D (outline, texCoord);
  b = Y;
  lowp vec3 R;
  R = texture2D (normalMap, texCoord).xyz;
  x = R;
  highp vec3 X;
  X.x = (x.x - 0.5);
  X.y = (0.5 - x.y);
  X.z = (x.z - 0.5);
  highp vec3 W;
  W = normalize((normalMatrix * normalize(X)));
  x = W;
  highp vec3 Z;
  Z = (((
    pow (clamp ((dot (W, light1.dir) - light1.attrib.x), 0.0, 1.0), light1.attrib.y)
   * light1.color) + (
    pow (clamp ((dot (W, light2.dir) - light2.attrib.x), 0.0, 1.0), light2.attrib.y)
   * light2.color)) + (pow (
    clamp ((dot (W, light3.dir) - light3.attrib.x), 0.0, 1.0)
  , light3.attrib.y) * light3.color));
  mediump vec3 o;
  o = Z;
  g = (ambientLight.xyz + (o * ambientLight.w));
  g = (((g * colorMul) * a.x) + a.zzz);
  highp vec3 O;
  O = (toViewScreenSpace - (2.0 * (
    dot (W, toViewScreenSpace)
   * W)));
  y = O;
  mediump vec2 I;
  I = (vec2(0.5, 0.5) - (y.xy * 0.5));
  lowp vec3 i;
  i = texture2D (envMap, I).xyz;
  r = i;
  highp vec3 l;
  highp vec3 S;
  S = y;
  l = (((
    pow (clamp ((dot (S, light1.dir) - light1.attrib.x), 0.0, 1.0), light1.attrib.y)
   * light1.color) + (
    pow (clamp ((dot (S, light2.dir) - light2.attrib.x), 0.0, 1.0), light2.attrib.y)
   * light2.color)) + (pow (
    clamp ((dot (S, light3.dir) - light3.attrib.x), 0.0, 1.0)
  , light3.attrib.y) * light3.color));
  mediump vec4 G;
  G.w = 1.0;
  G.xyz = (((
    (z.xyz * b.x)
   * g) + (
    (pow (r, vec3(mirrorPow)) * a.y)
   * mirrorMul)) + (pow (
    (l * specularMul)
  , vec3(specularPow)) * a.y));
  gl_FragColor = G;
}

