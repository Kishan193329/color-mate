uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D overlay;
uniform sampler2D blurmap3;
uniform sampler2D displacement;
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
  mediump float x;
  highp vec2 b;
  b = (texCoordTransformed + randomOffset);
  lowp float z;
  z = (texture2D (displacement, b).x - 0.5);
  x = z;
  mediump vec2 a;
  a = vec2((x * 0.025));
  lowp vec4 _;
  highp vec2 A;
  A = (texCoord + a);
  _ = texture2D (tex, A);
  g = _;
  lowp vec4 Y;
  highp vec2 R;
  R = (texCoord + a);
  Y = texture2D (outline, R);
  y = Y;
  if ((outlineColor == vec3(0.0, 0.0, 0.0))) {
    mediump vec4 X;
    mediump vec4 W;
    lowp vec4 Z;
    Z = texture2D (blurmap3, texCoord);
    W = Z;
    if ((randomOffset.x < 0.333)) {
      X = W;
    } else {
      X = (W * W);
      if ((randomOffset.x > 0.666)) {
        X = (X * 0.5);
      };
    };
    g = mix (g, X, (1.0 - y.y));
  } else {
    mediump vec4 o;
    o.w = 1.0;
    o.xyz = outlineColor;
    g = mix (g, o, (1.0 - y.y));
  };
  highp vec2 O;
  O = (texCoordTransformed + randomOffset);
  r = texture2D (overlay, O);
  mediump vec3 I;
  I = g.xyz;
  mediump vec3 i;
  mediump float l;
  l = ((max (
    max (g.x, g.y)
  , g.z) + min (
    min (g.x, g.y)
  , g.z)) / 2.0);
  if ((l > 0.8)) {
    lowp float S;
    mediump float G;
    G = ((l - 0.8) * 0.25);
    S = G;
    i = (g.xyz - vec3(S));
  } else {
    if ((l < 0.35)) {
      lowp float B;
      mediump float c;
      c = ((0.35 - l) * 0.4);
      B = c;
      i = (g.xyz + vec3(B));
    } else {
      i = I;
    };
  };
  mediump vec3 d;
  highp vec2 e;
  highp vec2 f;
  mediump vec2 h;
  mediump vec2 j;
  mediump float k;
  k = (i.z * 63.0);
  j.y = floor((floor(k) / 8.0));
  j.x = (floor(k) - (j.y * 8.0));
  h.y = floor((ceil(k) / 8.0));
  h.x = (ceil(k) - (h.y * 8.0));
  f = (((j * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * i.xy));
  e = (((h * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * i.xy));
  lowp vec4 m;
  m = texture2D (lut, f);
  mediump vec4 n;
  n = m;
  lowp vec4 p;
  p = texture2D (lut, e);
  mediump vec4 q;
  q = p;
  mediump float s;
  s = clamp (fract(k), 0.0, 1.0);
  d = ((n * (1.0 - s)) + (q * s)).xyz;
  mediump float t;
  if ((r.x < 0.5)) {
    lowp float u;
    u = (2.0 * r.x);
    t = max (((d.x + u) - 1.0), 0.0);
  } else {
    lowp float v;
    v = (2.0 * (r.x - 0.5));
    t = min ((d.x + v), 1.0);
  };
  mediump float C;
  if ((r.y < 0.5)) {
    lowp float D;
    D = (2.0 * r.y);
    C = max (((d.y + D) - 1.0), 0.0);
  } else {
    lowp float E;
    E = (2.0 * (r.y - 0.5));
    C = min ((d.y + E), 1.0);
  };
  mediump float F;
  if ((r.z < 0.5)) {
    lowp float H;
    H = (2.0 * r.z);
    F = max (((d.z + H) - 1.0), 0.0);
  } else {
    lowp float J;
    J = (2.0 * (r.z - 0.5));
    F = min ((d.z + J), 1.0);
  };
  mediump vec3 K;
  K.x = t;
  K.y = C;
  K.z = F;
  mediump vec4 L;
  L.w = 1.0;
  L.xyz = K;
  gl_FragColor = L;
}

