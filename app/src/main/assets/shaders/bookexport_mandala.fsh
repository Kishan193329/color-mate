uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D normalShadow;
uniform sampler2D lut;
uniform sampler2D sand;
uniform highp vec2 randomOffset;
varying highp vec2 texCoord;
varying highp vec2 texCoordTransformed;
uniform mediump vec3 outlineColor;
void main ()
{
  highp vec3 r;
  mediump vec4 y;
  mediump vec3 g;
  mediump vec3 x;
  mediump vec4 b;
  lowp vec4 z;
  highp vec2 a;
  a = (texCoordTransformed + randomOffset);
  z = texture2D (sand, a);
  b = z;
  lowp vec3 _;
  _ = texture2D (tex, texCoord).xyz;
  x = _;
  lowp vec3 A;
  A = texture2D (outline, texCoord).xyz;
  g = A;
  lowp vec4 Y;
  Y = texture2D (normalShadow, texCoord);
  y = Y;
  mediump float R;
  R = (y.w * 2.0);
  mediump vec3 X;
  X = mix ((vec3(0.25, 0.25, 0.25) + (outlineColor * 0.5)), x, g.x);
  r = X;
  highp float W;
  W = min (r.x, r.y);
  mediump float Z;
  Z = min (W, r.z);
  highp float o;
  o = max (r.x, r.y);
  mediump float O;
  O = max (o, r.z);
  mediump float I;
  I = ((0.5 - (
    (O + Z)
   / 2.0)) * 2.0);
  mediump vec3 i;
  mediump vec4 l;
  l.w = 1.0;
  l.xyz = (r + ((I - 
    clamp (I, -0.5, 0.5)
  ) * 0.125));
  highp vec2 S;
  highp vec2 G;
  mediump vec2 B;
  mediump vec2 c;
  mediump float d;
  d = (l.z * 63.0);
  c.y = floor((floor(d) / 8.0));
  c.x = (floor(d) - (c.y * 8.0));
  B.y = floor((ceil(d) / 8.0));
  B.x = (ceil(d) - (B.y * 8.0));
  G = (((c * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * l.xy));
  S = (((B * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * l.xy));
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
  i = ((f * (1.0 - k)) + (j * k)).xyz;
  mediump float m;
  if ((b.x < 0.5)) {
    m = max (((i.x + 
      (2.0 * b.x)
    ) - 1.0), 0.0);
  } else {
    m = min ((i.x + (2.0 * 
      (b.x - 0.5)
    )), 1.0);
  };
  mediump float n;
  if ((b.y < 0.5)) {
    n = max (((i.y + 
      (2.0 * b.y)
    ) - 1.0), 0.0);
  } else {
    n = min ((i.y + (2.0 * 
      (b.y - 0.5)
    )), 1.0);
  };
  mediump float p;
  if ((b.z < 0.5)) {
    p = max (((i.z + 
      (2.0 * b.z)
    ) - 1.0), 0.0);
  } else {
    p = min ((i.z + (2.0 * 
      (b.z - 0.5)
    )), 1.0);
  };
  mediump vec3 q;
  q.x = m;
  q.y = n;
  q.z = p;
  r = ((q * R) * 1.3);
  highp vec4 s;
  s.w = 1.0;
  s.xyz = r;
  gl_FragColor = s;
}

