uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D overlay;
uniform sampler2D overlay2;
uniform sampler2D blurmap1;
uniform sampler2D lut;
varying highp vec2 texCoord;
varying highp vec2 texCoordTransformed;
uniform highp vec2 randomOffset;
uniform highp vec2 effectLevel;
uniform highp float hazeLevel;
uniform mediump vec3 outlineColor;
void main ()
{
  mediump vec4 r;
  mediump vec4 y;
  mediump vec4 g;
  mediump vec4 x;
  lowp vec4 b;
  b = texture2D (tex, texCoord);
  highp vec4 z;
  z.w = 1.0;
  z.xyz = (b.xyz * (1.0 - (hazeLevel * 0.5)));
  x = z;
  lowp vec4 a;
  a = texture2D (outline, texCoord);
  g = a;
  lowp vec4 _;
  highp vec2 A;
  A = (texCoordTransformed + randomOffset);
  _ = texture2D (overlay, A);
  y = _;
  lowp vec4 Y;
  highp vec2 R;
  R = (texCoordTransformed - (randomOffset * 0.5));
  Y = texture2D (overlay2, R);
  r = Y;
  mediump vec4 X;
  X.w = 1.0;
  X.xyz = mix (outlineColor, x.xyz, g.y);
  lowp vec4 W;
  W.w = 1.0;
  W.xyz = texture2D (blurmap1, texCoord).xyz;
  x = (X + ((W * hazeLevel) * 0.5));
  y = (1.0 - ((1.0 - y) * (1.0 - 
    (y * effectLevel.x)
  )));
  mediump vec4 Z;
  Z.w = 1.0;
  Z.xyz = (y.xyz * (1.0 - x.xyz));
  y = Z;
  mediump vec3 o;
  mediump vec4 O;
  O.w = 1.0;
  O.xyz = clamp ((x.xyz + Z.xyz), 0.0, 1.0);
  highp vec2 I;
  highp vec2 i;
  mediump vec2 l;
  mediump vec2 S;
  mediump float G;
  G = (O.z * 63.0);
  S.y = floor((floor(G) / 8.0));
  S.x = (floor(G) - (S.y * 8.0));
  l.y = floor((ceil(G) / 8.0));
  l.x = (ceil(G) - (l.y * 8.0));
  i = (((S * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * O.xy));
  I = (((l * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * O.xy));
  lowp vec4 B;
  B = texture2D (lut, i);
  mediump vec4 c;
  c = B;
  lowp vec4 d;
  d = texture2D (lut, I);
  mediump vec4 e;
  e = d;
  mediump float f;
  f = clamp (fract(G), 0.0, 1.0);
  o = ((c * (1.0 - f)) + (e * f)).xyz;
  mediump float h;
  if ((o.x < 0.5)) {
    h = ((2.0 * o.x) * r.x);
  } else {
    h = (1.0 - ((2.0 * 
      (1.0 - o.x)
    ) * (1.0 - r.x)));
  };
  mediump float j;
  if ((o.y < 0.5)) {
    j = ((2.0 * o.y) * r.y);
  } else {
    j = (1.0 - ((2.0 * 
      (1.0 - o.y)
    ) * (1.0 - r.y)));
  };
  mediump float k;
  if ((o.z < 0.5)) {
    k = ((2.0 * o.z) * r.z);
  } else {
    k = (1.0 - ((2.0 * 
      (1.0 - o.z)
    ) * (1.0 - r.z)));
  };
  mediump vec3 m;
  m.x = h;
  m.y = j;
  m.z = k;
  mediump vec4 n;
  n.w = 1.0;
  n.xyz = m;
  gl_FragColor = n;
}

