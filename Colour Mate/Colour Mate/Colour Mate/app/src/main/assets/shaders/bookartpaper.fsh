uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D overlay;
uniform sampler2D lut;
varying highp vec2 texCoord;
varying highp vec2 texCoordTransformed;
uniform highp vec2 randomOffset;
uniform mediump vec3 outlineColor;
void main ()
{
  lowp vec4 r;
  lowp vec4 y;
  y = texture2D (tex, texCoord);
  lowp vec4 g;
  g = texture2D (outline, texCoord);
  highp vec2 x;
  x = (texCoordTransformed + randomOffset);
  r = texture2D (overlay, x);
  mediump vec3 b;
  b = mix (outlineColor, y.xyz, g.y);
  mediump vec3 z;
  mediump float a;
  a = ((max (
    max (b.x, b.y)
  , b.z) + min (
    min (b.x, b.y)
  , b.z)) / 2.0);
  if ((a > 0.8)) {
    lowp float _;
    mediump float A;
    A = ((a - 0.8) * 0.5);
    _ = A;
    z = (b - vec3(_));
  } else {
    if ((a < 0.225)) {
      lowp float Y;
      mediump float R;
      R = ((0.225 - a) * 0.5);
      Y = R;
      z = (b + vec3(Y));
    } else {
      z = b;
    };
  };
  mediump vec3 X;
  highp vec2 W;
  highp vec2 Z;
  mediump vec2 o;
  mediump vec2 O;
  mediump float I;
  I = (z.z * 63.0);
  O.y = floor((floor(I) / 8.0));
  O.x = (floor(I) - (O.y * 8.0));
  o.y = floor((ceil(I) / 8.0));
  o.x = (ceil(I) - (o.y * 8.0));
  Z = (((O * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * z.xy));
  W = (((o * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * z.xy));
  lowp vec4 i;
  i = texture2D (lut, Z);
  mediump vec4 l;
  l = i;
  lowp vec4 S;
  S = texture2D (lut, W);
  mediump vec4 G;
  G = S;
  mediump float B;
  B = clamp (fract(I), 0.0, 1.0);
  X = ((l * (1.0 - B)) + (G * B)).xyz;
  mediump float c;
  if ((r.x < 0.5)) {
    lowp float d;
    d = (2.0 * r.x);
    c = max (((X.x + d) - 1.0), 0.0);
  } else {
    lowp float e;
    e = (2.0 * (r.x - 0.5));
    c = min ((X.x + e), 1.0);
  };
  mediump float f;
  if ((r.y < 0.5)) {
    lowp float h;
    h = (2.0 * r.y);
    f = max (((X.y + h) - 1.0), 0.0);
  } else {
    lowp float j;
    j = (2.0 * (r.y - 0.5));
    f = min ((X.y + j), 1.0);
  };
  mediump float k;
  if ((r.z < 0.5)) {
    lowp float m;
    m = (2.0 * r.z);
    k = max (((X.z + m) - 1.0), 0.0);
  } else {
    lowp float n;
    n = (2.0 * (r.z - 0.5));
    k = min ((X.z + n), 1.0);
  };
  mediump vec3 p;
  p.x = c;
  p.y = f;
  p.z = k;
  mediump vec4 q;
  q.w = 1.0;
  q.xyz = p;
  gl_FragColor = q;
}

