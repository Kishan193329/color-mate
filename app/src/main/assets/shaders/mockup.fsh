uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D lut;
uniform sampler2D base;
uniform sampler2D uv;
uniform highp vec3 outlineColor;
varying highp vec2 texCoord;
void main ()
{
  mediump vec4 r;
  mediump vec4 y;
  mediump vec4 g;
  mediump vec4 x;
  mediump vec4 b;
  lowp vec4 z;
  z = texture2D (base, texCoord);
  b = z;
  lowp vec4 a;
  a = texture2D (uv, texCoord);
  x = a;
  mediump vec2 _;
  _.x = (x.x + ((
    (float(mod (x.z, 0.125)))
   * 8.0) / 256.0));
  _.y = ((1.0 - x.y) + ((
    (float(mod (x.z, 0.015625)))
   * 64.0) / 256.0));
  lowp vec4 A;
  A = texture2D (tex, _);
  g = A;
  lowp vec4 Y;
  Y = texture2D (outline, _);
  y = Y;
  highp vec4 R;
  R.w = 1.0;
  R.xyz = mix (outlineColor, g.xyz, y.y);
  r = R;
  mediump vec3 X;
  mediump float W;
  if ((r.x < 0.5)) {
    W = ((2.0 * r.x) * b.x);
  } else {
    W = (1.0 - ((2.0 * 
      (1.0 - r.x)
    ) * (1.0 - b.x)));
  };
  mediump float Z;
  if ((r.y < 0.5)) {
    Z = ((2.0 * r.y) * b.y);
  } else {
    Z = (1.0 - ((2.0 * 
      (1.0 - r.y)
    ) * (1.0 - b.y)));
  };
  mediump float o;
  if ((r.z < 0.5)) {
    o = ((2.0 * r.z) * b.z);
  } else {
    o = (1.0 - ((2.0 * 
      (1.0 - r.z)
    ) * (1.0 - b.z)));
  };
  mediump vec3 O;
  O.x = W;
  O.y = Z;
  O.z = o;
  X = ((O * x.w) + (b.xyz * (1.0 - x.w)));
  highp vec2 I;
  highp vec2 i;
  mediump vec2 l;
  mediump vec2 S;
  mediump float G;
  G = (X.z * 63.0);
  S.y = floor((floor(G) / 8.0));
  S.x = (floor(G) - (S.y * 8.0));
  l.y = floor((ceil(G) / 8.0));
  l.x = (ceil(G) - (l.y * 8.0));
  i = (((S * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * X.xy));
  I = (((l * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * X.xy));
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
  gl_FragColor = ((c * (1.0 - f)) + (e * f));
}

