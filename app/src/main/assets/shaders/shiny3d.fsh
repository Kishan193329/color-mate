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
uniform sampler2D normalmap;
uniform sampler2D envmap;
varying mediump vec2 texCoord;
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
  lowp vec3 a;
  a = normalize(mix ((texture2D (normalmap, texCoord).xyz - vec3(0.5, 0.5, 0.5)), vec3(0.0, 0.0, 1.0), 0.5));
  mediump vec3 _;
  _ = (normalMatrix * a);
  y = _;
  highp vec3 A;
  A = normalize(y);
  highp vec3 Y;
  Y = shattr.L20;
  highp vec3 R;
  R = (((
    ((((
      ((((0.429043 * shattr.L22) * (
        (A.x * A.x)
       - 
        (A.y * A.y)
      )) + ((0.743125 * Y) * (A.z * A.z))) + (0.886227 * shattr.L00))
     - 
      (0.247708 * Y)
    ) + (
      ((0.858086 * shattr.L2m2) * A.x)
     * A.y)) + ((
      (0.858086 * shattr.L21)
     * A.x) * A.z)) + (((0.858086 * shattr.L2m1) * A.y) * A.z))
   + 
    ((1.023328 * shattr.L11) * A.x)
  ) + (
    (1.023328 * shattr.L1m1)
   * A.y)) + ((1.023328 * shattr.L10) * A.z));
  mediump vec3 X;
  X = R;
  highp vec3 W;
  W = (toViewScreenSpace - (2.0 * (
    dot (y, toViewScreenSpace)
   * y)));
  r = W;
  mediump vec2 Z;
  Z = ((r.xy * 0.5) + vec2(0.5, 0.5));
  lowp vec3 o;
  o = vec3((pow (texture2D (envmap, Z).y, 20.0) * 0.25));
  highp vec4 O;
  O.w = 1.0;
  O.xyz = (((x.xyz * g.x) * (X * colorMul)) + o);
  gl_FragColor = O;
}

