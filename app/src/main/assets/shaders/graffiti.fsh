uniform sampler2D tex;
uniform sampler2D outline;
uniform sampler2D background;
varying highp vec2 texCoord;
void main ()
{
  mediump vec4 r;
  mediump vec4 y;
  mediump vec4 g;
  lowp vec4 x;
  x = texture2D (background, texCoord);
  g = x;
  lowp vec4 b;
  highp vec2 z;
  z = (texCoord + ((g.xy - vec2(0.5, 0.5)) * 0.025));
  b = texture2D (tex, z);
  y = b;
  lowp vec4 a;
  highp vec2 _;
  _ = (texCoord + ((g.xy - vec2(0.5, 0.5)) * 0.025));
  a = texture2D (outline, _);
  r = a;
  mediump vec4 A;
  A = mix ((r * y), vec4(0.5, 0.5, 0.5, 0.5), (1.0 - r.w));
  mediump float Y;
  if ((g.x < 0.5)) {
    Y = ((2.0 * g.x) * A.x);
  } else {
    Y = (1.0 - ((2.0 * 
      (1.0 - g.x)
    ) * (1.0 - A.x)));
  };
  mediump float R;
  if ((g.y < 0.5)) {
    R = ((2.0 * g.y) * A.y);
  } else {
    R = (1.0 - ((2.0 * 
      (1.0 - g.y)
    ) * (1.0 - A.y)));
  };
  mediump float X;
  if ((g.z < 0.5)) {
    X = ((2.0 * g.z) * A.z);
  } else {
    X = (1.0 - ((2.0 * 
      (1.0 - g.z)
    ) * (1.0 - A.z)));
  };
  mediump vec4 W;
  W.w = 1.0;
  W.x = Y;
  W.y = R;
  W.z = X;
  gl_FragColor = W;
}

