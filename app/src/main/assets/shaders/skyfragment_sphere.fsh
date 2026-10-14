uniform sampler2D skysphere;
varying highp vec3 pixelNormal;
void main ()
{
  mediump vec3 r;
  highp vec3 y;
  y = normalize(pixelNormal);
  r = y;
  mediump float g;
  mediump float x;
  x = (min (abs(
    (r.x / r.z)
  ), 1.0) / max (abs(
    (r.x / r.z)
  ), 1.0));
  mediump float b;
  b = (x * x);
  b = (((
    ((((
      ((((-0.01213232 * b) + 0.05368138) * b) - 0.1173503)
     * b) + 0.1938925) * b) - 0.3326756)
   * b) + 0.9999793) * x);
  b = (b + (float(
    (abs((r.x / r.z)) > 1.0)
  ) * (
    (b * -2.0)
   + 1.570796)));
  g = (b * sign((r.x / r.z)));
  if ((abs(r.z) > (1e-08 * abs(r.x)))) {
    if ((r.z < 0.0)) {
      if ((r.x >= 0.0)) {
        g += 3.141593;
      } else {
        g = (g - 3.141593);
      };
    };
  } else {
    g = (sign(r.x) * 1.570796);
  };
  mediump float z;
  z = ((g / 3.14159) + 0.5);
  mediump float a;
  a = sqrt(dot (r.xz, r.xz));
  mediump float _;
  mediump float A;
  A = (min (abs(
    (r.y / a)
  ), 1.0) / max (abs(
    (r.y / a)
  ), 1.0));
  mediump float Y;
  Y = (A * A);
  Y = (((
    ((((
      ((((-0.01213232 * Y) + 0.05368138) * Y) - 0.1173503)
     * Y) + 0.1938925) * Y) - 0.3326756)
   * Y) + 0.9999793) * A);
  Y = (Y + (float(
    (abs((r.y / a)) > 1.0)
  ) * (
    (Y * -2.0)
   + 1.570796)));
  _ = (Y * sign((r.y / a)));
  if ((abs(a) > (1e-08 * abs(r.y)))) {
    if ((a < 0.0)) {
      if ((r.y >= 0.0)) {
        _ += 3.141593;
      } else {
        _ = (_ - 3.141593);
      };
    };
  } else {
    _ = (sign(r.y) * 1.570796);
  };
  mediump vec2 R;
  R.x = z;
  R.y = (0.5 - (_ / 3.14159));
  lowp vec4 X;
  X = texture2D (skysphere, R);
  gl_FragColor = X;
}

