uniform sampler2D blurmap0;
uniform sampler2D outline;
uniform sampler2D bg;
varying highp vec2 texCoord;
void main ()
{
  mediump vec4 r;
  mediump vec4 y;
  mediump vec4 g;
  lowp vec4 x;
  x = texture2D (outline, texCoord);
  g = x;
  lowp vec4 b;
  b = texture2D (bg, texCoord);
  y = b;
  lowp vec4 z;
  z = texture2D (blurmap0, texCoord);
  r = z;
  highp vec4 a;
  mediump vec4 _;
  _.w = min (min ((1.0 - r.x), (1.0 - r.y)), (1.0 - r.z));
  _.x = (((1.0 - r.x) - _.w) / (1.0 - _.w));
  _.y = (((1.0 - r.y) - _.w) / (1.0 - _.w));
  _.z = (((1.0 - r.z) - _.w) / (1.0 - _.w));
  highp vec2 A;
  highp float Y;
  Y = (texCoord.x * 0.9659258);
  A.x = (Y + (texCoord.y * 0.258819));
  highp float R;
  R = (texCoord.x * 0.258819);
  A.y = (R + (texCoord.y * -0.9659258));
  mediump float X;
  highp vec2 W;
  W = (fract((80.0 * A)) - vec2(0.5, 0.5));
  highp float Z;
  Z = clamp ((1.3 * sqrt(
    dot (W, W)
  )), 0.1, 1.0);
  X = Z;
  highp vec2 o;
  o.x = (R + (texCoord.y * 0.9659258));
  o.y = (Y + (texCoord.y * -0.258819));
  mediump float O;
  highp vec2 I;
  I = (fract((80.0 * o)) - vec2(0.5, 0.5));
  highp float i;
  i = clamp ((1.3 * sqrt(
    dot (I, I)
  )), 0.1, 1.0);
  O = i;
  mediump float l;
  highp vec2 S;
  S = (fract((80.0 * texCoord)) - vec2(0.5, 0.5));
  highp float G;
  G = clamp ((1.3 * sqrt(
    dot (S, S)
  )), 0.1, 1.0);
  l = G;
  highp vec2 B;
  highp float c;
  c = (texCoord.x * 0.7071068);
  B.x = (c + (texCoord.y * 0.7071068));
  B.y = (c + (texCoord.y * -0.7071068));
  mediump float d;
  highp vec2 e;
  e = (fract((80.0 * B)) - vec2(0.5, 0.5));
  highp float f;
  f = clamp ((1.3 * sqrt(
    dot (e, e)
  )), 0.1, 1.0);
  d = f;
  mediump vec4 h;
  h.x = X;
  h.y = O;
  h.z = l;
  h.w = d;
  a = h;
  highp vec4 j;
  highp vec4 k;
  k = clamp (((vec4(0.5, 0.5, 0.5, 0.5) + 
    ((_ - a) * 25.0)
  ) / 17.0), 0.0, 1.0);
  j = (k * (k * (3.0 - 
    (2.0 * k)
  )));
  _ = (j * 17.0);
  mediump vec4 m;
  m = clamp (_, 0.0, 1.0);
  _ = m;
  mediump vec3 n;
  n.x = (1.0 - min (1.0, (
    (m.x * (1.0 - m.w))
   + m.w)));
  n.y = (1.0 - min (1.0, (
    (m.y * (1.0 - m.w))
   + m.w)));
  n.z = (1.0 - min (1.0, (
    (m.z * (1.0 - m.w))
   + m.w)));
  mediump vec4 p;
  p.w = 1.0;
  p.xyz = ((n * g.y) * y.xyz);
  gl_FragColor = p;
}

