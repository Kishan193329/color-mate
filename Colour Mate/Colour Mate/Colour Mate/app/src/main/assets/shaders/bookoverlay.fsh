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
  highp vec2 g;
  g = (texCoordTransformed + randomOffset);
  mediump vec3 x;
  x = mix (outlineColor, r.xyz, y.y);
  mediump float b;
  b = ((0.5 - (
    (max (max (x.x, x.y), x.z) + min (min (x.x, x.y), x.z))
   / 2.0)) * 2.0);
  mediump vec3 z;
  mediump vec4 a;
  a.w = 1.0;
  a.xyz = (x + ((b - 
    clamp (b, -0.5, 0.5)
  ) * 0.125));
  highp vec2 _;
  highp vec2 A;
  mediump vec2 Y;
  mediump vec2 R;
  mediump float X;
  X = (a.z * 63.0);
  R.y = floor((floor(X) / 8.0));
  R.x = (floor(X) - (R.y * 8.0));
  Y.y = floor((ceil(X) / 8.0));
  Y.x = (ceil(X) - (Y.y * 8.0));
  A = (((R * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * a.xy));
  _ = (((Y * vec2(0.125, 0.125)) + vec2(0.0009765625, 0.0009765625)) + (vec2(0.1230469, 0.1230469) * a.xy));
  lowp vec4 W;
  W = texture2D (lut, A);
  mediump vec4 Z;
  Z = W;
  lowp vec4 o;
  o = texture2D (lut, _);
  mediump vec4 O;
  O = o;
  mediump float I;
  I = clamp (fract(X), 0.0, 1.0);
  z = ((Z * (1.0 - I)) + (O * I)).xyz;
  lowp vec3 i;
  i = texture2D (overlay, g).xyz;
  mediump float l;
  if ((i.x < 0.5)) {
    l = ((2.0 * i.x) * z.x);
  } else {
    l = (1.0 - ((2.0 * 
      (1.0 - i.x)
    ) * (1.0 - z.x)));
  };
  mediump float S;
  if ((i.y < 0.5)) {
    S = ((2.0 * i.y) * z.y);
  } else {
    S = (1.0 - ((2.0 * 
      (1.0 - i.y)
    ) * (1.0 - z.y)));
  };
  mediump float G;
  if ((i.z < 0.5)) {
    G = ((2.0 * i.z) * z.z);
  } else {
    G = (1.0 - ((2.0 * 
      (1.0 - i.z)
    ) * (1.0 - z.z)));
  };
  mediump vec3 B;
  B.x = l;
  B.y = S;
  B.z = G;
  mediump vec4 c;
  c.w = 1.0;
  c.xyz = B;
  gl_FragColor = c;
}

