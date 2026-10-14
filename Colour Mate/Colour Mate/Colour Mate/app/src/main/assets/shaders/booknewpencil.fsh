uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D overlay;
uniform sampler2D background;
uniform sampler2D blurmap1;
uniform sampler2D blurmap2;
uniform sampler2D noise;
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
  mediump vec4 x;
  mediump vec4 b;
  mediump vec4 z;
  mediump vec4 a;
  lowp vec4 _;
  _ = texture2D (tex, texCoord);
  a = _;
  lowp vec4 A;
  A = texture2D (outline, texCoord);
  z = A;
  lowp vec4 Y;
  Y = texture2D (blurmap1, texCoord);
  b = Y;
  lowp vec4 R;
  R = texture2D (blurmap2, texCoord);
  x = R;
  lowp vec4 X;
  highp vec2 W;
  W = (texCoord * 2.25);
  X = texture2D (noise, W);
  g = X;
  lowp vec4 Z;
  highp vec2 o;
  o = (texCoordTransformed + randomOffset);
  Z = texture2D (overlay, o);
  y.w = Z.w;
  highp vec2 O;
  O = (texCoordTransformed - (randomOffset * 0.5));
  lowp vec3 I;
  I = texture2D (background, O).xyz;
  r = I;
  lowp vec4 i;
  highp vec2 l;
  l = (texCoordTransformed - (randomOffset * 0.75));
  i = texture2D (overlay, l);
  lowp vec4 S;
  highp vec2 G;
  G = (texCoordTransformed + (randomOffset * 0.25));
  S = texture2D (overlay, G);
  lowp vec4 B;
  highp vec2 c;
  c = (texCoordTransformed - (randomOffset * 0.37));
  B = texture2D (overlay, c);
  lowp vec4 d;
  highp vec2 e;
  e = (texCoordTransformed + (randomOffset * 0.66));
  d = texture2D (overlay, e);
  mediump vec4 f;
  f.w = 1.0;
  f.xyz = mix (outlineColor, a.xyz, z.y);
  highp vec2 h;
  highp vec2 j;
  mediump vec2 k;
  mediump vec2 m;
  mediump float n;
  n = (f.z * 63.0);
  m.y = floor((floor(n) / 8.0));
  m.x = (floor(n) - (m.y * 8.0));
  k.y = floor((ceil(n) / 8.0));
  k.x = (ceil(n) - (k.y * 8.0));
  j = (((m * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * f.xy));
  h = (((k * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * f.xy));
  lowp vec4 p;
  p = texture2D (lut, j);
  mediump vec4 q;
  q = p;
  lowp vec4 s;
  s = texture2D (lut, h);
  mediump vec4 t;
  t = s;
  mediump float u;
  u = clamp (fract(n), 0.0, 1.0);
  a.xyz = ((q * (1.0 - u)) + (t * u)).xyz;
  mediump vec3 v;
  v = clamp (((
    ((Z.xyz * (vec3(1.0, 1.0, 1.0) - (i.xyz * 
      (((1.0 - b.w) * 0.8) * g.y)
    ))) * (vec3(1.0, 1.0, 1.0) - (S.xyz * (
      ((1.0 - b.w) * 0.8)
     * g.y))))
   * 
    (vec3(1.0, 1.0, 1.0) - (B.xyz * ((
      (1.0 - x.w)
     * 0.8) * g.y)))
  ) * (vec3(1.0, 1.0, 1.0) - 
    (d.xyz * (((1.0 - x.w) * 0.8) * g.y))
  )), 0.5, 1.0);
  y.xyz = v;
  mediump float C;
  if ((y.x < 0.5)) {
    C = ((2.0 * y.x) * a.x);
  } else {
    C = (1.0 - ((2.0 * 
      (1.0 - y.x)
    ) * (1.0 - a.x)));
  };
  mediump float D;
  if ((y.y < 0.5)) {
    D = ((2.0 * y.y) * a.y);
  } else {
    D = (1.0 - ((2.0 * 
      (1.0 - y.y)
    ) * (1.0 - a.y)));
  };
  mediump float E;
  if ((y.z < 0.5)) {
    E = ((2.0 * y.z) * a.z);
  } else {
    E = (1.0 - ((2.0 * 
      (1.0 - y.z)
    ) * (1.0 - a.z)));
  };
  mediump vec3 F;
  F.x = C;
  F.y = D;
  F.z = E;
  a.xyz = mix (a.xyz, F, z.y);
  mediump float H;
  if ((a.x < 0.5)) {
    H = ((2.0 * a.x) * r.x);
  } else {
    H = (1.0 - ((2.0 * 
      (1.0 - a.x)
    ) * (1.0 - r.x)));
  };
  mediump float J;
  if ((a.y < 0.5)) {
    J = ((2.0 * a.y) * r.y);
  } else {
    J = (1.0 - ((2.0 * 
      (1.0 - a.y)
    ) * (1.0 - r.y)));
  };
  mediump float K;
  if ((a.z < 0.5)) {
    K = ((2.0 * a.z) * r.z);
  } else {
    K = (1.0 - ((2.0 * 
      (1.0 - a.z)
    ) * (1.0 - r.z)));
  };
  mediump vec3 L;
  L.x = H;
  L.y = J;
  L.z = K;
  mediump vec4 M;
  M.w = 1.0;
  M.xyz = L;
  gl_FragColor = M;
}

