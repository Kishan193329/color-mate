uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D blurmap0;
uniform sampler2D blurmap1;
uniform sampler2D blurmap2;
varying highp vec2 texCoord;
uniform highp vec2 timeStrength;
void main ()
{
  highp float r;
  highp vec2 y;
  highp float g;
  g = pow ((1.0 - timeStrength.x), 8.0);
  y = ((texCoord - vec2(0.5, 0.5)) * (1.0 - (g * 0.5)));
  highp float x;
  x = sqrt(dot (y, y));
  highp float b;
  highp float z;
  z = (min (abs(
    (y.x / y.y)
  ), 1.0) / max (abs(
    (y.x / y.y)
  ), 1.0));
  highp float a;
  a = (z * z);
  a = (((
    ((((
      ((((-0.01213232 * a) + 0.05368138) * a) - 0.1173503)
     * a) + 0.1938925) * a) - 0.3326756)
   * a) + 0.9999793) * z);
  a = (a + (float(
    (abs((y.x / y.y)) > 1.0)
  ) * (
    (a * -2.0)
   + 1.570796)));
  b = (a * sign((y.x / y.y)));
  if ((abs(y.y) > (1e-08 * abs(y.x)))) {
    if ((y.y < 0.0)) {
      if ((y.x >= 0.0)) {
        b += 3.141593;
      } else {
        b = (b - 3.141593);
      };
    };
  } else {
    b = (sign(y.x) * 1.570796);
  };
  r = (b - ((g * 
    (1.0 - timeStrength.x)
  ) * 16.0));
  r = (r - ((x * g) * 64.0));
  highp vec2 _;
  _.x = (sin(r) * x);
  _.y = (cos(r) * x);
  y = (vec2(0.5, 0.5) + _);
  highp float A;
  A = ((1.0 - pow (
    (1.0 - g)
  , 6.0)) * 2.999);
  mediump vec3 Y;
  if ((A < 1.0)) {
    mediump vec4 R;
    lowp vec4 X;
    X = texture2D (blurmap0, y);
    R = X;
    highp vec3 W;
    lowp vec3 Z;
    Z = (texture2D (outline, y).xyz * texture2D (tex, y).xyz);
    mediump vec3 o;
    o = (R.xyz * R.w);
    W = mix (Z, o, fract(A));
    Y = W;
  } else {
    if ((A < 2.0)) {
      mediump vec4 O;
      mediump vec4 I;
      lowp vec4 i;
      i = texture2D (blurmap0, y);
      I = i;
      lowp vec4 l;
      l = texture2D (blurmap1, y);
      O = l;
      highp vec3 S;
      mediump vec3 G;
      G = (I.xyz * I.w);
      mediump vec3 B;
      B = (O.xyz * O.w);
      S = mix (G, B, fract(A));
      Y = S;
    } else {
      if ((A < 3.0)) {
        mediump vec4 c;
        mediump vec4 d;
        lowp vec4 e;
        e = texture2D (blurmap1, y);
        d = e;
        lowp vec4 f;
        f = texture2D (blurmap2, y);
        c = f;
        highp vec3 h;
        mediump vec3 j;
        j = (d.xyz * d.w);
        mediump vec3 k;
        k = (c.xyz * c.w);
        h = mix (j, k, fract(A));
        Y = h;
      };
    };
  };
  mediump vec4 m;
  m.w = 1.0;
  m.xyz = Y;
  gl_FragColor = m;
}

