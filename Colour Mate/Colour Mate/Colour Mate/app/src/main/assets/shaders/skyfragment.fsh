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
varying highp vec3 pixelNormal;
uniform SphericalHarmonics shattr;
void main ()
{
  highp vec3 r;
  r = normalize(pixelNormal);
  highp vec3 y;
  y = shattr.L20;
  highp vec3 g;
  g = (((
    ((((
      ((((0.429043 * shattr.L22) * (
        (r.x * r.x)
       - 
        (r.y * r.y)
      )) + ((0.743125 * y) * (r.z * r.z))) + (0.886227 * shattr.L00))
     - 
      (0.247708 * y)
    ) + (
      ((0.858086 * shattr.L2m2) * r.x)
     * r.y)) + ((
      (0.858086 * shattr.L21)
     * r.x) * r.z)) + (((0.858086 * shattr.L2m1) * r.y) * r.z))
   + 
    ((1.023328 * shattr.L11) * r.x)
  ) + (
    (1.023328 * shattr.L1m1)
   * r.y)) + ((1.023328 * shattr.L10) * r.z));
  mediump vec4 x;
  x.w = 1.0;
  x.xyz = g;
  gl_FragColor = x;
}

