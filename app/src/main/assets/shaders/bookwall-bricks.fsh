uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D overlay;
uniform sampler2D lut;
varying highp vec2 texCoord;
varying highp vec2 texCoordTransformed;
uniform highp vec2 randomOffset;
uniform mediump vec3 outlineColor;
void main ()
{
  lowp vec4 r;
  r = texture2D (tex, texCoord);
  lowp vec4 y;
  y = texture2D (outline, texCoord);
  lowp vec4 g;
  highp vec2 x;
  x = (texCoordTransformed + randomOffset);
  g = texture2D (overlay, x);
  mediump vec4 b;
  b.w = 1.0;
  b.xyz = mix (outlineColor, r.xyz, y.y);
  mediump vec4 z;
  highp vec2 a;
  highp vec2 _;
  mediump vec2 A;
  mediump vec2 Y;
  mediump float R;
  R = (b.z * 63.0);
  Y.y = floor((floor(R) / 8.0));
  Y.x = (floor(R) - (Y.y * 8.0));
  A.y = floor((ceil(R) / 8.0));
  A.x = (ceil(R) - (A.y * 8.0));
  _ = (((Y * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * b.xy));
  a = (((A * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * b.xy));
  lowp vec4 X;
  X = texture2D (lut, _);
  mediump vec4 W;
  W = X;
  lowp vec4 Z;
  Z = texture2D (lut, a);
  mediump vec4 o;
  o = Z;
  mediump float O;
  O = clamp (fract(R), 0.0, 1.0);
  z = ((W * (1.0 - O)) + (o * O));
  mediump vec3 I;
  mediump float i;
  i = ((0.5 - (
    (max (max (z.x, z.y), z.z) + min (min (z.x, z.y), z.z))
   / 2.0)) * 2.0);
  I = (z.xyz + ((i - 
    clamp (i, -0.5, 0.5)
  ) * 0.125));
  lowp vec3 l;
  l = g.xyz;
  lowp vec3 S;
  mediump float G;
  G = ((max (
    max (g.x, g.y)
  , g.z) + min (
    min (g.x, g.y)
  , g.z)) / 2.0);
  if ((G > 0.75)) {
    lowp float B;
    mediump float c;
    c = ((G - 0.75) * 0.5);
    B = c;
    S = (g.xyz - vec3(B));
  } else {
    if ((G < 0.5)) {
      lowp float d;
      mediump float e;
      e = ((0.5 - G) * 0.5);
      d = e;
      S = (g.xyz + vec3(d));
    } else {
      S = l;
    };
  };
  mediump float f;
  if ((S.x < 0.5)) {
    f = max (((I.x + 
      (2.0 * S.x)
    ) - 1.0), 0.0);
  } else {
    f = min ((I.x + (2.0 * 
      (S.x - 0.5)
    )), 1.0);
  };
  mediump float h;
  if ((S.y < 0.5)) {
    h = max (((I.y + 
      (2.0 * S.y)
    ) - 1.0), 0.0);
  } else {
    h = min ((I.y + (2.0 * 
      (S.y - 0.5)
    )), 1.0);
  };
  mediump float j;
  if ((S.z < 0.5)) {
    j = max (((I.z + 
      (2.0 * S.z)
    ) - 1.0), 0.0);
  } else {
    j = min ((I.z + (2.0 * 
      (S.z - 0.5)
    )), 1.0);
  };
  mediump float k;
  if ((g.x < 0.5)) {
    k = ((2.0 * g.x) * f);
  } else {
    k = (1.0 - ((2.0 * 
      (1.0 - g.x)
    ) * (1.0 - f)));
  };
  mediump float m;
  if ((g.y < 0.5)) {
    m = ((2.0 * g.y) * h);
  } else {
    m = (1.0 - ((2.0 * 
      (1.0 - g.y)
    ) * (1.0 - h)));
  };
  mediump float n;
  if ((g.z < 0.5)) {
    n = ((2.0 * g.z) * j);
  } else {
    n = (1.0 - ((2.0 * 
      (1.0 - g.z)
    ) * (1.0 - j)));
  };
  mediump vec3 p;
  p.x = k;
  p.y = m;
  p.z = n;
  mediump vec4 q;
  q.w = 1.0;
  q.xyz = p;
  gl_FragColor = q;
}

