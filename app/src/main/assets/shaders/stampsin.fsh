uniform sampler2D tex;
uniform sampler2D outline;
uniform highp vec2 randomOffset;
varying highp vec2 texCoord;
void main ()
{
  mediump float r;
  highp float y;
  mediump vec4 g;
  mediump vec4 x;
  mediump vec4 b;
  lowp vec4 z;
  z = texture2D (outline, texCoord);
  b = z;
  lowp vec4 a;
  a = texture2D (tex, texCoord);
  x = a;
  mediump vec4 _;
  _.xyz = vec3(0.0, 0.0, 0.0);
  _.w = (1.0 - ((
    (x.x + x.y)
   + x.z) * 0.33));
  mediump float A;
  A = max (0.0, _.w);
  y = A;
  highp float Y;
  highp vec2 R;
  R = (texCoord - vec2(0.15, 0.15));
  highp vec2 X;
  X = (texCoord - (vec2(0.5, 0.25) + (randomOffset * 0.5)));
  Y = (cos((
    sqrt(dot (R, R))
   * 1200.0)) + cos((
    sqrt(dot (X, X))
   * 1200.0)));
  highp vec2 W;
  W = (texCoord - vec2(0.15, 0.85));
  Y = (Y + cos((
    sqrt(dot (W, W))
   * 1200.0)));
  highp float Z;
  Z = clamp (((
    (y - clamp (((Y / 6.0) + 0.5), 0.0, 1.0))
   * 6.0) + 0.5), 0.0, 1.0);
  r = Z;
  g = ((_ / (y + 1e-05)) * r);
  mediump vec3 o;
  o.x = (1.0 - min (1.0, (
    (g.x * (1.0 - g.w))
   + g.w)));
  o.y = (1.0 - min (1.0, (
    (g.y * (1.0 - g.w))
   + g.w)));
  o.z = (1.0 - min (1.0, (
    (g.z * (1.0 - g.w))
   + g.w)));
  mediump vec4 O;
  O.w = 1.0;
  O.xyz = (o * b.y);
  gl_FragColor = O;
}

