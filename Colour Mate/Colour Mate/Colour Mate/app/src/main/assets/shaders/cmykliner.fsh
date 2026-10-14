uniform sampler2D blurmap0;
uniform sampler2D outline;
uniform sampler2D bg;
varying highp vec2 texCoord;
uniform highp vec2 randomOffset;
void main ()
{
  mediump vec4 r;
  mediump vec4 y;
  mediump vec4 g;
  lowp vec4 x;
  x = texture2D (outline, texCoord);
  g = x;
  lowp vec4 b;
  highp vec2 z;
  z = (texCoord * 2.0);
  b = texture2D (bg, z);
  y = b;
  lowp vec4 a;
  a = texture2D (blurmap0, texCoord);
  r = a;
  mediump vec4 _;
  _.w = min (min ((1.0 - r.x), (1.0 - r.y)), (1.0 - r.z));
  _.x = (((1.0 - r.x) - _.w) / (1.0 - _.w));
  _.y = (((1.0 - r.y) - _.w) / (1.0 - _.w));
  _.z = (((1.0 - r.z) - _.w) / (1.0 - _.w));
  highp vec2 A;
  A = (vec2(0.1, 0.1) - texCoord);
  highp vec2 Y;
  Y = (vec2(0.9, 0.2) - texCoord);
  highp vec2 R;
  R = (vec2(0.25, 0.75) - texCoord);
  highp vec2 X;
  X = ((vec2(0.5, 0.5) + (randomOffset * 0.5)) - texCoord);
  highp vec4 W;
  W.x = (1.0 - (abs(
    (fract((sqrt(
      dot (A, A)
    ) * 140.0)) - 0.5)
  ) * 2.0));
  W.y = (1.0 - (abs(
    (fract((sqrt(
      dot (Y, Y)
    ) * 140.0)) - 0.5)
  ) * 2.0));
  W.z = (1.0 - (abs(
    (fract((sqrt(
      dot (R, R)
    ) * 140.0)) - 0.5)
  ) * 2.0));
  W.w = (1.0 - (abs(
    (fract((sqrt(
      dot (X, X)
    ) * 140.0)) - 0.5)
  ) * 2.0));
  highp vec4 Z;
  highp vec4 o;
  o = clamp ((vec4(0.5, 0.5, 0.5, 0.5) + (
    (_ - W)
   * 3.0)), 0.0, 1.0);
  Z = (o * (o * (3.0 - 
    (2.0 * o)
  )));
  _ = Z;
  mediump vec4 O;
  O = clamp (_, 0.0, 1.0);
  _ = O;
  mediump vec3 I;
  I.x = (1.0 - min (1.0, (
    (O.x * (1.0 - O.w))
   + O.w)));
  I.y = (1.0 - min (1.0, (
    (O.y * (1.0 - O.w))
   + O.w)));
  I.z = (1.0 - min (1.0, (
    (O.z * (1.0 - O.w))
   + O.w)));
  mediump vec4 i;
  i.w = 1.0;
  i.xyz = ((I * g.y) * y.xyz);
  gl_FragColor = i;
}

