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
uniform sampler2D outline;
uniform sampler2D envmap;
varying mediump vec2 texCoord;
varying mediump vec3 pixelNormal;
varying highp vec3 colorMul;
varying mediump mat3 normalMatrix;
varying mediump vec3 toViewScreenSpace;
uniform SphericalHarmonics shattr;
void main ()
{
  mediump vec3 r;
  highp vec3 y;
  mediump vec4 g;
  mediump vec4 x;
  lowp vec4 b;
  b = texture2D (tex, texCoord);
  x = b;
  lowp vec4 z;
  z = texture2D (outline, texCoord);
  g = z;
  mediump vec3 a;
  a = normalize(pixelNormal);
  y = a;
  highp vec3 _;
  _ = shattr.L20;
  highp vec3 A;
  A = (((
    ((((
      ((((0.429043 * shattr.L22) * (
        (y.x * y.x)
       - 
        (y.y * y.y)
      )) + ((0.743125 * _) * (y.z * y.z))) + (0.886227 * shattr.L00))
     - 
      (0.247708 * _)
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
  mediump vec3 Y;
  Y = A;
  mediump vec3 R;
  R = normalMatrix[2];
  mediump vec2 X;
  X = (((toViewScreenSpace - 
    (2.0 * (dot (R, toViewScreenSpace) * R))
  ).xy * 0.5) + vec2(0.5, 0.5));
  lowp vec3 W;
  W = vec3((pow (texture2D (envmap, X).y, 6.0) * 0.2));
  highp vec3 Z;
  Z = (((x.xyz * g.x) * (Y * colorMul)) + W);
  r = Z;
  mediump vec4 o;
  o.w = 1.0;
  o.xyz = r;
  gl_FragColor = o;
}

