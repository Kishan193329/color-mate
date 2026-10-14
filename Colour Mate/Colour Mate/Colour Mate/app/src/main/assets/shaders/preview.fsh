uniform sampler2D tex;
uniform sampler2D texPrev;
uniform sampler2D outline;
uniform sampler2D noise;
varying highp vec2 texCoord;
uniform mediump vec4 reveal;
void main ()
{
  mediump vec4 r;
  mediump float y;
  mediump vec4 g;
  lowp vec4 x;
  x = texture2D (tex, texCoord);
  g = x;
  highp vec2 b;
  b = (texCoord - reveal.xy);
  lowp vec4 z;
  highp vec2 a;
  a = (reveal.yx + texCoord);
  z = texture2D (noise, a);
  highp float _;
  _ = ((sqrt(
    dot (b, b)
  ) * 0.5) + (z.x * 0.5));
  y = _;
  mediump float A;
  A = (y - 0.1);
  mediump float Y;
  Y = clamp (((
    sqrt((reveal.w * reveal.z))
   - A) / (
    (y + 0.1)
   - A)), 0.0, 1.0);
  lowp vec4 R;
  R = texture2D (texPrev, texCoord);
  mediump vec4 X;
  X = mix (R, g, (Y * (Y * 
    (3.0 - (2.0 * Y))
  )));
  g = X;
  lowp vec4 W;
  W = texture2D (outline, texCoord);
  r = W;
  mediump vec4 Z;
  Z.xyz = (X.xyz * r.x);
  Z.w = (r.w + X.w);
  gl_FragColor = Z;
}

