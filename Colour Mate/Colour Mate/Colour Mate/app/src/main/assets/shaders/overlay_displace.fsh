uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D overlay;
varying highp vec2 texCoord;
varying highp vec2 texCoordTransformed;
uniform highp vec2 randomOffset;
uniform mediump vec3 outlineColor;
void main ()
{
  mediump float r;
  highp vec2 y;
  y = (texCoordTransformed + randomOffset);
  highp float x;
  x = 0.0;
  for (lowp int g = -2; g <= 2; g++) {
    lowp vec2 b;
    b.x = -2.0;
    b.y = float(g);
    mediump vec4 z;
    highp vec2 a;
    a = (y + (b * 0.001));
    z = texture2D (overlay, a);
    mediump float _;
    _ = dot (z.xyz, vec3(0.33333, 0.33333, 0.33333));
    x = (x + _);
    lowp vec2 A;
    A.x = -1.0;
    A.y = float(g);
    mediump vec4 Y;
    highp vec2 R;
    R = (y + (A * 0.001));
    Y = texture2D (overlay, R);
    mediump float X;
    X = dot (Y.xyz, vec3(0.33333, 0.33333, 0.33333));
    x = (x + X);
    lowp vec2 W;
    W.x = 0.0;
    W.y = float(g);
    mediump vec4 Z;
    highp vec2 o;
    o = (y + (W * 0.001));
    Z = texture2D (overlay, o);
    mediump float O;
    O = dot (Z.xyz, vec3(0.33333, 0.33333, 0.33333));
    x = (x + O);
    lowp vec2 I;
    I.x = 1.0;
    I.y = float(g);
    mediump vec4 i;
    highp vec2 l;
    l = (y + (I * 0.001));
    i = texture2D (overlay, l);
    mediump float S;
    S = dot (i.xyz, vec3(0.33333, 0.33333, 0.33333));
    x = (x + S);
    lowp vec2 G;
    G.x = 2.0;
    G.y = float(g);
    mediump vec4 B;
    highp vec2 c;
    c = (y + (G * 0.001));
    B = texture2D (overlay, c);
    mediump float d;
    d = dot (B.xyz, vec3(0.33333, 0.33333, 0.33333));
    x = (x + d);
  };
  highp float e;
  e = ((x / 25.0) - 0.5);
  r = e;
  mediump vec2 f;
  f = vec2((r * 0.01));
  lowp vec4 h;
  highp vec2 j;
  j = (texCoord + f);
  h = texture2D (tex, j);
  lowp vec4 k;
  highp vec2 m;
  m = (texCoord + f);
  k = texture2D (outline, m);
  highp vec2 n;
  n = (texCoordTransformed + randomOffset);
  mediump vec3 p;
  p = mix (outlineColor, h.xyz, k.x);
  lowp vec3 q;
  q = texture2D (overlay, n).xyz;
  mediump float s;
  if ((q.x < 0.5)) {
    s = ((2.0 * q.x) * p.x);
  } else {
    s = (1.0 - ((2.0 * 
      (1.0 - q.x)
    ) * (1.0 - p.x)));
  };
  mediump float t;
  if ((q.y < 0.5)) {
    t = ((2.0 * q.y) * p.y);
  } else {
    t = (1.0 - ((2.0 * 
      (1.0 - q.y)
    ) * (1.0 - p.y)));
  };
  mediump float u;
  if ((q.z < 0.5)) {
    u = ((2.0 * q.z) * p.z);
  } else {
    u = (1.0 - ((2.0 * 
      (1.0 - q.z)
    ) * (1.0 - p.z)));
  };
  mediump vec3 v;
  v.x = s;
  v.y = t;
  v.z = u;
  mediump vec4 C;
  C.w = 1.0;
  C.xyz = v;
  gl_FragColor = C;
}

