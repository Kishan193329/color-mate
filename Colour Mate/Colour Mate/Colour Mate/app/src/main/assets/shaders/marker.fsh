uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D preprocessed;
uniform sampler2D noise;
uniform sampler2D hatch;
uniform sampler2D background;
varying highp vec2 texCoord;
void main ()
{
  mediump vec4 r;
  highp vec2 y;
  mediump vec4 g;
  mediump vec4 x;
  mediump vec4 b;
  mediump vec4 z;
  mediump vec4 a;
  lowp vec4 _;
  highp vec2 A;
  A = (texCoord * 3.0);
  _ = texture2D (background, A);
  a = _;
  lowp vec4 Y;
  highp vec2 R;
  R = (texCoord * 2.25);
  Y = texture2D (noise, R);
  z = Y;
  lowp vec4 X;
  X = texture2D (preprocessed, texCoord);
  b = X;
  lowp vec4 W;
  W = texture2D (outline, texCoord);
  x = W;
  lowp vec4 Z;
  Z = texture2D (tex, texCoord);
  g = Z;
  mediump vec2 o;
  o = normalize((g.xy - g.zz));
  y = o;
  highp vec2 O;
  O.x = y.y;
  O.y = -(y.x);
  highp vec2 I;
  I = (((
    (texCoord.x + 10.0)
   * y) + (
    (texCoord.y + 10.0)
   * O)) * 5.0);
  lowp vec4 i;
  i = texture2D (hatch, I);
  r = i;
  g = Z;
  g = (1.0 - ((1.0 - g) * 1.612903));
  mediump vec4 l;
  l.w = 1.0;
  l.xyz = (mix ((
    mix (g.xyz, vec3(1.0, 1.0, 1.0), (r.y + ((1.0 - 
      pow (b.y, 4.0)
    ) * z.y)))
   * 
    (1.0 + (x.y - 1.0))
  ), vec3(1.0, 1.0, 1.0), (1.0 - x.w)) * a.xyz);
  gl_FragColor = l;
}

