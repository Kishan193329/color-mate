uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D overlay;
uniform sampler2D blurmap3;
uniform sampler2D lut;
varying highp vec2 texCoord;
varying highp vec2 texCoordTransformed;
uniform highp vec2 randomOffset;
uniform mediump vec3 outlineColor;
void main ()
{
  lowp vec4 r;
  mediump vec4 y;
  mediump vec4 g;
  lowp vec4 x;
  x = texture2D (tex, texCoord);
  g = x;
  lowp vec4 b;
  b = texture2D (outline, texCoord);
  y = b;
  if ((outlineColor == vec3(0.0, 0.0, 0.0))) {
    mediump vec4 z;
    mediump vec4 a;
    lowp vec4 _;
    _ = texture2D (blurmap3, texCoord);
    a = _;
    if ((randomOffset.x < 0.333)) {
      z = a;
    } else {
      z = (a * a);
      if ((randomOffset.x > 0.666)) {
        z = (z * 0.5);
      };
    };
    g = mix (g, z, (1.0 - y.y));
  } else {
    mediump vec4 A;
    A.w = 1.0;
    A.xyz = outlineColor;
    g = mix (g, A, (1.0 - y.y));
  };
  highp vec2 Y;
  Y = (texCoordTransformed + randomOffset);
  r = texture2D (overlay, Y);
  mediump vec4 R;
  R = clamp ((g * 1.1), 0.0, 1.0);
  g = R;
  mediump vec3 X;
  X = R.xyz;
  mediump vec3 W;
  mediump float Z;
  Z = ((max (
    max (R.x, R.y)
  , R.z) + min (
    min (R.x, R.y)
  , R.z)) / 2.0);
  if ((Z > 0.65)) {
    lowp float o;
    mediump float O;
    O = ((Z - 0.65) * 0.4);
    o = O;
    W = (R.xyz - vec3(o));
  } else {
    if ((Z < 0.3)) {
      lowp float I;
      mediump float i;
      i = ((0.3 - Z) * 0.5);
      I = i;
      W = (R.xyz + vec3(I));
    } else {
      W = X;
    };
  };
  highp vec2 l;
  highp vec2 S;
  mediump vec2 G;
  mediump vec2 B;
  mediump float c;
  c = (W.z * 63.0);
  B.y = floor((floor(c) / 8.0));
  B.x = (floor(c) - (B.y * 8.0));
  G.y = floor((ceil(c) / 8.0));
  G.x = (ceil(c) - (G.y * 8.0));
  S = (((B * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * W.xy));
  l = (((G * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * W.xy));
  lowp vec4 d;
  d = texture2D (lut, S);
  mediump vec4 e;
  e = d;
  lowp vec4 f;
  f = texture2D (lut, l);
  mediump vec4 h;
  h = f;
  mediump float j;
  j = clamp (fract(c), 0.0, 1.0);
  g.xyz = ((e * (1.0 - j)) + (h * j)).xyz;
  mediump float k;
  if ((r.x < 0.5)) {
    lowp float m;
    m = (2.0 * r.x);
    k = max (((g.x + m) - 1.0), 0.0);
  } else {
    lowp float n;
    n = (2.0 * (r.x - 0.5));
    k = min ((g.x + n), 1.0);
  };
  mediump float p;
  if ((r.y < 0.5)) {
    lowp float q;
    q = (2.0 * r.y);
    p = max (((g.y + q) - 1.0), 0.0);
  } else {
    lowp float s;
    s = (2.0 * (r.y - 0.5));
    p = min ((g.y + s), 1.0);
  };
  mediump float t;
  if ((r.z < 0.5)) {
    lowp float u;
    u = (2.0 * r.z);
    t = max (((g.z + u) - 1.0), 0.0);
  } else {
    lowp float v;
    v = (2.0 * (r.z - 0.5));
    t = min ((g.z + v), 1.0);
  };
  mediump vec3 C;
  C.x = k;
  C.y = p;
  C.z = t;
  mediump vec4 D;
  D.w = 1.0;
  D.xyz = C;
  gl_FragColor = D;
}

