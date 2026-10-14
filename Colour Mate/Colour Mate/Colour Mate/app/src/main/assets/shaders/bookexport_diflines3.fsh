uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D basetex;
uniform sampler2D blurmap3;
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
  lowp vec4 x;
  x = texture2D (outline, texCoord);
  g = x;
  lowp vec4 b;
  b = texture2D (tex, texCoord);
  y = b;
  lowp vec4 z;
  highp vec2 a;
  a = (texCoordTransformed + randomOffset);
  z = texture2D (basetex, a);
  r = z;
  if ((outlineColor == vec3(0.0, 0.0, 0.0))) {
    mediump vec4 _;
    lowp vec4 A;
    A = texture2D (blurmap3, texCoord);
    _ = A;
    y = mix (y, _, (1.0 - g.y));
  } else {
    mediump vec4 Y;
    Y.w = 1.0;
    Y.xyz = outlineColor;
    y = mix (y, Y, (1.0 - g.y));
  };
  mediump float R;
  R = ((0.5 - (
    (max (max (y.x, y.y), y.z) + min (min (y.x, y.y), y.z))
   / 2.0)) * 2.0);
  mediump vec3 X;
  mediump vec4 W;
  W.w = 1.0;
  W.xyz = (y.xyz + ((R - 
    clamp (R, -0.5, 0.5)
  ) * 0.125));
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
  if ((r.x < 0.5)) {
    d = max (((X.x + 
      (2.0 * r.x)
    ) - 1.0), 0.0);
  } else {
    d = min ((X.x + (2.0 * 
      (r.x - 0.5)
    )), 1.0);
  };
  mediump float e;
  if ((r.y < 0.5)) {
    e = max (((X.y + 
      (2.0 * r.y)
    ) - 1.0), 0.0);
  } else {
    e = min ((X.y + (2.0 * 
      (r.y - 0.5)
    )), 1.0);
  };
  mediump float f;
  if ((r.z < 0.5)) {
    f = max (((X.z + 
      (2.0 * r.z)
    ) - 1.0), 0.0);
  } else {
    f = min ((X.z + (2.0 * 
      (r.z - 0.5)
    )), 1.0);
  };
  mediump vec3 h;
  h.x = d;
  h.y = e;
  h.z = f;
  mediump vec4 j;
  j.w = 1.0;
  j.xyz = h;
  gl_FragColor = j;
}

