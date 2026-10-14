uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D overlay;
uniform sampler2D bevel;
uniform sampler2D blurmap3;
uniform sampler2D lut;
varying highp vec2 texCoord;
uniform mediump vec3 outlineColor;
void main ()
{
  mediump vec4 r;
  mediump vec4 y;
  mediump vec4 g;
  mediump vec4 x;
  lowp vec4 b;
  b = texture2D (overlay, texCoord);
  x = b;
  lowp vec4 z;
  z = texture2D (tex, texCoord);
  g = z;
  lowp vec4 a;
  a = texture2D (outline, texCoord);
  y = a;
  lowp vec4 _;
  _ = texture2D (bevel, texCoord);
  r = _;
  if ((outlineColor == vec3(0.0, 0.0, 0.0))) {
    mediump vec4 A;
    lowp vec4 Y;
    Y = texture2D (blurmap3, texCoord);
    A = Y;
    A = ((A * A) * 0.5);
    g = mix (g, A, (1.0 - y.y));
  } else {
    mediump vec4 R;
    R.w = 1.0;
    R.xyz = outlineColor;
    g = mix (g, R, (1.0 - y.y));
  };
  mediump vec3 X;
  X = g.xyz;
  mediump vec3 W;
  mediump float Z;
  Z = ((max (
    max (g.x, g.y)
  , g.z) + min (
    min (g.x, g.y)
  , g.z)) / 2.0);
  if ((Z > 0.5)) {
    lowp float o;
    mediump float O;
    O = ((Z - 0.5) * 0.25);
    o = O;
    W = (g.xyz - vec3(o));
  } else {
    if ((Z < 0.35)) {
      lowp float I;
      mediump float i;
      i = ((0.35 - Z) * 0.4);
      I = i;
      W = (g.xyz + vec3(I));
    } else {
      W = X;
    };
  };
  mediump vec3 l;
  highp vec2 S;
  highp vec2 G;
  mediump vec2 B;
  mediump vec2 c;
  mediump float d;
  d = (W.z * 63.0);
  c.y = floor((floor(d) / 8.0));
  c.x = (floor(d) - (c.y * 8.0));
  B.y = floor((ceil(d) / 8.0));
  B.x = (ceil(d) - (B.y * 8.0));
  G = (((c * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * W.xy));
  S = (((B * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * W.xy));
  lowp vec4 e;
  e = texture2D (lut, G);
  mediump vec4 f;
  f = e;
  lowp vec4 h;
  h = texture2D (lut, S);
  mediump vec4 j;
  j = h;
  mediump float k;
  k = clamp (fract(d), 0.0, 1.0);
  l = ((f * (1.0 - k)) + (j * k)).xyz;
  mediump float m;
  if ((l.x < 0.5)) {
    m = ((2.0 * l.x) * x.x);
  } else {
    m = (1.0 - ((2.0 * 
      (1.0 - l.x)
    ) * (1.0 - x.x)));
  };
  mediump float n;
  if ((l.y < 0.5)) {
    n = ((2.0 * l.y) * x.y);
  } else {
    n = (1.0 - ((2.0 * 
      (1.0 - l.y)
    ) * (1.0 - x.y)));
  };
  mediump float p;
  if ((l.z < 0.5)) {
    p = ((2.0 * l.z) * x.z);
  } else {
    p = (1.0 - ((2.0 * 
      (1.0 - l.z)
    ) * (1.0 - x.z)));
  };
  mediump vec3 q;
  q.x = m;
  q.y = n;
  q.z = p;
  g.xyz = q;
  mediump float s;
  if ((r.x < 0.5)) {
    mediump float t;
    t = (2.0 * r.x);
    mediump float u;
    if ((t == 0.0)) {
      u = t;
    } else {
      u = max ((1.0 - (
        (1.0 - m)
       / t)), 0.0);
    };
    s = u;
  } else {
    mediump float v;
    v = (2.0 * (r.x - 0.5));
    mediump float C;
    if ((v == 1.0)) {
      C = v;
    } else {
      C = min ((m / (1.0 - v)), 1.0);
    };
    s = C;
  };
  mediump float D;
  if ((r.y < 0.5)) {
    mediump float E;
    E = (2.0 * r.y);
    mediump float F;
    if ((E == 0.0)) {
      F = E;
    } else {
      F = max ((1.0 - (
        (1.0 - n)
       / E)), 0.0);
    };
    D = F;
  } else {
    mediump float H;
    H = (2.0 * (r.y - 0.5));
    mediump float J;
    if ((H == 1.0)) {
      J = H;
    } else {
      J = min ((n / (1.0 - H)), 1.0);
    };
    D = J;
  };
  mediump float K;
  if ((r.z < 0.5)) {
    mediump float L;
    L = (2.0 * r.z);
    mediump float M;
    if ((L == 0.0)) {
      M = L;
    } else {
      M = max ((1.0 - (
        (1.0 - p)
       / L)), 0.0);
    };
    K = M;
  } else {
    mediump float N;
    N = (2.0 * (r.z - 0.5));
    mediump float P;
    if ((N == 1.0)) {
      P = N;
    } else {
      P = min ((p / (1.0 - N)), 1.0);
    };
    K = P;
  };
  mediump vec3 Q;
  Q.x = s;
  Q.y = D;
  Q.z = K;
  mediump vec4 T;
  T.w = 1.0;
  T.xyz = Q;
  gl_FragColor = T;
}

