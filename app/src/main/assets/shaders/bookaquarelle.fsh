uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D indexTex;
uniform sampler2D blurmap1;
uniform sampler2D overlay;
uniform sampler2D papertex;
uniform sampler2D lut;
varying highp vec2 texCoord;
uniform mediump vec3 outlineColor;
uniform highp vec2 randomOffset;
void main ()
{
  mediump float r;
  mediump float y;
  mediump vec4 g;
  mediump vec4 x;
  mediump vec4 b;
  mediump vec4 z;
  highp vec2 a;
  lowp vec2 _;
  _ = texture2D (indexTex, texCoord).xy;
  a = _;
  highp float A;
  A = (((a.x * 256.0) + a.y) / 13.0);
  highp float Y;
  Y = cos(A);
  highp vec2 R;
  R.x = Y;
  R.y = sin(A);
  highp vec2 X;
  X.x = R.y;
  X.y = -(Y);
  lowp vec4 W;
  highp vec2 Z;
  Z = ((texCoord * 2.0) + randomOffset);
  W = texture2D (overlay, Z);
  z = W;
  lowp vec4 o;
  highp vec2 O;
  O = (((
    (texCoord.x - 0.5)
   * R) + (
    (texCoord.y - 0.5)
   * X)) - randomOffset);
  o = texture2D (overlay, O);
  b = o;
  lowp vec4 I;
  I = texture2D (tex, texCoord);
  x = I;
  lowp vec4 i;
  i = texture2D (outline, texCoord);
  g = i;
  lowp float l;
  l = texture2D (blurmap1, texCoord).w;
  y = l;
  highp vec2 S;
  S = (texCoord - randomOffset);
  lowp float G;
  G = texture2D (papertex, S).x;
  r = G;
  x.xyz = mix (vec3(1.0, 1.0, 1.0), x.xyz, mix ((
    ((1.0 - b.y) * 0.5)
   + 0.5), (
    (1.0 - pow (y, 12.0))
   * z.y), 0.5));
  x.xyz = mix (outlineColor, x.xyz, g.y);
  mediump float B;
  B = ((0.5 - (
    (max (max (x.x, x.y), x.z) + min (min (x.x, x.y), x.z))
   / 2.0)) * 2.0);
  mediump vec3 c;
  mediump vec4 d;
  d.w = 1.0;
  d.xyz = (x.xyz + ((B - 
    clamp (B, -0.5, 0.5)
  ) * 0.125));
  highp vec2 e;
  highp vec2 f;
  mediump vec2 h;
  mediump vec2 j;
  mediump float k;
  k = (d.z * 63.0);
  j.y = floor((floor(k) / 8.0));
  j.x = (floor(k) - (j.y * 8.0));
  h.y = floor((ceil(k) / 8.0));
  h.x = (ceil(k) - (h.y * 8.0));
  f = (((j * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * d.xy));
  e = (((h * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * d.xy));
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
  c = ((n * (1.0 - s)) + (q * s)).xyz;
  mediump float t;
  if ((r < 0.5)) {
    t = max (((c.x + 
      (2.0 * r)
    ) - 1.0), 0.0);
  } else {
    t = min ((c.x + (2.0 * 
      (r - 0.5)
    )), 1.0);
  };
  mediump float u;
  if ((r < 0.5)) {
    u = max (((c.y + 
      (2.0 * r)
    ) - 1.0), 0.0);
  } else {
    u = min ((c.y + (2.0 * 
      (r - 0.5)
    )), 1.0);
  };
  mediump float v;
  if ((r < 0.5)) {
    v = max (((c.z + 
      (2.0 * r)
    ) - 1.0), 0.0);
  } else {
    v = min ((c.z + (2.0 * 
      (r - 0.5)
    )), 1.0);
  };
  mediump vec3 C;
  C.x = t;
  C.y = u;
  C.z = v;
  mediump vec4 D;
  D.w = 1.0;
  D.xyz = C;
  gl_FragColor = D;
}

