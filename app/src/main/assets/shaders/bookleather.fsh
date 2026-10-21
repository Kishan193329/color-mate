uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D overlay;
uniform sampler2D blurmap3;
uniform sampler2D bevel;
uniform sampler2D lut;
varying highp vec2 texCoord;
uniform mediump vec3 outlineColor;
void main ()
{
  mediump vec4 r;
  mediump vec4 y;
  mediump vec4 g;
  mediump vec4 x;
  mediump vec4 b;
  lowp vec4 z;
  z = texture2D (tex, texCoord);
  b = z;
  lowp vec4 a;
  a = texture2D (outline, texCoord);
  x = a;
  lowp vec4 _;
  _ = texture2D (overlay, texCoord);
  y = _;
  lowp vec4 A;
  A = texture2D (bevel, texCoord);
  r = A;
  if ((outlineColor == vec3(0.0, 0.0, 0.0))) {
    lowp vec4 Y;
    Y = texture2D (blurmap3, texCoord);
    g = Y;
    g = ((g * g) * 0.85);
  } else {
    mediump vec4 R;
    R.w = 1.0;
    R.xyz = outlineColor;
    g = R;
  };
  mediump vec3 X;
  mediump vec4 W;
  W.w = 1.0;
  W.xyz = mix (g.xyz, b.xyz, x.y);
  highp vec2 Z;
  highp vec2 o;
  mediump vec2 O;
  mediump vec2 I;
  mediump float i;
  i = (W.z * 63.0);
  I.y = floor((floor(i) / 8.0));
  I.x = (floor(i) - (I.y * 8.0));
  O.y = floor((ceil(i) / 8.0));
  O.x = (ceil(i) - (O.y * 8.0));
  o = (((I * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * W.xy));
  Z = (((O * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * W.xy));
  lowp vec4 l;
  l = texture2D (lut, o);
  mediump vec4 S;
  S = l;
  lowp vec4 G;
  G = texture2D (lut, Z);
  mediump vec4 B;
  B = G;
  mediump float c;
  c = clamp (fract(i), 0.0, 1.0);
  X = ((S * (1.0 - c)) + (B * c)).xyz;
  mediump float d;
  if ((y.x < 0.5)) {
    d = max (((X.x + 
      (2.0 * y.x)
    ) - 1.0), 0.0);
  } else {
    d = min ((X.x + (2.0 * 
      (y.x - 0.5)
    )), 1.0);
  };
  mediump float e;
  if ((y.y < 0.5)) {
    e = max (((X.y + 
      (2.0 * y.y)
    ) - 1.0), 0.0);
  } else {
    e = min ((X.y + (2.0 * 
      (y.y - 0.5)
    )), 1.0);
  };
  mediump float f;
  if ((y.z < 0.5)) {
    f = max (((X.z + 
      (2.0 * y.z)
    ) - 1.0), 0.0);
  } else {
    f = min ((X.z + (2.0 * 
      (y.z - 0.5)
    )), 1.0);
  };
  mediump float h;
  if ((r.x < 0.5)) {
    h = max (((d + 
      (2.0 * r.x)
    ) - 1.0), 0.0);
  } else {
    h = min ((d + (2.0 * 
      (r.x - 0.5)
    )), 1.0);
  };
  mediump float j;
  if ((r.y < 0.5)) {
    j = max (((e + 
      (2.0 * r.y)
    ) - 1.0), 0.0);
  } else {
    j = min ((e + (2.0 * 
      (r.y - 0.5)
    )), 1.0);
  };
  mediump float k;
  if ((r.z < 0.5)) {
    k = max (((f + 
      (2.0 * r.z)
    ) - 1.0), 0.0);
  } else {
    k = min ((f + (2.0 * 
      (r.z - 0.5)
    )), 1.0);
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

