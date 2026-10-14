uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D overlay;
uniform sampler2D background;
uniform sampler2D blurmap1;
uniform sampler2D lut;
varying highp vec2 texCoord;
varying highp vec2 texCoordTransformed;
uniform highp vec2 randomOffset;
uniform mediump vec3 outlineColor;
void main ()
{
  mediump vec3 r;
  lowp vec4 y;
  mediump vec4 g;
  lowp vec4 x;
  lowp vec4 b;
  b = texture2D (tex, texCoord);
  x.w = b.w;
  lowp vec4 z;
  z = texture2D (outline, texCoord);
  lowp vec4 a;
  a = texture2D (blurmap1, texCoord);
  g = a;
  lowp vec4 _;
  highp vec2 A;
  A = (texCoordTransformed + randomOffset);
  _ = texture2D (overlay, A);
  y.w = _.w;
  mediump vec3 Y;
  mediump vec4 R;
  R.w = 1.0;
  R.xyz = mix (outlineColor, b.xyz, z.y);
  highp vec2 X;
  highp vec2 W;
  mediump vec2 Z;
  mediump vec2 o;
  mediump float O;
  O = (R.z * 63.0);
  o.y = floor((floor(O) / 8.0));
  o.x = (floor(O) - (o.y * 8.0));
  Z.y = floor((ceil(O) / 8.0));
  Z.x = (ceil(O) - (Z.y * 8.0));
  W = (((o * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * R.xy));
  X = (((Z * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * R.xy));
  lowp vec4 I;
  I = texture2D (lut, W);
  mediump vec4 i;
  i = I;
  lowp vec4 l;
  l = texture2D (lut, X);
  mediump vec4 S;
  S = l;
  mediump float G;
  G = clamp (fract(O), 0.0, 1.0);
  Y = ((i * (1.0 - G)) + (S * G)).xyz;
  x.xyz = Y;
  highp vec2 B;
  B = (texCoordTransformed - (randomOffset * 0.5));
  lowp vec3 c;
  c = texture2D (background, B).xyz;
  r = c;
  lowp vec4 d;
  highp vec2 e;
  e = (texCoordTransformed - (randomOffset * 0.75));
  d = texture2D (overlay, e);
  lowp vec4 f;
  highp vec2 h;
  h = (texCoordTransformed + (randomOffset * 0.25));
  f = texture2D (overlay, h);
  mediump vec3 j;
  j = clamp (((_.xyz * 
    (vec3(1.0, 1.0, 1.0) - (d.xyz * (1.0 - g.w)))
  ) * (vec3(1.0, 1.0, 1.0) - 
    (f.xyz * (1.0 - g.w))
  )), 0.5, 1.0);
  y.xyz = j;
  lowp float k;
  if ((y.x < 0.5)) {
    k = ((2.0 * y.x) * x.x);
  } else {
    k = (1.0 - ((2.0 * 
      (1.0 - y.x)
    ) * (1.0 - x.x)));
  };
  lowp float m;
  if ((y.y < 0.5)) {
    m = ((2.0 * y.y) * x.y);
  } else {
    m = (1.0 - ((2.0 * 
      (1.0 - y.y)
    ) * (1.0 - x.y)));
  };
  lowp float n;
  if ((y.z < 0.5)) {
    n = ((2.0 * y.z) * x.z);
  } else {
    n = (1.0 - ((2.0 * 
      (1.0 - y.z)
    ) * (1.0 - x.z)));
  };
  lowp vec3 p;
  p.x = k;
  p.y = m;
  p.z = n;
  x.xyz = mix (x.xyz, p, z.y);
  mediump float q;
  if ((x.x < 0.5)) {
    q = ((2.0 * x.x) * r.x);
  } else {
    q = (1.0 - ((2.0 * 
      (1.0 - x.x)
    ) * (1.0 - r.x)));
  };
  mediump float s;
  if ((x.y < 0.5)) {
    s = ((2.0 * x.y) * r.y);
  } else {
    s = (1.0 - ((2.0 * 
      (1.0 - x.y)
    ) * (1.0 - r.y)));
  };
  mediump float t;
  if ((x.z < 0.5)) {
    t = ((2.0 * x.z) * r.z);
  } else {
    t = (1.0 - ((2.0 * 
      (1.0 - x.z)
    ) * (1.0 - r.z)));
  };
  mediump vec3 u;
  u.x = q;
  u.y = s;
  u.z = t;
  mediump vec4 v;
  v.w = 1.0;
  v.xyz = u;
  gl_FragColor = v;
}

