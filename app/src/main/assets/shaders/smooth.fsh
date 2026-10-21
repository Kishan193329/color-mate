uniform sampler2D outline;
varying highp vec2 texCoord;
void main ()
{
  highp vec2 y;
  highp float g;
  highp vec4 x;
  x = vec4(0.0, 0.0, 0.0, 0.0);
  g = 0.0;
  highp vec2 b;
  b.x = 0.0004882812;
  b.y = 0.0004882812;
  y = b;
  for (highp int r = -4; r <= 4; r++) {
    for (highp int z = -4; z <= 4; z++) {
      highp float a;
      a = exp((-(
        float(((z * z) + (r * r)))
      ) * 0.05));
      highp vec2 _;
      _.x = float(z);
      _.y = float(r);
      lowp vec4 A;
      highp vec2 Y;
      Y = (texCoord + (y * _));
      A = texture2D (outline, Y);
      x = (x + (A * a));
      g = (g + a);
    };
  };
  gl_FragColor = (x / g);
}

