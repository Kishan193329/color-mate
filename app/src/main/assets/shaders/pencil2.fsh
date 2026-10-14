uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D indexTex;
uniform sampler2D hatch;
uniform sampler2D disp;
uniform sampler2D background;
uniform highp vec2 randomOffset;
varying highp vec2 texCoord;
void main ()
{
  mediump vec4 r;
  highp vec2 y;
  mediump vec4 g;
  mediump vec4 x;
  mediump vec4 b;
  mediump vec4 z;
  lowp vec4 a;
  highp vec2 _;
  _ = (texCoord * 3.0);
  a = texture2D (background, _);
  z = a;
  lowp vec4 A;
  highp vec2 Y;
  Y = (texCoord + randomOffset);
  A = texture2D (disp, Y);
  b = A;
  lowp vec4 R;
  R = texture2D (outline, texCoord);
  x = R;
  lowp vec4 X;
  X = texture2D (tex, texCoord);
  g = X;
  lowp vec2 W;
  W = texture2D (indexTex, texCoord).xy;
  y = W;
  highp float Z;
  Z = (((y.x * 256.0) + y.y) / 13.0);
  highp float o;
  o = cos((Z + (b.x * 0.001)));
  highp vec2 O;
  O.x = o;
  O.y = sin((Z + (b.x * 0.002)));
  highp vec2 I;
  I.x = O.y;
  I.y = -(o);
  highp vec2 i;
  i = (((
    (texCoord.x - 0.5)
   * O) + (
    (texCoord.y - 0.5)
   * I)) * 1.25);
  lowp vec4 l;
  l = texture2D (hatch, i);
  r = l;
  mediump vec4 S;
  S.w = 1.0;
  S.xyz = (mix (vec3(1.0, 1.0, 1.0), mix (vec3(0.125, 0.125, 0.125), 
    mix (vec3(1.0, 1.0, 1.0), g.xyz, r.y)
  , x.y), x.w) * z.xyz);
  gl_FragColor = S;
}

