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
varying mediump vec3 toView;
varying mediump vec3 toViewScreenSpace;
varying highp float fade;
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
  highp vec3 a;
  a = shattr.L20;
  highp vec3 _;
  _ = (((
    ((((
      ((((0.429043 * shattr.L22) * (
        (toView.x * toView.x)
       - 
        (toView.y * toView.y)
      )) + ((0.743125 * a) * (toView.z * toView.z))) + (0.886227 * shattr.L00))
     - 
      (0.247708 * a)
    ) + (
      ((0.858086 * shattr.L2m2) * toView.x)
     * toView.y)) + ((
      (0.858086 * shattr.L21)
     * toView.x) * toView.z)) + (((0.858086 * shattr.L2m1) * toView.y) * toView.z))
   + 
    ((1.023328 * shattr.L11) * toView.x)
  ) + (
    (1.023328 * shattr.L1m1)
   * toView.y)) + ((1.023328 * shattr.L10) * toView.z));
  mediump vec3 A;
  A = _;
  mediump vec3 Y;
  Y = normalize(pixelNormal);
  y = Y;
  highp vec3 R;
  R = shattr.L20;
  highp vec3 X;
  X = (((
    ((((
      ((((0.429043 * shattr.L22) * (
        (y.x * y.x)
       - 
        (y.y * y.y)
      )) + ((0.743125 * R) * (y.z * y.z))) + (0.886227 * shattr.L00))
     - 
      (0.247708 * R)
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
  mediump vec3 W;
  W = X;
  mediump vec3 Z;
  Z = normalMatrix[2];
  mediump vec2 o;
  o = (((toViewScreenSpace - 
    (2.0 * (dot (Z, toViewScreenSpace) * Z))
  ).xy * 0.5) + vec2(0.5, 0.5));
  lowp vec3 O;
  O = vec3((pow (texture2D (envmap, o).y, 4.0) * 0.25));
  highp vec3 I;
  I = (((x.xyz * g.x) * (W * colorMul)) + O);
  r = I;
  highp vec4 i;
  i.w = 1.0;
  i.xyz = mix (A, r, exp(-(fade)));
  gl_FragColor = i;
}

