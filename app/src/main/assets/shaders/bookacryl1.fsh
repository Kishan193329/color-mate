uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D blurmap0;
uniform sampler2D indexTex;
uniform sampler2D noise;
uniform sampler2D overlay;
uniform sampler2D paper;
uniform sampler2D lut;
varying highp vec2 texCoord;
varying highp vec2 texCoordTransformed;
uniform highp vec2 randomOffset;
uniform mediump vec3 outlineColor;
void main ()
{
  mediump vec4 r;
  highp vec2 y;
  mediump vec3 g;
  mediump vec4 x;
  mediump vec4 b;
  mediump vec4 z;
  mediump vec4 a;
  lowp vec4 _;
  highp vec2 A;
  A = (texCoord * 2.25);
  _ = texture2D (noise, A);
  a = _;
  lowp vec4 Y;
  Y = texture2D (tex, texCoord);
  z = Y;
  lowp vec4 R;
  R = texture2D (outline, texCoord);
  b = R;
  lowp vec4 X;
  X = texture2D (blurmap0, texCoord);
  x = X;
  highp vec2 W;
  W = (texCoordTransformed - (randomOffset * 0.5));
  lowp vec3 Z;
  Z = texture2D (paper, W).xyz;
  g = Z;
  mediump vec3 o;
  o = mix (g, vec3(0.5, 0.5, 0.5), 0.75);
  g = o;
  lowp vec2 O;
  O = texture2D (indexTex, texCoord).xy;
  y = O;
  highp float I;
  I = (((y.x * 256.0) + y.y) / 13.0);
  highp float i;
  i = cos(I);
  highp vec2 l;
  l.x = i;
  l.y = sin(I);
  highp vec2 S;
  S.x = l.y;
  S.y = -(i);
  lowp vec4 G;
  highp vec2 B;
  B = (((
    (texCoordTransformed.x * l)
   + 
    (texCoordTransformed.y * S)
  ) * 2.0) + randomOffset);
  G = texture2D (overlay, B);
  r = G;
  mediump vec4 c;
  c.w = 1.0;
  c.xyz = mix (outlineColor, z.xyz, b.y);
  highp vec2 d;
  highp vec2 e;
  mediump vec2 f;
  mediump vec2 h;
  mediump float j;
  j = (c.z * 63.0);
  h.y = floor((floor(j) / 8.0));
  h.x = (floor(j) - (h.y * 8.0));
  f.y = floor((ceil(j) / 8.0));
  f.x = (ceil(j) - (f.y * 8.0));
  e = (((h * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * c.xy));
  d = (((f * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * c.xy));
  lowp vec4 k;
  k = texture2D (lut, e);
  mediump vec4 m;
  m = k;
  lowp vec4 n;
  n = texture2D (lut, d);
  mediump vec4 p;
  p = n;
  mediump float q;
  q = clamp (fract(j), 0.0, 1.0);
  z.xyz = ((m * (1.0 - q)) + (p * q)).xyz;
  z = (1.0 - ((1.0 - z) * 1.43));
  r = (r - vec4(0.25, 0.25, 0.25, 0.25));
  r = (r + ((
    (1.0 - pow (x.w, 3.0))
   * 0.8) * a.y));
  z.xyz = mix (z.xyz, clamp (mix (z.xyz, vec3(1.0, 1.0, 1.0), r.x), 0.0, 1.0), b.y);
  mediump float s;
  if ((z.x < 0.5)) {
    s = ((2.0 * z.x) * o.x);
  } else {
    s = (1.0 - ((2.0 * 
      (1.0 - z.x)
    ) * (1.0 - o.x)));
  };
  mediump float t;
  if ((z.y < 0.5)) {
    t = ((2.0 * z.y) * o.y);
  } else {
    t = (1.0 - ((2.0 * 
      (1.0 - z.y)
    ) * (1.0 - o.y)));
  };
  mediump float u;
  if ((z.z < 0.5)) {
    u = ((2.0 * z.z) * o.z);
  } else {
    u = (1.0 - ((2.0 * 
      (1.0 - z.z)
    ) * (1.0 - o.z)));
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

