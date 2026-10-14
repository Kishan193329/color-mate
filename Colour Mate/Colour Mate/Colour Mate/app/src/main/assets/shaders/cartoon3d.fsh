struct SphericalHarmonics {
  highp vec3 L00;
  highp vec3 L1m1;
  highp vec3 L10;
  highp vec3 L11;
  highp vec3 L2m2;
  highp vec3 L2m1;
  highp vec3 L20;
  highp vec3 L21;
  highp vec3 L22;
};
uniform sampler2D tex;
uniform sampler2D normalMap;
uniform sampler2D controlMap;
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
uniform SphericalHarmonics shattr;
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
  x = (normalMatrix * normalize(X));
  highp vec3 W;
  W = shattr.L20;
  highp vec3 Z;
  Z = (((
    ((((
      ((((
        (((0.429043 * shattr.L22) * ((x.x * x.x) - (x.y * x.y))) + ((0.743125 * W) * (x.z * x.z)))
       + 
        (0.886227 * shattr.L00)
      ) - (0.247708 * W)) + ((
        (0.858086 * shattr.L2m2)
       * x.x) * x.y)) + (((0.858086 * shattr.L21) * x.x) * x.z))
     + 
      (((0.858086 * shattr.L2m1) * x.y) * x.z)
    ) + (
      (1.023328 * shattr.L11)
     * x.x)) + ((1.023328 * shattr.L1m1) * x.y)) + ((1.023328 * shattr.L10) * x.z))
   * colorMul) * a.x) + a.zzz);
  g = Z;
  highp vec3 o;
  o = (toViewScreenSpace - (2.0 * (
    dot (x, toViewScreenSpace)
   * x)));
  y = o;
  mediump vec2 O;
  O = (vec2(0.5, 0.5) + (y.xy * 0.5));
  lowp vec3 I;
  I = texture2D (envMap, O).xyz;
  r = I;
  highp vec3 i;
  i = shattr.L20;
  highp vec3 l;
  l = (((
    ((((
      ((((0.429043 * shattr.L22) * (
        (y.x * y.x)
       - 
        (y.y * y.y)
      )) + ((0.743125 * i) * (y.z * y.z))) + (0.886227 * shattr.L00))
     - 
      (0.247708 * i)
    ) + (
      ((0.858086 * shattr.L2m2) * y.x)
     * y.y)) + ((
      (0.858086 * shattr.L21)
     * y.x) * y.z)) + (((0.858086 * shattr.L2m1) * y.y) * y.z))
   + 
    ((1.023328 * shattr.L11) * y.x)
  ) + (
    (1.023328 * shattr.L1m1)
   * y.y)) + ((1.023328 * shattr.L10) * y.z));
  mediump vec3 S;
  S = (((
    (z.xyz * b.x)
   * 
    clamp (g, 0.0, 1.0)
  ) + (
    (pow (r, vec3(mirrorPow)) * a.y)
   * mirrorMul)) + (pow (
    clamp ((l * specularMul), 0.0, 100.0)
  , vec3(specularPow)) * a.y));
  highp vec3 G;
  G = S;
  highp vec4 B;
  B.w = 1.0;
  B.xyz = ((floor(
    (G * 3.0)
  ) + vec3(0.5, 0.5, 0.5)) / 3.0);
  gl_FragColor = B;
}

