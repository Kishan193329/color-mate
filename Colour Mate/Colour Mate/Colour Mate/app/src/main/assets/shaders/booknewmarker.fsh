uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D overlay;
uniform sampler2D background;
uniform sampler2D indexTex;
uniform sampler2D lut;
varying highp vec2 texCoord;
varying highp vec2 texCoordTransformed;
uniform highp vec2 randomOffset;
uniform highp float markerMode;
uniform mediump vec3 outlineColor;
void main ()
{
  mediump vec3 r;
  mediump vec4 y;
  mediump vec4 g;
  mediump vec4 x;
  lowp vec4 b;
  b = texture2D (tex, texCoord);
  g = b;
  lowp vec4 z;
  z = texture2D (outline, texCoord);
  y = z;
  highp vec2 a;
  a = (texCoordTransformed - (randomOffset * 0.5));
  lowp vec3 _;
  _ = texture2D (background, a).xyz;
  r = _;
  mediump vec4 A;
  A.w = 1.0;
  A.xyz = mix (outlineColor, g.xyz, y.y);
  highp vec2 Y;
  highp vec2 R;
  mediump vec2 X;
  mediump vec2 W;
  mediump float Z;
  Z = (A.z * 63.0);
  W.y = floor((floor(Z) / 8.0));
  W.x = (floor(Z) - (W.y * 8.0));
  X.y = floor((ceil(Z) / 8.0));
  X.x = (ceil(Z) - (X.y * 8.0));
  R = (((W * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * A.xy));
  Y = (((X * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * A.xy));
  lowp vec4 o;
  o = texture2D (lut, R);
  mediump vec4 O;
  O = o;
  lowp vec4 I;
  I = texture2D (lut, Y);
  mediump vec4 i;
  i = I;
  mediump float l;
  l = clamp (fract(Z), 0.0, 1.0);
  g.xyz = ((O * (1.0 - l)) + (i * l)).xyz;
  if ((markerMode > 0.5)) {
    mediump vec2 S;
    mediump vec2 G;
    lowp vec2 B;
    B = texture2D (indexTex, texCoord).xy;
    G = B;
    mediump float c;
    c = (((G.x * 256.0) + G.y) / 13.0);
    mediump float d;
    d = cos(c);
    mediump vec2 e;
    e.x = d;
    e.y = sin(c);
    mediump vec2 f;
    f.x = e.y;
    f.y = -(d);
    S = ((texCoordTransformed.x * e) + (texCoordTransformed.y * f));
    lowp vec4 h;
    highp vec2 j;
    j = (S + randomOffset);
    h = texture2D (overlay, j);
    x = h;
  } else {
    lowp vec4 k;
    highp vec2 m;
    m = (texCoordTransformed + randomOffset);
    k = texture2D (overlay, m);
    x = k;
  };
  mediump float n;
  if ((g.x < 0.5)) {
    n = ((2.0 * g.x) * x.x);
  } else {
    n = (1.0 - ((2.0 * 
      (1.0 - g.x)
    ) * (1.0 - x.x)));
  };
  mediump float p;
  if ((g.y < 0.5)) {
    p = ((2.0 * g.y) * x.y);
  } else {
    p = (1.0 - ((2.0 * 
      (1.0 - g.y)
    ) * (1.0 - x.y)));
  };
  mediump float q;
  if ((g.z < 0.5)) {
    q = ((2.0 * g.z) * x.z);
  } else {
    q = (1.0 - ((2.0 * 
      (1.0 - g.z)
    ) * (1.0 - x.z)));
  };
  mediump vec3 s;
  s.x = n;
  s.y = p;
  s.z = q;
  g.xyz = mix (g.xyz, s, y.y);
  mediump float t;
  if ((g.x < 0.5)) {
    t = ((2.0 * g.x) * r.x);
  } else {
    t = (1.0 - ((2.0 * 
      (1.0 - g.x)
    ) * (1.0 - r.x)));
  };
  mediump float u;
  if ((g.y < 0.5)) {
    u = ((2.0 * g.y) * r.y);
  } else {
    u = (1.0 - ((2.0 * 
      (1.0 - g.y)
    ) * (1.0 - r.y)));
  };
  mediump float v;
  if ((g.z < 0.5)) {
    v = ((2.0 * g.z) * r.z);
  } else {
    v = (1.0 - ((2.0 * 
      (1.0 - g.z)
    ) * (1.0 - r.z)));
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

