uniform sampler2D tex;
uniform sampler2D outline;
varying highp vec2 texCoord;
uniform highp vec2 timeStrength;
uniform highp vec3 touchVector;
void main ()
{
  mediump vec4 r;
  mediump vec4 y;
  highp float g;
  highp vec2 x;
  highp vec2 b;
  b = (texCoord - vec2(0.5, 0.5));
  x = b;
  highp float z;
  z = sqrt(dot (b, b));
  highp float a;
  highp float _;
  _ = (min (abs(
    (b.x / b.y)
  ), 1.0) / max (abs(
    (b.x / b.y)
  ), 1.0));
  highp float A;
  A = (_ * _);
  A = (((
    ((((
      ((((-0.01213232 * A) + 0.05368138) * A) - 0.1173503)
     * A) + 0.1938925) * A) - 0.3326756)
   * A) + 0.9999793) * _);
  A = (A + (float(
    (abs((b.x / b.y)) > 1.0)
  ) * (
    (A * -2.0)
   + 1.570796)));
  a = (A * sign((b.x / b.y)));
  if ((abs(b.y) > (1e-08 * abs(b.x)))) {
    if ((b.y < 0.0)) {
      if ((b.x >= 0.0)) {
        a += 3.141593;
      } else {
        a = (a - 3.141593);
      };
    };
  } else {
    a = (sign(b.x) * 1.570796);
  };
  g = (((float(mod (
    (2.356194 + a)
  , 6.283185))) / 6.283185) * 14.0);
  g = ((timeStrength.x * 6.283185) + ((
    mix (fract(g), (1.0 - fract(g)), (ceil((float(mod (g, 2.0)))) - 1.0))
   * 6.283185) / 14.0));
  highp vec2 Y;
  Y.x = (cos(g) * z);
  Y.y = (sin(g) * z);
  x = (((touchVector.xy * 0.2) + vec2(0.4, 0.4)) + Y);
  lowp vec4 R;
  R = texture2D (outline, x);
  y = R;
  lowp vec4 X;
  X = texture2D (tex, x);
  r = X;
  mediump vec4 W;
  W.w = 1.0;
  W.xyz = (r.xyz * y.xyz);
  gl_FragColor = W;
}

