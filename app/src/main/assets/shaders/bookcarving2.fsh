uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D basetex;
uniform sampler2D bevel;
uniform sampler2D lut;
uniform highp vec2 randomOffset;
varying highp vec2 texCoord;
varying highp vec2 texCoordTransformed;
uniform mediump vec3 outlineColor;
void main ()
{
  mediump vec4 r;
  mediump vec4 y;
  mediump vec4 g;
  mediump vec4 x;
  lowp vec4 b;
  b = texture2D (tex, texCoord);
  x = b;
  lowp vec4 z;
  z = texture2D (outline, texCoord);
  g = z;
  lowp vec4 a;
  a = texture2D (bevel, texCoord);
  y = a;
  lowp vec4 _;
  highp vec2 A;
  A = (texCoordTransformed + randomOffset);
  _ = texture2D (basetex, A);
  r = _;
  mediump float Y;
  Y = ((0.5 - (
    (max (max (x.x, x.y), x.z) + min (min (x.x, x.y), x.z))
   / 2.0)) * 2.0);
  mediump vec3 R;
  mediump vec4 X;
  X.w = 1.0;
  X.xyz = mix (outlineColor, (x.xyz + (
    (Y - clamp (Y, -0.5, 0.5))
   * 0.125)), g.y);
  highp vec2 W;
  highp vec2 Z;
  mediump vec2 o;
  mediump vec2 O;
  mediump float I;
  I = (X.z * 63.0);
  O.y = floor((floor(I) / 8.0));
  O.x = (floor(I) - (O.y * 8.0));
  o.y = floor((ceil(I) / 8.0));
  o.x = (ceil(I) - (o.y * 8.0));
  Z = (((O * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * X.xy));
  W = (((o * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * X.xy));
  lowp vec4 i;
  i = texture2D (lut, Z);
  mediump vec4 l;
  l = i;
  lowp vec4 S;
  S = texture2D (lut, W);
  mediump vec4 G;
  G = S;
  mediump float B;
  B = clamp (fract(I), 0.0, 1.0);
  R = ((l * (1.0 - B)) + (G * B)).xyz;
  highp vec3 c;
  c = mix (r.xyz, vec3(0.5, 0.5, 0.5), randomOffset.y);
  highp float d;
  if ((R.x < 0.5)) {
    d = ((2.0 * R.x) * c.x);
  } else {
    d = (1.0 - ((2.0 * 
      (1.0 - R.x)
    ) * (1.0 - c.x)));
  };
  highp float e;
  if ((R.y < 0.5)) {
    e = ((2.0 * R.y) * c.y);
  } else {
    e = (1.0 - ((2.0 * 
      (1.0 - R.y)
    ) * (1.0 - c.y)));
  };
  highp float f;
  if ((R.z < 0.5)) {
    f = ((2.0 * R.z) * c.z);
  } else {
    f = (1.0 - ((2.0 * 
      (1.0 - R.z)
    ) * (1.0 - c.z)));
  };
  mediump vec3 h;
  h.x = d;
  h.y = e;
  h.z = f;
  mediump float j;
  if ((y.x < 0.5)) {
    j = max (((h.x + 
      (2.0 * y.x)
    ) - 1.0), 0.0);
  } else {
    j = min ((h.x + (2.0 * 
      (y.x - 0.5)
    )), 1.0);
  };
  mediump float k;
  if ((y.y < 0.5)) {
    k = max (((h.y + 
      (2.0 * y.y)
    ) - 1.0), 0.0);
  } else {
    k = min ((h.y + (2.0 * 
      (y.y - 0.5)
    )), 1.0);
  };
  mediump float m;
  if ((y.z < 0.5)) {
    m = max (((h.z + 
      (2.0 * y.z)
    ) - 1.0), 0.0);
  } else {
    m = min ((h.z + (2.0 * 
      (y.z - 0.5)
    )), 1.0);
  };
  mediump vec3 n;
  n.x = j;
  n.y = k;
  n.z = m;
  mediump vec4 p;
  p.w = 1.0;
  p.xyz = n;
  gl_FragColor = p;
}

