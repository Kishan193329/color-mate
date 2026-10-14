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
varying mediump vec3 pixelNormal;
varying highp vec3 colorMul;
varying highp float ambientOcculsion;
uniform SphericalHarmonics shattr;
void main ()
{
  mediump vec3 r;
  mediump vec3 y;
  y = normalize(pixelNormal);
  highp vec3 g;
  g = shattr.L20;
  highp vec3 x;
  x = (((
    ((((
      ((((
        (0.429043 * shattr.L22)
       * 
        ((y.x * y.x) - (y.y * y.y))
      ) + (
        (0.743125 * g)
       * 
        (y.z * y.z)
      )) + (0.886227 * shattr.L00)) - (0.247708 * g))
     + 
      (((0.858086 * shattr.L2m2) * y.x) * y.y)
    ) + (
      ((0.858086 * shattr.L21) * y.x)
     * y.z)) + ((
      (0.858086 * shattr.L2m1)
     * y.y) * y.z)) + ((1.023328 * shattr.L11) * y.x))
   + 
    ((1.023328 * shattr.L1m1) * y.y)
  ) + (
    (1.023328 * shattr.L10)
   * y.z)) * ambientOcculsion);
  r = x;
  highp vec4 b;
  b.w = 1.0;
  b.xyz = (colorMul * r);
  gl_FragColor = b;
}

